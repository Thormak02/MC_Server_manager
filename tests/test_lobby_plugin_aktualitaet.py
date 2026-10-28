"""Nach einem Update muss die Lobby auch WIRKLICH die neue Plugin-Fassung laden.

Gemeldet wurde: aus der Lobby liess sich der 26.3-Paper-Server nicht betreten. Im Chat
stand "Verbinde zu ...", danach passierte nichts - keine Meldung, kein Wechsel. Das
Velocity-Log zeigte in neun Minuten keinen einzigen Eintrag ueber einen Serverwechsel, die
Plugin-Nachricht ist dort also nie angekommen.

Ursache war ein Wettlauf beim Managerstart:
  1. ``maybe_autobuild_plugin`` startet den Build im HINTERGRUND.
  2. Wenige Zeilen spaeter starten die Autostart-Server und nehmen dabei das Jar mit, das
     gerade auf der Platte liegt - also das alte.
  3. Ist der Build fertig, ist die Lobby laengst gestartet und haelt die Datei gesperrt;
     der Kopierversuch scheitert.
Die Lobby lief damit bis zum naechsten Neustart von Hand mit der vorigen Fassung - bei
frisch geschriebener config.yml. Das alte Plugin kannte deren Feld ``velocity_name`` nicht
und schickte ein Transfer-Paket statt eines Velocity-"Connect"; Velocity verwirft so ein
Paket vom Backend wortlos.
"""

from pathlib import Path

import pytest


def _lobby_anlegen(tmp_path, *, jar_inhalt=b"alt"):
    ordner = tmp_path / "lob"
    (ordner / "plugins" / "MCSMLobby").mkdir(parents=True)
    (ordner / "plugins" / "MCSMLobby.jar").write_bytes(jar_inhalt)
    return ordner


def _server(db, ordner, *, slug="lob", port=25569):
    from app.models.server import Server

    # servers.name ist eindeutig - der Slug macht den Namen pro Test unterscheidbar.
    server = Server(name=f"Lobby {slug}", slug=slug, server_type="paper", mc_version="26.3",
                    base_path=str(ordner), gateway_enabled=True, gateway_hostname="lobby",
                    gateway_is_default=True, port=port)
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


# ------------------------------------------------- Warten auf den Build

def test_warte_auf_build_kehrt_sofort_zurueck_wenn_nichts_laeuft():
    from app.services import plugin_build_service as pb

    assert pb.warte_auf_build(5.0) is True


def test_warte_auf_build_gibt_auf_statt_ewig_zu_haengen(monkeypatch):
    """Ein haengender Build darf den Managerstart nicht blockieren."""
    from app.services import plugin_build_service as pb

    monkeypatch.setattr(pb, "is_building", lambda: True)

    assert pb.warte_auf_build(0.2) is False


def test_server_starten_erst_nach_dem_build():
    """Die Reihenfolge im Start ist der ganze Punkt - sonst greift die Reparatur nicht."""
    import inspect

    from app import main

    quelle = inspect.getsource(main.lifespan)
    warten = quelle.index("warte_auf_build")
    starten = quelle.index("start_servers_marked_for_manager_startup()")

    assert warten < starten, "Die Server starten wieder vor dem Plugin-Build"


# ------------------------------------------------- Erkennen einer alten Fassung

def test_abweichendes_jar_wird_erkannt(tmp_path):
    from app.services.lobby_service import _jar_weicht_ab

    neu = tmp_path / "neu.jar"
    neu.write_bytes(b"A" * 100)
    gleich = tmp_path / "gleich.jar"
    gleich.write_bytes(b"A" * 100)
    anders_gross = tmp_path / "gross.jar"
    anders_gross.write_bytes(b"A" * 101)
    anders_inhalt = tmp_path / "inhalt.jar"
    anders_inhalt.write_bytes(b"B" * 100)

    assert _jar_weicht_ab(neu, gleich) is False
    assert _jar_weicht_ab(neu, anders_gross) is True
    assert _jar_weicht_ab(neu, anders_inhalt) is True
    assert _jar_weicht_ab(neu, tmp_path / "fehlt.jar") is True


def test_gesperrtes_jar_wird_deutlich_gemeldet(client, tmp_path, monkeypatch):
    """Vorher stand da nur "Jar gesperrt (laeuft) - Update beim Neustart"."""
    import shutil

    import app.services.app_setting_service as svc
    import app.services.lobby_service as lobby_service
    from app.db.session import SessionLocal

    ordner = _lobby_anlegen(tmp_path)

    def _gesperrt(*_a, **_k):
        raise PermissionError("Die Datei wird von einem anderen Prozess verwendet")

    monkeypatch.setattr(shutil, "copy2", _gesperrt)

    with SessionLocal() as db:
        server = _server(db, ordner)
        svc.set_network_domain(db, "mc.friedrich-dietrich.de")
        svc.set_network_port(db, 25565)

        ok, meldung = lobby_service._write_plugin_for_server(
            db, server, is_lobby=True, lobby_target=None)

    assert ok
    assert "VERALTET" in meldung, meldung


def test_gesperrtes_aber_identisches_jar_meldet_nichts(client, tmp_path, monkeypatch):
    """Ueberzumelden waere auch falsch: liegt dieselbe Fassung schon da, ist nichts kaputt."""
    import shutil

    import app.services.app_setting_service as svc
    import app.services.lobby_service as lobby_service
    from app.db.session import SessionLocal

    aktuelles_jar = lobby_service._plugin_jar()
    if not aktuelles_jar.exists():
        pytest.skip("Kein Plugin-Jar vorhanden")
    ordner = _lobby_anlegen(tmp_path, jar_inhalt=aktuelles_jar.read_bytes())

    def _gesperrt(*_a, **_k):
        raise PermissionError("gesperrt")

    monkeypatch.setattr(shutil, "copy2", _gesperrt)

    with SessionLocal() as db:
        server = _server(db, ordner, slug="lob2", port=25579)
        svc.set_network_domain(db, "mc.friedrich-dietrich.de")
        svc.set_network_port(db, 25565)

        _ok, meldung = lobby_service._write_plugin_for_server(
            db, server, is_lobby=True, lobby_target=None)

    assert "VERALTET" not in meldung, meldung


def test_veraltetes_plugin_steht_im_protokoll(client, tmp_path, monkeypatch):
    import shutil

    from sqlalchemy import select

    import app.services.app_setting_service as svc
    import app.services.lobby_service as lobby_service
    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog

    ordner = _lobby_anlegen(tmp_path)
    monkeypatch.setattr(shutil, "copy2", lambda *a, **k: (_ for _ in ()).throw(
        PermissionError("gesperrt")))

    with SessionLocal() as db:
        server = _server(db, ordner, slug="lob3", port=25589)
        svc.set_network_domain(db, "mc.friedrich-dietrich.de")
        svc.set_network_port(db, 25565)
        lobby_service._write_plugin_for_server(db, server, is_lobby=True, lobby_target=None)

        eintraege = list(db.scalars(select(AuditLog).where(
            AuditLog.action == "lobby.plugin_veraltet")))

    assert len(eintraege) == 1
    assert "neu startet" in (eintraege[0].details or "")


def test_plugin_ist_veraltet_erkennt_beide_faelle(client, tmp_path):
    import app.services.lobby_service as lobby_service
    from app.db.session import SessionLocal

    aktuelles_jar = lobby_service._plugin_jar()
    if not aktuelles_jar.exists():
        pytest.skip("Kein Plugin-Jar vorhanden")

    alt = _lobby_anlegen(tmp_path / "a", jar_inhalt=b"alte fassung")
    aktuell = _lobby_anlegen(tmp_path / "b", jar_inhalt=aktuelles_jar.read_bytes())
    ohne = tmp_path / "c"
    ohne.mkdir(parents=True)

    with SessionLocal() as db:
        s_alt = _server(db, alt, slug="va", port=25601)
        s_neu = _server(db, aktuell, slug="vb", port=25602)
        s_ohne = _server(db, ohne, slug="vc", port=25603)

        assert lobby_service.plugin_ist_veraltet(s_alt) is True
        assert lobby_service.plugin_ist_veraltet(s_neu) is False
        # Gar nicht installiert ist kein "veraltet" - sonst meldet jeder normale Server.
        assert lobby_service.plugin_ist_veraltet(s_ohne) is False


def test_start_liefert_das_frische_jar_aus(client, tmp_path):
    """Der Weg, der die Lobby heute repariert: Neustart -> Datei frei -> neues Jar."""
    import app.services.app_setting_service as svc
    import app.services.lobby_service as lobby_service
    from app.db.session import SessionLocal

    aktuelles_jar = lobby_service._plugin_jar()
    if not aktuelles_jar.exists():
        pytest.skip("Kein Plugin-Jar vorhanden")
    ordner = _lobby_anlegen(tmp_path, jar_inhalt=b"alte fassung")

    with SessionLocal() as db:
        server = _server(db, ordner, slug="start", port=25604)
        svc.set_network_domain(db, "mc.friedrich-dietrich.de")
        svc.set_network_port(db, 25565)

        lobby_service.refresh_plugin_for_server(db, server)

    assert (Path(ordner) / "plugins" / "MCSMLobby.jar").read_bytes() == \
        aktuelles_jar.read_bytes()


# ------------------------------------------------- Eine Regel fuer "Velocity-Backend"

@pytest.mark.parametrize("typ,port,erwartet", [
    ("paper", 25569, True),
    ("purpur", 25569, True),
    ("folia", 25569, True),
    ("spigot", 25570, False),     # kein Backend -> nativer Transfer
    ("neoforge", 25590, False),
    ("paper", None, False),       # ohne Port schreibt proxy_service kein Backend
    ("paper", 0, False),
])
def test_velocity_backend_regel(typ, port, erwartet):
    """Dieselbe Frage wie in proxy_service.velocity_backends - und dieselbe Antwort."""
    import app.services.lobby_service as lobby_service

    class _Server:
        server_type = typ
        port = None

    s = _Server()
    s.port = port

    assert lobby_service._ist_velocity_backend(s) is erwartet


def test_regel_deckt_sich_mit_dem_proxy():
    """Laufen die beiden Listen auseinander, zeigt die Lobby auf Namen, die es nicht gibt."""
    import app.services.lobby_service as lobby_service
    import app.services.proxy_service as proxy_service

    assert lobby_service._BACKEND_TYPES_VELOCITY == proxy_service._BACKEND_TYPES


def test_spigot_lobby_bekommt_keinen_velocity_namen(client, tmp_path):
    """Der /lobby-Rueckweg setzte den Namen allein anhand des Netzwerkmodus."""
    import app.services.app_setting_service as svc
    import app.services.lobby_service as lobby_service
    from app.db.session import SessionLocal
    from app.models.server import Server

    ordner = tmp_path / "spiglobby"
    ordner.mkdir()
    with SessionLocal() as db:
        db.add(Server(name="Spigot-Lobby", slug="spiglobby", server_type="spigot",
                      mc_version="1.21.11", base_path=str(ordner), gateway_enabled=True,
                      gateway_hostname="lobby", gateway_is_default=True, port=25571))
        db.commit()
        svc.set_network_domain(db, "mc.friedrich-dietrich.de")
        svc.set_network_port(db, 25565)
        svc.set_network_mode(db, "velocity")

        ziel = lobby_service._lobby_transfer_target(db)

    assert ziel is not None
    assert ziel["velocity_name"] == "", ziel
