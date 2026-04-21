from __future__ import annotations

import json
import re
import secrets
import shutil
import time
import urllib.parse
import zipfile
from pathlib import Path, PurePosixPath
from typing import Iterable

from sqlalchemy import delete
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models.installed_content import InstalledContent
from app.models.server import Server
from app.schemas.modpack import (
    ModpackExecuteResponse,
    ModpackImportEntry,
    ModpackPreviewResponse,
    ModpackPreviewSnapshot,
)
from app.services import audit_service, content_service


_MODRINTH_VERSION_URL_RE = re.compile(
    r"/data/(?P<project>[^/]+)/versions/(?P<version>[^/]+)/",
    re.IGNORECASE,
)
_VALID_IMPORT_SOURCES = {"local_archive", "modrinth", "curseforge"}
_SUPPORTED_SERVER_TYPES = {"vanilla", "paper", "spigot", "fabric", "forge"}
_MAX_PREVIEW_AGE_SECONDS = 24 * 60 * 60


def _preview_store_root() -> Path:
    settings = get_settings()
    root = settings.data_dir / "modpack_previews"
    root.mkdir(parents=True, exist_ok=True)
    return root


def _token_dir(token: str) -> Path:
    return _preview_store_root() / token


def _snapshot_file(token: str) -> Path:
    return _token_dir(token) / "snapshot.json"


def _archive_file(token: str) -> Path:
    return _token_dir(token) / "archive.zip"


def _cleanup_stale_previews() -> None:
    root = _preview_store_root()
    now_ts = time.time()
    for child in root.iterdir():
        if not child.is_dir():
            continue
        try:
            age = now_ts - child.stat().st_mtime
        except OSError:
            continue
        if age <= _MAX_PREVIEW_AGE_SECONDS:
            continue
        shutil.rmtree(child, ignore_errors=True)


def _safe_relative_path(raw_path: str) -> Path:
    normalized = raw_path.replace("\\", "/").strip()
    if not normalized:
        raise ValueError("Leerer relativer Pfad.")
    pure = PurePosixPath(normalized)
    if pure.is_absolute():
        raise ValueError("Absolute Pfade sind nicht erlaubt.")
    parts = [part for part in pure.parts if part not in {"", "."}]
    if any(part == ".." for part in parts):
        raise ValueError("Pfad darf nicht aus dem Zielordner ausbrechen.")
    if not parts:
        raise ValueError("Ungueltiger relativer Pfad.")
    return Path(*parts)


def _recommended_server_type(loader: str | None) -> str:
    normalized = (loader or "").strip().lower()
    if normalized in _SUPPORTED_SERVER_TYPES:
        return normalized
    if normalized in {"quilt", "neoforge"}:
        return "fabric" if normalized == "quilt" else "forge"
    return "vanilla"


def _parse_loader_id(loader_id: str | None) -> tuple[str | None, str | None]:
    if not loader_id:
        return None, None
    normalized = loader_id.strip()
    lower = normalized.lower()
    known_prefixes = (
        "forge-",
        "fabric-loader-",
        "fabric-",
        "quilt-loader-",
        "quilt-",
        "neoforge-",
        "paper-",
        "spigot-",
        "vanilla-",
    )
    for prefix in known_prefixes:
        if lower.startswith(prefix):
            loader = prefix.replace("-loader", "").rstrip("-")
            version = normalized[len(prefix) :].strip() or None
            return loader, version
    return lower, None


def _modrinth_loader_and_version(dependencies: dict[str, object]) -> tuple[str | None, str | None]:
    keys = (
        ("forge", "forge"),
        ("fabric-loader", "fabric"),
        ("quilt-loader", "quilt"),
        ("neoforge", "neoforge"),
        ("paper", "paper"),
        ("spigot", "spigot"),
    )
    for key, loader_name in keys:
        value = dependencies.get(key)
        if value:
            return loader_name, str(value)
    return None, None


def _extract_modrinth_ids_from_url(url: str) -> tuple[str | None, str | None]:
    parsed = urllib.parse.urlparse(url)
    match = _MODRINTH_VERSION_URL_RE.search(parsed.path)
    if not match:
        return None, None
    return match.group("project"), match.group("version")


def _count_files_under_roots(zip_file: zipfile.ZipFile, roots: Iterable[str]) -> int:
    prefixes = [f"{root.strip('/').replace('\\', '/')}/" for root in roots if root.strip("/")]
    if not prefixes:
        return 0
    count = 0
    for info in zip_file.infolist():
        if info.is_dir():
            continue
        normalized = info.filename.replace("\\", "/")
        if any(normalized.startswith(prefix) for prefix in prefixes):
            count += 1
    return count


def _parse_modrinth_archive(token: str, archive_path: Path, source: str, source_ref: str | None) -> ModpackPreviewSnapshot:
    warnings: list[str] = []
    with zipfile.ZipFile(archive_path, "r") as zipped:
        try:
            index_raw = zipped.read("modrinth.index.json")
        except KeyError as exc:
            raise ValueError("modrinth.index.json fehlt im Archiv.") from exc

        index_payload = json.loads(index_raw.decode("utf-8"))
        dependencies = index_payload.get("dependencies") or {}
        if not isinstance(dependencies, dict):
            dependencies = {}

        loader, loader_version = _modrinth_loader_and_version(dependencies)
        mc_version = str(dependencies.get("minecraft") or "") or None
        files = index_payload.get("files") or []
        if not isinstance(files, list):
            files = []

        entries: list[ModpackImportEntry] = []
        for file_item in files:
            if not isinstance(file_item, dict):
                continue
            raw_path = str(file_item.get("path") or "").strip()
            if not raw_path:
                continue
            try:
                safe_path = _safe_relative_path(raw_path)
            except ValueError:
                warnings.append(f"Datei mit ungueltigem Pfad uebersprungen: {raw_path}")
                continue

            normalized_path = safe_path.as_posix().lower()
            if not (normalized_path.startswith("mods/") or normalized_path.startswith("plugins/")):
                continue

            downloads = file_item.get("downloads") or []
            if not isinstance(downloads, list):
                downloads = []
            download_url = next(
                (
                    str(item).strip()
                    for item in downloads
                    if str(item).strip().startswith(("http://", "https://"))
                ),
                "",
            )
            if not download_url:
                warnings.append(f"Keine Download-URL fuer {safe_path.as_posix()}.")
                continue

            project_id, version_id = _extract_modrinth_ids_from_url(download_url)
            entries.append(
                ModpackImportEntry(
                    name=safe_path.name,
                    path=safe_path.as_posix(),
                    provider_name="modrinth",
                    content_type="plugin" if normalized_path.startswith("plugins/") else "mod",
                    project_id=project_id,
                    version_id=version_id,
                    download_url=download_url,
                )
            )

        override_roots = [root for root in ("overrides", "server-overrides") if _count_files_under_roots(zipped, [root]) > 0]
        override_file_count = _count_files_under_roots(zipped, override_roots)

        return ModpackPreviewSnapshot(
            token=token,
            source=source,
            source_ref=source_ref,
            pack_format="modrinth",
            pack_name=str(index_payload.get("name") or "Modrinth Modpack"),
            pack_version=str(index_payload.get("versionId") or "") or None,
            mc_version=mc_version,
            loader=loader,
            loader_version=loader_version,
            recommended_server_type=_recommended_server_type(loader),
            entries=entries,
            override_roots=override_roots,
            override_file_count=override_file_count,
            warnings=warnings,
        )


def _parse_curseforge_archive(token: str, archive_path: Path, source: str, source_ref: str | None) -> ModpackPreviewSnapshot:
    warnings: list[str] = []
    with zipfile.ZipFile(archive_path, "r") as zipped:
        try:
            manifest_raw = zipped.read("manifest.json")
        except KeyError as exc:
            raise ValueError("manifest.json fehlt im CurseForge-Archiv.") from exc

        manifest_payload = json.loads(manifest_raw.decode("utf-8"))
        minecraft = manifest_payload.get("minecraft") or {}
        if not isinstance(minecraft, dict):
            minecraft = {}

        mc_version = str(minecraft.get("version") or "") or None
        mod_loaders = minecraft.get("modLoaders") or []
        if not isinstance(mod_loaders, list):
            mod_loaders = []
        loader_id = None
        for item in mod_loaders:
            if not isinstance(item, dict):
                continue
            if bool(item.get("primary")):
                loader_id = str(item.get("id") or "").strip()
                break
        if not loader_id and mod_loaders and isinstance(mod_loaders[0], dict):
            loader_id = str(mod_loaders[0].get("id") or "").strip()
        loader, loader_version = _parse_loader_id(loader_id)

        files = manifest_payload.get("files") or []
        if not isinstance(files, list):
            files = []
        entries: list[ModpackImportEntry] = []
        for file_item in files:
            if not isinstance(file_item, dict):
                continue
            project_id = int(file_item.get("projectID") or 0)
            file_id = int(file_item.get("fileID") or 0)
            if project_id <= 0 or file_id <= 0:
                continue
            entries.append(
                ModpackImportEntry(
                    name=f"project-{project_id}",
                    path=f"mods/{project_id}-{file_id}.jar",
                    provider_name="curseforge",
                    content_type="mod",
                    project_id=str(project_id),
                    version_id=str(file_id),
                )
            )

        overrides_root = str(manifest_payload.get("overrides") or "overrides").strip()
        override_roots = [overrides_root] if overrides_root else []
        override_file_count = _count_files_under_roots(zipped, override_roots)

        return ModpackPreviewSnapshot(
            token=token,
            source=source,
            source_ref=source_ref,
            pack_format="curseforge",
            pack_name=str(manifest_payload.get("name") or "CurseForge Modpack"),
            pack_version=str(manifest_payload.get("version") or "") or None,
            mc_version=mc_version,
            loader=loader,
            loader_version=loader_version,
            recommended_server_type=_recommended_server_type(loader),
            entries=entries,
            override_roots=override_roots,
            override_file_count=override_file_count,
            warnings=warnings,
        )


def _parse_archive(token: str, archive_path: Path, source: str, source_ref: str | None) -> ModpackPreviewSnapshot:
    try:
        with zipfile.ZipFile(archive_path, "r") as zipped:
            names = {name.replace("\\", "/") for name in zipped.namelist()}
    except zipfile.BadZipFile as exc:
        raise ValueError("Ungueltiges Archiv. Bitte ZIP/MRPACK verwenden.") from exc

    if "modrinth.index.json" in names:
        return _parse_modrinth_archive(token, archive_path, source, source_ref)
    if "manifest.json" in names:
        return _parse_curseforge_archive(token, archive_path, source, source_ref)
    raise ValueError("Archiv enthaelt weder modrinth.index.json noch manifest.json.")


def _download_modrinth_archive(preview_archive_path: Path, reference: str | None, explicit_version_id: str | None) -> str:
    version_id = (explicit_version_id or "").strip()
    source_ref = (reference or "").strip() or None
    if source_ref and "modrinth.com" in source_ref.lower():
        parsed = urllib.parse.urlparse(source_ref)
        parts = [part for part in parsed.path.split("/") if part]
        if "version" in (part.lower() for part in parts):
            version_id = parts[-1]
        elif parts:
            source_ref = parts[-1]

    if version_id:
        version_payload = content_service._request_json(
            f"{content_service.MODRINTH_BASE}/version/{version_id}",
            headers=content_service._modrinth_headers(),
        )
    else:
        project_ref = (source_ref or "").strip()
        if not project_ref:
            raise ValueError("Modrinth Referenz oder Version-ID ist erforderlich.")
        versions_payload = content_service._request_json(
            f"{content_service.MODRINTH_BASE}/project/{project_ref}/version",
            headers=content_service._modrinth_headers(),
        )
        versions = versions_payload if isinstance(versions_payload, list) else []
        if not versions:
            raise ValueError("Keine Modrinth-Versionen fuer dieses Projekt gefunden.")
        versions.sort(key=lambda item: str(item.get("date_published") or ""), reverse=True)
        version_payload = versions[0]
        version_id = str(version_payload.get("id") or "").strip()
        if not version_id:
            raise ValueError("Modrinth Version-ID konnte nicht ermittelt werden.")

    files = version_payload.get("files") or []
    if not isinstance(files, list) or not files:
        raise ValueError("Die Modrinth-Version enthaelt keine herunterladbare Datei.")
    primary = next((item for item in files if isinstance(item, dict) and item.get("primary")), files[0])
    if not isinstance(primary, dict):
        raise ValueError("Modrinth Archivdatei konnte nicht gelesen werden.")
    download_url = str(primary.get("url") or "").strip()
    if not download_url:
        raise ValueError("Download-URL fuer Modrinth Modpack fehlt.")
    content_service._download_file(download_url, preview_archive_path, headers=content_service._modrinth_headers())
    return version_id


def _parse_curseforge_reference(reference: str | None) -> tuple[int | None, int | None]:
    raw = (reference or "").strip()
    if not raw:
        return None, None
    numbers = [int(match) for match in re.findall(r"\d+", raw)]
    if len(numbers) < 2:
        return None, None
    return numbers[-2], numbers[-1]


def _download_curseforge_archive(
    preview_archive_path: Path,
    *,
    project_id: int | None,
    file_id: int | None,
    reference: str | None,
) -> tuple[int, int]:
    ref_project_id, ref_file_id = _parse_curseforge_reference(reference)
    resolved_project_id = int(project_id or 0) or int(ref_project_id or 0)
    resolved_file_id = int(file_id or 0) or int(ref_file_id or 0)
    if resolved_project_id <= 0 or resolved_file_id <= 0:
        raise ValueError("CurseForge braucht Projekt-ID und Datei-ID.")

    file_payload = content_service._request_json(
        f"{content_service.CURSEFORGE_BASE}/v1/mods/{resolved_project_id}/files/{resolved_file_id}",
        headers=content_service._curseforge_headers(),
    )
    data = file_payload.get("data") if isinstance(file_payload, dict) else {}
    if not isinstance(data, dict):
        data = {}
    download_url = str(data.get("downloadUrl") or "").strip()
    if not download_url:
        url_payload = content_service._request_json(
            f"{content_service.CURSEFORGE_BASE}/v1/mods/{resolved_project_id}/files/{resolved_file_id}/download-url",
            headers=content_service._curseforge_headers(),
        )
        data2 = url_payload.get("data") if isinstance(url_payload, dict) else {}
        if isinstance(data2, dict):
            download_url = str(data2.get("url") or "").strip()
    if not download_url:
        raise ValueError("CurseForge Download-URL konnte nicht ermittelt werden.")
    content_service._download_file(download_url, preview_archive_path, headers=content_service._curseforge_headers())
    return resolved_project_id, resolved_file_id


def _write_snapshot(snapshot: ModpackPreviewSnapshot) -> None:
    snapshot_path = _snapshot_file(snapshot.token)
    snapshot_path.parent.mkdir(parents=True, exist_ok=True)
    snapshot_path.write_text(snapshot.model_dump_json(indent=2), encoding="utf-8")


def _snapshot_to_response(snapshot: ModpackPreviewSnapshot) -> ModpackPreviewResponse:
    return ModpackPreviewResponse(
        token=snapshot.token,
        source=snapshot.source,
        source_ref=snapshot.source_ref,
        pack_name=snapshot.pack_name,
        pack_version=snapshot.pack_version,
        mc_version=snapshot.mc_version,
        loader=snapshot.loader,
        loader_version=snapshot.loader_version,
        recommended_server_type=snapshot.recommended_server_type,
        entry_count=len(snapshot.entries),
        entries=snapshot.entries,
        override_file_count=snapshot.override_file_count,
        warnings=snapshot.warnings,
    )


def create_preview(
    *,
    source: str,
    modrinth_reference: str | None = None,
    modrinth_version_id: str | None = None,
    curseforge_project_id: int | None = None,
    curseforge_file_id: int | None = None,
    curseforge_reference: str | None = None,
    local_archive_name: str | None = None,
    local_archive_bytes: bytes | None = None,
) -> ModpackPreviewResponse:
    normalized_source = (source or "").strip().lower()
    if normalized_source not in _VALID_IMPORT_SOURCES:
        raise ValueError("Ungueltige Modpack-Quelle.")

    _cleanup_stale_previews()
    token = secrets.token_urlsafe(18).replace("-", "").replace("_", "")
    preview_dir = _token_dir(token)
    preview_dir.mkdir(parents=True, exist_ok=True)
    preview_archive = _archive_file(token)

    source_ref: str | None = None
    try:
        if normalized_source == "local_archive":
            if not local_archive_bytes:
                raise ValueError("Bitte eine lokale ZIP/MRPACK-Datei hochladen.")
            preview_archive.write_bytes(local_archive_bytes)
            source_ref = (local_archive_name or "local_archive").strip() or "local_archive"
        elif normalized_source == "modrinth":
            resolved_version_id = _download_modrinth_archive(
                preview_archive,
                reference=modrinth_reference,
                explicit_version_id=modrinth_version_id,
            )
            source_ref = resolved_version_id
        else:
            project_id, file_id = _download_curseforge_archive(
                preview_archive,
                project_id=curseforge_project_id,
                file_id=curseforge_file_id,
                reference=curseforge_reference,
            )
            source_ref = f"{project_id}:{file_id}"

        snapshot = _parse_archive(token, preview_archive, normalized_source, source_ref)
        _write_snapshot(snapshot)
        return _snapshot_to_response(snapshot)
    except Exception:
        shutil.rmtree(preview_dir, ignore_errors=True)
        raise


def load_preview(token: str) -> ModpackPreviewSnapshot:
    normalized = (token or "").strip()
    if not normalized:
        raise ValueError("Preview-Token fehlt.")
    snapshot_path = _snapshot_file(normalized)
    if not snapshot_path.exists():
        raise ValueError("Preview nicht gefunden oder abgelaufen.")
    payload = snapshot_path.read_text(encoding="utf-8")
    snapshot = ModpackPreviewSnapshot.model_validate_json(payload)
    if snapshot.token != normalized:
        raise ValueError("Preview-Token ungueltig.")
    return snapshot


def discard_preview(token: str) -> None:
    normalized = (token or "").strip()
    if not normalized:
        return
    shutil.rmtree(_token_dir(normalized), ignore_errors=True)


def _download_direct_entry(entry: ModpackImportEntry, target_file: Path) -> None:
    target_file.parent.mkdir(parents=True, exist_ok=True)
    url = (entry.download_url or "").strip()
    if not url:
        raise ValueError("Direkte Download-URL fehlt.")
    headers: dict[str, str] | None = None
    if entry.provider_name == "modrinth":
        headers = content_service._modrinth_headers()
    content_service._download_file(url, target_file, headers=headers)


def _upsert_direct_installed_entry(
    db: Session,
    *,
    server: Server,
    entry: ModpackImportEntry,
    target_file: Path,
    user_id: int | None,
) -> None:
    if entry.content_type not in {"mod", "plugin"}:
        return
    file_name = target_file.name
    external_project_id = entry.project_id or entry.path
    external_version_id = entry.version_id or (entry.download_url or "")

    db.execute(
        delete(InstalledContent)
        .where(InstalledContent.server_id == server.id)
        .where(InstalledContent.file_name == file_name)
    )

    installed_entry = InstalledContent(
        server_id=server.id,
        provider_name=entry.provider_name or "modpack",
        content_type=entry.content_type,
        external_project_id=str(external_project_id),
        external_version_id=str(external_version_id),
        name=entry.name or file_name,
        version_label=entry.version_id,
        file_name=file_name,
        installed_by_user_id=user_id,
    )
    db.add(installed_entry)
    db.commit()


def _apply_overrides(
    *,
    snapshot: ModpackPreviewSnapshot,
    archive_path: Path,
    server: Server,
) -> tuple[int, list[str]]:
    warnings: list[str] = []
    copied = 0
    roots = [root.strip("/").replace("\\", "/") for root in snapshot.override_roots if root.strip("/")]
    if not roots:
        return 0, warnings

    base_path = Path(server.base_path).expanduser().resolve()
    with zipfile.ZipFile(archive_path, "r") as zipped:
        for info in zipped.infolist():
            if info.is_dir():
                continue
            normalized = info.filename.replace("\\", "/")
            matching_root = next(
                (root for root in roots if normalized.startswith(f"{root}/")),
                None,
            )
            if not matching_root:
                continue
            relative_payload_path = normalized[len(matching_root) + 1 :]
            if not relative_payload_path:
                continue
            try:
                safe_relative = _safe_relative_path(relative_payload_path)
            except ValueError:
                warnings.append(f"Override-Pfad uebersprungen: {relative_payload_path}")
                continue
            target = (base_path / safe_relative).resolve()
            if base_path not in [target, *target.parents]:
                warnings.append(f"Unsicherer Override-Pfad uebersprungen: {relative_payload_path}")
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            with zipped.open(info, "r") as source_fp:
                with target.open("wb") as target_fp:
                    shutil.copyfileobj(source_fp, target_fp)
            copied += 1

    return copied, warnings


def execute_preview(
    db: Session,
    *,
    snapshot: ModpackPreviewSnapshot,
    server: Server,
    initiated_by_user_id: int | None,
    created_server: bool = False,
    notes: list[str] | None = None,
) -> ModpackExecuteResponse:
    archive_path = _archive_file(snapshot.token)
    if not archive_path.exists():
        raise ValueError("Import-Archiv nicht mehr verfuegbar.")

    warnings = list(snapshot.warnings)
    resolved_notes = list(notes or [])
    installed_count = 0

    for entry in snapshot.entries:
        try:
            if (
                entry.provider_name == "modrinth"
                and entry.project_id
                and entry.version_id
            ):
                content_service.install_modrinth(
                    db,
                    server,
                    entry.project_id,
                    entry.version_id,
                    entry.content_type,
                    initiated_by_user_id,
                )
                installed_count += 1
                continue

            if (
                entry.provider_name == "curseforge"
                and entry.project_id
                and entry.version_id
            ):
                content_service.install_curseforge(
                    db,
                    server,
                    int(entry.project_id),
                    int(entry.version_id),
                    entry.content_type,
                    initiated_by_user_id,
                )
                installed_count += 1
                continue

            relative_path = _safe_relative_path(entry.path)
            server_base = Path(server.base_path).expanduser().resolve()
            target_file = (server_base / relative_path).resolve()
            if server_base not in [target_file, *target_file.parents]:
                raise ValueError("Zielpfad liegt ausserhalb des Serverordners.")
            _download_direct_entry(entry, target_file)
            _upsert_direct_installed_entry(
                db,
                server=server,
                entry=entry,
                target_file=target_file,
                user_id=initiated_by_user_id,
            )
            installed_count += 1
        except Exception as exc:
            warnings.append(f"{entry.name}: {exc}")

    overrides_copied, override_warnings = _apply_overrides(
        snapshot=snapshot,
        archive_path=archive_path,
        server=server,
    )
    warnings.extend(override_warnings)

    audit_service.log_action(
        db,
        action="modpack.import_execute",
        user_id=initiated_by_user_id,
        server_id=server.id,
        details=(
            f"source={snapshot.source} pack={snapshot.pack_name} "
            f"installed={installed_count} overrides={overrides_copied} warnings={len(warnings)}"
        ),
    )

    return ModpackExecuteResponse(
        server_id=server.id,
        server_name=server.name,
        created_server=created_server,
        installed_count=installed_count,
        overrides_copied=overrides_copied,
        warnings=warnings,
        notes=resolved_notes,
    )
