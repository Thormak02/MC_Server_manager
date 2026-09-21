"""Lobby-API: lokaler TCP/JSON-Endpoint fuer die Vorab-Pruefung des Serverwechsels.

Warum ueberhaupt ein Endpoint? Ein nativer Transfer TRENNT die Lobby-Verbindung - ist
das Ziel gebannt/voll/nicht bereit, fliegt der Spieler raus statt eine Meldung zu
bekommen. Also wird VOR dem Transfer gefragt. Die Antwort kommt aus
``lobby_service.check_join_allowed`` - derselben Funktion, die auch der Python-Hub
benutzt. Damit sagen BEIDE Lobbys (Python-Hub und Bukkit-Lobby) garantiert dasselbe,
egal mit welchem Client der Spieler unterwegs ist.

Protokoll (bewusst identisch zur Presence-Bridge: eine Zeile JSON in UTF-8, mit
Zeilenumbruch abgeschlossen):
    -> {"token": "...", "op": "join_check", "server_id": 7, "player": "David",
        "client": {"brand": "vanilla", "source": "bukkit"}, "override": false}
    <- {"ok": true} | {"ok": true, "note": ".."}
     | {"ok": false, "reason": "..", "confirm": true}   <- Rueckfrage, ueberstimmbar
     | {"ok": false, "reason": ".."}                    <- hart
     | {"error": ".."}                                  <- kein Urteil
    -> {"token": "...", "op": "fit_check", "server_ids": [2, 5, 7], "player": "David",
        "client": {"brand": "vanilla"}}
    <- {"ok": true, "fits": {"2": {"level": "confirm", "short": "Braucht NeoForge"}}}
Danach wird die Verbindung geschlossen (kurzlebig, ein Request pro Verbindung).

``client`` und ``override`` sind optional. Ein altes Plugin schickt gar keinen
client-Block - dann bleibt der Client-Abgleich stumm und die Antwort ist exakt die von
frueher. Genau dieses Vorhandensein IST die Versionsverhandlung; eine Versionsnummer
braucht es nicht.

NIEMALS EIN JSON-ARRAY IN DER ANTWORT. Der Mini-Parser des Plugins (Json.java) kennt
kein '[' - er faellt bis zur Zahl durch, wirft und liefert null, und null liest
JoinCheck als ALLOW. Ein Array in der Antwort wuerde auf jeder noch nicht neu
gestarteten Lobby also auch Ban und Whitelist still abschalten. ``fits`` ist deshalb ein
OBJEKT (Schluessel = Server-ID als String) und mehrere Hinweise werden zu EINEM String
verkettet. In der ANFRAGE sind Arrays erlaubt - die liest Python.

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

# Obergrenze fuer fit_check: jede ID kostet eine eigene DB-Session. Ein Menue hat
# ein paar Dutzend Eintraege - was darueber liegt, bleibt einfach ohne Marker (und
# ein fehlender Marker heisst "passt", also fail-open).
_MAX_FIT_IDS = 64

_SRV_LOCK = threading.Lock()
_SRV_SOCK: socket.socket | None = None
_SRV_STATE: dict = {}


def _handle_request(payload: dict) -> dict:
    """Eine authentifizierte Anfrage beantworten."""
    op = str(payload.get("op") or "").strip()
    if op == "ping":
        return {"ok": True, "pong": True}
    if op == "join_check":
        return _join_check(payload)
    if op == "fit_check":
        return _fit_check(payload)
    return {"error": "unknown_op"}


def _join_check(payload: dict) -> dict:
    """Vorab-Pruefung fuer EINEN Wechsel."""
    try:
        server_id = int(payload.get("server_id"))
    except (TypeError, ValueError):
        return {"error": "bad_server_id"}

    from app.services import join_match_service, lobby_service

    player = str(payload.get("player") or "")
    # Alles Unerwartete im client-Block (fehlend, Liste, Zahl, Unsinn) ergibt None -
    # und None schaltet den Client-Abgleich fuer diese Anfrage schlicht ab.
    client = join_match_service.profile_from_payload(payload.get("client"))
    override = bool(payload.get("override"))

    if client is None and not override:
        # Ohne Client gibt es nichts abzugleichen. Dann laeuft die Anfrage ueber denselben
        # schmalen Einstiegspunkt wie der Python-Hub - alte Jars und Hub sagen garantiert
        # dasselbe.
        allowed, reason = lobby_service.check_join_allowed_by_id(server_id, player)
        return {"ok": True} if allowed else {"ok": False, "reason": reason}

    verdict = lobby_service.evaluate_join_by_id(
        server_id, player, client=client, override=override
    )
    if not verdict.ok:
        if verdict.confirm:
            # Ohne diese Zeile hinterlaesst eine zu Unrecht verhinderte Verbindung
            # keinerlei Spur - und der Brand, auf dem sie beruht, ist faelschbar.
            print(f"[lobby-api] Rueckfrage ({verdict.code}) fuer {player!r} "
                  f"auf Server {server_id}: {verdict.reason}")
            return {"ok": False, "reason": verdict.reason, "confirm": True}
        return {"ok": False, "reason": verdict.reason}
    if verdict.note:
        return {"ok": True, "note": verdict.note}
    return {"ok": True}


def _fit_check(payload: dict) -> dict:
    """Marker fuer das Server-Menue: welche Ziele passen NICHT zum Client?

    ``player`` wird mitgeschickt, aber nicht gebraucht - die Passung haengt am Client,
    nicht am Namen. Nur Server mit ``level != "ok"`` kommen in die Antwort; ein
    fehlender Eintrag heisst "passt".
    """
    raw_ids = payload.get("server_ids")
    if not isinstance(raw_ids, (list, tuple)):
        return {"error": "bad_server_ids"}

    from app.services import join_match_service, lobby_service

    fits: dict[str, dict] = {}
    client = join_match_service.profile_from_payload(payload.get("client"))
    if client is None:
        return {"ok": True, "fits": fits}     # kein Client bekannt -> keine Marker

    # Gebuendelt: EINE DB-Session fuer das ganze Menue statt einer je Eintrag.
    for server_id, fit in lobby_service.evaluate_fits_by_ids(
            list(raw_ids)[:_MAX_FIT_IDS], client).items():
        fits[str(server_id)] = {"level": fit.level, "short": fit.short}
    return {"ok": True, "fits": fits}


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
