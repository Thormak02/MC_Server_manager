"""Tests fuer Hub-Praesentation (MOTD/Name/Max) + Whitelist + Serverlisten-Ping."""

from __future__ import annotations

from app.services import app_setting_service as A
from app.services import hub_service
from app.services import mc_protocol as mp


# ----------------------------- pure helpers --------------------------------- #
def test_parse_name_list():
    assert A._parse_name_list("") == set()
    assert A._parse_name_list("A, b\nC , ,d") == {"a", "b", "c", "d"}
    assert A._parse_name_list("Thormak2002") == {"thormak2002"}


# ----------------------------- settings (DB) -------------------------------- #
def test_hub_config_defaults_and_setters(client):
    import app.db.session as dbs

    with dbs.SessionLocal() as db:
        assert A.get_hub_name(db) == "Universal-Lobby"
        assert A.get_hub_max_players(db) == 100
        assert A.get_hub_whitelist_enabled(db) is False
        A.set_hub_name(db, "MyHub")
        A.set_hub_motd(db, "Willkommen")
        A.set_hub_max_players(db, 50)
        A.set_hub_whitelist_enabled(db, True)
        A.set_hub_whitelist(db, "Alice, Bob\nCharlie")

    cfg = A.get_hub_config_runtime()
    assert cfg["name"] == "MyHub"
    assert cfg["motd"] == "Willkommen"
    assert cfg["max_players"] == 50
    assert cfg["whitelist_enabled"] is True
    assert cfg["whitelist"] == {"alice", "bob", "charlie"}


def test_hub_max_players_validation(client):
    import app.db.session as dbs
    import pytest

    with dbs.SessionLocal() as db:
        with pytest.raises(ValueError):
            A.set_hub_max_players(db, 0)


# ----------------------------- whitelist logic ------------------------------ #
def test_whitelist_off_admits_everyone(monkeypatch):
    monkeypatch.setattr(
        A, "get_hub_config_runtime",
        lambda: {"whitelist_enabled": False, "whitelist": set()},
    )
    assert hub_service.Hub._whitelist_ok(object(), "Anyone") is True


def test_whitelist_on_case_insensitive(monkeypatch):
    monkeypatch.setattr(
        A, "get_hub_config_runtime",
        lambda: {"whitelist_enabled": True, "whitelist": {"thormak2002"}},
    )
    assert hub_service.Hub._whitelist_ok(object(), "Thormak2002") is True
    assert hub_service.Hub._whitelist_ok(object(), "someone_else") is False


# ----------------------------- status ping ---------------------------------- #
class _FakeReader:
    def __init__(self, packets):
        self._packets = list(packets)

    def read_packet(self, timeout=10.0):
        if not self._packets:
            raise ConnectionError("closed")
        return self._packets.pop(0)


class _FakeSock:
    def __init__(self):
        self.sent: list[bytes] = []

    def sendall(self, data):
        self.sent.append(bytes(data))


class _FakeHs:
    protocol_version = 767


class _FakePlayer:
    def __init__(self, alive, has_sock):
        self.alive = alive
        self.sock = object() if has_sock else None


def _fake_hub(players):
    hub = object.__new__(hub_service.Hub)  # ohne __init__ (kein Replay noetig)
    hub.players = players
    return hub


def test_status_ping_response_and_pong(monkeypatch):
    monkeypatch.setattr(
        A, "get_hub_config_runtime",
        lambda: {"name": "TestHub", "motd": "Hallo Welt", "max_players": 42},
    )
    # ein echter Spieler (mit Socket) + der virtuelle Bot (sock None) -> online=1
    hub = _fake_hub({1: _FakePlayer(True, True), 0: _FakePlayer(True, False)})
    sock = _FakeSock()
    reader = _FakeReader([(0x00, b""), (0x01, (123).to_bytes(8, "big", signed=True))])

    hub_service.Hub._handle_status(hub, reader, sock, _FakeHs())

    assert len(sock.sent) == 2
    assert b"Hallo Welt" in sock.sent[0]
    assert b'"online": 1' in sock.sent[0]
    assert b'"name": "TestHub"' in sock.sent[0]
    assert sock.sent[1] == mp.build_pong_packet(123)


def test_status_ping_without_ping_payload(monkeypatch):
    monkeypatch.setattr(
        A, "get_hub_config_runtime",
        lambda: {"name": "H", "motd": "M", "max_players": 5},
    )
    hub = _fake_hub({})
    sock = _FakeSock()
    reader = _FakeReader([(0x00, b"")])  # nur Status Request, kein Ping

    hub_service.Hub._handle_status(hub, reader, sock, _FakeHs())

    assert len(sock.sent) == 1  # nur Status Response, kein Pong
    assert b'"online": 0' in sock.sent[0]
