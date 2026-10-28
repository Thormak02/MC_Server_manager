"""Minecrafts eigenes Pausieren muss aus bleiben - der Manager schlaeft selbst.

Seit 1.21.2 parkt ein Server seinen Haupt-Thread, wenn ``pause-when-empty-seconds``
Sekunden niemand da war (Vorgabe 60). Der Port bleibt dabei OFFEN. Gemessen am
28.10.2026 auf dem 26.3-Paper-Server:

    [11:23:23] Done (20.121s)! For help, type "help"
    [11:24:23] Server empty for 60 seconds, pausing

Der Serverlisten-Ping wurde danach weiter beantwortet - der Manager, der Sleep-Proxy und
das Lobby-Menue hielten den Server also fuer verfuegbar -, ein Beitritt lief aber nicht
mehr durch. Beim Wechsel aus der Lobby wartete Velocity stumm bis zu seinem read-timeout
von 185 Sekunden; der Spieler sah nichts, in keinem Log stand ein Fehler.

Zwei Schlafmechanismen fuer dieselbe Aufgabe, und nur einer davon ist fuer den Manager
sichtbar. Also bleibt der sichtbare.
"""

from pathlib import Path

import pytest


def _server(tmp_path, inhalt="level-name=world\nserver-port=25573\n"):
    from app.models.server import Server

    ordner = tmp_path / "srv"
    ordner.mkdir(parents=True, exist_ok=True)
    (ordner / "server.properties").write_text(inhalt, encoding="utf-8")
    return Server(name="Test", slug="test", server_type="paper", mc_version="26.3",
                  base_path=str(ordner), port=25573)


def _wert(server, schluessel="pause-when-empty-seconds"):
    pfad = Path(server.base_path) / "server.properties"
    for zeile in pfad.read_text(encoding="utf-8").splitlines():
        if zeile.strip().startswith(f"{schluessel}="):
            return zeile.split("=", 1)[1].strip()
    return None


def test_vorgabe_wird_abgeschaltet(tmp_path):
    from app.services.server_service import disable_server_pause

    server = _server(tmp_path, "level-name=world\npause-when-empty-seconds=60\n")

    assert disable_server_pause(server) is True
    assert _wert(server) == "0"


def test_fehlender_schluessel_wird_ergaenzt(tmp_path):
    """Steht er noch nicht drin, schreibt Minecraft ihn beim naechsten Start mit 60."""
    from app.services.server_service import disable_server_pause

    server = _server(tmp_path)

    assert disable_server_pause(server) is True
    assert _wert(server) == "0"


@pytest.mark.parametrize("vorhanden", ["0", "-1", " 0 "])
def test_schon_aus_wird_nicht_angefasst(tmp_path, vorhanden):
    """Sonst meldet der Manager bei JEDEM Start eine Aenderung, die keine ist."""
    from app.services.server_service import disable_server_pause

    server = _server(tmp_path, f"level-name=world\npause-when-empty-seconds={vorhanden}\n")
    vorher = (Path(server.base_path) / "server.properties").read_text(encoding="utf-8")

    assert disable_server_pause(server) is False
    assert (Path(server.base_path) / "server.properties").read_text(encoding="utf-8") == vorher


def test_andere_einstellungen_bleiben_stehen(tmp_path):
    from app.services.server_service import disable_server_pause

    server = _server(tmp_path, "motd=Hallo\npause-when-empty-seconds=60\nwhite-list=true\n")

    disable_server_pause(server)

    inhalt = (Path(server.base_path) / "server.properties").read_text(encoding="utf-8")
    assert "motd=Hallo" in inhalt
    assert "white-list=true" in inhalt
    assert "pause-when-empty-seconds=0" in inhalt
    assert "pause-when-empty-seconds=60" not in inhalt


def test_fehlender_ordner_wirft_nicht(tmp_path):
    """Der Start darf daran nie scheitern."""
    from app.models.server import Server
    from app.services.server_service import disable_server_pause

    server = Server(name="Weg", slug="weg", server_type="paper", mc_version="26.3",
                    base_path=str(tmp_path / "gibtsnicht"), port=25573)

    disable_server_pause(server)   # kein Ausnahmefehler


def test_start_schaltet_die_pause_ab():
    """Der Aufruf muss im Startpfad stehen, solange der Server noch aus ist."""
    import inspect

    from app.services import process_service

    quelle = inspect.getsource(process_service.start_server)

    assert "disable_server_pause" in quelle
    # Vor dem eigentlichen Startbefehl - danach wuerde die laufende Instanz die
    # Datei schon gelesen haben.
    assert quelle.index("disable_server_pause") < quelle.index("_command_for_server")
