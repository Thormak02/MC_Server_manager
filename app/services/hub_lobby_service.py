"""Universal-Lobby: der begehbare Python-Hub als manager-verwalteter Listener.

Startet/stoppt ``hub_service.Hub`` analog zum Gateway - **gated hinter dem Setting
``hub_lobby_enabled`` (Default AUS)**, damit sich am bestehenden Netzwerk nichts
aendert, solange die Universal-Lobby nicht eingeschaltet ist.

Der Hub laeuft auf einem eigenen (per Default lokalen) Port; das Gateway leitet den
Verkehr dorthin (siehe Schritt 1.3). So bleibt ``mc.friedrich-dietrich.de`` der einzige
oeffentliche Eingang. Reconcile ist idempotent -> gefahrlos bei jedem Start/Idle-Tick.
"""

from __future__ import annotations

import socket
from dataclasses import dataclass
from threading import RLock, Thread
from typing import Optional

_LOCK = RLock()
_LISTENER: "Optional[_HubListener]" = None
_BIND_FAILED = False


@dataclass
class _HubListener:
    port: int
    sock: socket.socket
    hub: object                      # hub_service.Hub (geladenes Replay + Roster)
    replay: str = ""                 # aktives modded-Replay
    vanilla: Optional[str] = None    # aktives vanilla-Replay (fuer Reconcile-Vergleich)
    thread: Optional[Thread] = None
    stopped: bool = False


def _glog(event: str, detail: str = "") -> None:
    try:
        from app.services import gateway_service

        gateway_service._glog(f"hublobby.{event}", detail)  # gemeinsames Gateway-Log
    except Exception:  # noqa: BLE001 - Logging darf den Hub nie stoeren
        pass


def is_running() -> bool:
    with _LOCK:
        return _LISTENER is not None


def current_port() -> int | None:
    with _LOCK:
        return _LISTENER.port if _LISTENER is not None else None


def _accept_loop(listener: "_HubListener") -> None:
    while not listener.stopped:
        try:
            conn, addr = listener.sock.accept()
        except OSError:
            break  # Socket geschlossen -> Listener gestoppt
        Thread(
            target=listener.hub.handle, args=(conn, addr),
            daemon=True, name="hublobby-conn",
        ).start()


def start_hub_lobby(port: int, replay_path: str, vanilla_replay_path: str | None = None) -> bool:
    """Hub-Listener auf ``port`` binden und mit ``replay_path`` (+ optional Vanilla-Replay)
    bedienen (ohne Flag-Check).

    Idempotent: gleicher Port -> No-op. Anderer Port/Replay -> Neustart. Bind ohne
    SO_REUSEADDR (wie Gateway/Sleep-Proxy); bindet beim naechsten Reconcile nach, falls
    der Port noch belegt ist."""
    global _LISTENER, _BIND_FAILED
    from app.services import hub_service

    vanilla = vanilla_replay_path or None
    with _LOCK:
        if (_LISTENER is not None and _LISTENER.port == int(port)
                and _LISTENER.replay == replay_path and _LISTENER.vanilla == vanilla):
            return True  # nichts geaendert
        if _LISTENER is not None:
            _stop_locked()  # Port ODER Replay geaendert -> neu starten
        try:
            hub = hub_service.Hub(replay_path, vanilla)
        except Exception as exc:  # noqa: BLE001 - fehlendes/kaputtes Replay
            _glog("replay_failed", f"{replay_path}: {exc!r}")
            return False
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.bind(("0.0.0.0", int(port)))
            sock.listen(64)
        except OSError as exc:
            if not _BIND_FAILED:
                _glog("bind_failed", f"port={port} error={exc!r} (Port belegt? bindet nach.)")
                _BIND_FAILED = True
            return False
        _BIND_FAILED = False
        listener = _HubListener(port=int(port), sock=sock, hub=hub,
                                replay=replay_path, vanilla=vanilla)
        # Virtueller Lobby-Bot (macht den Hub schon mit einem echten Client testbar).
        Thread(target=hub.animate_bot, daemon=True, name="hublobby-bot").start()
        thread = Thread(target=_accept_loop, args=(listener,), daemon=True, name="hublobby")
        listener.thread = thread
        _LISTENER = listener
        thread.start()
        _glog("started", f"port={port} replay={replay_path}")
        return True


def _stop_locked() -> None:
    global _LISTENER
    if _LISTENER is None:
        return
    _LISTENER.stopped = True
    try:
        _LISTENER.sock.close()
    except OSError:
        pass
    _glog("stopped", f"port={_LISTENER.port}")
    _LISTENER = None


def stop_hub_lobby() -> None:
    with _LOCK:
        _stop_locked()


def reconcile_hub_lobby() -> None:
    """Listener an ``hub_lobby_enabled`` / Port / Replay angleichen (selbstheilend)."""
    from app.services import app_setting_service as s

    if not s.get_hub_lobby_enabled_runtime():
        if is_running():
            stop_hub_lobby()
        return
    start_hub_lobby(
        s.get_hub_lobby_port_runtime(),
        s.get_hub_lobby_replay_runtime(),
        s.get_hub_lobby_vanilla_replay_runtime() or None,
    )
