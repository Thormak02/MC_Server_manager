"""Der Serverlisten-Ping muss vom gemeinten Server beantwortet werden.

Gemeldet wurde: in der Minecraft-Serverliste zeigen ALLE Eintraege dieselbe MOTD
("Universal-Lobby - alle Versionen und Modpacks"), alle 0/100, alle gruen - auch ein
Server, der nachweislich aus war.

Ursache war eine Verwechslung von Status-Ping und Join. Das Gateway schob beide in den
Velocity-Proxy, sobald der Zielserver ein Velocity-Backend ist. Velocity beantwortet einen
Ping wegen ``ping-passthrough = "DISABLED"`` aber SELBST - mit seiner eigenen MOTD und
``show-max-players``. Ein Ping braucht weder Forwarding noch Anmeldung; er darf den Server
direkt treffen, und dann steht in der Liste, was in dessen server.properties steht.

Zweiter Teil: ein Alias unter der eigenen Domain, zu dem es keinen Server gibt, fiel auf
die Default-Route und zeigte damit die MOTD der Lobby - ein Tippfehler sah aus wie ein
laufender Server.
"""

import socket
import threading
import time

import pytest

from app.services import mc_protocol as mp


def _handshake(hostname: str, next_state: int, port: int, protocol: int = 767) -> bytes:
    payload = (
        mp.encode_varint(0x00)
        + mp.encode_varint(protocol)
        + mp.encode_string(hostname)
        + int(port).to_bytes(2, "big")
        + mp.encode_varint(next_state)
    )
    return mp.encode_varint(len(payload)) + payload


class _Horcher:
    """Ein Socket, der eine Antwort schickt und merkt, was ankam."""

    def __init__(self, antwort: bytes):
        self.antwort = antwort
        self.empfangen: list[bytes] = []
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind(("127.0.0.1", 0))
        self.sock.listen(1)
        self.port = self.sock.getsockname()[1]
        threading.Thread(target=self._bedienen, daemon=True).start()

    def _bedienen(self):
        try:
            conn, _ = self.sock.accept()
            self.empfangen.append(conn.recv(4096))
            conn.sendall(self.antwort)
            conn.close()
        except OSError:
            pass

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


@pytest.fixture
def gateway(monkeypatch):
    """Gateway mit zwei Zielen: dem Server selbst und dem Velocity-Proxy."""
    import app.services.gateway_service as gw
    import app.services.sleep_proxy_service as sp
    from app.services import process_service as ps

    monkeypatch.setattr(gw, "_glog", lambda *a, **k: None)
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(ps, "is_running", lambda sid: True)

    server = _Horcher(b"ANTWORT-VOM-SERVER")
    velocity = _Horcher(b"ANTWORT-VON-VELOCITY")
    monkeypatch.setattr("app.services.proxy_service.is_running", lambda: True)

    routen = gw.GatewayRoutes(
        by_hostname={"26.3-paper": 9, "lobby": 7},
        default_server_id=7,
        internal_ports={9: server.port, 7: server.port},
        aliases=("26.3-paper", "lobby"),
        domain="mc.friedrich-dietrich.de",
        velocity_enabled=True,
        velocity_internal_port=velocity.port,
        velocity_backend_ids=frozenset({7, 9}),
    )
    monkeypatch.setattr(gw, "_current_routes", lambda: routen)

    port = sp.find_free_port()
    assert gw.start_gateway(port)
    try:
        yield {"port": port, "server": server, "velocity": velocity, "routen": routen}
    finally:
        gw.stop_gateway()
        server.close()
        velocity.close()


def _frage(port: int, hostname: str, next_state: int) -> bytes:
    conn = socket.create_connection(("127.0.0.1", port), timeout=5)
    try:
        conn.sendall(_handshake(hostname, next_state, port))
        time.sleep(0.5)
        try:
            return conn.recv(4096)
        except OSError:
            return b""
    finally:
        conn.close()


# ------------------------------------------------------- Der gemeldete Fehler

def test_ping_auf_einen_alias_beantwortet_der_server_selbst(gateway):
    """Sonst steht in der Liste die MOTD der Lobby statt der des Servers."""
    antwort = _frage(gateway["port"], "26.3-paper.mc.friedrich-dietrich.de",
                     mp.NEXT_STATE_STATUS)

    assert antwort == b"ANTWORT-VOM-SERVER"
    assert gateway["velocity"].empfangen == [], "Der Ping lief durch Velocity"


def test_join_auf_dasselbe_ziel_laeuft_weiterhin_ueber_velocity(gateway):
    """Ein JOIN muss ueber den Proxy - sonst trifft er das Backend ohne Forwarding
    und wird gekickt. Nur der Ping darf direkt gehen."""
    antwort = _frage(gateway["port"], "26.3-paper.mc.friedrich-dietrich.de",
                     mp.NEXT_STATE_LOGIN)

    assert antwort == b"ANTWORT-VON-VELOCITY"


def test_unbekannter_alias_zeigt_nicht_die_lobby(gateway):
    """Ein Tippfehler im Hostnamen sah bisher aus wie ein laufender Server."""
    antwort = _frage(gateway["port"], "1.21.11-spigot.mc.friedrich-dietrich.de",
                     mp.NEXT_STATE_STATUS)

    assert gateway["velocity"].empfangen == []
    assert gateway["server"].empfangen == []
    assert b"Netzwerk" in antwort, antwort[:120]


def test_blanke_domain_zeigt_weiterhin_die_netzwerk_motd(gateway):
    """Am Apex GEHOERT die Netzwerk-MOTD hin - das soll sich nicht aendern."""
    antwort = _frage(gateway["port"], "mc.friedrich-dietrich.de", mp.NEXT_STATE_STATUS)

    assert antwort == b"ANTWORT-VON-VELOCITY"


# ------------------------------------------------------- Routenentscheidung

@pytest.mark.parametrize("hostname,unbekannt", [
    ("26.3-paper.mc.friedrich-dietrich.de", False),   # bekannter Alias
    ("lobby.mc.friedrich-dietrich.de", False),
    ("mc.friedrich-dietrich.de", False),              # Apex
    ("gibtsnicht.mc.friedrich-dietrich.de", True),    # Tippfehler
    ("1.21.11-spigot.mc.friedrich-dietrich.de", True),  # Server ohne Gateway-Freigabe
    ("modlobby.mc.friedrich-dietrich.de", False),     # gehoert dem Hub
    ("vanlobby.mc.friedrich-dietrich.de", False),
    ("modlobby-9.mc.friedrich-dietrich.de", False),   # Per-Pack-Tag des Dispatchers
    ("fremde-domain.de", False),                      # nicht unsere Domain
    ("", False),
])
def test_unbekannter_alias_wird_erkannt(hostname, unbekannt):
    from app.services.gateway_service import GatewayRoutes, decide_route

    routen = GatewayRoutes(by_hostname={"26.3-paper": 9, "lobby": 7},
                           default_server_id=7, domain="mc.friedrich-dietrich.de")

    assert decide_route(hostname, 767, routen).alias_unbekannt is unbekannt


def test_ohne_domain_bleibt_alles_wie_vorher():
    """Ohne konfigurierte Domain gibt es kein 'unter unserer Domain' - also nie unbekannt."""
    from app.services.gateway_service import GatewayRoutes, decide_route

    routen = GatewayRoutes(by_hostname={"atm10": 2}, default_server_id=2, domain="")

    assert decide_route("irgendwas.fremd.de", 767, routen).alias_unbekannt is False
    assert decide_route("atm10.fremd.de", 767, routen).server_id == 2


def test_toter_zielport_antwortet_gar_nicht_statt_mit_muell(gateway, monkeypatch):
    """Im Status-Zustand ist Paket 0x00 die Status-ANTWORT.

    Ein Login-Disconnect dorthin liest der Client als Status-JSON ohne version/players -
    der Eintrag in der Serverliste ist danach kaputt statt ehrlich offline. Deshalb darf
    bei einem toten Port nichts gesendet werden.
    """
    import app.services.gateway_service as gw

    routen = gateway["routen"]
    tot = routen.internal_ports | {9: 1}   # Port 1: hier horcht niemand
    monkeypatch.setattr(gw, "_current_routes", lambda: gw.GatewayRoutes(
        by_hostname=routen.by_hostname, default_server_id=routen.default_server_id,
        internal_ports=tot, aliases=routen.aliases, domain=routen.domain,
        velocity_enabled=True, velocity_internal_port=routen.velocity_internal_port,
        velocity_backend_ids=routen.velocity_backend_ids))

    antwort = _frage(gateway["port"], "26.3-paper.mc.friedrich-dietrich.de",
                     mp.NEXT_STATE_STATUS)

    assert antwort == b"", f"Es kam etwas zurueck: {antwort[:60]!r}"
