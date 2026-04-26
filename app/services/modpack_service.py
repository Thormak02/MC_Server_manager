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

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models.installed_content import InstalledContent
from app.models.pending_modpack_install import PendingModpackInstall
from app.models.server import Server
from app.models.server_modpack_state import ServerModpackState
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
_SUPPORTED_SERVER_TYPES = {"vanilla", "paper", "spigot", "fabric", "forge", "neoforge"}
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
    if normalized == "quilt":
        return "fabric"
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
        parts = [part for part in parsed.path.split("/") if part]
        if not parts:
            return None, None
        lower_parts = [part.lower() for part in parts]

        project_id: str | None = None
        version_id: str | None = None

        if "version" in lower_parts:
            index = lower_parts.index("version")
            if index + 1 < len(parts):
                version_id = parts[index + 1]
            if index - 1 >= 0:
                parent = parts[index - 1]
                parent_lower = parent.lower()
                if parent_lower not in {"project", "mod", "modpack", "plugin", "resourcepack", "shader", "datapack"}:
                    project_id = parent

        if not project_id and len(parts) >= 2:
            if lower_parts[0] in {"project", "mod", "modpack", "plugin", "resourcepack", "shader", "datapack"}:
                project_id = parts[1]

        return project_id, version_id
    return match.group("project"), match.group("version")


def _is_http_404_error(exc: Exception) -> bool:
    return "HTTP 404" in str(exc).upper()


def _is_curseforge_distribution_blocked_error(message: str) -> bool:
    normalized = message.lower()
    return (
        "allowmoddistribution=false" in normalized
        or "download per curseforge api" in normalized
    )


def _looks_like_server_pack_name(value: str) -> bool:
    normalized = value.strip().lower()
    if not normalized:
        return False
    markers = ("server", "serverpack", "server-pack", "serverfiles", "server-files")
    return any(marker in normalized for marker in markers)


def _pick_modrinth_archive_file(files: list[dict], *, prefer_server_pack: bool) -> tuple[dict | None, bool]:
    candidates = [item for item in files if isinstance(item, dict)]
    if not candidates:
        return None, False

    if prefer_server_pack:
        for item in candidates:
            file_name = str(item.get("filename") or item.get("name") or "").strip()
            if _looks_like_server_pack_name(file_name):
                return item, True

    primary = next((item for item in candidates if bool(item.get("primary"))), candidates[0])
    file_name = str(primary.get("filename") or primary.get("name") or "").strip()
    return primary, _looks_like_server_pack_name(file_name)


def _modrinth_version_has_server_pack(version_payload: dict) -> bool:
    if not isinstance(version_payload, dict):
        return False
    files = version_payload.get("files")
    if isinstance(files, list):
        for item in files:
            if not isinstance(item, dict):
                continue
            file_name = str(item.get("filename") or item.get("name") or "").strip()
            if _looks_like_server_pack_name(file_name):
                return True
    version_name = str(version_payload.get("name") or version_payload.get("version_number") or "").strip()
    return _looks_like_server_pack_name(version_name)


def _is_modrinth_client_only_entry(file_item: dict) -> bool:
    if not isinstance(file_item, dict):
        return False
    env = file_item.get("env")
    if not isinstance(env, dict):
        return False
    server = str(env.get("server") or "").strip().lower()
    return server == "unsupported"


def _is_curseforge_server_pack_file(file_payload: dict[str, object]) -> bool:
    if not isinstance(file_payload, dict):
        return False
    return bool(file_payload.get("isServerPack"))


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


def _parse_modrinth_archive(
    token: str,
    archive_path: Path,
    source: str,
    source_ref: str | None,
    *,
    client_filter_fallback: bool = False,
) -> ModpackPreviewSnapshot:
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
            if client_filter_fallback and _is_modrinth_client_only_entry(file_item):
                warnings.append(f"Client-only Datei uebersprungen: {safe_path.as_posix()}")
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
                    required=True,
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
            client_filter_fallback=client_filter_fallback,
            warnings=warnings,
        )


def _parse_curseforge_archive(
    token: str,
    archive_path: Path,
    source: str,
    source_ref: str | None,
    *,
    client_filter_fallback: bool = False,
) -> ModpackPreviewSnapshot:
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
                    required=bool(file_item.get("required", True)),
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
            client_filter_fallback=client_filter_fallback,
            warnings=warnings,
        )


def _parse_archive(
    token: str,
    archive_path: Path,
    source: str,
    source_ref: str | None,
    *,
    client_filter_fallback: bool = False,
) -> ModpackPreviewSnapshot:
    try:
        with zipfile.ZipFile(archive_path, "r") as zipped:
            names = {name.replace("\\", "/") for name in zipped.namelist()}
    except zipfile.BadZipFile as exc:
        raise ValueError("Ungueltiges Archiv. Bitte ZIP/MRPACK verwenden.") from exc

    if "modrinth.index.json" in names:
        return _parse_modrinth_archive(
            token,
            archive_path,
            source,
            source_ref,
            client_filter_fallback=client_filter_fallback,
        )
    if "manifest.json" in names:
        return _parse_curseforge_archive(
            token,
            archive_path,
            source,
            source_ref,
            client_filter_fallback=client_filter_fallback,
        )
    raise ValueError("Archiv enthaelt weder modrinth.index.json noch manifest.json.")


def _download_modrinth_archive(
    preview_archive_path: Path,
    reference: str | None,
    explicit_version_id: str | None,
    *,
    decision: dict[str, object] | None = None,
) -> str:
    version_id = (explicit_version_id or "").strip()
    source_ref = (reference or "").strip() or None
    if source_ref and "modrinth.com" in source_ref.lower():
        parsed_project_id, parsed_version_id = _extract_modrinth_ids_from_url(source_ref)
        if parsed_version_id and not version_id:
            version_id = parsed_version_id
        if parsed_project_id:
            source_ref = parsed_project_id

    def _load_version_payload(resolved_version_id: str) -> dict:
        payload = content_service._request_json(
            f"{content_service.MODRINTH_BASE}/version/{resolved_version_id}",
            headers=content_service._modrinth_headers(),
        )
        if not isinstance(payload, dict):
            raise ValueError("Ungueltige Antwort von Modrinth (Version).")
        return payload

    selected_server_pack = False
    project_id_hint: str | None = None
    if version_id:
        version_payload = _load_version_payload(version_id)
    else:
        project_ref = (source_ref or "").strip()
        if not project_ref:
            raise ValueError("Modrinth Referenz oder Version-ID ist erforderlich.")
        try:
            versions_payload = content_service._request_json(
                f"{content_service.MODRINTH_BASE}/project/{project_ref}/version",
                headers=content_service._modrinth_headers(),
            )
            versions = versions_payload if isinstance(versions_payload, list) else []
            if not versions:
                raise ValueError("Keine Modrinth-Versionen fuer dieses Projekt gefunden.")
            versions.sort(key=lambda item: str(item.get("date_published") or ""), reverse=True)
            server_versions = [item for item in versions if _modrinth_version_has_server_pack(item)]
            if server_versions:
                version_payload = server_versions[0]
                selected_server_pack = True
            else:
                version_payload = versions[0]
            version_id = str(version_payload.get("id") or "").strip()
            project_id_hint = str(version_payload.get("project_id") or project_ref or "").strip() or None
            if not version_id:
                raise ValueError("Modrinth Version-ID konnte nicht ermittelt werden.")
        except ValueError as exc:
            # Fallback: einzelne Referenz kann auch direkt eine Version-ID sein.
            if not _is_http_404_error(exc):
                raise
            try:
                version_payload = _load_version_payload(project_ref)
                version_id = project_ref
                project_id_hint = str(version_payload.get("project_id") or "").strip() or None
            except ValueError as version_exc:
                raise ValueError(
                    "Modrinth Referenz konnte weder als Projekt noch als Version-ID aufgeloest werden."
                ) from version_exc

    files = version_payload.get("files") or []
    if not isinstance(files, list) or not files:
        raise ValueError("Die Modrinth-Version enthaelt keine herunterladbare Datei.")
    selected_file, file_is_server = _pick_modrinth_archive_file(files, prefer_server_pack=True)
    if not isinstance(selected_file, dict):
        raise ValueError("Modrinth Archivdatei konnte nicht gelesen werden.")
    selected_server_pack = selected_server_pack or file_is_server
    download_url = str(selected_file.get("url") or "").strip()
    if not download_url:
        raise ValueError("Download-URL fuer Modrinth Modpack fehlt.")
    content_service._download_file(download_url, preview_archive_path, headers=content_service._modrinth_headers())
    if decision is not None:
        decision["server_pack_selected"] = bool(selected_server_pack)
        decision["client_filter_fallback"] = not bool(selected_server_pack)
        decision["upstream_project_id"] = project_id_hint or str(version_payload.get("project_id") or "").strip() or None
        decision["upstream_version_id"] = version_id
        decision["upstream_reference"] = source_ref
    return version_id


def _parse_curseforge_reference(reference: str | None) -> tuple[int | None, int | None]:
    raw = (reference or "").strip()
    if not raw:
        return None, None
    lowered = raw.lower()
    explicit_project_hint = "/projects/" in lowered or "project-id" in lowered or "projectid" in lowered
    explicit_file_hint = "/files/" in lowered or "file-id" in lowered or "fileid" in lowered
    if re.search(r"[a-z]", lowered) and not explicit_project_hint and not explicit_file_hint:
        return None, None
    numbers = [int(match) for match in re.findall(r"\d+", raw)]
    if len(numbers) >= 2:
        return numbers[-2], numbers[-1]
    if len(numbers) == 1:
        single = numbers[0]
        if explicit_project_hint:
            return single, None
        if explicit_file_hint:
            return None, single
        # Ohne klare URL-Hinweise ist eine einzelne Zahl meist die Datei-ID.
        return None, single
    if len(numbers) < 1:
        return None, None
    return None, None


def _looks_like_http_url(value: str | None) -> bool:
    raw = (value or "").strip()
    if not raw:
        return False
    parsed = urllib.parse.urlparse(raw)
    return parsed.scheme in {"http", "https"} and bool(parsed.netloc)


def _extract_curseforge_shared_profile_code(reference: str | None) -> str | None:
    raw = (reference or "").strip()
    if not raw:
        return None

    def _normalize_candidate(value: str) -> str:
        cleaned = (value or "").strip().strip("/").strip()
        cleaned = cleaned.strip(".,;:()[]{}<>\"'")
        return cleaned

    def _is_valid_code(value: str) -> bool:
        if not value:
            return False
        if value.isdigit():
            return False
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9-]{4,63}", value):
            return False
        return bool(re.search(r"[A-Za-z]", value))

    parsed = urllib.parse.urlparse(raw)
    if parsed.scheme in {"http", "https", "curseforge"}:
        query_params = urllib.parse.parse_qs(parsed.query)
        for key in ("code", "profileCode", "profile_code", "shareCode", "share_code"):
            values = query_params.get(key) or query_params.get(key.lower()) or []
            for value in values:
                candidate = _normalize_candidate(str(value))
                if _is_valid_code(candidate):
                    return candidate

        path_parts = [part for part in parsed.path.split("/") if part]
        lower_parts = [part.lower() for part in path_parts]
        if "shared-profile" in lower_parts:
            idx = lower_parts.index("shared-profile")
            if idx + 1 < len(path_parts):
                candidate = _normalize_candidate(path_parts[idx + 1])
                if _is_valid_code(candidate):
                    return candidate

        for part in reversed(path_parts):
            candidate = _normalize_candidate(part)
            if _is_valid_code(candidate):
                return candidate
        return None

    candidate = _normalize_candidate(raw)
    if _is_valid_code(candidate):
        return candidate
    return None


def _download_curseforge_shared_profile_archive(preview_archive_path: Path, profile_code: str) -> str:
    normalized_code = (profile_code or "").strip()
    if not normalized_code:
        raise ValueError("CurseForge Profilcode fehlt.")
    url = f"{content_service.CURSEFORGE_BASE}/v1/shared-profile/{urllib.parse.quote(normalized_code)}"
    try:
        content_service._download_file(url, preview_archive_path)
    except Exception as exc:
        raise ValueError(f"CurseForge Profilcode konnte nicht heruntergeladen werden: {exc}") from exc
    return normalized_code


def _download_direct_archive_url(preview_archive_path: Path, reference_url: str) -> str:
    normalized = (reference_url or "").strip()
    if not _looks_like_http_url(normalized):
        raise ValueError("Direktlink ist ungueltig.")
    try:
        content_service._download_file(normalized, preview_archive_path)
    except Exception as exc:
        raise ValueError(f"Download per Direktlink fehlgeschlagen: {exc}") from exc
    return normalized


def _pick_curseforge_latest_file(files: list[dict], *, prefer_server_pack: bool = False) -> tuple[dict | None, bool]:
    best: dict | None = None
    best_key: tuple[int, int, str, int] | None = None
    channel_weight = {1: 3, 2: 2, 3: 1}

    for item in files:
        if not isinstance(item, dict):
            continue
        file_id = int(item.get("id") or 0)
        if file_id <= 0:
            continue
        release_type = int(item.get("releaseType") or 3)
        weight = channel_weight.get(release_type, 0)
        server_weight = 1 if bool(item.get("isServerPack")) else 0
        if not prefer_server_pack:
            server_weight = 0
        file_date = str(item.get("fileDate") or "")
        key = (server_weight, weight, file_date, file_id)
        if best is None or (best_key is not None and key > best_key):
            best = item
            best_key = key

    return best, bool(best and best.get("isServerPack"))


def _download_curseforge_archive(
    preview_archive_path: Path,
    *,
    project_id: int | None,
    file_id: int | None,
    reference: str | None,
    decision: dict[str, object] | None = None,
) -> tuple[int, int]:
    ref_project_id, ref_file_id = _parse_curseforge_reference(reference)
    resolved_project_id = int(project_id or 0) or int(ref_project_id or 0)
    resolved_file_id = int(file_id or 0) or int(ref_file_id or 0)
    if resolved_project_id <= 0 and resolved_file_id <= 0:
        raise ValueError("CurseForge: Projekt-ID, Datei-ID oder URL ist erforderlich.")

    headers = content_service._curseforge_headers()
    resolved_file_payload: dict[str, object] = {}
    selected_server_pack = False

    if resolved_file_id > 0 and resolved_project_id <= 0:
        file_payload = content_service._request_json(
            f"{content_service.CURSEFORGE_BASE}/v1/mods/files/{resolved_file_id}",
            headers=headers,
        )
        data = file_payload.get("data") if isinstance(file_payload, dict) else {}
        resolved_file_payload = data if isinstance(data, dict) else {}
        resolved_project_id = int(resolved_file_payload.get("modId") or 0)
        if resolved_project_id <= 0:
            raise ValueError("CurseForge Projekt-ID konnte aus Datei-ID nicht ermittelt werden.")
    elif resolved_project_id > 0 and resolved_file_id <= 0:
        files_payload = content_service._request_json(
            f"{content_service.CURSEFORGE_BASE}/v1/mods/{resolved_project_id}/files?pageSize=50&index=0",
            headers=headers,
        )
        files_data = files_payload.get("data") if isinstance(files_payload, dict) else []
        files = files_data if isinstance(files_data, list) else []
        latest, selected_server_pack = _pick_curseforge_latest_file(files, prefer_server_pack=True)
        if latest is None:
            raise ValueError("Keine CurseForge Dateien fuer das Projekt gefunden.")
        resolved_file_payload = latest
        resolved_file_id = int(latest.get("id") or 0)
        if resolved_file_id <= 0:
            raise ValueError("CurseForge Datei-ID konnte nicht ermittelt werden.")
    else:
        try:
            file_payload = content_service._request_json(
                f"{content_service.CURSEFORGE_BASE}/v1/mods/{resolved_project_id}/files/{resolved_file_id}",
                headers=headers,
            )
            data = file_payload.get("data") if isinstance(file_payload, dict) else {}
            resolved_file_payload = data if isinstance(data, dict) else {}
        except ValueError:
            # Fallback fuer Referenzen, die nur eine Datei-ID liefern.
            file_payload = content_service._request_json(
                f"{content_service.CURSEFORGE_BASE}/v1/mods/files/{resolved_file_id}",
                headers=headers,
            )
            data = file_payload.get("data") if isinstance(file_payload, dict) else {}
            resolved_file_payload = data if isinstance(data, dict) else {}
            payload_project_id = int(resolved_file_payload.get("modId") or 0)
            if payload_project_id > 0:
                resolved_project_id = payload_project_id

    if resolved_project_id <= 0 or resolved_file_id <= 0:
        raise ValueError("CurseForge Projekt-/Datei-ID konnte nicht aufgeloest werden.")

    selected_server_pack = selected_server_pack or _is_curseforge_server_pack_file(resolved_file_payload)
    server_pack_file_id = int(resolved_file_payload.get("serverPackFileId") or 0)
    if not selected_server_pack and server_pack_file_id > 0:
        server_payload = content_service._request_json(
            f"{content_service.CURSEFORGE_BASE}/v1/mods/{resolved_project_id}/files/{server_pack_file_id}",
            headers=headers,
        )
        server_data = server_payload.get("data") if isinstance(server_payload, dict) else {}
        if isinstance(server_data, dict) and int(server_data.get("id") or 0) > 0:
            resolved_file_payload = server_data
            resolved_file_id = int(server_data.get("id") or 0)
            selected_server_pack = _is_curseforge_server_pack_file(server_data) or True

    download_url = str(resolved_file_payload.get("downloadUrl") or "").strip()
    if not download_url:
        url_payload = content_service._request_json(
            f"{content_service.CURSEFORGE_BASE}/v1/mods/{resolved_project_id}/files/{resolved_file_id}/download-url",
            headers=headers,
        )
        data2 = url_payload.get("data") if isinstance(url_payload, dict) else {}
        if isinstance(data2, dict):
            download_url = str(data2.get("url") or "").strip()
    if not download_url:
        raise ValueError("CurseForge Download-URL konnte nicht ermittelt werden.")
    content_service._download_file(download_url, preview_archive_path, headers=headers)
    if decision is not None:
        decision["server_pack_selected"] = bool(selected_server_pack)
        decision["client_filter_fallback"] = not bool(selected_server_pack)
        decision["upstream_project_id"] = str(resolved_project_id)
        decision["upstream_version_id"] = str(resolved_file_id)
        decision["upstream_reference"] = reference
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
    client_filter_fallback = False
    upstream_project_id: str | None = None
    upstream_version_id: str | None = None
    upstream_reference: str | None = None
    try:
        if normalized_source == "local_archive":
            if not local_archive_bytes:
                raise ValueError("Bitte eine lokale ZIP/MRPACK-Datei hochladen.")
            preview_archive.write_bytes(local_archive_bytes)
            source_ref = (local_archive_name or "local_archive").strip() or "local_archive"
            upstream_reference = source_ref
        elif normalized_source == "modrinth":
            modrinth_decision: dict[str, object] = {}
            resolved_version_id = _download_modrinth_archive(
                preview_archive,
                reference=modrinth_reference,
                explicit_version_id=modrinth_version_id,
                decision=modrinth_decision,
            )
            source_ref = resolved_version_id
            client_filter_fallback = bool(modrinth_decision.get("client_filter_fallback"))
            upstream_project_id = str(modrinth_decision.get("upstream_project_id") or "").strip() or None
            upstream_version_id = str(modrinth_decision.get("upstream_version_id") or "").strip() or source_ref
            upstream_reference = str(modrinth_decision.get("upstream_reference") or modrinth_reference or "").strip() or None
        else:
            has_explicit_ids = bool(int(curseforge_project_id or 0) > 0 or int(curseforge_file_id or 0) > 0)
            ref_project_id, ref_file_id = _parse_curseforge_reference(curseforge_reference)
            has_reference_ids = bool(int(ref_project_id or 0) > 0 or int(ref_file_id or 0) > 0)

            if not has_explicit_ids and not has_reference_ids:
                shared_code = _extract_curseforge_shared_profile_code(curseforge_reference)
                if shared_code:
                    resolved_code = _download_curseforge_shared_profile_archive(preview_archive, shared_code)
                    source_ref = f"shared:{resolved_code}"
                    client_filter_fallback = True
                    upstream_reference = resolved_code
                elif _looks_like_http_url(curseforge_reference):
                    resolved_url = _download_direct_archive_url(preview_archive, str(curseforge_reference or ""))
                    source_ref = f"url:{resolved_url}"
                    client_filter_fallback = True
                    upstream_reference = resolved_url
                else:
                    raise ValueError(
                        "CurseForge: Bitte Projekt-ID/Datei-ID, Share-Link oder Import-Code angeben."
                    )
            else:
                curseforge_decision: dict[str, object] = {}
                project_id, file_id = _download_curseforge_archive(
                    preview_archive,
                    project_id=curseforge_project_id,
                    file_id=curseforge_file_id,
                    reference=curseforge_reference,
                    decision=curseforge_decision,
                )
                source_ref = f"{project_id}:{file_id}"
                client_filter_fallback = bool(curseforge_decision.get("client_filter_fallback"))
                upstream_project_id = str(curseforge_decision.get("upstream_project_id") or "").strip() or str(project_id)
                upstream_version_id = str(curseforge_decision.get("upstream_version_id") or "").strip() or str(file_id)
                upstream_reference = str(curseforge_decision.get("upstream_reference") or curseforge_reference or "").strip() or None

        snapshot = _parse_archive(
            token,
            preview_archive,
            normalized_source,
            source_ref,
            client_filter_fallback=client_filter_fallback,
        )
        snapshot.upstream_project_id = upstream_project_id
        snapshot.upstream_version_id = upstream_version_id
        snapshot.upstream_reference = upstream_reference
        if client_filter_fallback:
            snapshot.warnings.append(
                "Kein dediziertes Server-Modpack gefunden; Fallback auf Client-Pack mit Client-only-Filter."
            )
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


def get_server_modpack_state(db: Session, server_id: int) -> ServerModpackState | None:
    return db.scalar(
        select(ServerModpackState).where(ServerModpackState.server_id == int(server_id))
    )


def _parse_curseforge_source_ref_ids(value: str | None) -> tuple[str | None, str | None]:
    raw = (value or "").strip()
    if not raw:
        return None, None
    if ":" not in raw:
        return None, None
    left, right = raw.split(":", 1)
    project_id = left.strip()
    version_id = right.strip()
    if project_id.isdigit() and version_id.isdigit():
        return project_id, version_id
    return None, None


def _snapshot_upstream_identity(snapshot: ModpackPreviewSnapshot) -> tuple[str | None, str | None, str | None]:
    upstream_project_id = (snapshot.upstream_project_id or "").strip() or None
    upstream_version_id = (snapshot.upstream_version_id or "").strip() or None
    upstream_reference = (snapshot.upstream_reference or snapshot.source_ref or "").strip() or None

    if snapshot.source == "curseforge" and not upstream_project_id:
        project_id, version_id = _parse_curseforge_source_ref_ids(snapshot.source_ref)
        upstream_project_id = upstream_project_id or project_id
        upstream_version_id = upstream_version_id or version_id
    if snapshot.source == "modrinth" and not upstream_version_id:
        upstream_version_id = (snapshot.source_ref or "").strip() or None

    return upstream_project_id, upstream_version_id, upstream_reference


def upsert_server_modpack_state_from_snapshot(
    db: Session,
    *,
    server: Server,
    snapshot: ModpackPreviewSnapshot,
    set_pending: bool,
) -> ServerModpackState:
    state = get_server_modpack_state(db, server.id)
    if state is None:
        state = ServerModpackState(server_id=server.id, source=snapshot.source)

    upstream_project_id, upstream_version_id, upstream_reference = _snapshot_upstream_identity(snapshot)
    state.source = snapshot.source
    state.pack_name = snapshot.pack_name
    state.pack_version = snapshot.pack_version
    state.source_ref = upstream_reference
    state.upstream_project_id = upstream_project_id
    if set_pending:
        state.pending_version_id = upstream_version_id
    state.last_check_error = None

    db.add(state)
    db.commit()
    db.refresh(state)
    return state


def _set_server_modpack_state_error(
    db: Session,
    *,
    server_id: int,
    message: str,
) -> None:
    state = get_server_modpack_state(db, server_id)
    if state is None:
        return
    state.last_check_error = message
    db.add(state)
    db.commit()


def _modpack_versions_for_state(
    *,
    server: Server,
    state: ServerModpackState,
    release_channel: str = "all",
) -> list[dict]:
    source = (state.source or "").strip().lower()
    project_id = (state.upstream_project_id or "").strip()
    if source == "modrinth":
        if not project_id:
            raise ValueError("Modrinth-Projektreferenz fehlt. Bitte Referenz manuell setzen.")
        loader = server.server_type if server.server_type != "vanilla" else None
        return content_service.list_modrinth_versions(
            project_id=project_id,
            mc_version=server.mc_version,
            loader=loader,
            release_channel=release_channel,
        )
    if source == "curseforge":
        if not project_id or not project_id.isdigit():
            raise ValueError("CurseForge Projekt-ID fehlt. Bitte Referenz/Code manuell setzen.")
        loader = server.server_type if server.server_type != "vanilla" else None
        return content_service.list_curseforge_versions(
            int(project_id),
            server.mc_version,
            loader,
            "modpack",
            release_channel=release_channel,
        )
    raise ValueError("Diese Modpack-Quelle unterstuetzt keine Versionsabfrage.")


def build_modpack_state_payload(
    db: Session,
    *,
    server: Server,
    include_latest: bool = False,
    release_channel: str = "all",
) -> dict[str, object]:
    state = get_server_modpack_state(db, server.id)
    pending = get_pending_install(db, server.id)
    if state is None:
        return {
            "has_modpack": False,
            "pending_install": pending is not None,
            "pending_pack_name": pending.pack_name if pending else None,
        }

    payload: dict[str, object] = {
        "has_modpack": True,
        "source": state.source,
        "pack_name": state.pack_name,
        "pack_version": state.pack_version,
        "source_ref": state.source_ref,
        "project_id": state.upstream_project_id,
        "current_version_id": state.current_version_id,
        "pending_version_id": state.pending_version_id,
        "pending_install": pending is not None,
        "pending_pack_name": pending.pack_name if pending else None,
        "last_known_version_id": state.last_known_version_id,
        "last_known_version_label": state.last_known_version_label,
        "last_check_error": state.last_check_error,
        "can_check_updates": state.source in {"modrinth", "curseforge"} and bool(state.upstream_project_id),
    }

    if not include_latest:
        return payload

    try:
        versions = _modpack_versions_for_state(server=server, state=state, release_channel=release_channel)
        latest = versions[0] if versions else None
        if latest:
            latest_id = str(latest.get("id") or "").strip() or None
            latest_label = str(
                latest.get("name") or latest.get("version_number") or latest.get("id") or ""
            ).strip() or latest_id
            payload["latest_version_id"] = latest_id
            payload["latest_version_label"] = latest_label
            payload["update_available"] = bool(
                latest_id and state.current_version_id and str(state.current_version_id) != latest_id
            )
            state.last_known_version_id = latest_id
            state.last_known_version_label = latest_label
            state.last_check_error = None
            db.add(state)
            db.commit()
        else:
            payload["latest_version_id"] = None
            payload["latest_version_label"] = None
            payload["update_available"] = False
    except Exception as exc:
        message = str(exc)
        payload["latest_version_id"] = None
        payload["latest_version_label"] = None
        payload["update_available"] = False
        payload["latest_error"] = message
        state.last_check_error = message
        db.add(state)
        db.commit()

    return payload


def list_modpack_update_versions(
    *,
    server: Server,
    state: ServerModpackState,
    release_channel: str = "all",
) -> list[dict]:
    return _modpack_versions_for_state(server=server, state=state, release_channel=release_channel)


def queue_modpack_update_for_server(
    db: Session,
    *,
    server: Server,
    requested_by_user_id: int | None,
    target_version_id: str | None = None,
    reference_override: str | None = None,
) -> ModpackPreviewResponse:
    state = get_server_modpack_state(db, server.id)
    if state is None:
        raise ValueError("Dieser Server hat keine gespeicherten Modpack-Metadaten.")

    source = (state.source or "").strip().lower()
    normalized_target = (target_version_id or "").strip() or None
    normalized_reference = (reference_override or "").strip() or None

    if source == "modrinth":
        modrinth_reference = normalized_reference or (state.upstream_project_id or "").strip() or (state.source_ref or "").strip()
        if not modrinth_reference:
            raise ValueError("Modrinth Referenz fehlt. Bitte Projekt-ID/Slug oder URL angeben.")
        preview = create_preview(
            source="modrinth",
            modrinth_reference=modrinth_reference,
            modrinth_version_id=normalized_target,
        )
    elif source == "curseforge":
        project_raw = (state.upstream_project_id or "").strip()
        project_id = int(project_raw) if project_raw.isdigit() else None
        file_id = int(normalized_target) if normalized_target and normalized_target.isdigit() else None
        if normalized_target and file_id is None:
            raise ValueError("Fuer CurseForge muss die Zielversion eine numerische Datei-ID sein.")

        if normalized_reference:
            preview = create_preview(
                source="curseforge",
                curseforge_reference=normalized_reference,
                curseforge_project_id=project_id,
                curseforge_file_id=file_id,
            )
        elif project_id is not None:
            preview = create_preview(
                source="curseforge",
                curseforge_project_id=project_id,
                curseforge_file_id=file_id,
            )
        else:
            raise ValueError("CurseForge Projekt-ID fehlt. Bitte Referenz/Code/Link angeben.")
    else:
        raise ValueError("Updates werden fuer diese Modpack-Quelle nicht unterstuetzt.")

    queue_pending_install(
        db,
        server=server,
        snapshot=load_preview(preview.token),
        requested_by_user_id=requested_by_user_id,
    )
    return preview


def get_pending_install(db: Session, server_id: int) -> PendingModpackInstall | None:
    return db.scalar(
        select(PendingModpackInstall).where(PendingModpackInstall.server_id == int(server_id))
    )


def queue_pending_install(
    db: Session,
    *,
    server: Server,
    snapshot: ModpackPreviewSnapshot,
    requested_by_user_id: int | None,
) -> PendingModpackInstall:
    pending = get_pending_install(db, server.id)
    previous_token: str | None = None
    if pending is None:
        pending = PendingModpackInstall(
            server_id=server.id,
            preview_token=snapshot.token,
            pack_name=snapshot.pack_name,
            requested_by_user_id=requested_by_user_id,
            last_error=None,
        )
    else:
        previous_token = pending.preview_token
        pending.preview_token = snapshot.token
        pending.pack_name = snapshot.pack_name
        pending.requested_by_user_id = requested_by_user_id
        pending.last_error = None
    db.add(pending)
    db.commit()
    db.refresh(pending)
    if previous_token and previous_token != snapshot.token:
        discard_preview(previous_token)
    upsert_server_modpack_state_from_snapshot(
        db,
        server=server,
        snapshot=snapshot,
        set_pending=True,
    )
    audit_service.log_action(
        db,
        action="modpack.install_queued",
        user_id=requested_by_user_id,
        server_id=server.id,
        details=f"pack={snapshot.pack_name} token={snapshot.token}",
    )
    return pending


def delete_pending_install_for_server(
    db: Session,
    server_id: int,
    *,
    discard_preview_archive: bool = True,
) -> str | None:
    pending = get_pending_install(db, server_id)
    if pending is None:
        return None
    token = pending.preview_token
    db.delete(pending)
    db.commit()
    if discard_preview_archive:
        discard_preview(token)
    return token


def run_pending_install_for_server(
    db: Session,
    *,
    server: Server,
    initiated_by_user_id: int | None,
) -> ModpackExecuteResponse | None:
    pending = get_pending_install(db, server.id)
    if pending is None:
        return None

    try:
        snapshot = load_preview(pending.preview_token)
    except Exception as exc:
        pending.last_error = str(exc)
        db.add(pending)
        db.commit()
        _set_server_modpack_state_error(
            db,
            server_id=server.id,
            message=str(exc),
        )
        raise ValueError(f"Modpack-Preview nicht mehr verfuegbar: {exc}") from exc

    try:
        # Wiederholte Versuche sollen deterministisch bleiben.
        _reset_server_content_before_install(db, server)
        result = execute_preview(
            db,
            snapshot=snapshot,
            server=server,
            initiated_by_user_id=initiated_by_user_id,
            created_server=False,
            notes=["Modpack wurde beim ersten Start installiert."],
        )
    except Exception as exc:
        pending.last_error = str(exc)
        db.add(pending)
        db.commit()
        _set_server_modpack_state_error(
            db,
            server_id=server.id,
            message=str(exc),
        )
        raise

    token = pending.preview_token
    db.delete(pending)
    db.commit()
    discard_preview(token)

    state = upsert_server_modpack_state_from_snapshot(
        db,
        server=server,
        snapshot=snapshot,
        set_pending=False,
    )
    _, upstream_version_id, _ = _snapshot_upstream_identity(snapshot)
    if upstream_version_id:
        state.current_version_id = upstream_version_id
    state.pending_version_id = None
    state.last_check_error = None
    db.add(state)
    db.commit()
    return result


def _reset_server_content_before_install(db: Session, server: Server) -> None:
    base_path = Path(server.base_path).expanduser().resolve()
    if not base_path.exists() or not base_path.is_dir():
        return

    for subdir in ("mods", "plugins"):
        target = (base_path / subdir).resolve()
        if base_path not in [target, *target.parents]:
            continue
        if target.exists():
            shutil.rmtree(target, ignore_errors=True)
        target.mkdir(parents=True, exist_ok=True)

    db.execute(delete(InstalledContent).where(InstalledContent.server_id == server.id))
    db.commit()


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
        is_required = bool(entry.required)
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
                apply_client_filter = bool(snapshot.client_filter_fallback or snapshot.pack_format == "curseforge")
                content_service.install_curseforge(
                    db,
                    server,
                    int(entry.project_id),
                    int(entry.version_id),
                    entry.content_type,
                    initiated_by_user_id,
                    resolve_dependencies=True,
                    enforce_compatibility=False,
                    keep_existing_dependency_version=True,
                    client_filter_fallback=apply_client_filter,
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
            message = str(exc or "")
            if (
                entry.provider_name == "curseforge"
                and "Nicht serverrelevanter CurseForge-Inhalt" in message
            ):
                warnings.append(
                    f"Eintrag uebersprungen ({entry.name}): nicht serverrelevant ({message})."
                )
                continue
            if (
                snapshot.client_filter_fallback
                and entry.provider_name == "curseforge"
                and "Client-only CurseForge-Inhalt" in message
            ):
                warnings.append(
                    f"Eintrag uebersprungen ({entry.name}): client-only Mod im Fallback ({message})."
                )
                continue
            if (
                entry.provider_name == "curseforge"
                and _is_curseforge_distribution_blocked_error(message)
            ):
                warnings.append(
                    "Eintrag uebersprungen "
                    f"({entry.name}): CurseForge erlaubt keinen API-Download "
                    "(allowModDistribution=false). Falls noetig, Mod manuell "
                    "aus dem Modpack/Projekt beziehen."
                )
                continue
            if is_required:
                raise ValueError(f"Pflicht-Eintrag fehlgeschlagen ({entry.name}): {exc}") from exc
            warnings.append(f"Optionaler Eintrag fehlgeschlagen ({entry.name}): {exc}")

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
