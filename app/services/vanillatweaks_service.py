"""Vanilla Tweaks-Integration (inoffizielle, aber stabile Generator-API).

Ablauf: Kategorien laden -> Auswahl -> Generieren (POST) -> ZIP holen.
Datapacks und Crafting Tweaks landen als Datapacks im Welt-Ordner
`<server>/<level-name>/datapacks/`. Resource Packs werden vom Manager selbst
gehostet (data/resourcepacks/) und ueber server.properties als
Server-Resource-Pack gesetzt (benoetigt eine oeffentliche Basis-URL).
Die Share-Code-Aufloesung ist hier noch nicht umgesetzt.

Verifizierte Endpunkte:
- Kategorien: GET /assets/resources/json/{version}/{dp|ct|rp}categories.json
  -> {versionName, categories:[{category, packs:[{name, display, ...}]}]}
- Generieren: POST /assets/server/zip{datapacks|craftingtweaks|resourcepacks}.php
  (Body packs=<JSON {kategorie:[namen]}> & version=<x.y>) -> {status, link}
  Datapack-Link ist ein Container ("UNZIP_ME") mit inneren .zip-Datapacks.
"""

from __future__ import annotations

import io
import json
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

from sqlalchemy import delete
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models.installed_content import InstalledContent
from app.models.server import Server
from app.services import audit_service, content_service

VT_BASE = "https://vanillatweaks.net"

# pack_type -> (json-prefix, zip-endpoint)
_PACK_TYPES = {
    "datapacks": ("dp", "zipdatapacks.php"),
    "craftingtweaks": ("ct", "zipcraftingtweaks.php"),
    "resourcepacks": ("rp", "zipresourcepacks.php"),
}
# Diese Typen werden als Datapacks im Welt-Ordner abgelegt.
_DATAPACK_LIKE = {"datapacks", "craftingtweaks"}


def _headers() -> dict[str, str]:
    ua = get_settings().modrinth_user_agent or "mc-server-manager/1.0"
    return {"User-Agent": ua}


def map_vt_version(mc_version: str | None) -> str:
    """Server-MC-Version auf die VT-Versionsgruppe (major.minor) abbilden."""
    parts = str(mc_version or "").strip().split(".")
    if len(parts) >= 2 and parts[0].isdigit() and parts[1].isdigit():
        return f"{parts[0]}.{parts[1]}"
    return "1.21"


def list_categories(pack_type: str, version: str) -> list[dict]:
    pack_type = (pack_type or "").strip().lower()
    if pack_type not in _PACK_TYPES:
        raise ValueError(f"Unbekannter Pack-Typ: {pack_type}")
    prefix, _endpoint = _PACK_TYPES[pack_type]
    url = f"{VT_BASE}/assets/resources/json/{version}/{prefix}categories.json"
    payload = content_service._request_json(url, headers=_headers())
    if isinstance(payload, dict):
        return list(payload.get("categories", []))
    return []


def _post_json(url: str, form: dict[str, str]) -> dict:
    data = urllib.parse.urlencode(form).encode()
    req = urllib.request.Request(
        url,
        data=data,
        headers={**_headers(), "Content-Type": "application/x-www-form-urlencoded"},
    )
    try:
        with urllib.request.urlopen(
            req, timeout=45, context=content_service._tls_context()
        ) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raise ValueError(f"Vanilla Tweaks HTTP {exc.code}: {exc.reason}") from exc
    except urllib.error.URLError as exc:
        reason = getattr(exc, "reason", exc)
        raise ValueError(f"Vanilla Tweaks Netzwerkfehler: {reason}") from exc


def _get_bytes(url: str) -> bytes:
    req = urllib.request.Request(url, headers=_headers())
    with urllib.request.urlopen(
        req, timeout=90, context=content_service._tls_context()
    ) as resp:
        return resp.read()


def generate_zip(
    pack_type: str, version: str, selection: dict[str, list[str]]
) -> bytes:
    pack_type = (pack_type or "").strip().lower()
    if pack_type not in _PACK_TYPES:
        raise ValueError(f"Unbekannter Pack-Typ: {pack_type}")
    if not selection:
        raise ValueError("Keine Packs ausgewaehlt.")
    _prefix, endpoint = _PACK_TYPES[pack_type]
    resp = _post_json(
        f"{VT_BASE}/assets/server/{endpoint}",
        {"packs": json.dumps(selection), "version": version},
    )
    if str(resp.get("status")) != "success" or not resp.get("link"):
        raise ValueError(f"Generierung fehlgeschlagen: {resp}")
    return _get_bytes(f"{VT_BASE}{resp['link']}")


def install_datapacks(
    db: Session,
    server: Server,
    pack_type: str,
    selection: dict[str, list[str]],
    user_id: int | None,
) -> tuple[list[str], list[str]]:
    """VT-Datapacks/Crafting-Tweaks generieren und in <welt>/datapacks ablegen."""
    pack_type = (pack_type or "").strip().lower()
    if pack_type not in _DATAPACK_LIKE:
        raise ValueError("Nur datapacks/craftingtweaks werden abgelegt.")
    version = map_vt_version(server.mc_version)
    archive = generate_zip(pack_type, version, selection)

    target_dir = content_service._target_dir(server, "datapack")
    target_dir.mkdir(parents=True, exist_ok=True)

    notes: list[str] = []
    warnings: list[str] = []
    with zipfile.ZipFile(io.BytesIO(archive)) as container:
        inner_zips = [n for n in container.namelist() if n.lower().endswith(".zip")]
        # Container ("UNZIP_ME") enthaelt die einzelnen Datapack-.zips.
        members = inner_zips or [n for n in container.namelist() if not n.endswith("/")]
        for member in members:
            raw = container.read(member)
            base_name = member.rsplit("/", 1)[-1]
            if not base_name.lower().endswith(".zip"):
                base_name = f"{base_name}.zip"
            file_name = content_service._safe_file_name(base_name)
            try:
                (target_dir / file_name).write_bytes(raw)
            except OSError as exc:
                warnings.append(f"{file_name}: {exc}")
                continue
            display = file_name[:-4] if file_name.lower().endswith(".zip") else file_name
            db.execute(
                delete(InstalledContent).where(
                    InstalledContent.server_id == server.id,
                    InstalledContent.content_type == "datapack",
                    InstalledContent.file_name == file_name,
                )
            )
            db.add(
                InstalledContent(
                    server_id=server.id,
                    provider_name="vanillatweaks",
                    content_type="datapack",
                    external_project_id=f"vt:{pack_type}",
                    external_version_id=version,
                    name=display,
                    version_label=version,
                    file_name=file_name,
                    installed_by_user_id=user_id,
                )
            )
            notes.append(display)

    db.commit()
    audit_service.log_action(
        db,
        action="vanillatweaks.install",
        user_id=user_id,
        server_id=server.id,
        details=f"type={pack_type} version={version} installed={len(notes)}",
    )
    return notes, warnings


def install_resourcepack(
    db: Session,
    server: Server,
    selection: dict[str, list[str]],
    user_id: int | None,
):
    """VT-Resource-Pack generieren, selbst hosten und als Server-Resource-Pack
    (server.properties) setzen.

    Der VT-Download-Link ist temporaer -> der Manager legt das ZIP unter
    data/resourcepacks/ ab und liefert es unter der oeffentlichen Basis-URL
    (`MCSM_PUBLIC_BASE_URL`) aus, die Clients erreichen.
    """
    import hashlib

    from app.services.app_setting_service import get_public_base_url_runtime

    settings = get_settings()
    base = (get_public_base_url_runtime() or "").strip().rstrip("/")
    if not base:
        raise ValueError(
            "Oeffentliche Manager-URL fehlt (unter Einstellungen setzen oder "
            "MCSM_PUBLIC_BASE_URL), wird zum Hosten des Resource Packs benoetigt."
        )

    version = map_vt_version(server.mc_version)
    archive = generate_zip("resourcepacks", version, selection)
    sha1 = hashlib.sha1(archive).hexdigest()

    rp_dir = Path(settings.data_dir) / "resourcepacks"
    rp_dir.mkdir(parents=True, exist_ok=True)
    file_name = f"vt_{server.id}_{sha1[:12]}.zip"
    (rp_dir / file_name).write_bytes(archive)

    url = f"{base}/resourcepacks/{file_name}"
    return content_service.apply_server_resource_pack(
        db,
        server,
        url=url,
        sha1=sha1,
        provider="vanillatweaks",
        project_id="vt:resourcepacks",
        version_id=version,
        name="Vanilla Tweaks Resource Pack",
        version_label=version,
        user_id=user_id,
    )
