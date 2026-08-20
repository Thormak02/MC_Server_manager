"""Modpack-Erkennung fuer den Dispatcher: welcher Backend-Server passt zu den Mods
eines Clients?

Der Dispatcher liest im Protokoll die Mods, die ein Client vom Server verlangt (die
beidseitig-pflichtigen Mods; reine Clientside-Mods interessieren nicht). Diese Menge
wird hier gegen die Mod-Ausstattung der Gateway-Server gematcht: ein Server passt,
wenn er ALLE vom Client verlangten Mods hat (der Server darf zusaetzliche Server-Mods
haben - z.B. Performance-Mods - die dem Client egal sind).

Die Mod-IDs eines Servers werden aus seinem ``mods/``-Ordner gelesen (NeoForge/Forge
ueber ``*.mods.toml``, Fabric/Quilt ueber ``*.mod.json``). Ergebnis wird gecacht und
bei Aenderung des mods-Ordners (mtime/Anzahl) neu gebaut.
"""

from __future__ import annotations

import json
import re
import zipfile
from pathlib import Path

from sqlalchemy.orm import Session

# modId="..."  bzw.  modId = '...'  in [[mods]]-Bloecken von (neoforge.)mods.toml
_TOML_MODID_RE = re.compile(r"""modId\s*=\s*["']([A-Za-z0-9_\-]+)["']""")

# Cache: server_id -> (signature, frozenset[mod_id]).  signature erkennt Aenderungen.
_MOD_CACHE: dict[int, tuple[tuple, frozenset[str]]] = {}


def _mod_ids_from_jar(jar_path: Path) -> set[str]:
    """Mod-IDs aus einer einzelnen Mod-Jar lesen (alle Loader)."""
    ids: set[str] = set()
    try:
        with zipfile.ZipFile(jar_path) as zf:
            names = set(zf.namelist())
            for meta in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                if meta in names:
                    text = zf.read(meta).decode("utf-8", "ignore")
                    ids.update(_TOML_MODID_RE.findall(text))
            for meta in ("fabric.mod.json", "quilt.mod.json"):
                if meta in names:
                    try:
                        data = json.loads(zf.read(meta).decode("utf-8", "ignore"))
                    except Exception:  # noqa: BLE001
                        continue
                    # Fabric: {"id": "..."}; Quilt: {"quilt_loader": {"id": "..."}}
                    if isinstance(data, dict):
                        if data.get("id"):
                            ids.add(str(data["id"]))
                        loader = data.get("quilt_loader")
                        if isinstance(loader, dict) and loader.get("id"):
                            ids.add(str(loader["id"]))
    except (zipfile.BadZipFile, OSError):
        pass
    return ids


def _mods_signature(mods_dir: Path) -> tuple:
    """Guenstige Signatur des mods-Ordners (Name+Groesse+mtime je Jar), um Cache-
    Invalidierung ohne erneutes Oeffnen aller Jars zu ermoeglichen."""
    sig: list[tuple] = []
    try:
        for jar in sorted(mods_dir.glob("*.jar")):
            try:
                st = jar.stat()
                sig.append((jar.name, st.st_size, int(st.st_mtime)))
            except OSError:
                continue
    except OSError:
        pass
    return tuple(sig)


def harvest_server_mod_ids(base_path: str) -> frozenset[str]:
    """Alle Mod-IDs im ``mods/``-Ordner eines Servers (leer, wenn kein mods-Ordner)."""
    mods_dir = Path(base_path).expanduser().resolve() / "mods"
    if not mods_dir.is_dir():
        return frozenset()
    ids: set[str] = set()
    for jar in mods_dir.glob("*.jar"):
        ids.update(_mod_ids_from_jar(jar))
    return frozenset(ids)


def server_mod_ids_cached(server) -> frozenset[str]:
    """Wie harvest_server_mod_ids, aber gecacht bis sich der mods-Ordner aendert."""
    mods_dir = Path(server.base_path).expanduser().resolve() / "mods"
    sig = _mods_signature(mods_dir)
    cached = _MOD_CACHE.get(server.id)
    if cached is not None and cached[0] == sig:
        return cached[1]
    ids = harvest_server_mod_ids(server.base_path)
    _MOD_CACHE[server.id] = (sig, ids)
    return ids


def match_backend_for_client(
    db: Session, client_required_mods: set[str]
) -> tuple[int | None, str]:
    """Passenden Gateway-Backend-Server fuer die vom Client verlangten Mods finden.

    Ein echter Server hat neben den beidseitigen Mods oft **Server-only**-Mods
    (Performance/Backups), ein Client oft **Client-only**-Mods (Sodium, Minimap) -
    keine Menge ist Teilmenge der anderen. Deshalb wird nach **Ueberdeckung** gerankt:

    1. Server, die **alle** verlangten Mods haben (Voll-Match), gewinnen; darunter der
       spezifischste (kleinste Mod-Ausstattung).
    2. Sonst der Server mit der groessten Schnittmenge - aber nur als **eindeutiger**
       Sieger (kein Gleichstand). So werden versehentlich mitgesendete Client-only-Mods
       toleriert, ohne falsch zu routen.

    Rueckgabe: (server_id oder None, kurze Begruendung fuer Logs/Diagnose).
    """
    from sqlalchemy import select

    from app.models.server import Server

    wanted = {m.strip().lower() for m in client_required_mods if m and m.strip()}
    if not wanted:
        return None, "keine Server-Mods verlangt (vanilla/client-only)"

    servers = db.scalars(select(Server).where(Server.gateway_enabled.is_(True))).all()
    # (server_id, overlap, is_full_cover, total_server_mods)
    scored: list[tuple[int, int, bool, int]] = []
    for srv in servers:
        server_mods = {m.lower() for m in server_mod_ids_cached(srv)}
        if not server_mods:
            continue
        overlap = len(wanted & server_mods)
        if overlap == 0:
            continue
        scored.append((srv.id, overlap, wanted <= server_mods, len(server_mods)))

    if not scored:
        return None, f"kein Server deckt die {len(wanted)} verlangten Mods ab"

    full = [s for s in scored if s[2]]
    if full:
        full.sort(key=lambda s: s[3])  # kleinste Ausstattung = spezifischster Pack
        return full[0][0], f"Voll-Match ({len(wanted)} verlangte Mods)"

    scored.sort(key=lambda s: s[1], reverse=True)
    if len(scored) > 1 and scored[0][1] == scored[1][1]:
        return None, f"mehrdeutig ({scored[0][1]} gemeinsame Mods bei mehreren Servern)"
    top = scored[0]
    return top[0], f"Best-Overlap ({top[1]}/{len(wanted)} Mods, keine Voll-Abdeckung)"
