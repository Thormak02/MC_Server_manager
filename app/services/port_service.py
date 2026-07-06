"""Zuweisung freier Server-Ports.

"Frei" heisst host-weit frei (echter Bind-Test auf 0.0.0.0), nicht nur "kein
anderer Server-Datensatz nutzt den Port". Vergeben wird aus einem konfigurier-
baren Bereich (Standard 25565-25999, Minecraft-Konvention), der sich nicht mit
ueblichen Diensten oder dem Manager-Port ueberschneidet. Dadurch bekommt jeder
Server einen festen, kollisionsfreien Port - auch praktisch fuers Sleep-Feature.
"""

from __future__ import annotations

import socket
from collections.abc import Iterable

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models.server import Server


def is_port_free(port: int) -> bool:
    """Host-weit pruefen, ob ``port`` aktuell an keinem Dienst gebunden ist.

    Bewusst OHNE SO_REUSEADDR: unter Windows wuerde REUSEADDR das Binden an
    einen bereits belegten Port erlauben (Hijacking) und den Port faelschlich
    als frei melden. Ohne REUSEADDR ist der Bind exklusiv -> korrekte Erkennung.
    """
    if not (1 <= port <= 65535):
        return False
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        try:
            probe.bind(("0.0.0.0", port))
            return True
        except OSError:
            return False


def used_server_ports(db: Session, *, exclude_server_id: int | None = None) -> set[int]:
    """Alle in der DB vergebenen Ports (oeffentlich + Sleep-intern)."""
    stmt = select(Server.id, Server.port, Server.sleep_internal_port)
    ports: set[int] = set()
    for server_id, port, internal_port in db.execute(stmt):
        if exclude_server_id is not None and server_id == exclude_server_id:
            continue
        if port:
            ports.add(int(port))
        if internal_port:
            ports.add(int(internal_port))
    return ports


def allocate_server_port(
    db: Session,
    *,
    preferred: int | None = None,
    exclude: Iterable[int] | None = None,
    exclude_server_id: int | None = None,
) -> int:
    """Einen freien Port zurueckgeben.

    Bevorzugt ``preferred`` (falls im Bereich, nicht vergeben und host-frei),
    sonst den ersten passenden Port aus dem konfigurierten Bereich. ``exclude``
    schliesst zusaetzliche Ports aus (z.B. den oeffentlichen Port bei der
    Sleep-Intern-Port-Wahl).
    """
    settings = get_settings()
    start = int(settings.server_port_range_start)
    end = int(settings.server_port_range_end)
    if start > end:
        start, end = end, start

    reserved = used_server_ports(db, exclude_server_id=exclude_server_id)
    if exclude:
        reserved |= {int(p) for p in exclude}

    if (
        preferred is not None
        and start <= preferred <= end
        and preferred not in reserved
        and is_port_free(preferred)
    ):
        return int(preferred)

    for port in range(start, end + 1):
        if port in reserved:
            continue
        if is_port_free(port):
            return port

    raise ValueError(
        f"Kein freier Port im Bereich {start}-{end} verfuegbar. "
        "Bitte den Bereich (MCSM_SERVER_PORT_RANGE_*) erweitern."
    )
