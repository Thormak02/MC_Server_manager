"""Die Verbindung HALTEN, bis ein schlafender Server joinbar ist.

Warum das noetig ist: der Client haengt ``ReadTimeoutHandler(30)`` als ersten Handler
in seine Netty-Pipeline und gibt nach 30 s ohne eingehendes Paket auf. Gemessene
Weckdauern im Live-Setup: ATM10SKY 55-56 s, die 26.2-Kopie 35-39 s - beide darueber.
Der 1.21.11-Vorgaenger brauchte nur 17 s und blieb damit zufaellig unter der Schwelle;
genau deshalb "ging es vorher" und hoerte beim Wechsel auf 26.2 auf.

Gehalten wird mit dem Login Plugin Request (clientbound 0x04, ab Client 1.13): ein
folgenloses Paket, das der Client immer beantwortet und bei dem er im Login-Zustand
BLEIBT. Jedes eingehende Paket setzt seine Uhr zurueck - damit ist die Haltezeit
unbegrenzt.

Die zwei Zusicherungen, auf die es ankommt:
  1. Ein moderner Client wird ueber die 30 s hinaus gehalten statt weggeschickt.
  2. Seine Antworten auf UNSERE Heartbeats erreichen das Backend NIE - sonst kickt
     das Ziel mit "Unexpected custom data from client", und zwar genau in der
     Sekunde, in der es endlich bereit ist.
"""
from __future__ import annotations

import socket

import pytest

from app.services import mc_protocol
from app.services import sleep_proxy_service as sp

MODERN = 767       # 1.21.1 - weit ueber der 1.13-Schwelle (Protokoll 393)
ANCIENT = 5        # 1.7.10 - dort kennt der Login-Zustand kein Heartbeat-Paket


def _framed(packet_id: int, body: bytes = b"") -> bytes:
    """Ein vollstaendig gerahmtes Paket (Laenge + ID + Rumpf)."""
    return mc_protocol._wrap_packet(mc_protocol.encode_varint(packet_id) + body)


def _login_start(name: str = "Thormak") -> bytes:
    return _framed(0x00, mc_protocol.encode_string(name) + b"\x00" * 16)


class _HoldClient:
    """Attrappen-Socket, der auf jeden Heartbeat wie ein echter Client antwortet.

    Der Vanilla-Client antwortet auf einen unbekannten Kanal mit Login Plugin
    Response (serverbound 0x02, "nicht verstanden"). Genau das wird hier
    nachgebildet, damit der Filter unter realistischen Bedingungen laeuft.
    """

    def __init__(self, extra_chunks=None):
        self.sent: list[bytes] = []
        self.closed = False
        self._replies: list[bytes] = []
        self._extra = list(extra_chunks or [])

    def sendall(self, data):
        data = bytes(data)
        self.sent.append(data)
        _length, body = mc_protocol.read_varint(data, 0)
        packet_id, after_id = mc_protocol.read_varint(data, body)
        if packet_id == sp._LOGIN_CB_PLUGIN_REQUEST:
            message_id, _ = mc_protocol.read_varint(data, after_id)
            self._replies.append(_framed(
                sp._LOGIN_SB_PLUGIN_RESPONSE,
                mc_protocol.encode_varint(message_id) + b"\x00",
            ))

    def settimeout(self, _value):
        pass

    def recv(self, _size=0):
        if self._extra:
            return self._extra.pop(0)
        if self._replies:
            return self._replies.pop(0)
        raise socket.timeout()

    def close(self):
        self.closed = True

    def heartbeats(self) -> list[bytes]:
        out = []
        for data in self.sent:
            _length, body = mc_protocol.read_varint(data, 0)
            packet_id, _ = mc_protocol.read_varint(data, body)
            if packet_id == sp._LOGIN_CB_PLUGIN_REQUEST:
                out.append(data)
        return out


@pytest.fixture()
def fast(monkeypatch):
    """Zeiten stark verkuerzen - geprueft werden Protokoll-Entscheidungen, nicht Uhren."""
    monkeypatch.setattr(sp, "_HEARTBEAT_INTERVAL", 0.0)       # jede Runde ein Heartbeat
    monkeypatch.setattr(sp, "_WAKE_POLL_INTERVAL", 0.0)
    monkeypatch.setattr(sp, "_HOLD_READY_TIMEOUT", 2.0)
    monkeypatch.setattr(sp, "_WAKE_READY_TIMEOUT", 0.3)
    monkeypatch.setattr(sp, "request_wake", lambda sid: (True, "laeuft bereits"))
    monkeypatch.setattr(sp, "_log", lambda *a, **kw: None)
    return monkeypatch


def _never_ready(monkeypatch) -> None:
    """Der Server wird nie fertig. Bewusst eine eigene Funktion statt einer grossen
    Zahl: bei Poll-Intervall 0 dreht die Schleife tausende Runden pro Sekunde, da
    waere jede Obergrenze schnell erreicht."""
    monkeypatch.setattr("app.services.process_service.is_server_ready", lambda _sid: False)


def _ready_after(monkeypatch, runs: int) -> list[int]:
    """is_server_ready liefert erst beim ``runs``-ten Aufruf True."""
    calls: list[int] = []

    def ready(_sid):
        calls.append(1)
        return len(calls) >= runs

    monkeypatch.setattr("app.services.process_service.is_server_ready", ready)
    return calls


def test_modern_client_is_held_until_the_server_is_ready(fast):
    """Der Kern: nicht wegschicken, sondern halten und dann weiterleiten."""
    _ready_after(fast, 6)
    client = _HoldClient()
    pending = bytearray(_login_start())

    assert sp._wake_server(2, client, pending=pending, protocol_version=MODERN) is True
    # Gehalten heisst: es ging wiederholt etwas RAUS an den Client.
    assert len(client.heartbeats()) >= 3
    # Und keine Absage - der Spieler wird weitergeleitet, nicht getrennt.
    assert not any(b"erneut verbinden" in s for s in client.sent)


def test_heartbeat_is_a_valid_login_plugin_request(fast):
    _ready_after(fast, 3)
    client = _HoldClient()
    sp._wake_server(2, client, pending=bytearray(_login_start()), protocol_version=MODERN)

    beat = client.heartbeats()[0]
    length, body = mc_protocol.read_varint(beat, 0)
    assert length == len(beat) - body                      # Rahmen stimmt
    packet_id, offset = mc_protocol.read_varint(beat, body)
    assert packet_id == 0x04                               # Login Plugin Request
    message_id, offset = mc_protocol.read_varint(beat, offset)
    assert message_id >= 1
    channel, _ = mc_protocol._read_string(beat, offset)
    assert channel == "mcsm:wake"                           # eigener, unbekannter Kanal


def test_heartbeat_ids_keep_counting_up(fast):
    """Jeder Heartbeat braucht eine eigene Message-ID - sonst ordnet der Client zu."""
    _ready_after(fast, 5)
    client = _HoldClient()
    sp._wake_server(2, client, pending=bytearray(_login_start()), protocol_version=MODERN)

    ids = []
    for beat in client.heartbeats():
        _length, body = mc_protocol.read_varint(beat, 0)
        _pid, offset = mc_protocol.read_varint(beat, body)
        message_id, _ = mc_protocol.read_varint(beat, offset)
        ids.append(message_id)
    assert ids == sorted(set(ids)) and len(ids) == len(set(ids))


def test_heartbeat_replies_never_reach_the_backend(fast):
    """DIE entscheidende Zusicherung.

    ``pending`` geht nach dem Wecken 1:1 ans Backend. Lagen dort die Antworten auf
    unsere erfundenen Heartbeats, kickte das Ziel den Spieler mit "Unexpected custom
    data from client" - genau dann, wenn es endlich bereit ist.
    """
    _ready_after(fast, 6)
    client = _HoldClient()
    pending = bytearray(_login_start())

    assert sp._wake_server(2, client, pending=pending, protocol_version=MODERN) is True
    assert client.heartbeats(), "ohne Heartbeat prueft der Test nichts"

    # Im Puffer steht GENAU der Login-Start, nichts sonst.
    assert bytes(pending) == _login_start()
    # Und darin kein einziges Login-Plugin-Response-Paket.
    length, body = mc_protocol.read_varint(bytes(pending), 0)
    packet_id, _ = mc_protocol.read_varint(bytes(pending), body)
    assert packet_id == 0x00                       # nur Login Start
    assert length == len(pending) - body           # und nichts dahinter


def test_a_late_login_start_is_preserved(fast):
    """Kommt der Login Start erst waehrend des Haltens, darf er nicht verloren gehen."""
    _ready_after(fast, 6)
    late = _login_start("David")
    client = _HoldClient(extra_chunks=[late])
    pending = bytearray()          # Handshake schon raus, Login Start noch nicht

    assert sp._wake_server(2, client, pending=pending, protocol_version=MODERN) is True
    assert bytes(pending) == late


def test_ancient_client_is_not_held_but_told(fast):
    """Vor 1.13 gibt es kein Heartbeat-Paket - dann lieber ehrlich absagen.

    Entscheidend: es wird KEIN Login Plugin Request geschickt. Ein Client, dessen
    Decoder die ID 0x04 im Login-Zustand nicht kennt, wuerde daran abbrechen.
    """
    _never_ready(fast)
    client = _HoldClient()

    assert sp._wake_server(2, client, pending=bytearray(_login_start()),
                           protocol_version=ANCIENT) is False
    assert client.heartbeats() == []
    assert any(b"erneut verbinden" in s for s in client.sent)


def test_unknown_protocol_version_is_not_held(fast):
    """Ohne Versionsangabe (Gateway-Altaufruf) bleibt es beim alten, kurzen Verhalten."""
    _never_ready(fast)
    client = _HoldClient()
    assert sp._wake_server(2, client, pending=bytearray(), protocol_version=None) is False
    assert client.heartbeats() == []


def test_hold_gives_up_eventually(fast):
    """Auch mit Heartbeat nicht endlos: ein gescheiterter Start darf nicht ewig halten."""
    _never_ready(fast)
    client = _HoldClient()
    assert sp._wake_server(2, client, pending=bytearray(), protocol_version=MODERN) is False
    assert any(b"erneut verbinden" in s for s in client.sent)


# --- _consume_client_packets einzeln ---------------------------------------------

def test_consume_filters_only_our_own_replies():
    pending, scratch = bytearray(), bytearray()
    reply = _framed(sp._LOGIN_SB_PLUGIN_RESPONSE, mc_protocol.encode_varint(1) + b"\x00")
    start = _login_start()
    scratch.extend(reply + start + reply)

    sp._consume_client_packets(pending, scratch)

    assert bytes(pending) == start      # nur der Login Start bleibt
    assert bytes(scratch) == b""


def test_consume_keeps_a_partial_packet_for_later():
    """TCP liefert Bruchstuecke - ein halbes Paket darf nicht fehlgedeutet werden."""
    pending, scratch = bytearray(), bytearray()
    start = _login_start()
    scratch.extend(start[:4])
    sp._consume_client_packets(pending, scratch)
    assert bytes(pending) == b"" and bytes(scratch) == start[:4]

    scratch.extend(start[4:])
    sp._consume_client_packets(pending, scratch)
    assert bytes(pending) == start and bytes(scratch) == b""


def test_consume_preserves_order_of_kept_packets():
    pending, scratch = bytearray(), bytearray()
    first, second = _login_start("A"), _framed(0x03, b"\x01")
    reply = _framed(sp._LOGIN_SB_PLUGIN_RESPONSE, mc_protocol.encode_varint(7) + b"\x00")
    scratch.extend(first + reply + second)

    sp._consume_client_packets(pending, scratch)

    assert bytes(pending) == first + second


def test_consume_passes_unparsable_bytes_through():
    """Im Zweifel weitergeben, nicht verwerfen - sonst zerreisst der Login-Strom."""
    pending, scratch = bytearray(), bytearray()
    scratch.extend(b"\x00")          # Laenge 0 = kein gueltiges Paket
    sp._consume_client_packets(pending, scratch)
    assert bytes(scratch) == b"\x00" or bytes(pending) == b"\x00"
