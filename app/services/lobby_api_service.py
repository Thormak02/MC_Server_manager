"""Lobby-API: lokaler TCP/JSON-Endpoint fuer die Vorab-Pruefung des Serverwechsels.

Warum ueberhaupt ein Endpoint? Ein nativer Transfer TRENNT die Lobby-Verbindung - ist
das Ziel gebannt/voll/nicht bereit, fliegt der Spieler raus statt eine Meldung zu
bekommen. Also wird VOR dem Transfer gefragt. Die Antwort kommt aus
``lobby_service.check_join_allowed`` - derselben Funktion, die auch der Python-Hub
benutzt. Damit sagen BEIDE Lobbys (Python-Hub und Bukkit-Lobby) garantiert dasselbe,
egal mit welchem Client der Spieler unterwegs ist.

Protokoll (bewusst identisch zur Presence-Bridge: eine Zeile JSON in UTF-8, mit
Zeilenumbruch abgeschlossen):
    -> {"token": "...", "op": "join_check", "server_id": 7, "player": "David"}
    <- {"ok": true}                      | {"ok": false, "reason": "..."}
Danach wird die Verbindung geschlossen (kurzlebig, ein Request pro Verbindung).

Nur auf 127.0.0.1 gebunden - die Lobby-Server laufen auf demselben Host wie der Manager.

FAIL-OPEN ist Absicht und gilt auf BEIDEN Seiten: antwortet der Endpoint nicht, passt
der Token nicht oder ist die Antwort unverstaendlich, laesst das Plugin den Wechsel zu.
Eine kaputte Pruefung darf niemanden aussperren. Nur ein explizites ``ok: false``
lehnt ab - und liefert dann auch gleich den passenden Text fuer den Spieler.
"""

from __future__ import annotations

import json
import secrets
import socket
import threading

# Ein Request ist ein paar hundert Bytes - alles darueber ist Unsinn oder Angriff.
_MAX_LINE_BYTES = 8 * 1024
_CLIENT_TIMEOUT = 10.0

_SRV_LOCK = threading.Lock()
_SRV_SOCK: socket.socket | None = None
_SRV_STATE: dict = {}


def _handle_request(payload: dict) -> dict:
    """Eine authentifizierte Anfrage beantworten."""
    op = str(payload.get("op") or "").strip()
    if op == "ping":
        return {"ok": True, "pong": True}
    if op != "join_check":
        return {"error": "unknown_op"}

    try:
        server_id = int(payload.get("server_id"))
    except (TypeError, ValueError):
        return {"error": "bad_server_id"}

    from app.services import lobby_service

    allowed, reason = lobby_service.check_join_allowed_by_id(
        server_id, str(payload.get("player") or "")
    )
    return {"ok": bool(allowed)} if allowed else {"ok": False, "reason": reason}


def _read_line(conn: socket.socket) -> bytes | None:
    buffer = bytearray()
    while b"\n" not in buffer:
        if len(buffer) > _MAX_LINE_BYTES:
            return None
        chunk = conn.recv(4096)
        if not chunk:
            return None
        buffer.extend(chunk)
    return bytes(buffer.split(b"\n", 1)[0])


def _handle_client(conn: socket.socket, token: str) -> None:
    try:
        conn.settimeout(_CLIENT_TIMEOUT)
        line = _read_line(conn)
        if line is None:
            return
        try:
            payload = json.loads(line.decode("utf-8", "replace"))
        except ValueError:
            payload = None
        if not isinstance(payload, dict):
            response = {"error": "bad_request"}
        elif not secrets.compare_digest(str(payload.get("token") or ""), token):
            response = {"error": "auth"}
        else:
            try:
                response = _handle_request(payload)
            except Exception as exc:  # noqa: BLE001 - lieber durchlassen als aussperren
                print(f"[lobby-api] Pruefung fehlgeschlagen: {exc!r}")
                response = {"error": "internal"}
        conn.sendall((json.dumps(response, ensure_ascii=False) + "\n").encode("utf-8"))
        # Erst nach dem Flush schliessen: sonst verwirft Windows die Antwort per RST.
        try:
            conn.shutdown(socket.SHUT_WR)
        except OSError:
            pass
    except OSError:
        pass
    except Exception as exc:  # noqa: BLE001
        print(f"[lobby-api] Client-Fehler: {exc!r}")
    finally:
        try:
            conn.close()
        except OSError:
            pass


def _accept_loop(listener: socket.socket, token: str) -> None:
    while True:
        try:
            conn, _addr = listener.accept()
        except OSError:
            return  # Socket geschlossen -> Server gestoppt
        threading.Thread(target=_handle_client, args=(conn, token),
                         daemon=True, name="lobby-api-client").start()


def start_server(port: int, token: str) -> bool:
    """Endpoint binden (idempotent; bindet neu, wenn Port oder Token sich aendern)."""
    global _SRV_SOCK, _SRV_STATE
    with _SRV_LOCK:
        desired = {"port": int(port), "token": token}
        if _SRV_SOCK is not None and _SRV_STATE == desired:
            return True
        _stop_locked()
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.bind(("127.0.0.1", int(port)))   # NUR loopback (Lobbys sind lokal)
            sock.listen(16)
        except OSError as exc:
            print(f"[lobby-api] Port {port} nicht bindbar: {exc}")
            return False
        _SRV_SOCK = sock
        _SRV_STATE = desired
        threading.Thread(target=_accept_loop, args=(sock, token),
                         daemon=True, name="lobby-api-accept").start()
        return True


def _stop_locked() -> None:
    global _SRV_SOCK, _SRV_STATE
    if _SRV_SOCK is not None:
        try:
            _SRV_SOCK.close()
        except OSError:
            pass
    _SRV_SOCK = None
    _SRV_STATE = {}


def stop_server() -> None:
    with _SRV_LOCK:
        _stop_locked()


def server_running() -> bool:
    with _SRV_LOCK:
        return _SRV_SOCK is not None


def active_port() -> int:
    with _SRV_LOCK:
        return int(_SRV_STATE.get("port") or 0)


def reconcile_lobby_api() -> bool:
    """Endpoint passend zu den Einstellungen aufsetzen (beim Start + nach Aenderungen).

    Bewusst NICHT hinter einem Schalter: die Ablehnung soll ueberall gleich greifen.
    Nur Loopback, Token-geschuetzt - dadurch keine neue Angriffsflaeche nach aussen.
    """
    try:
        from app.services import app_setting_service

        cfg = app_setting_service.get_lobby_api_runtime()
        return start_server(int(cfg["port"]), str(cfg["token"]))
    except Exception as exc:  # noqa: BLE001 - darf den Manager-Start nie verhindern
        print(f"[lobby-api] Start fehlgeschlagen: {exc!r}")
        return False
