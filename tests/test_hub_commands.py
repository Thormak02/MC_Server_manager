"""Chat-Befehle im Python-Universal-Hub.

Der Hub ist kein Bukkit-Server und kann das MCSMLobby-Plugin nicht laden - /hub, /servers,
/server <alias> und /lobby muessen daher direkt im Hub implementiert sein (vorher wurde das
Chat-Command-Paket 0x04 kommentarlos verworfen).
"""
from types import SimpleNamespace

from app.services import hub_service, mc_play as pl


class _StubHub:
    """Minimaler Hub-Ersatz: nur die zu testenden Methoden + aufgezeichnete Ausgaben."""

    _tell = hub_service.Hub._tell
    _on_command = hub_service.Hub._on_command
    _transfer_by_alias = hub_service.Hub._transfer_by_alias

    def __init__(self):
        self.sent: list[bytes] = []
        self.menus = 0

    def _send(self, session, data: bytes) -> None:
        self.sent.append(data)

    def _open_menu(self, session) -> None:
        self.menus += 1


def _session():
    return SimpleNamespace(menu_open=False, name="Tester")


_SERVERS = [
    {"key": "atm10-sky", "display": "&aATM10", "host": "atm10-sky.mc.example.de", "port": 25590},
    {"key": "lobby", "display": "&aLobby", "host": "lobby.mc.example.de", "port": 25565},
]


def test_hub_command_opens_menu(monkeypatch):
    monkeypatch.setattr(hub_service, "_menu_servers", lambda: list(_SERVERS))
    for cmd in ("/hub", "/servers", "/server"):
        hub, sess = _StubHub(), _session()
        hub._on_command(sess, cmd)
        assert hub.menus == 1, cmd


def test_hub_server_alias_transfers(monkeypatch):
    monkeypatch.setattr(hub_service, "_menu_servers", lambda: list(_SERVERS))
    hub, sess = _StubHub(), _session()
    hub._on_command(sess, "/server atm10-sky")
    # Transfer auf denselben Weg wie ein Klick im Kompass-Menue
    assert pl.build_transfer("atm10-sky.mc.example.de", 25590) in hub.sent
    assert hub.menus == 0


def test_hub_server_alias_is_case_insensitive(monkeypatch):
    monkeypatch.setattr(hub_service, "_menu_servers", lambda: list(_SERVERS))
    hub, sess = _StubHub(), _session()
    hub._on_command(sess, "/server ATM10-Sky")
    assert pl.build_transfer("atm10-sky.mc.example.de", 25590) in hub.sent


def test_hub_unknown_server_lists_options(monkeypatch):
    monkeypatch.setattr(hub_service, "_menu_servers", lambda: list(_SERVERS))
    hub, sess = _StubHub(), _session()
    hub._on_command(sess, "/server gibtsnicht")
    # Kein Transfer, aber eine Rueckmeldung mit den bekannten Aliassen
    assert not any(pl.build_transfer(s["host"], s["port"]) in hub.sent for s in _SERVERS)
    assert hub.sent, "Es muss eine Rueckmeldung kommen"


def test_hub_lobby_command_answers(monkeypatch):
    monkeypatch.setattr(hub_service, "_menu_servers", lambda: list(_SERVERS))
    hub, sess = _StubHub(), _session()
    hub._on_command(sess, "/lobby")
    assert hub.sent and hub.menus == 0


def test_hub_command_without_slash_and_empty(monkeypatch):
    monkeypatch.setattr(hub_service, "_menu_servers", lambda: list(_SERVERS))
    # Das 0x04-Paket liefert den Befehl OHNE Slash
    hub, sess = _StubHub(), _session()
    hub._on_command(sess, "server atm10-sky")
    assert pl.build_transfer("atm10-sky.mc.example.de", 25590) in hub.sent
    # Leere Eingabe darf nicht crashen
    hub2, sess2 = _StubHub(), _session()
    hub2._on_command(sess2, "   ")
    assert not hub2.sent and hub2.menus == 0
