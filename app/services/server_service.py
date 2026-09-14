import re
import threading
from pathlib import Path

from sqlalchemy import delete, func, select, update
from sqlalchemy.orm import Session

from app.core.constants import DEFAULT_SERVER_STATUS, UserRole
from app.models.installed_content import InstalledContent
from app.models.scheduled_job import ScheduledJob
from app.models.server import Server
from app.models.server_permission import ServerPermission
from app.models.user import User
from app.services.java_runtime_service import choose_best_java_profile
from app.services.memory_settings_service import validate_memory_bounds
from app.schemas.server import ServerCreate, ServerImportConfirm


# Serialisiert Port-/Namens-/Slug-Vergabe bis zum Commit, damit zwei gleichzeitige
# Server-Erstellungen/Duplikate nicht denselben freien Port oder Namen bekommen
# (allocate/generate lesen nur COMMITTED Zeilen -> ohne Lock waere das ein Race).
_SERVER_ALLOC_LOCK = threading.Lock()

# Quell-Server, deren Ordner gerade kopiert wird (Duplizieren). Solange gesperrt darf der
# Quell-Server nicht gestartet werden (sonst sperrt/veraendert er Welt-Dateien waehrend der Kopie).
_DUP_LOCK = threading.Lock()
_ACTIVE_DUP_SOURCES: set[int] = set()


def is_active_duplication_source(server_id: int) -> bool:
    with _DUP_LOCK:
        return server_id in _ACTIVE_DUP_SOURCES


def _mark_duplication_source(server_id: int) -> None:
    with _DUP_LOCK:
        _ACTIVE_DUP_SOURCES.add(server_id)


def _unmark_duplication_source(server_id: int) -> None:
    with _DUP_LOCK:
        _ACTIVE_DUP_SOURCES.discard(server_id)


# Klon-IDs, deren Kopie gerade laeuft, + offene Abbruch-Anforderungen (z.B. wenn der Nutzer
# einen noch kopierenden/fehlerhaften Klon loeschen will -> Kopie sauber stoppen, dann loeschen).
_ACTIVE_DUP_CLONES: set[int] = set()
_CANCEL_DUP_CLONES: set[int] = set()


def is_clone_copy_active(clone_id: int) -> bool:
    with _DUP_LOCK:
        return clone_id in _ACTIVE_DUP_CLONES


def request_cancel_duplication(clone_id: int) -> None:
    with _DUP_LOCK:
        _CANCEL_DUP_CLONES.add(clone_id)


def _is_duplication_cancelled(clone_id: int) -> bool:
    with _DUP_LOCK:
        return clone_id in _CANCEL_DUP_CLONES


def _mark_clone_copy_active(clone_id: int) -> None:
    with _DUP_LOCK:
        _ACTIVE_DUP_CLONES.add(clone_id)


def _clear_clone_copy(clone_id: int) -> None:
    with _DUP_LOCK:
        _ACTIVE_DUP_CLONES.discard(clone_id)
        _CANCEL_DUP_CLONES.discard(clone_id)


def wait_for_duplication_to_stop(clone_id: int, timeout: float = 8.0) -> bool:
    """Nach request_cancel_duplication warten, bis der Kopier-Thread wirklich gestoppt hat
    (damit anschliessend keine Datei-Handles das Loeschen blockieren)."""
    import time

    deadline = time.monotonic() + timeout
    while is_clone_copy_active(clone_id):
        if time.monotonic() >= deadline:
            return False
        time.sleep(0.15)
    return True


_XMS_PATTERN = re.compile(r"(?i)-Xms\S+")
_XMX_PATTERN = re.compile(r"(?i)-Xmx\S+")
_JAVA_TOKEN_PATTERN = re.compile(r'(?i)("[^"]*java(?:\.exe)?"|java(?:\.exe)?)')


def slugify(value: str) -> str:
    value = value.strip().lower()
    value = re.sub(r"[^a-z0-9]+", "-", value)
    value = value.strip("-")
    return value or "server"


def _generate_unique_slug(db: Session, name: str) -> str:
    base_slug = slugify(name)
    candidate = base_slug
    index = 2
    while db.scalar(select(Server).where(Server.slug == candidate)) is not None:
        candidate = f"{base_slug}-{index}"
        index += 1
    return candidate


def _generate_unique_name(db: Session, name: str) -> str:
    normalized = name.strip() or "Server"
    candidate = normalized
    index = 2
    while db.scalar(select(Server).where(Server.name == candidate)) is not None:
        candidate = f"{normalized} ({index})"
        index += 1
    return candidate


def list_servers_for_user(db: Session, user: User) -> list[Server]:
    if user.role == UserRole.SUPER_ADMIN.value:
        return list(db.scalars(select(Server).order_by(Server.name.asc())).all())

    query = (
        select(Server)
        .join(ServerPermission, ServerPermission.server_id == Server.id)
        .where(ServerPermission.user_id == user.id, ServerPermission.can_view.is_(True))
        .order_by(Server.name.asc())
    )
    return list(db.scalars(query).all())


def get_server_by_id(db: Session, server_id: int) -> Server | None:
    return db.get(Server, server_id)


def can_view_server(db: Session, user: User, server: Server) -> bool:
    if user.role == UserRole.SUPER_ADMIN.value:
        return True
    permission = db.scalar(
        select(ServerPermission).where(
            ServerPermission.user_id == user.id,
            ServerPermission.server_id == server.id,
        )
    )
    return bool(permission and permission.can_view)


def can_control_server(db: Session, user: User, server: Server) -> bool:
    if user.role == UserRole.SUPER_ADMIN.value:
        return True
    permission = db.scalar(
        select(ServerPermission).where(
            ServerPermission.user_id == user.id,
            ServerPermission.server_id == server.id,
        )
    )
    if not permission:
        return False
    return bool(permission.can_manage or permission.can_restart or permission.can_console)


def can_edit_server_files(db: Session, user: User, server: Server) -> bool:
    if user.role == UserRole.SUPER_ADMIN.value:
        return True
    permission = db.scalar(
        select(ServerPermission).where(
            ServerPermission.user_id == user.id,
            ServerPermission.server_id == server.id,
        )
    )
    if not permission:
        return False
    return bool(permission.can_manage or permission.can_edit_files)


def create_server(db: Session, data: ServerCreate) -> Server:
    from app.services import port_service

    base_path = str(Path(data.base_path).resolve())
    unique_name = _generate_unique_name(db, data.name)
    memory_min_mb, memory_max_mb = validate_memory_bounds(data.memory_min_mb, data.memory_max_mb)
    java_profile_id = data.java_profile_id
    if java_profile_id is None:
        auto_profile = choose_best_java_profile(db, mc_version=data.mc_version)
        if auto_profile is not None:
            java_profile_id = auto_profile.id

    # Kein Port angegeben -> host-weit freien Port aus dem Bereich vergeben.
    # Vergabe + Insert unter dem Alloc-Lock, damit parallele Erstellungen/Duplikate nicht
    # denselben Port bekommen (used_server_ports sieht nur committete Zeilen).
    with _SERVER_ALLOC_LOCK:
        assigned_port = data.port
        if assigned_port is None:
            try:
                assigned_port = port_service.allocate_server_port(db)
            except ValueError:
                assigned_port = None

        server = Server(
            name=unique_name,
            slug=_generate_unique_slug(db, unique_name),
            server_type=data.server_type,
            mc_version=data.mc_version,
            loader_version=data.loader_version,
            base_path=base_path,
            start_mode=data.start_mode,
            start_command=data.start_command,
            start_bat_path=data.start_bat_path,
            java_profile_id=java_profile_id,
            memory_min_mb=memory_min_mb,
            memory_max_mb=memory_max_mb,
            port=assigned_port,
            status=DEFAULT_SERVER_STATUS,
            auto_restart=False,
            auto_start_with_manager=False,
        )
        db.add(server)
        db.commit()
        db.refresh(server)

    # Safety cleanup for environments where old orphan rows existed and IDs get reused.
    cleanup_changed = False
    for table in (InstalledContent, ServerPermission, ScheduledJob):
        result = db.execute(delete(table).where(table.server_id == server.id))
        if (result.rowcount or 0) > 0:
            cleanup_changed = True
    if cleanup_changed:
        db.commit()
        db.refresh(server)

    return server


def _rebase_path_string(value: str | None, old_base: str, new_base: str) -> str | None:
    """Absolute Verweise auf den Quell-Serverordner (z.B. in start_command / start_bat_path)
    auf den Klon-Ordner umbiegen. Best-effort ueber String-Ersetzung des Basispfads."""
    if not value or not old_base:
        return value
    if old_base in value:
        return value.replace(old_base, new_base)
    # Windows: gross-/kleinschreibungsunabhaengig probieren.
    lowered = value.lower()
    idx = lowered.find(old_base.lower())
    if idx != -1:
        return value[:idx] + new_base + value[idx + len(old_base):]
    return value


def _unique_server_dir(root: Path, slug: str) -> Path:
    """Freien Zielordner unter dem Storage-Root fuer den Slug finden (slug, slug-2, ...)."""
    candidate = root / slug
    counter = 2
    while candidate.exists():
        candidate = root / f"{slug}-{counter}"
        counter += 1
    return candidate


def _copy_server_relations(db: Session, source_id: int, clone_id: int) -> None:
    """DB-Verknuepfungen des Servers auf den Klon kopieren: installierte Inhalte,
    Zugriffsrechte, geplante Tasks (pausiert), Modpack-Herkunft. NICHT: Backups/Historien."""
    from app.models.server_modpack_state import ServerModpackState

    for row in db.scalars(
        select(InstalledContent).where(InstalledContent.server_id == source_id)
    ):
        db.add(InstalledContent(
            server_id=clone_id, provider_name=row.provider_name, content_type=row.content_type,
            external_project_id=row.external_project_id, external_version_id=row.external_version_id,
            name=row.name, version_label=row.version_label, file_name=row.file_name,
            file_sha1=row.file_sha1, file_sha512=row.file_sha512,
            local_adopt_state=row.local_adopt_state, declared_mc_version=row.declared_mc_version,
            installed_by_user_id=row.installed_by_user_id,
        ))

    for row in db.scalars(
        select(ServerPermission).where(ServerPermission.server_id == source_id)
    ):
        db.add(ServerPermission(
            server_id=clone_id, user_id=row.user_id, can_view=row.can_view,
            can_console=row.can_console, can_restart=row.can_restart,
            can_edit_files=row.can_edit_files, can_manage=row.can_manage,
        ))

    for row in db.scalars(
        select(ScheduledJob).where(ScheduledJob.server_id == source_id)
    ):
        # Geplante Tasks werden pausiert uebernommen (Nutzer aktiviert sie bewusst) und
        # bekommen einen frischen Zeitplan (keine geerbten last/next-Zeiten).
        db.add(ScheduledJob(
            server_id=clone_id, job_type=row.job_type,
            schedule_expression=row.schedule_expression, command_payload=row.command_payload,
            is_enabled=False, last_run_at=None, next_run_at=None,
        ))

    state = db.scalar(
        select(ServerModpackState).where(ServerModpackState.server_id == source_id)
    )
    if state is not None:
        db.add(ServerModpackState(
            server_id=clone_id, source=state.source, pack_name=state.pack_name,
            source_ref=state.source_ref, upstream_project_id=state.upstream_project_id,
            current_version_id=state.current_version_id, pending_version_id=state.pending_version_id,
            pack_version=state.pack_version, last_known_version_id=state.last_known_version_id,
            last_known_version_label=state.last_known_version_label,
            last_check_error=state.last_check_error,
        ))

    db.commit()


def _dir_total_size(path: Path) -> int:
    """Gesamtgroesse eines Verzeichnisbaums (Bytes), scandir-basiert (unter Windows nutzt
    entry.stat() die bereits gecachten Verzeichnisdaten -> schnell auch ueber SMB)."""
    import os

    total = 0
    stack = [str(path)]
    while stack:
        current = stack.pop()
        try:
            with os.scandir(current) as it:
                for entry in it:
                    try:
                        if entry.is_dir(follow_symlinks=False):
                            stack.append(entry.path)
                        elif entry.is_file(follow_symlinks=False):
                            total += entry.stat(follow_symlinks=False).st_size
                    except OSError:
                        pass
        except OSError:
            pass
    return total


class _DuplicationCancelled(Exception):
    """Signalisiert einen vom Nutzer angeforderten Abbruch der laufenden Kopie."""


def _robust_copy_tree(src, dst, *, on_file=None, is_cancelled=None) -> list[str]:
    """Kopiert einen Ordner rekursiv wie Explorer/xcopy: einzelne gesperrte/unlesbare Dateien
    werden UEBERSPRUNGEN, statt den ganzen Vorgang abzubrechen (shutil.copytree bricht schon beim
    ersten Fehler ab - z.B. bei einer vom Manager offen gehaltenen logs/latest.log, alphabetisch
    VOR mods/plugins/world -> dann fehlt der Rest). Rueckgabe: Liste uebersprungener Pfade."""
    import os
    import shutil
    import time

    src = Path(src)
    dst = Path(dst)
    skipped: list[str] = []
    for root, _dirs, files in os.walk(src):
        if is_cancelled and is_cancelled():
            raise _DuplicationCancelled()
        rel = Path(root).relative_to(src)
        target_dir = dst / rel
        try:
            target_dir.mkdir(parents=True, exist_ok=True)
        except OSError:
            skipped.append(f"{rel}\\*")
            continue
        for name in files:
            if is_cancelled and is_cancelled():
                raise _DuplicationCancelled()
            source_file = Path(root) / name
            dest_file = target_dir / name
            copied_ok = False
            for attempt in range(3):
                try:
                    shutil.copy2(source_file, dest_file)
                    copied_ok = True
                    break
                except OSError:
                    if attempt < 2:
                        time.sleep(0.2)   # kurzer Retry (transiente SMB-/Datei-Sperre)
            if copied_ok:
                if on_file is not None:
                    on_file(dest_file)
            else:
                skipped.append(str(rel / name))
    return skipped


def _copy_server_backups(db: Session, source_id: int, clone_id: int) -> list[str]:
    """Backups der Quelle fuer den Klon uebernehmen (ZIP-Datei kopieren + DB-Zeile anlegen).
    Backups liegen ausserhalb des Serverordners, werden also NICHT vom Ordner-Copy erfasst.
    Best-effort - Rueckgabe: Warnungen."""
    import shutil

    from app.models.backup import Backup
    from app.services import backup_service

    clone = db.get(Server, clone_id)
    if clone is None:
        return []
    warnings: list[str] = []
    try:
        target_dir = backup_service._backup_folder_for_server(db, clone)
    except Exception as exc:  # noqa: BLE001
        return [f"Backups nicht kopierbar: {exc}"]
    copied = 0
    for backup in backup_service.list_backups_for_server(db, source_id):
        src_zip = Path(backup.storage_path)
        if (backup.status or "") != "success" or not src_zip.exists() or not src_zip.is_file():
            continue
        dest = (target_dir / src_zip.name).resolve()
        try:
            shutil.copy2(src_zip, dest)
        except OSError as exc:
            warnings.append(f"Backup '{backup.backup_name}' nicht kopiert: {exc}")
            continue
        db.add(Backup(
            server_id=clone_id, backup_name=backup.backup_name, backup_type=backup.backup_type,
            storage_path=str(dest), created_by_user_id=backup.created_by_user_id,
            size_bytes=backup.size_bytes, status="success",
        ))
        copied += 1
    if copied:
        db.commit()
    return warnings


def _perform_duplicate(db: Session, source_id: int, clone_id: int, user_id: int | None) -> None:
    """Die eigentliche (potenziell langsame) Klon-Arbeit: Serverordner 1:1 kopieren,
    DB-Verknuepfungen uebernehmen, server.properties auf den neuen Port angleichen.
    Gibt die Quell-Sperre am Ende immer frei."""
    import shutil

    from app.services import audit_service
    from app.services.process_service import is_running, report_progress

    try:
        source = db.get(Server, source_id)
        clone = db.get(Server, clone_id)
        if source is None or clone is None:
            return
        clone_base = clone.base_path
        try:
            # Erneut sicherstellen, dass die Quelle nicht (wieder) laeuft - sonst waeren die
            # Welt-Dateien gesperrt/inkonsistent (die Vorabpruefung deckt nur den Klick-Moment ab).
            if is_running(source_id):
                raise ValueError("Quell-Server wurde waehrend des Kopierens gestartet - Abbruch.")
            # Gesamtgroesse einmal bestimmen: fuer Speicher-Check UND Fortschrittsanzeige.
            total_bytes = 0
            try:
                total_bytes = _dir_total_size(Path(source.base_path))
                free = shutil.disk_usage(str(Path(clone_base).parent)).free
                if total_bytes and free < total_bytes + (256 * 1024 * 1024):
                    raise ValueError(
                        f"Nicht genug freier Speicher (benoetigt ~{total_bytes // (1024 * 1024)} MB, "
                        f"frei ~{free // (1024 * 1024)} MB)."
                    )
            except ValueError:
                raise
            except Exception:
                total_bytes = 0   # Groessen-/Speicherpruefung darf das Duplizieren nicht blockieren

            # Fortschritt ueber dieselbe Anzeige wie der Startvorgang (Detailseite pollt sie).
            _mb = 1024 * 1024
            report_progress(clone_id, active=True, stage="duplicate", percent=0,
                            message="Serverdateien werden kopiert ...")
            copied = [0]
            last_pct = [-1]

            def _on_file(dest_file):
                if not total_bytes:
                    return
                try:
                    copied[0] += Path(dest_file).stat().st_size
                except OSError:
                    return
                pct = min(99, int(copied[0] * 100 / total_bytes))
                if pct != last_pct[0]:
                    last_pct[0] = pct
                    report_progress(
                        clone_id, active=True, stage="duplicate", percent=pct,
                        message=f"Serverdateien werden kopiert ... "
                                f"{copied[0] // _mb} / {total_bytes // _mb} MB",
                    )

            # Wie Explorer/xcopy: gesperrte Einzeldateien ueberspringen statt den ganzen
            # Vorgang abzubrechen (sonst fehlten mods/plugins/world nach einer offenen logs-Datei).
            skipped = _robust_copy_tree(
                source.base_path, clone.base_path,
                on_file=_on_file,
                is_cancelled=lambda: _is_duplication_cancelled(clone_id),
            )

            report_progress(clone_id, active=True, stage="duplicate", percent=99,
                            message="Inhalte, Rechte, Tasks & Backups werden uebernommen ...")
            _copy_server_relations(db, source_id, clone_id)
            try:
                sync_server_settings_to_files(clone)   # schreibt server-port = neuer Port
            except Exception:
                pass
            backup_warnings = _copy_server_backups(db, source_id, clone_id)

            clone.status = DEFAULT_SERVER_STATUS       # "stopped" - fertig
            db.add(clone)
            db.commit()

            done_msg = "Kopie abgeschlossen."
            if skipped:
                done_msg += f" {len(skipped)} gesperrte Datei(en) uebersprungen."
            if backup_warnings:
                done_msg += f" {len(backup_warnings)} Backup-Hinweis(e)."
            report_progress(clone_id, active=False, stage="stopped", percent=100, message=done_msg)
            audit_service.log_action(
                db, action="server.duplicate", user_id=user_id, server_id=clone_id,
                details=(f"source={source_id} name={clone.name} port={clone.port} "
                         f"skipped={len(skipped)} backup_warnings={len(backup_warnings)}"),
            )
        except _DuplicationCancelled:
            # Nutzer loescht den Klon -> Kopie stoppen. Ordner/DB-Zeile raeumt der Loesch-Vorgang auf.
            db.rollback()
            report_progress(clone_id, active=False, stage="stopped", percent=0,
                            message="Kopie abgebrochen.")
        except Exception as exc:
            db.rollback()
            # Halb kopierten Klon-Ordner wieder entfernen (kein GB-Muell auf der NAS-Freigabe).
            try:
                shutil.rmtree(clone_base, ignore_errors=True)
            except Exception:
                pass
            broken = db.get(Server, clone_id)
            if broken is not None:
                broken.status = "error"
                db.add(broken)
                db.commit()
            report_progress(clone_id, active=False, stage="error", percent=0,
                            message=f"Kopie fehlgeschlagen: {exc}")
            try:
                audit_service.log_action(
                    db, action="server.duplicate_failed", user_id=user_id, server_id=clone_id,
                    details=str(exc),
                )
            except Exception:
                pass
            raise
    finally:
        _clear_clone_copy(clone_id)
        _unmark_duplication_source(source_id)


def duplicate_server(db: Session, source: Server, user_id: int | None, *,
                     run_async: bool = True) -> Server:
    """Einen Server 1:1 duplizieren (Ordner, Welt, Mods/Plugins, Einstellungen, Inhalte,
    Rechte, geplante Tasks). Der Klon bekommt einen neuen Namen, freien Port und deaktiviertes
    Netzwerk (Alias/Lobby sind eindeutig). Die Quelle MUSS gestoppt sein (konsistente Kopie).

    Die (bei grossen Welten langsame) Datei-Kopie laeuft standardmaessig im Hintergrund; der Klon
    startet im Status 'provisioning' und wird nach Abschluss 'stopped' (oder 'error')."""
    from app.services import app_setting_service, port_service
    from app.services.process_service import is_running

    if (source.status or "").strip().lower() == "provisioning":
        raise ValueError("Dieser Server wird gerade erstellt/kopiert - bitte warten.")
    if is_running(source.id):
        raise ValueError("Bitte den Server zuerst stoppen, bevor du ihn duplizierst.")

    storage_root = app_setting_service.ensure_server_storage_initialized(db)
    src_path = Path(source.base_path).resolve()
    old_base = str(source.base_path)

    # Namen/Slug/Ports vergeben + Klon-Zeile schreiben unter dem Alloc-Lock (atomar gegen
    # parallele Erstellungen/Duplikate - sonst gleiche Ports oder Namens-IntegrityError).
    with _SERVER_ALLOC_LOCK:
        new_name = _generate_unique_name(db, f"{source.name} (Kopie)")
        new_slug = _generate_unique_slug(db, new_name)
        new_base = _unique_server_dir(storage_root, new_slug)
        new_base_str = str(new_base)
        dst_path = new_base.resolve()

        # Verschachtelung Quelle<->Ziel verhindern (sonst kopiert copytree endlos in sich selbst
        # bzw. den ganzen Storage-Root in den Klon).
        if (dst_path == src_path or dst_path.is_relative_to(src_path)
                or src_path.is_relative_to(dst_path)):
            raise ValueError(
                "Zielordner liegt im Quellordner (oder umgekehrt) - Duplizieren abgebrochen."
            )

        new_port = port_service.allocate_server_port(db)
        new_sleep_port = None
        if source.sleep_enabled:
            new_sleep_port = port_service.allocate_server_port(db, exclude={new_port})

        clone = Server(
            name=new_name,
            slug=new_slug,
            server_type=source.server_type,
            mc_version=source.mc_version,
            loader_version=source.loader_version,
            base_path=new_base_str,
            start_mode=source.start_mode,
            start_command=_rebase_path_string(source.start_command, old_base, new_base_str),
            start_bat_path=_rebase_path_string(source.start_bat_path, old_base, new_base_str),
            java_profile_id=source.java_profile_id,
            memory_min_mb=source.memory_min_mb,
            memory_max_mb=source.memory_max_mb,
            port=new_port,
            status="provisioning",
            auto_restart=source.auto_restart,
            auto_start_with_manager=False,          # Sicherheit: Klon nicht automatisch mitstarten
            sleep_enabled=source.sleep_enabled,
            sleep_delay_seconds=source.sleep_delay_seconds,
            sleep_internal_port=new_sleep_port,
            gateway_enabled=False,                   # Netzwerk/Alias sind eindeutig -> auf dem Klon aus
            gateway_hostname=None,
            gateway_is_default=False,
        )
        db.add(clone)
        db.commit()
        db.refresh(clone)

    # Quelle fuer die Dauer der Kopie als "wird dupliziert" sperren (start_server verweigert
    # dann den Start dieses Servers); Klon als "Kopie laeuft" markieren (fuer Loeschen-Abbruch).
    _mark_duplication_source(source.id)
    _mark_clone_copy_active(clone.id)

    # Sofort einen Fortschritts-Eintrag setzen, damit die Detailseite gleich "wird kopiert" zeigt.
    from app.services import process_service as _ps
    _ps.report_progress(clone.id, active=True, stage="duplicate", percent=0,
                        message="Kopie wird vorbereitet ...")

    if run_async:
        clone_id, src_id = clone.id, source.id

        def _bg() -> None:
            from app.db.session import SessionLocal
            with SessionLocal() as worker_db:
                try:
                    _perform_duplicate(worker_db, src_id, clone_id, user_id)
                except Exception:
                    pass   # Status/Sperre wurden bereits in _perform_duplicate behandelt

        threading.Thread(target=_bg, daemon=True).start()
    else:
        _perform_duplicate(db, source.id, clone.id, user_id)
        db.refresh(clone)

    return clone


def _memory_token(value_mb: int | None, kind: str) -> str | None:
    if value_mb is None:
        return None
    if value_mb <= 0:
        return None
    return f"-X{kind}{value_mb}M"


def _apply_memory_flags_to_command(
    command: str,
    memory_min_mb: int | None,
    memory_max_mb: int | None,
) -> str:
    updated = _XMS_PATTERN.sub("", command)
    updated = _XMX_PATTERN.sub("", updated)
    updated = " ".join(updated.split())

    tokens: list[str] = []
    xms = _memory_token(memory_min_mb, "ms")
    xmx = _memory_token(memory_max_mb, "mx")
    if xms:
        tokens.append(xms)
    if xmx:
        tokens.append(xmx)
    if not tokens:
        return updated

    java_match = _JAVA_TOKEN_PATTERN.search(updated)
    if not java_match:
        return " ".join(tokens + [updated]).strip()

    insert_pos = java_match.end()
    before = updated[:insert_pos]
    after = updated[insert_pos:].strip()
    merged = f"{before} {' '.join(tokens)}"
    if after:
        merged = f"{merged} {after}"
    return merged.strip()


def _upsert_server_property(server: Server, key: str, value: str) -> str | None:
    base_path = Path(server.base_path).expanduser().resolve()
    if not base_path.exists() or not base_path.is_dir():
        return f"Serverordner nicht gefunden: {base_path}"

    properties_path = base_path / "server.properties"
    lines: list[str] = []
    if properties_path.exists():
        lines = properties_path.read_text(encoding="utf-8", errors="ignore").splitlines()

    prefix = f"{key}="
    replaced = False
    output: list[str] = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith(prefix):
            output.append(f"{key}={value}")
            replaced = True
        else:
            output.append(line)

    if not replaced:
        output.append(f"{key}={value}")

    properties_path.write_text("\n".join(output).strip() + "\n", encoding="utf-8")
    return None


def enable_accept_transfers(server: Server) -> None:
    """Server transfer-bereit machen, damit eine Transfer-Lobby (1.20.5+) Spieler
    hierher schicken darf.

    Mojangs echte server.properties-Option heisst ``accepts-transfers`` (MIT 's';
    der Server generiert sie selbst mit Default ``false``). Wir schreiben zur
    Sicherheit auch die alternative Schreibweise ``accept-transfers`` - unbekannte
    Keys ignoriert der Server, das schadet also nicht.
    """
    _upsert_server_property(server, "accepts-transfers", "true")
    _upsert_server_property(server, "accept-transfers", "true")


def _sync_forge_jvm_args(server: Server) -> str | None:
    base_path = Path(server.base_path).expanduser().resolve()
    args_path = base_path / "user_jvm_args.txt"
    existing_lines: list[str] = []
    if args_path.exists():
        existing_lines = args_path.read_text(encoding="utf-8", errors="ignore").splitlines()

    output: list[str] = []
    for line in existing_lines:
        stripped = line.strip()
        if stripped.lower().startswith("-xms") or stripped.lower().startswith("-xmx"):
            continue
        output.append(line)

    xms = _memory_token(server.memory_min_mb, "ms")
    xmx = _memory_token(server.memory_max_mb, "mx")
    if xms:
        output.append(xms)
    if xmx:
        output.append(xmx)

    args_path.write_text("\n".join(output).strip() + "\n", encoding="utf-8")
    return None


def _sync_legacy_forge_run_bat_memory(server: Server) -> str | None:
    """Speicher-Flags in einem generierten Legacy-Forge ``run.bat`` aktualisieren.

    Modernes Forge (>=1.17) bezieht den Speicher aus ``user_jvm_args.txt`` und
    hat KEINE ``-Xmx`` im run.bat – solche run.bat werden nicht angefasst.
    """
    base_path = Path(server.base_path).expanduser().resolve()
    run_bat = base_path / "run.bat"
    if not run_bat.exists():
        return None
    try:
        content = run_bat.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return None
    if "-xmx" not in content.lower():
        return None

    lines = content.splitlines()
    changed = False
    for idx, line in enumerate(lines):
        if "java" not in line.lower():
            continue
        new_line = _apply_memory_flags_to_command(
            line, server.memory_min_mb, server.memory_max_mb
        )
        if new_line != line:
            lines[idx] = new_line
            changed = True
        break
    if changed:
        run_bat.write_text("\n".join(lines).strip() + "\n", encoding="utf-8")
    return None


def _sync_bat_start_memory(server: Server) -> str | None:
    base_path = Path(server.base_path).expanduser().resolve()
    bat_path = Path(server.start_bat_path or str(base_path / "start.bat")).expanduser()
    if not bat_path.is_absolute():
        bat_path = (base_path / bat_path).resolve()
    else:
        bat_path = bat_path.resolve()
    if not bat_path.exists():
        return f"Startdatei nicht gefunden: {bat_path}"

    content = bat_path.read_text(encoding="utf-8", errors="ignore")
    lines = content.splitlines()
    changed = False
    for idx, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or stripped.upper().startswith("REM ") or stripped.startswith("::"):
            continue
        if "java" not in stripped.lower():
            continue
        new_line = _apply_memory_flags_to_command(line, server.memory_min_mb, server.memory_max_mb)
        if new_line != line:
            lines[idx] = new_line
            changed = True
        break

    if changed:
        bat_path.write_text("\n".join(lines).strip() + "\n", encoding="utf-8")
    return None


_PENDING_STATUSES = {
    "starting",
    "restarting",
    "stopping",
    "backup_running",
    "provisioning",
}


def server_status_view(server: Server) -> dict[str, str]:
    """Anzeige-Status + Farb-Key fuer die UI.

    color: online (gruen), pending (orange), sleeping (lila), offline (rot).
    Ein Sleep-Server im Zustand 'stopped' wird als 'sleeping' angezeigt.
    """
    status = server.status or "stopped"
    if status == "running":
        return {"status": "running", "color": "online"}
    if status in _PENDING_STATUSES:
        return {"status": status, "color": "pending"}
    if getattr(server, "sleep_enabled", False) and status == "stopped":
        return {"status": "sleeping", "color": "sleeping"}
    if status in {"crashed", "error"}:
        return {"status": status, "color": "offline"}
    return {"status": status, "color": "offline"}


_SLEEP_DELAY_UNITS: list[tuple[str, int]] = [
    ("days", 86400),
    ("hours", 3600),
    ("minutes", 60),
    ("seconds", 1),
]


def split_sleep_delay_seconds(seconds: int | None) -> dict[str, object]:
    """Sekunden in die groesste glatt teilbare Einheit zerlegen.

    z.B. 300 -> {value: 5, unit: "minutes"}, 86400 -> {value: 1, unit: "days"}.
    Fuer die UI, damit die Verzoegerung in Sek/Min/Std/Tage angezeigt wird.
    """
    total = int(seconds) if seconds else 0
    if total <= 0:
        return {"value": 0, "unit": "seconds"}
    for unit, mult in _SLEEP_DELAY_UNITS:
        if total % mult == 0:
            return {"value": total // mult, "unit": unit}
    return {"value": total, "unit": "seconds"}


def sleep_delay_to_seconds(value: int | None, unit: str | None) -> int | None:
    """Wert + Einheit (seconds/minutes/hours/days) in Sekunden umrechnen."""
    if value is None:
        return None
    multipliers = {mult_unit: mult for mult_unit, mult in _SLEEP_DELAY_UNITS}
    mult = multipliers.get((unit or "seconds").strip().lower(), 1)
    return max(0, int(value)) * mult


_GATEWAY_HOSTNAME_RE = re.compile(r"^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$")


def normalize_gateway_hostname(raw: str | None) -> str:
    """Alias normalisieren: Kleinschreibung, ohne fuehrende/abschliessende Punkte."""
    return (raw or "").strip().strip(".").lower()


def is_valid_gateway_hostname(value: str) -> bool:
    return (
        bool(value)
        and len(value) <= 255
        and ".." not in value
        and _GATEWAY_HOSTNAME_RE.match(value) is not None
    )


def gateway_hostname_taken(
    db: Session, hostname: str, *, exclude_server_id: int | None = None
) -> bool:
    stmt = select(Server.id).where(func.lower(Server.gateway_hostname) == hostname.lower())
    if exclude_server_id is not None:
        stmt = stmt.where(Server.id != exclude_server_id)
    return db.scalar(stmt) is not None


def cleanup_velocity_leftovers(server: Server) -> list[str]:
    """Reste aus der (entfernten) Velocity-Zeit beim Start bereinigen (idempotent).

    Der Manager erkennt seine eigene Backend-Signatur am loopback-Bind
    (``server-ip=127.0.0.1``) und stellt dann ``online-mode=true`` wieder her und
    gibt den Bind frei. Zusaetzlich wird eine aktive Velocity-Forwarding-Sektion in
    ``config/paper-global.yml`` deaktiviert. Ein bewusst 'cracked' Standalone-Server
    (online-mode=false OHNE loopback) bleibt unangetastet.
    """
    notes: list[str] = []
    base_path = Path(server.base_path).expanduser().resolve()
    if not base_path.exists():
        return notes

    props = base_path / "server.properties"
    was_backend = False
    if props.exists():
        server_ip = None
        for line in props.read_text(encoding="utf-8", errors="ignore").splitlines():
            if line.strip().startswith("server-ip="):
                server_ip = line.split("=", 1)[1].strip()
        if server_ip == "127.0.0.1":
            was_backend = True
            _upsert_server_property(server, "online-mode", "true")
            _upsert_server_property(server, "server-ip", "")
            notes.append("Velocity-Rest entfernt: online-mode=true, oeffentlicher Bind.")

    # legacy-Rest: spigot.yml bungeecord=true wieder abschalten (sonst bricht ein nun
    # eigenstaendiger Server die Auth - bungeecord=true erwartet einen Proxy davor).
    if was_backend:
        try:
            _set_spigot_bungeecord(base_path, False)
            notes.append("Legacy-Rest entfernt: spigot.yml bungeecord=false.")
        except Exception:  # noqa: BLE001 - darf den Start nie stoeren
            pass

    paper_global = base_path / "config" / "paper-global.yml"
    if paper_global.exists():
        try:
            import yaml

            data = yaml.safe_load(paper_global.read_text(encoding="utf-8", errors="ignore")) or {}
            velocity = ((data.get("proxies") or {}).get("velocity") or {})
            if isinstance(velocity, dict) and velocity.get("enabled"):
                velocity["enabled"] = False
                data["proxies"]["velocity"] = velocity
                paper_global.write_text(
                    yaml.safe_dump(data, sort_keys=False, allow_unicode=True), encoding="utf-8"
                )
                notes.append("Velocity-Forwarding in paper-global.yml deaktiviert.")
        except Exception:  # noqa: BLE001 - darf den Start nie stoeren
            pass
    return notes


# NUR die Paper-Familie kann Velocity "modern forwarding" -> nur sie laeuft als loopback-Backend
# HINTER Velocity. Spigot/Bukkit koennen KEIN modern forwarding; ein Legacy/offline-mode-Backend
# haette bei DIREKT-Verbindungen (Port/Domain, die Velocity umgehen) keine echte UUID. Darum sind
# Spigot/Bukkit KEINE Backends, sondern eigenstaendige ONLINE-MODE-Server (wie die Modded-Server):
# per Gateway-Route + nativem Lobby-Transfer direkt angesprungen -> echte Mojang-UUID auf ALLEN
# Wegen (Direktverbindung UND Lobby).
_VELOCITY_BACKEND_TYPES = {"paper", "purpur", "folia"}
_MODERN_FORWARDING_TYPES = {"paper", "purpur", "folia"}


def is_velocity_backend(server: Server, *, network_mode: str | None = None) -> bool:
    """Ob dieser Server ein Velocity-Backend ist (loopback + Forwarding).

    Nur im Modus 'velocity' und nur Bukkit-basierte, gateway_enabled Server. Modded-/
    Vanilla-Server sind KEINE Backends - sie werden per nativem Transfer direkt
    angesprungen (bleiben oeffentlich, online-mode=true).
    """
    if (network_mode or "").strip().lower() != "velocity":
        return False
    if not getattr(server, "gateway_enabled", False):
        return False
    return str(getattr(server, "server_type", "") or "").lower() in _VELOCITY_BACKEND_TYPES


def velocity_forwarding_mode(db: Session) -> str:
    """'modern' NUR wenn ALLE Velocity-Backends Paper-Familie sind; sonst 'legacy' (BungeeCord).

    Velocitys Forwarding-Modus ist global. Sobald EIN Backend Spigot/Bukkit ist (kann kein
    modern forwarding), muss das ganze Netzwerk auf legacy - sonst bekaeme dieser Server keine
    echte Mojang-UUID (Offline-UUID -> Skin/Spielstand-Verlust). Reine Paper-Netze bleiben auf
    dem sichereren modern forwarding."""
    from sqlalchemy import select

    from app.services import app_setting_service

    net = app_setting_service.get_network_mode(db)
    if (net or "").strip().lower() != "velocity":
        return "modern"
    for srv in db.scalars(select(Server)).all():
        if (is_velocity_backend(srv, network_mode=net)
                and str(getattr(srv, "server_type", "") or "").lower() not in _MODERN_FORWARDING_TYPES):
            return "legacy"
    return "modern"


def apply_velocity_backend_forwarding(server: Server, secret: str, *, mode: str = "modern") -> list[str]:
    """Server als Velocity-Backend einrichten - Gegenstueck zu ``cleanup_velocity_leftovers``.

    ``mode='modern'`` (Paper-Familie): paper-global.yml velocity + Secret.
    ``mode='legacy'`` (BungeeCord; noetig sobald ein Spigot/Bukkit-Backend im Netz ist):
    spigot.yml ``settings.bungeecord=true`` (liest Spigot UND Paper) - kein Secret.

    Beide: server.properties ``online-mode=false`` (der Proxy authentifiziert) +
    ``server-ip=127.0.0.1`` (nur ueber Velocity erreichbar). SICHERHEIT: bei legacy vertraut
    das Backend den weitergereichten Daten -> es darf NUR ueber den Proxy erreichbar sein
    (Backends binden loopback; oeffentlich ist nur der Gateway-Port)."""
    if (mode or "modern").strip().lower() == "legacy":
        return _apply_legacy_forwarding(server)

    notes: list[str] = []
    base_path = Path(server.base_path).expanduser().resolve()
    if not base_path.exists():
        return notes
    if not (secret or "").strip():
        notes.append("Velocity-Forwarding uebersprungen: kein Secret.")
        return notes

    # SICHERHEIT: ZUERST das Modern-Forwarding in paper-global.yml schreiben (und nur bei
    # Erfolg danach online-mode=false + loopback). So laeuft NIE ein offline-mode-Server
    # ohne Forwarding-Enforcement (sonst koennte jeder ueber den oeffentlichen Sleep-Port
    # jeden Usernamen faelschen). Schlaegt der YAML-Schreibvorgang fehl -> online-mode=true.
    forwarding_ok = False
    cfg_dir = base_path / "config"
    paper_global = cfg_dir / "paper-global.yml"
    try:
        import yaml

        data: dict = {}
        if paper_global.exists():
            loaded = yaml.safe_load(paper_global.read_text(encoding="utf-8", errors="ignore"))
            if isinstance(loaded, dict):
                data = loaded
        proxies = data.get("proxies")
        if not isinstance(proxies, dict):
            proxies = {}
        velocity = proxies.get("velocity")
        if not isinstance(velocity, dict):
            velocity = {}
        velocity["enabled"] = True
        velocity["online-mode"] = True
        velocity["secret"] = secret
        proxies["velocity"] = velocity
        data["proxies"] = proxies
        cfg_dir.mkdir(parents=True, exist_ok=True)
        paper_global.write_text(
            yaml.safe_dump(data, sort_keys=False, allow_unicode=True), encoding="utf-8"
        )
        forwarding_ok = True
    except Exception as exc:  # noqa: BLE001 - darf den Start nie stoeren
        notes.append(f"Velocity-Forwarding (paper-global.yml) fehlgeschlagen: {exc}")

    if forwarding_ok:
        # Ein evtl. von legacy uebrig gebliebenes bungeecord=true ABSCHALTEN, sonst laeuft das
        # Backend mit modern UND legacy gleichzeitig -> Doppel-Forwarding -> Kick.
        try:
            _set_spigot_bungeecord(base_path, False)
        except Exception:  # noqa: BLE001 - best-effort, darf modern nicht kippen
            pass
        _upsert_server_property(server, "online-mode", "false")
        _upsert_server_property(server, "server-ip", "127.0.0.1")
        notes.append("Velocity-Forwarding aktiv: online-mode=false, loopback, Secret gesetzt.")
    else:
        # Fail-safe: kein offline-mode ohne Forwarding -> online-mode=true (Mojang-Auth).
        _upsert_server_property(server, "online-mode", "true")
        notes.append(
            "Velocity-Forwarding NICHT geschrieben -> online-mode=true erzwungen (Sicherheit). "
            "Backend laeuft erst hinter dem Proxy, wenn paper-global.yml schreibbar ist."
        )
    return notes


def _set_spigot_bungeecord(base_path: Path, enabled: bool) -> None:
    """spigot.yml ``settings.bungeecord`` setzen (partiell; wirft bei Schreibfehler)."""
    import yaml

    spigot_yml = base_path / "spigot.yml"
    data: dict = {}
    if spigot_yml.exists():
        loaded = yaml.safe_load(spigot_yml.read_text(encoding="utf-8", errors="ignore"))
        if isinstance(loaded, dict):
            data = loaded
    settings = data.get("settings")
    if not isinstance(settings, dict):
        settings = {}
    # Beim Ausschalten nichts anlegen, wenn ohnehin schon aus/fehlend.
    if not enabled and not settings.get("bungeecord"):
        return
    settings["bungeecord"] = bool(enabled)
    data["settings"] = settings
    spigot_yml.write_text(
        yaml.safe_dump(data, sort_keys=False, allow_unicode=True), encoding="utf-8"
    )


def _set_paper_velocity_enabled(base_path: Path, enabled: bool) -> None:
    """paper-global.yml ``proxies.velocity.enabled`` setzen, wenn die Datei existiert (best-effort)."""
    paper_global = base_path / "config" / "paper-global.yml"
    if not paper_global.exists():
        return
    try:
        import yaml

        pdata = yaml.safe_load(paper_global.read_text(encoding="utf-8", errors="ignore")) or {}
        vel = ((pdata.get("proxies") or {}).get("velocity") or {})
        if isinstance(pdata, dict) and isinstance(vel, dict) and bool(vel.get("enabled")) != enabled:
            vel["enabled"] = enabled
            pdata.setdefault("proxies", {})["velocity"] = vel
            paper_global.write_text(
                yaml.safe_dump(pdata, sort_keys=False, allow_unicode=True), encoding="utf-8"
            )
    except Exception:  # noqa: BLE001 - Paper-spezifisch, darf Spigot nicht stoeren
        pass


def _apply_legacy_forwarding(server: Server) -> list[str]:
    """Legacy (BungeeCord) Forwarding: spigot.yml ``settings.bungeecord=true`` (Spigot UND Paper).

    Damit reicht der (online-mode=true) Proxy die ECHTE Mojang-UUID + Skin durch. Bei Paper wird
    zusaetzlich das modern-velocity-Forwarding deaktiviert (sonst Doppel-Forwarding -> Kick). Nur bei
    Erfolg online-mode=false + loopback; sonst Fail-safe online-mode=true (kein offline-mode ohne
    Forwarding-Enforcement)."""
    notes: list[str] = []
    base_path = Path(server.base_path).expanduser().resolve()
    if not base_path.exists():
        return notes

    ok = False
    try:
        _set_spigot_bungeecord(base_path, True)
        # Paper: modern-velocity-Forwarding deaktivieren, damit es nicht mit legacy kollidiert.
        _set_paper_velocity_enabled(base_path, False)
        ok = True
    except Exception as exc:  # noqa: BLE001 - darf den Start nie stoeren
        notes.append(f"Legacy-Forwarding (spigot.yml) fehlgeschlagen: {exc}")

    if ok:
        _upsert_server_property(server, "online-mode", "false")
        _upsert_server_property(server, "server-ip", "127.0.0.1")
        notes.append("Velocity-Forwarding (legacy/BungeeCord) aktiv: online-mode=false, loopback, "
                     "spigot.yml bungeecord=true (echte Mojang-UUID + Skin).")
    else:
        _upsert_server_property(server, "online-mode", "true")
        notes.append("Legacy-Forwarding NICHT geschrieben -> online-mode=true erzwungen (Sicherheit).")
    return notes


def is_behind_front_proxy(server: Server, *, network_mode: str | None = None) -> bool:
    """Server laeuft auf seinem internen Port.

    Nur Sleep-Server: der Sleep-Proxy belegt den oeffentlichen Port und leitet auf
    den internen weiter. Gateway-Server bleiben auf ihrem OEFFENTLICHEN Port (direkt
    erreichbar); das Gateway leitet nur transparent dorthin. Der ``network_mode``-
    Parameter bleibt fuer Aufrufer-Kompatibilitaet erhalten.
    """
    return bool(server.sleep_enabled)


def effective_server_port(server: Server, *, network_mode: str | None = None) -> int | None:
    """Port, auf dem der echte MC-Server laeuft. Sleep-Server laufen auf
    ``sleep_internal_port`` (der Proxy belegt den oeffentlichen Port), sonst der
    normale Port."""
    if is_behind_front_proxy(server, network_mode=network_mode) and server.sleep_internal_port:
        return int(server.sleep_internal_port)
    return server.port


def active_front_port(db: Session) -> int | None:
    """Der belegte Front-Door-Port (Gateway ODER Velocity), sonst None.

    In beiden Modi bindet der Front-Door den network_port -> ein normaler Server darf
    diesen Port nicht als eigenen Bind bekommen (sonst Bind-Konflikt ohne Warnung)."""
    from app.services import app_setting_service

    if app_setting_service.get_network_mode(db) in ("gateway", "velocity"):
        return app_setting_service.get_network_port(db)
    return None


def sync_server_settings_to_files(
    server: Server, *, network_mode: str | None = None
) -> list[str]:
    warnings: list[str] = []

    internal_port = effective_server_port(server, network_mode=network_mode)
    if internal_port is not None:
        warning = _upsert_server_property(server, "server-port", str(internal_port))
        if warning:
            warnings.append(warning)

    if server.server_type in {"forge", "neoforge"}:
        warning = _sync_forge_jvm_args(server)
        if warning:
            warnings.append(warning)
        if server.server_type == "forge":
            # Legacy-Forge (<1.17) startet ueber ein generiertes run.bat mit
            # literalen -Xms/-Xmx – dort die Speicher-Flags mitziehen.
            warning = _sync_legacy_forge_run_bat_memory(server)
            if warning:
                warnings.append(warning)
    elif server.start_mode == "bat":
        warning = _sync_bat_start_memory(server)
        if warning:
            warnings.append(warning)
    elif server.start_mode == "command" and server.start_command:
        server.start_command = _apply_memory_flags_to_command(
            server.start_command,
            server.memory_min_mb,
            server.memory_max_mb,
        )

    return warnings


def prepare_ports_before_start(db: Session, server: Server) -> list[str]:
    """Vor dem Start Ports/``server.properties`` automatisch angleichen.

    - Sleep-/Gateway-Server bekommen einen internen Port, der weder mit ihrem
      oeffentlichen Port noch mit dem Gateway-Port kollidiert (bei Bedarf wird ein
      neuer vergeben). So loesen sich Port-Konflikte selbst – z.B. ein veraltetes
      ``server-port=25565``, das jetzt dem Gateway gehoert.
    - Danach wird ``server.properties`` auf den effektiven Port geschrieben, damit
      der Serverprozess garantiert den richtigen Port bindet.
    """
    from app.services import app_setting_service, port_service

    warnings: list[str] = []
    # Der Front-Door-Port (Gateway ODER Velocity) ist nur "reserviert", wenn auch
    # eine Eingangstuer laeuft. Sonst ist 25565 ein ganz normaler Port -> nicht
    # anfassen (sonst zeigte eine Neuvergabe einen bereits gebundenen Proxy ins Leere).
    mode = app_setting_service.get_network_mode(db)
    behind_proxy = is_behind_front_proxy(server, network_mode=mode)
    front_port = active_front_port(db)
    front_label = "Netzwerk-Port"

    reallocated = False
    if behind_proxy:
        internal = server.sleep_internal_port
        collides = (
            not internal
            or internal == server.port
            or (front_port is not None and internal == front_port)
        )
        if collides:
            try:
                server.sleep_internal_port = port_service.allocate_server_port(
                    db,
                    exclude={p for p in (server.port, front_port) if p},
                    exclude_server_id=server.id,
                )
                db.add(server)
                db.commit()
                reallocated = True
            except ValueError as exc:
                warnings.append(str(exc))
    elif front_port is not None and server.port and server.port == front_port:
        # Standalone-Server auf dem Front-Door-Port kann nicht binden. Der
        # oeffentliche Port ist spielerseitig -> nicht stillschweigend verschieben.
        warnings.append(
            f"Port {server.port} ist der {front_label} und kann nicht gebunden werden. "
            "Bitte einen anderen Port setzen oder den Server ueber das Netzwerk erreichbar machen."
        )

    warnings.extend(sync_server_settings_to_files(server, network_mode=mode))

    # Wurde der interne Port neu vergeben, den Sleep-Proxy auffrischen, damit sein
    # Forward-Ziel auf den neuen Port zeigt (der Listener rebindet, siehe start_proxy).
    if reallocated:
        try:
            from app.services import sleep_proxy_service

            sleep_proxy_service.reconcile_proxies()
        except Exception:  # noqa: BLE001
            pass

    return warnings


def update_server_settings(
    db: Session,
    server: Server,
    *,
    mc_version: str | None,
    loader_version: str | None,
    java_profile_id: int | None,
    memory_min_mb: int | None,
    memory_max_mb: int | None,
    port: int | None,
    auto_restart: bool,
    auto_start_with_manager: bool,
    start_mode: str | None,
    start_command: str | None,
    start_bat_path: str | None,
    sleep_enabled: bool = False,
    sleep_delay_seconds: int | None = None,
    gateway_enabled: bool = False,
    gateway_hostname: str | None = None,
    gateway_is_default: bool = False,
) -> tuple[Server, list[str]]:
    if mc_version is not None:
        stripped_version = mc_version.strip()
        if stripped_version:
            server.mc_version = stripped_version
    memory_min_mb, memory_max_mb = validate_memory_bounds(memory_min_mb, memory_max_mb)
    server.loader_version = (loader_version or "").strip() or None
    server.java_profile_id = java_profile_id
    if server.java_profile_id is None:
        auto_profile = choose_best_java_profile(db, mc_version=server.mc_version)
        if auto_profile is not None:
            server.java_profile_id = auto_profile.id
            warnings = [f"Java-Profil automatisch gesetzt: {auto_profile.name}"]
        else:
            warnings = []
    else:
        warnings = []
    server.memory_min_mb = memory_min_mb
    server.memory_max_mb = memory_max_mb
    server.port = port
    server.auto_restart = auto_restart
    server.auto_start_with_manager = auto_start_with_manager
    if start_mode:
        server.start_mode = start_mode
    server.start_command = start_command
    server.start_bat_path = start_bat_path

    server.sleep_enabled = bool(sleep_enabled)
    if sleep_delay_seconds is not None:
        server.sleep_delay_seconds = max(0, int(sleep_delay_seconds))
    if server.sleep_enabled:
        # Interner Port fuer den echten Server hinter dem Proxy sicherstellen.
        # Der Front-Door-Port (Velocity) darf NIE als interner Port dienen.
        from app.services import port_service

        front_port = active_front_port(db)
        if not server.port:
            warnings.append(
                "Sleep-Modus benoetigt einen festen Port - bitte Port setzen."
            )
            server.sleep_enabled = False
        elif (
            not server.sleep_internal_port
            or server.sleep_internal_port in {server.port, front_port}
        ):
            preferred = server.port + 1 if server.port < 65535 else None
            try:
                server.sleep_internal_port = port_service.allocate_server_port(
                    db,
                    preferred=preferred,
                    exclude={p for p in (server.port, front_port) if p},
                    exclude_server_id=server.id,
                )
            except ValueError as exc:
                warnings.append(str(exc))
                server.sleep_enabled = False
        if server.sleep_enabled and server.status in {
            "running",
            "starting",
            "restarting",
        }:
            warnings.append(
                "Sleep-Modus aktiviert: bitte den Server neu starten, damit er "
                "auf den internen Port wechselt und der Proxy den oeffentlichen "
                "Port uebernehmen kann."
            )

    # --- Gateway-Routing (Hostname-Alias, jeder Typ/jede Version) ---
    normalized_alias = normalize_gateway_hostname(gateway_hostname)
    server.gateway_enabled = bool(gateway_enabled)
    server.gateway_hostname = None
    server.gateway_is_default = False
    if server.gateway_enabled:
        if not normalized_alias:
            warnings.append("Gateway-Alias (Hostname) ist erforderlich.")
            server.gateway_enabled = False
        elif not is_valid_gateway_hostname(normalized_alias):
            warnings.append(
                f"Ungueltiger Alias '{normalized_alias}' - erlaubt sind nur "
                "Kleinbuchstaben, Ziffern, '-' und '.'."
            )
            server.gateway_enabled = False
        elif gateway_hostname_taken(db, normalized_alias, exclude_server_id=server.id):
            warnings.append(
                f"Der Alias '{normalized_alias}' ist bereits vergeben - bitte einen "
                "anderen waehlen."
            )
            server.gateway_enabled = False
        else:
            server.gateway_hostname = normalized_alias
            server.gateway_is_default = bool(gateway_is_default)
            # Gateway = reiner Router: der Server bleibt auf seinem OEFFENTLICHEN
            # Port (direkt erreichbar). Er darf nur nicht der Netzwerk-Port selbst
            # sein (den belegt das Gateway).
            from app.services import app_setting_service

            front_port = app_setting_service.get_network_port(db)
            if server.port and server.port == front_port:
                warnings.append(
                    f"Port {server.port} ist der Netzwerk-Port (Gateway) und kann nicht "
                    "vom Server belegt werden. Bitte einen anderen Port setzen."
                )
                server.gateway_enabled = False
                server.gateway_hostname = None
                server.gateway_is_default = False

    if server.gateway_is_default:
        db.execute(update(Server).where(Server.id != server.id).values(gateway_is_default=False))

    # Eine Transfer-Lobby (1.20.5+) kann Spieler nur hierher schicken, wenn der
    # Server Transfers akzeptiert. Sofort auf die Platte schreiben (greift beim
    # naechsten (Neu-)Start des Servers - ein laufender Server liest es nicht neu).
    if server.gateway_enabled:
        enable_accept_transfers(server)

    warnings.extend(sync_server_settings_to_files(server))

    db.add(server)
    db.commit()
    db.refresh(server)

    # Proxy-/Gateway-Zustand an die neue Einstellung angleichen.
    try:
        from app.services import sleep_proxy_service

        sleep_proxy_service.reconcile_proxies()
    except Exception:  # noqa: BLE001 - UI-Update darf daran nicht scheitern
        pass
    try:
        from app.services import gateway_service

        gateway_service.reconcile_gateway()
    except Exception:  # noqa: BLE001
        pass
    # Lobby-Plugin-config an die (evtl. geaenderten) Gateway-Aliase angleichen.
    try:
        from app.services import lobby_service

        lobby_service.sync_lobby_plugin(db)
    except Exception:  # noqa: BLE001
        pass

    return server, warnings


def create_server_from_import(db: Session, data: ServerImportConfirm) -> Server:
    create_data = ServerCreate(
        name=data.name.strip(),
        server_type=data.server_type,
        mc_version=data.mc_version or "unknown",
        loader_version=data.loader_version,
        base_path=data.base_path,
        start_mode=data.start_mode,
        start_command=data.start_command,
        start_bat_path=data.start_bat_path,
        java_profile_id=data.java_profile_id,
        memory_min_mb=data.memory_min_mb,
        memory_max_mb=data.memory_max_mb,
        port=data.port,
    )
    return create_server(db, create_data)


def get_dashboard_summary(db: Session, user: User) -> dict[str, object]:
    visible_servers = list_servers_for_user(db, user)
    running_servers = sum(1 for server in visible_servers if server.status == "running")

    user_count: int | None = None
    if user.role == UserRole.SUPER_ADMIN.value:
        user_count = int(db.scalar(select(func.count(User.id))) or 0)

    return {
        "total_servers": len(visible_servers),
        "running_servers": running_servers,
        "user_count": user_count,
        "servers": visible_servers,
    }
