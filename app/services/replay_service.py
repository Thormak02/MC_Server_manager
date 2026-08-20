"""Replay-Kern fuer die begehbare modded Lobby (Phase 3).

Ein echter Modpack-Handshake wird einmal mitgeschnitten (scripts/capture_handshake.py
-> .replay) und danach jedem frischen Client dieses Packs vorgespielt: wir tun so, als
waeren wir der Modpack-Server, spielen die aufgezeichneten Server->Client-Pakete ab und
warten an den Stellen, wo der echte Server auf eine Client-Antwort wartete. So kommt der
Client durch NeoForges Mod-/Registry-Aushandlung, ohne dass wir die Mods laufen lassen.

Hier liegen nur die **reinen** Bausteine (Datei parsen, Ablaufplan bilden) - das
Socket-Handling ist im scripts/replay_lobby.py bzw. spaeter im Dispatcher.

.replay-Format: ``MCRP\\x01`` dann pro Paket ``[dir:1][len:4 BE][raw]``
(dir 0 = S->C zum Abspielen, 1 = C->S = Checkpoint/warten).
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from app.services import mc_dispatch as mcd

_MAGIC = b"MCRP\x01"


@dataclass(frozen=True)
class Record:
    to_client: bool   # True = S->C (abspielen), False = C->S (Checkpoint)
    packet_id: int
    raw: bytes


@dataclass(frozen=True)
class Step:
    """Ein Ablaufschritt: entweder eine Sende-Charge (S->C) oder ein Warte-Checkpoint."""
    send: list[bytes]   # zu sendende Roh-Pakete (leer bei einem Warte-Schritt)
    wait: int           # Anzahl erwarteter Client-Pakete (0 bei einem Sende-Schritt)


def load_replay(data: bytes) -> list[Record]:
    """Roh-Bytes einer .replay-Datei in Records zerlegen."""
    if data[: len(_MAGIC)] != _MAGIC:
        raise ValueError("keine gueltige .replay-Datei (Magic fehlt)")
    off = len(_MAGIC)
    records: list[Record] = []
    while off < len(data):
        if off + 5 > len(data):
            break
        direction = data[off]
        length = int.from_bytes(data[off + 1 : off + 5], "big")
        off += 5
        raw = data[off : off + length]
        off += length
        if len(raw) != length:
            break
        parsed = mcd.try_read_packet(raw)
        pid = parsed[0] if parsed else -1
        records.append(Record(to_client=(direction == 0), packet_id=pid, raw=bytes(raw)))
    return records


def load_replay_file(path: str) -> list[Record]:
    return load_replay(Path(path).expanduser().read_bytes())


def find_config_start(records: list[Record]) -> int:
    """Index des ersten Config-Pakets (direkt nach dem Login-Ack).

    Login (ohne Kompression): C Handshake, C LoginStart, S LoginSuccess(0x02),
    C LoginAck(0x03). Wir spielen den Login NICHT ab (der neue Client hat eigene
    UUID/Name) -> ab hier beginnt der abspielbare Teil.
    """
    for i, rec in enumerate(records):
        if rec.to_client and rec.packet_id == 0x02:  # LoginSuccess
            for j in range(i + 1, len(records)):
                if not records[j].to_client and records[j].packet_id == 0x03:  # LoginAck
                    return j + 1
            break
    return 0


def build_steps(records: list[Record], start: int = 0) -> list[Step]:
    """Records ab ``start`` in Sende-Chargen und Warte-Checkpoints gruppieren.

    Aufeinanderfolgende S->C-Pakete werden zu einer Sende-Charge zusammengefasst;
    eine Folge von C->S-Paketen wird zu einem Warte-Schritt (Anzahl = so viele
    Client-Pakete abwarten, wie der echte Client an dieser Stelle sendete).
    """
    steps: list[Step] = []
    i = start
    n = len(records)
    while i < n:
        if records[i].to_client:
            batch: list[bytes] = []
            while i < n and records[i].to_client:
                batch.append(records[i].raw)
                i += 1
            steps.append(Step(send=batch, wait=0))
        else:
            count = 0
            while i < n and not records[i].to_client:
                count += 1
                i += 1
            steps.append(Step(send=[], wait=count))
    return steps
