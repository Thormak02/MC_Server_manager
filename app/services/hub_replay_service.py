"""Per-Pack-Replay-Registry fuer den Universal-Hub.

Konvention: jedes Modpack bekommt sein Config-Replay unter
``data/replays/<server-slug>_capture.replay``. Existiert die Datei, kann der Hub
dieses Pack spoofen. Die Registry ``{server_id: pfad}`` wird beim Reconcile gebaut und
dem Hub uebergeben. Die Auswahl je Client laeuft ueber "Path A": der Dispatcher taggt
das erkannte Pack (Host ``modlobby-<server_id>.<domain>``) in die Weiterleitung, der Hub
waehlt daraus das passende Profil.

``data/`` ist gitignored -> Captures sind per-Box-Laufzeitdaten, nichts wird committet.
"""

from __future__ import annotations

from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.server import Server

REPLAY_DIR = "data/replays"


def replay_dir() -> Path:
    return Path(REPLAY_DIR)


def replay_path_for(slug: str) -> str:
    """Konventions-Pfad des Pack-Replays (relativ zum Repo-Root/cwd)."""
    return str(Path(REPLAY_DIR) / f"{slug}_capture.replay")


def has_replay(slug: str) -> bool:
    return Path(replay_path_for(slug)).exists()


def build_pack_registry(db: Session) -> dict[int, str]:
    """``{server_id: replay_pfad}`` fuer gateway_enabled-Server, deren Replay existiert."""
    registry: dict[int, str] = {}
    for srv in db.scalars(select(Server).where(Server.gateway_enabled.is_(True))).all():
        path = replay_path_for(srv.slug)
        if Path(path).exists():
            registry[srv.id] = path
    return registry


def build_pack_registry_runtime() -> dict[int, str]:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return build_pack_registry(db)
