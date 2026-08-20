#!/usr/bin/env python3
"""Multi-Client-Universal-Lobby (Phase 3b) - Testlauf.

Startet den geteilten Hub: mehrere Clients landen in EINER Welt und sehen sich
gegenseitig (Bewegung + Chat). Lobby-Bot ist als virtueller Spieler immer dabei,
damit man den Hub schon mit einem einzigen Client testen kann.

    python scripts/hub_lobby.py --listen 25601 --replay atm10_capture.replay

Dann mit dem ATM10-Client auf <server-ip>:25601 verbinden. Fuer den Multiplayer-Test
einen zweiten Client verbinden - beide sehen sich laufen und koennen chatten.
"""

from __future__ import annotations

import argparse
import sys

sys.path.insert(0, ".")  # Repo-Root fuer app.services.*

from app.services import hub_service  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--listen", type=int, required=True)
    ap.add_argument("--replay", required=True, help="Pfad zur .replay-Datei")
    args = ap.parse_args()
    hub_service.serve(args.listen, args.replay)


if __name__ == "__main__":
    main()
