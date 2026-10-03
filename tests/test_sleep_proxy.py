import socket
import threading
import time

from app.services import mc_protocol as mp
from app.services import sleep_proxy_service as sp


def _handshake(next_state: int, port: int) -> bytes:
    payload = (
        mp.encode_varint(0x00)
        + mp.encode_varint(765)
        + mp.encode_string("localhost")
        + int(port).to_bytes(2, "big")
        + mp.encode_varint(next_state)
    )
    return mp.encode_varint(len(payload)) + payload


def test_sleeping_status_response(monkeypatch):
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp.process_service, "is_running", lambda sid: False)

    public_port = sp.find_free_port()
    assert sp.start_proxy(9991, public_port, sp.find_free_port())
    try:
        client = socket.create_connection(("127.0.0.1", public_port), timeout=5)
        client.sendall(_handshake(mp.NEXT_STATE_STATUS, public_port))
        client.sendall(mp.encode_varint(1) + mp.encode_varint(0x00))  # status request
        data = client.recv(4096)
        client.close()
        assert b"Schlaeft" in data  # MOTD aus der synthetischen Status-Antwort
    finally:
        sp.stop_proxy(9991)


def test_server_status_view_maps_sleeping_and_colors():
    from types import SimpleNamespace as S

    from app.services.server_service import server_status_view

    assert server_status_view(S(status="running", sleep_enabled=True)) == {
        "status": "running",
        "color": "online",
    }
    # Sleep-Server im Zustand stopped -> "sleeping" / lila.
    assert server_status_view(S(status="stopped", sleep_enabled=True)) == {
        "status": "sleeping",
        "color": "sleeping",
    }
    assert server_status_view(S(status="stopped", sleep_enabled=False)) == {
        "status": "stopped",
        "color": "offline",
    }
    assert server_status_view(S(status="starting", sleep_enabled=False))["color"] == "pending"
    # Ein Absturz wird nicht als "sleeping" maskiert.
    assert server_status_view(S(status="crashed", sleep_enabled=True))["color"] == "offline"


def test_sleep_delay_split_and_roundtrip():
    from app.services.server_service import (
        sleep_delay_to_seconds,
        split_sleep_delay_seconds,
    )

    assert split_sleep_delay_seconds(300) == {"value": 5, "unit": "minutes"}
    assert split_sleep_delay_seconds(3600) == {"value": 1, "unit": "hours"}
    assert split_sleep_delay_seconds(86400) == {"value": 1, "unit": "days"}
    assert split_sleep_delay_seconds(90) == {"value": 90, "unit": "seconds"}
    assert split_sleep_delay_seconds(0) == {"value": 0, "unit": "seconds"}

    for seconds in (0, 45, 300, 3600, 5400, 86400, 172800):
        parts = split_sleep_delay_seconds(seconds)
        assert sleep_delay_to_seconds(parts["value"], parts["unit"]) == seconds

    assert sleep_delay_to_seconds(2, "days") == 172800
    assert sleep_delay_to_seconds(None, "days") is None


def test_reconcile_starts_and_stops_proxy(client, monkeypatch):
    # Reloadetes Modul aus sys.modules verwenden (conftest reloadet es je Test).
    import app.services.sleep_proxy_service as sp_live
    from app.db.session import SessionLocal
    from app.models.server import Server

    monkeypatch.setattr(sp_live, "_log", lambda *a, **k: None)

    pub = sp_live.find_free_port()
    internal = sp_live.find_free_port()
    while internal == pub:
        internal = sp_live.find_free_port()

    with SessionLocal() as db:
        srv = Server(
            name="sleepy-rc",
            slug="sleepy-rc",
            server_type="paper",
            mc_version="1.20.1",
            base_path="C:/tmp/sleepy-rc",
            port=pub,
            sleep_enabled=True,
            sleep_internal_port=internal,
        )
        db.add(srv)
        db.commit()
        sid = srv.id

    try:
        sp_live.reconcile_proxies()
        assert sid in sp_live._PROXIES  # Proxy laeuft fuer Sleep-Server

        with SessionLocal() as db:
            srv = db.get(Server, sid)
            srv.sleep_enabled = False
            db.add(srv)
            db.commit()

        sp_live.reconcile_proxies()
        assert sid not in sp_live._PROXIES  # nach Deaktivierung gestoppt
    finally:
        sp_live.shutdown_all()


def test_sleep_proxy_binds_public_for_direct_access(client, monkeypatch):
    """Der Wake-Proxy bindet 0.0.0.0 -> Direkt-Verbindungen (Port/Domain) wecken + erreichen den
    Server. Spigot ist online-mode (kein Backend) -> sicher trotz oeffentlichem Port."""
    import app.services.sleep_proxy_service as sp_live
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import app_setting_service

    monkeypatch.setattr(sp_live, "_log", lambda *a, **k: None)
    pub = sp_live.find_free_port()
    internal = sp_live.find_free_port()
    while internal == pub:
        internal = sp_live.find_free_port()

    with SessionLocal() as db:
        app_setting_service.set_network_mode(db, "velocity")
        srv = Server(
            name="spig-be", slug="spig-be", server_type="spigot", mc_version="1.21.11",
            base_path="C:/tmp/spig-be", port=pub, sleep_enabled=True,
            sleep_internal_port=internal, gateway_enabled=True,
        )
        db.add(srv)
        db.commit()
        sid = srv.id

    try:
        sp_live.reconcile_proxies()
        assert sid in sp_live._PROXIES
        assert sp_live._PROXIES[sid].bind_host == "0.0.0.0"   # oeffentlich -> direkt erreichbar
    finally:
        sp_live.shutdown_all()


def test_transfer_intent_triggers_wake(monkeypatch):
    """Ein per Lobby-Transfer weitergereichter Client nutzt next_state=3 (Transfer).
    Der schlafende Server muss trotzdem geweckt werden (frueher nur bei next_state=2).
    """
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp.process_service, "is_running", lambda sid: False)
    woke: list[int] = []

    def fake_wake(server_id, client, **_kwargs):
        # **_kwargs: _handle_connection uebergibt jetzt deadline/pending.
        woke.append(server_id)
        return False  # kein Backend im Test -> nach dem Wecken abbrechen

    monkeypatch.setattr(sp, "_wake_server", fake_wake)

    public_port = sp.find_free_port()
    assert sp.start_proxy(9993, public_port, sp.find_free_port())
    try:
        client = socket.create_connection(("127.0.0.1", public_port), timeout=5)
        client.sendall(_handshake(mp.NEXT_STATE_TRANSFER, public_port))
        time.sleep(0.4)
        client.close()
        assert woke == [9993]  # Transfer-Intent hat den Wake ausgeloest
    finally:
        sp.stop_proxy(9993)


def test_forward_when_running(monkeypatch):
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp.process_service, "is_running", lambda sid: True)

    backend = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    backend.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    backend.bind(("127.0.0.1", 0))
    backend.listen(1)
    internal_port = backend.getsockname()[1]
    received: list[bytes] = []

    def serve():
        conn, _ = backend.accept()
        received.append(conn.recv(4096))
        conn.sendall(b"HELLO")
        conn.close()

    threading.Thread(target=serve, daemon=True).start()

    public_port = sp.find_free_port()
    assert sp.start_proxy(9992, public_port, internal_port)
    try:
        client = socket.create_connection(("127.0.0.1", public_port), timeout=5)
        hs = _handshake(mp.NEXT_STATE_LOGIN, public_port)
        client.sendall(hs)
        time.sleep(0.4)
        response = client.recv(4096)
        client.close()
        assert received and received[0] == hs  # Handshake transparent weitergeleitet
        assert response == b"HELLO"
    finally:
        sp.stop_proxy(9992)
        backend.close()


# --------------------------------------------------------------------------- #
# Wecken ohne Stille: Grenzen, Hintergrund-Start, Abbruch-Erkennung
# --------------------------------------------------------------------------- #
class _FakeClient:
    """Attrappen-Socket: sammelt Gesendetes, liefert vorgegebene recv-Stuecke.

    Kein echtes Netz, keine echten Prozesse - die Tests pruefen Protokoll-
    Entscheidungen, nicht Sockets. ``None`` als Stueck bedeutet "Timeout".
    """

    def __init__(self, recv_chunks=None):
        self.sent: list[bytes] = []
        self.timeouts: list[float | None] = []
        self.closed = False
        self._chunks = list(recv_chunks or [])

    def sendall(self, data):
        self.sent.append(bytes(data))

    def settimeout(self, value):
        self.timeouts.append(value)

    def recv(self, _size=0):
        chunk = self._chunks.pop(0) if self._chunks else None
        if chunk is None:
            raise socket.timeout()
        return chunk

    def close(self):
        self.closed = True

    def data(self) -> bytes:
        return b"".join(self.sent)


class _FakeDB:
    """Minimaler SessionLocal-Ersatz: liefert immer denselben Server."""

    def __init__(self, server):
        self._server = server

    def __enter__(self):
        return self

    def __exit__(self, *_exc):
        return False

    def get(self, _model, _ident):
        return self._server


def _reset_wake_state() -> None:
    sp._WAKE_PENDING.clear()
    sp._WAKE_ERRORS.clear()


def test_wake_timeout_stays_below_client_patience():
    """Stilles Warten muss unter der Geduld des Clients bleiben.

    Der Client haengt ReadTimeoutHandler(30) als ERSTEN Handler in seine
    Netty-Pipeline und gibt nach 30 s ohne eingehendes Paket auf - in jedem
    Zustand und auch bei Fabric/Forge/NeoForge. 15 s Warten + 5 s Handshake-
    Lesen = 20 s lassen 10 s Luft. Diese Zusicherung kann kein anderer Test
    geben: test_gateway patcht das Limit auf 0.3 und prueft nur das Verhalten.
    """
    assert sp._WAKE_READY_TIMEOUT <= 22.0
    assert sp._WAKE_READY_TIMEOUT + sp._HANDSHAKE_READ_TIMEOUT <= 25.0


def test_wake_server_reports_start_failure_at_once(monkeypatch):
    """Harter Startfehler -> sofortige Meldung statt stummem Ablaufen."""
    _reset_wake_state()
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(
        sp, "request_wake", lambda sid: (False, "Serverordner existiert nicht.")
    )
    monkeypatch.setattr(sp.process_service, "is_server_ready", lambda sid: False)

    client = _FakeClient()
    started = time.monotonic()
    ok = sp._wake_server(7001, client)
    elapsed = time.monotonic() - started

    assert ok is False
    assert b"nicht moeglich" in client.data()
    assert b"Serverordner existiert nicht." in client.data()
    assert elapsed < 1.0  # nicht die vollen 15 s abgewartet


def test_wake_server_picks_up_background_failure(monkeypatch):
    """Der Fehler entsteht erst im Hintergrund-Thread - die Schleife holt ihn ab."""
    _reset_wake_state()
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp, "SessionLocal", lambda: _FakeDB(object()))
    monkeypatch.setattr(sp.process_service, "is_running", lambda sid: False)
    monkeypatch.setattr(sp.process_service, "is_server_ready", lambda sid: False)
    monkeypatch.setattr(
        sp.process_service,
        "start_server",
        lambda db, server, initiated_by_user_id=None: (
            False,
            "Serverordner existiert nicht.",
        ),
    )

    client = _FakeClient()
    ok = sp._wake_server(7002, client, deadline=time.monotonic() + 5.0)

    assert ok is False
    assert b"nicht moeglich" in client.data()


def test_booting_backend_says_starting_not_unreachable(monkeypatch):
    """Prozess laeuft, Port noch zu -> "startet noch" statt "nicht erreichbar"."""
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp.process_service, "is_running", lambda sid: True)

    closed_port = sp.find_free_port()  # niemand lauscht dort -> refused
    client = _FakeClient()
    sp._forward_to_backend(client, closed_port, 7003, b"", join=True)

    assert b"startet noch" in client.data()
    assert b"nicht erreichbar" not in client.data()


def test_stopped_backend_says_unreachable(monkeypatch):
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp.process_service, "is_running", lambda sid: False)

    client = _FakeClient()
    sp._forward_to_backend(client, sp.find_free_port(), 7003, b"", join=True)

    assert b"nicht erreichbar" in client.data()


def test_status_connect_failure_stays_silent(monkeypatch):
    """Im Status-Zustand darf KEIN Login-Disconnect raus.

    Paket 0x00 ist dort die Status-Response; der Client wuerde den Text als
    Status-JSON ohne version/players lesen -> kaputter Serverlisten-Eintrag.
    """
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp.process_service, "is_running", lambda sid: True)

    client = _FakeClient()
    sp._forward_to_backend(client, sp.find_free_port(), 7004, b"", join=False)

    assert client.sent == []


def test_request_wake_is_non_blocking_and_deduplicates(monkeypatch):
    """Zweimal wecken -> start_server genau einmal (ein Thread je Server)."""
    _reset_wake_state()
    calls: list[int] = []
    release = threading.Event()

    def fake_start(db, server, initiated_by_user_id=None):
        calls.append(1)
        release.wait(5.0)  # Weckvorgang bewusst offen halten
        return True, "ok"

    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp, "SessionLocal", lambda: _FakeDB(object()))
    monkeypatch.setattr(sp.process_service, "is_running", lambda sid: False)
    monkeypatch.setattr(sp.process_service, "start_server", fake_start)

    try:
        started = time.monotonic()
        ok_first, _msg_first = sp.request_wake(7005)
        assert time.monotonic() - started < 1.0  # kehrt sofort zurueck
        assert ok_first is True

        deadline = time.monotonic() + 3.0
        while not calls and time.monotonic() < deadline:
            time.sleep(0.02)
        assert calls == [1]  # Hintergrund-Thread laeuft

        ok_second, msg_second = sp.request_wake(7005)
        assert ok_second is True
        assert "bereits" in msg_second.lower()
        time.sleep(0.2)
        assert calls == [1]  # kein zweiter Start
    finally:
        release.set()
        deadline = time.monotonic() + 3.0
        while 7005 in sp._WAKE_PENDING and time.monotonic() < deadline:
            time.sleep(0.02)
        _reset_wake_state()


def test_request_wake_skips_running_server(monkeypatch):
    _reset_wake_state()
    monkeypatch.setattr(sp.process_service, "is_running", lambda sid: True)

    def boom(*_a, **_k):
        raise AssertionError("laufender Server darf nicht neu gestartet werden")

    monkeypatch.setattr(sp.process_service, "start_server", boom)

    ok, message = sp.request_wake(7007)
    assert ok is True
    assert "bereits" in message
    assert 7007 not in sp._WAKE_PENDING


def test_wake_server_aborts_when_client_leaves(monkeypatch):
    """Bricht der Spieler ab, wird kein Backend-Socket mehr geoeffnet."""
    _reset_wake_state()
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp, "request_wake", lambda sid: (True, "angestossen"))
    monkeypatch.setattr(sp.process_service, "is_server_ready", lambda sid: False)

    opened: list[tuple] = []

    def boom(*args, **_kwargs):
        opened.append(args)
        raise AssertionError("kein Backend-Socket bei abgebrochenem Client")

    monkeypatch.setattr(sp.socket, "create_connection", boom)

    client = _FakeClient(recv_chunks=[b""])  # b"" = Gegenseite hat geschlossen
    started = time.monotonic()
    ok = sp._wake_server(7006, client, deadline=time.monotonic() + 30.0)
    elapsed = time.monotonic() - started

    assert ok is False
    assert opened == []
    assert elapsed < 2.0  # nicht bis zur Deadline gewartet
    assert client.sent == []  # kein Disconnect an einen weggegangenen Client


def test_wake_server_keeps_bytes_sent_while_waiting(monkeypatch):
    """Waehrend des Wartens gelesene Login-Bytes gehen nicht verloren.

    Der Login-Start kann in einem eigenen TCP-Segment kommen; wird er beim
    Mitlesen verworfen, kommt der Decoder des Servers aus dem Takt.
    """
    _reset_wake_state()
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp, "request_wake", lambda sid: (True, "angestossen"))

    ready_calls: list[int] = []

    def fake_ready(_sid):
        ready_calls.append(1)
        return len(ready_calls) > 2  # erst nach zwei Runden bereit

    monkeypatch.setattr(sp.process_service, "is_server_ready", fake_ready)

    pending = bytearray(b"\x10handshake")
    client = _FakeClient(recv_chunks=[b"\x05login"])
    ok = sp._wake_server(
        7008, client, deadline=time.monotonic() + 10.0, pending=pending
    )

    assert ok is True
    assert bytes(pending) == b"\x10handshake\x05login"
