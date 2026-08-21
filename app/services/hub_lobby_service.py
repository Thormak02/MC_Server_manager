"""Universal-Lobby: der begehbare Python-Hub als manager-verwalteter Listener.

Startet/stoppt ``hub_service.Hub`` analog zum Gateway - **gated hinter dem Setting
``hub_lobby_enabled`` (Default AUS)**, damit sich am bestehenden Netzwerk nichts
aendert, solange die Universal-Lobby nicht eingeschaltet ist.

Der Hub bindet ZWEI lokale Ports, damit der Client-Typ eindeutig ist (unabhaengig davon,
wie ein davor geschalteter ViaProxy den Hostnamen weiterreicht):
  * modded-Port  -> Gateway leitet ``modlobby`` direkt hierher (1.21.1, kein ViaProxy).
  * vanilla-Port -> Ziel von ViaProxy bzw. direktes ``vanlobby`` (nur wenn ein
    Vanilla-Replay geladen ist).
So bleibt ``mc.friedrich-dietrich.de`` der einzige oeffentliche Eingang. Reconcile ist
idempotent -> gefahrlos bei jedem Start/Idle-Tick.
"""

from __future__ import annotations

import socket
from dataclasses import dataclass, field
from threading import RLock, Thread
from typing import Optional

_LOCK = RLock()
_LISTENER: "Optional[_HubListener]" = None
_BIND_FAILED = False


@dataclass
class _HubListener:
    modded_port: int
    vanilla_port: int
    modded_sock: socket.socket
    vanilla_sock: Optional[socket.socket]   # None, wenn kein Vanilla-Profil geladen
    hub: object                             # hub_service.Hub (Profile + Roster)
    replay: str = ""                        # aktives modded-Replay (Reconcile-Vergleich)
    vanilla: Optional[str] = None           # aktives vanilla-Replay
    pack_replays: dict = field(default_factory=dict)  # {server_id: pfad} (Per-Pack)
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
        return _LISTENER.modded_port if _LISTENER is not None else None


def current_vanilla_port() -> int | None:
    with _LOCK:
        return _LISTENER.vanilla_port if (_LISTENER and _LISTENER.vanilla_sock) else None


def _accept_loop(listener: "_HubListener", sock: socket.socket, kind: str) -> None:
    while not listener.stopped:
        try:
            conn, addr = sock.accept()
        except OSError:
            break  # Socket geschlossen -> Listener gestoppt
        Thread(
            target=listener.hub.handle, args=(conn, addr, kind),
            daemon=True, name=f"hublobby-{kind}",
        ).start()


def _bind(port: int) -> socket.socket | None:
    """Ohne SO_REUSEADDR binden (wie Gateway/Sleep-Proxy)."""
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.bind(("0.0.0.0", int(port)))
        sock.listen(64)
        return sock
    except OSError:
        return None


def start_hub_lobby(modded_port: int, vanilla_port: int, replay_path: str,
                    vanilla_replay_path: str | None = None,
                    pack_replays: dict[int, str] | None = None) -> bool:
    """Hub mit modded-Port (+ vanilla-Port, falls Vanilla-Replay) binden (ohne Flag-Check).

    Idempotent: gleiche Ports/Replays (inkl. Per-Pack-Registry) -> No-op. Aenderung ->
    Neustart. Bindet beim naechsten Reconcile nach, falls ein Port noch belegt ist."""
    global _LISTENER, _BIND_FAILED
    from app.services import hub_service

    vanilla = vanilla_replay_path or None
    packs = dict(pack_replays or {})
    with _LOCK:
        if (_LISTENER is not None
                and _LISTENER.modded_port == int(modded_port)
                and _LISTENER.vanilla_port == int(vanilla_port)
                and _LISTENER.replay == replay_path and _LISTENER.vanilla == vanilla):
            if _LISTENER.pack_replays == packs:
                return True  # nichts geaendert
            # NUR die Per-Pack-Replays haben sich geaendert (neuer Capture) -> live
            # aktualisieren statt Hub-Rebuild: keine Kicks der Lobby-Spieler, kein
            # riskantes Rebind ohne SO_REUSEADDR (TIME_WAIT koennte den Hub offline nehmen).
            _LISTENER.hub.update_pack_profiles(packs)
            _LISTENER.pack_replays = packs
            _glog("packs_updated", f"{sorted(packs)}")
            return True
        if _LISTENER is not None:
            _stop_locked()
        try:
            hub = hub_service.Hub(replay_path, vanilla, packs)
        except Exception as exc:  # noqa: BLE001 - fehlendes/kaputtes Replay
            _glog("replay_failed", f"{replay_path}: {exc!r}")
            return False

        modded_sock = _bind(modded_port)
        if modded_sock is None:
            if not _BIND_FAILED:
                _glog("bind_failed", f"modded-port={modded_port} belegt? bindet nach.")
                _BIND_FAILED = True
            return False
        # Vanilla-Port nur binden, wenn es ueberhaupt ein Vanilla-Profil gibt.
        vanilla_sock = _bind(vanilla_port) if hub.vanilla is not None else None
        if hub.vanilla is not None and vanilla_sock is None:
            if not _BIND_FAILED:
                _glog("bind_failed", f"vanilla-port={vanilla_port} belegt? bindet nach.")
                _BIND_FAILED = True
            modded_sock.close()
            return False
        _BIND_FAILED = False

        listener = _HubListener(
            modded_port=int(modded_port), vanilla_port=int(vanilla_port),
            modded_sock=modded_sock, vanilla_sock=vanilla_sock, hub=hub,
            replay=replay_path, vanilla=vanilla, pack_replays=packs,
        )
        Thread(target=hub.animate_bot, daemon=True, name="hublobby-bot").start()
        Thread(target=_accept_loop, args=(listener, modded_sock, "modded"),
               daemon=True, name="hublobby-modded").start()
        if vanilla_sock is not None:
            Thread(target=_accept_loop, args=(listener, vanilla_sock, "vanilla"),
                   daemon=True, name="hublobby-vanilla").start()
        _LISTENER = listener
        _glog("started",
              f"modded:{modded_port} vanilla:{vanilla_port if vanilla_sock else '-'} replay={replay_path}")
        return True


def _stop_locked() -> None:
    global _LISTENER
    if _LISTENER is None:
        return
    _LISTENER.stopped = True
    for sock in (_LISTENER.modded_sock, _LISTENER.vanilla_sock):
        if sock is not None:
            try:
                sock.close()
            except OSError:
                pass
    _glog("stopped", f"modded:{_LISTENER.modded_port}")
    _LISTENER = None


def stop_hub_lobby() -> None:
    with _LOCK:
        _stop_locked()


def reconcile_hub_lobby() -> None:
    """Listener an ``hub_lobby_enabled`` / Ports / Replays angleichen (selbstheilend)."""
    from app.services import app_setting_service as s

    if not s.get_hub_lobby_enabled_runtime():
        if is_running():
            stop_hub_lobby()
        return
    from app.services import hub_replay_service

    start_hub_lobby(
        s.get_hub_lobby_port_runtime(),
        s.get_hub_lobby_vanilla_port_runtime(),
        s.get_hub_lobby_replay_runtime(),
        s.get_hub_lobby_vanilla_replay_runtime() or None,
        pack_replays=hub_replay_service.build_pack_registry_runtime(),
    )
