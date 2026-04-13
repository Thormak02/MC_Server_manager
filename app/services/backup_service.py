from __future__ import annotations

import shutil
import zipfile
from datetime import datetime
from pathlib import Path

from sqlalchemy import desc, select
from sqlalchemy.orm import Session

from app.models.backup import Backup
from app.models.restore_history import RestoreHistory
from app.models.server import Server
from app.services import audit_service
from app.services.app_setting_service import get_backup_storage_root
from app.services.process_service import is_running, send_console_command, start_server, stop_server


_BACKUP_SCOPES = {"full", "world", "config"}
_PRE_ACTIONS = {"none", "save_all", "stop_start"}


def _safe_name(value: str) -> str:
    sanitized = "".join(ch if ch.isalnum() or ch in {"-", "_", "."} else "-" for ch in value.strip())
    sanitized = sanitized.strip("-_.")
    return sanitized or "backup"


def _server_base(server: Server) -> Path:
    path = Path(server.base_path).expanduser().resolve()
    if not path.exists() or not path.is_dir():
        raise ValueError(f"Serverordner nicht gefunden: {path}")
    return path


def _backup_folder_for_server(db: Session, server: Server) -> Path:
    root = get_backup_storage_root(db)
    root.mkdir(parents=True, exist_ok=True)
    target = (root / f"server-{server.id}-{_safe_name(server.slug)}").resolve()
    target.mkdir(parents=True, exist_ok=True)
    return target


def _iter_backup_files(server_base: Path, scope: str) -> list[Path]:
    normalized = scope if scope in _BACKUP_SCOPES else "full"
    paths: list[Path] = []

    if normalized == "full":
        for file in server_base.rglob("*"):
            if file.is_file():
                paths.append(file)
        return paths

    if normalized == "world":
        candidates = ["world", "world_nether", "world_the_end"]
        for name in candidates:
            folder = server_base / name
            if folder.exists() and folder.is_dir():
                for file in folder.rglob("*"):
                    if file.is_file():
                        paths.append(file)
        for config_file in ("server.properties", "eula.txt"):
            file = server_base / config_file
            if file.exists() and file.is_file():
                paths.append(file)
        return paths

    # config
    for name in ("config", "plugins", "mods"):
        folder = server_base / name
        if folder.exists() and folder.is_dir():
            for file in folder.rglob("*"):
                if file.is_file():
                    paths.append(file)
    for config_file in (
        "server.properties",
        "eula.txt",
        "whitelist.json",
        "ops.json",
        "banned-players.json",
        "banned-ips.json",
    ):
        file = server_base / config_file
        if file.exists() and file.is_file():
            paths.append(file)
    return paths


def _write_zip(server_base: Path, files: list[Path], destination_zip: Path) -> tuple[int, int, int]:
    destination_zip.parent.mkdir(parents=True, exist_ok=True)
    size_bytes = 0
    written_files = 0
    skipped_files = 0
    destination_zip_resolved = destination_zip.resolve()
    with zipfile.ZipFile(destination_zip, mode="w", compression=zipfile.ZIP_DEFLATED) as archive:
        for file in files:
            if not file.exists() or not file.is_file():
                continue
            try:
                file_resolved = file.resolve()
                if file_resolved == destination_zip_resolved:
                    skipped_files += 1
                    continue
                relative = file.relative_to(server_base).as_posix()
                archive.write(file_resolved, arcname=relative)
                try:
                    size_bytes += file_resolved.stat().st_size
                except OSError:
                    pass
                written_files += 1
            except (OSError, ValueError):
                skipped_files += 1
                continue
    return size_bytes, written_files, skipped_files


def list_backups_for_server(db: Session, server_id: int) -> list[Backup]:
    return list(
        db.scalars(
            select(Backup)
            .where(Backup.server_id == server_id)
            .order_by(desc(Backup.created_at))
        ).all()
    )


def list_restore_history_for_server(db: Session, server_id: int) -> list[RestoreHistory]:
    return list(
        db.scalars(
            select(RestoreHistory)
            .where(RestoreHistory.server_id == server_id)
            .order_by(desc(RestoreHistory.restored_at))
        ).all()
    )


def get_backup(db: Session, backup_id: int) -> Backup | None:
    return db.get(Backup, backup_id)


def create_backup(
    db: Session,
    *,
    server: Server,
    initiated_by_user_id: int | None,
    backup_scope: str = "full",
    pre_action: str = "none",
    backup_type: str = "manual",
    custom_name: str | None = None,
) -> Backup:
    scope = backup_scope.strip().lower() if backup_scope else "full"
    if scope not in _BACKUP_SCOPES:
        raise ValueError("Ungueltiger Backup-Scope. Erlaubt: full/world/config.")
    action = pre_action.strip().lower() if pre_action else "none"
    if action not in _PRE_ACTIONS:
        raise ValueError("Ungueltige Backup-Pre-Action. Erlaubt: none/save_all/stop_start.")

    base = _server_base(server)
    now = datetime.now()
    backup_label = _safe_name(custom_name or f"{server.slug}-{scope}-{now:%Y%m%d-%H%M%S}")
    target_dir = _backup_folder_for_server(db, server)
    zip_path = (target_dir / f"{backup_label}.zip").resolve()

    backup = Backup(
        server_id=server.id,
        backup_name=backup_label,
        backup_type=backup_type,
        storage_path=str(zip_path),
        created_by_user_id=initiated_by_user_id,
        status="running",
    )
    db.add(backup)
    db.commit()
    db.refresh(backup)

    was_running = is_running(server.id)
    previous_status = server.status
    try:
        server.status = "backup_running"
        db.add(server)
        db.commit()

        if was_running and action == "save_all":
            send_console_command(db, server, "save-all flush", initiated_by_user_id)
        if was_running and action == "stop_start":
            stop_server(db, server, initiated_by_user_id, force=False)

        files = _iter_backup_files(base, scope)
        # Falls Backup-Zielordner innerhalb des Serverordners liegt, Ziel-Dateien ausschliessen.
        target_dir = zip_path.parent.resolve()
        if target_dir.is_relative_to(base):
            files = [f for f in files if not f.is_relative_to(target_dir)]
        if not files:
            raise ValueError("Keine Dateien fuer dieses Backup gefunden.")
        size_bytes, written_files, skipped_files = _write_zip(base, files, zip_path)
        if written_files <= 0:
            raise ValueError("Keine lesbaren Dateien fuer dieses Backup gefunden.")

        backup.size_bytes = size_bytes
        backup.status = "success"
        db.add(backup)
        db.commit()
        db.refresh(backup)

        audit_service.log_action(
            db,
            action="backup.create",
            user_id=initiated_by_user_id,
            server_id=server.id,
            details=(
                f"backup_id={backup.id} scope={scope} pre_action={action} "
                f"size={size_bytes} written={written_files} skipped={skipped_files}"
            ),
        )
        return backup
    except Exception as exc:
        backup.status = "error"
        backup.size_bytes = None
        db.add(backup)
        db.commit()
        raise ValueError(f"Backup fehlgeschlagen: {exc}") from exc
    finally:
        if action == "stop_start" and was_running and not is_running(server.id):
            start_server(db, server, initiated_by_user_id)
        if server.status == "backup_running":
            server.status = previous_status if previous_status != "backup_running" else "stopped"
            db.add(server)
            db.commit()


def delete_backup(db: Session, *, backup: Backup, initiated_by_user_id: int | None) -> None:
    path = Path(backup.storage_path).expanduser().resolve()
    if path.exists() and path.is_file():
        path.unlink()

    audit_service.log_action(
        db,
        action="backup.delete",
        user_id=initiated_by_user_id,
        server_id=backup.server_id,
        details=f"backup_id={backup.id} name={backup.backup_name}",
    )
    db.delete(backup)
    db.commit()


def _clear_directory_contents(target: Path) -> None:
    for child in target.iterdir():
        if child.is_dir():
            shutil.rmtree(child)
        else:
            child.unlink()


def restore_backup(
    db: Session,
    *,
    server: Server,
    backup: Backup,
    initiated_by_user_id: int | None,
    stop_if_running: bool = True,
    start_after_restore: bool = False,
    notes: str | None = None,
) -> RestoreHistory:
    path = Path(backup.storage_path).expanduser().resolve()
    if not path.exists() or not path.is_file():
        raise ValueError("Backup-Datei nicht gefunden.")

    was_running = is_running(server.id)
    if was_running and not stop_if_running:
        raise ValueError("Server laeuft noch. Fuer Restore muss der Server gestoppt sein.")

    restore_row = RestoreHistory(
        server_id=server.id,
        backup_id=backup.id,
        restored_by_user_id=initiated_by_user_id,
        status="running",
        notes=notes,
    )
    db.add(restore_row)
    db.commit()
    db.refresh(restore_row)

    base = _server_base(server)
    previous_status = server.status
    try:
        server.status = "backup_running"
        db.add(server)
        db.commit()

        if was_running:
            stop_server(db, server, initiated_by_user_id, force=False)

        temp_dir = (base.parent / f".restore_tmp_{server.id}_{int(datetime.now().timestamp())}").resolve()
        if temp_dir.exists():
            shutil.rmtree(temp_dir)
        temp_dir.mkdir(parents=True, exist_ok=True)

        try:
            with zipfile.ZipFile(path, mode="r") as archive:
                archive.extractall(temp_dir)

            _clear_directory_contents(base)

            for item in temp_dir.iterdir():
                destination = base / item.name
                shutil.move(str(item), str(destination))
        finally:
            if temp_dir.exists():
                shutil.rmtree(temp_dir, ignore_errors=True)

        restore_row.status = "success"
        db.add(restore_row)
        db.commit()
        db.refresh(restore_row)

        if start_after_restore and (was_running or stop_if_running):
            start_server(db, server, initiated_by_user_id)

        audit_service.log_action(
            db,
            action="backup.restore",
            user_id=initiated_by_user_id,
            server_id=server.id,
            details=f"backup_id={backup.id} start_after_restore={start_after_restore}",
        )
        return restore_row
    except Exception as exc:
        restore_row.status = "error"
        restore_row.notes = (notes or "") + f" | Fehler: {exc}"
        db.add(restore_row)
        db.commit()
        raise ValueError(f"Restore fehlgeschlagen: {exc}") from exc
    finally:
        if server.status == "backup_running":
            server.status = previous_status if previous_status != "backup_running" else "stopped"
            db.add(server)
            db.commit()
