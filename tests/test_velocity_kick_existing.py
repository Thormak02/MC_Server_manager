"""Zweite Anmeldung desselben Spielers: ersetzen oder abweisen?

Beide Haltungen sind vertretbar, deshalb ist es eine Einstellung und keine Entscheidung
im Quelltext:

* **Ersetzen** (Vorgabe) ist noetig, damit der Rueckweg aus der Lobby verlaesslich geht -
  ein nativer Transfer fuehrt den Client zum SELBEN Proxy zurueck, und die alte Sitzung
  ist dabei noch einen Moment registriert. Raeumt ausserdem haengende Verbindungen weg.
* **Abweisen** will, wer oft mit zwei Instanzen arbeitet: sonst wirft der zweite Start
  den ersten still hinaus.

Unbedenklich ist beides nur, weil der Proxy die Anmeldung prueft - deshalb pruefen die
Tests ``online-mode = true`` gleich mit.
"""

import pytest


def _cfg(**extra):
    basis = {"bind_port": 25605, "domain": "mc.example.de", "motd": "x", "max_players": 100}
    basis.update(extra)
    return basis


def _zeile(toml, schluessel):
    for roh in toml.splitlines():
        if roh.strip().startswith(schluessel):
            return roh.strip()
    return ""


# ------------------------------------------------------------ Erzeugte Konfiguration

@pytest.mark.parametrize("wert,erwartet", [
    (True, "kick-existing-players = true"),
    (False, "kick-existing-players = false"),
])
def test_einstellung_landet_in_der_toml(wert, erwartet):
    from app.services import proxy_service

    toml = proxy_service.render_velocity_toml(_cfg(kick_existing=wert), [], None)

    assert _zeile(toml, "kick-existing-players") == erwartet


def test_ohne_angabe_wird_ersetzt():
    """Die Vorgabe traegt den Rueckweg aus der Lobby - sie muss auch greifen, wenn
    der Aufrufer nichts mitgibt."""
    from app.services import proxy_service

    toml = proxy_service.render_velocity_toml(_cfg(), [], None)

    assert _zeile(toml, "kick-existing-players") == "kick-existing-players = true"


def test_anmeldepruefung_bleibt_an():
    """Ohne sie waere 'ersetzen' ein Weg, andere hinauszuwerfen."""
    from app.services import proxy_service

    toml = proxy_service.render_velocity_toml(_cfg(kick_existing=True), [], None)

    assert "online-mode = true" in toml


# ------------------------------------------------------------ Einstellung speichern

def test_vorgabe_ist_ersetzen(client):
    from app.db.session import SessionLocal
    from app.services import app_setting_service as A

    with SessionLocal() as db:
        assert A.get_velocity_kick_existing(db) is True


def test_umstellen_und_zurueck(client):
    from app.db.session import SessionLocal
    from app.services import app_setting_service as A

    with SessionLocal() as db:
        A.set_velocity_kick_existing(db, False)
        assert A.get_velocity_kick_existing(db) is False
        A.set_velocity_kick_existing(db, True)
        assert A.get_velocity_kick_existing(db) is True


def test_wert_geht_in_die_laufzeitkonfiguration(client):
    from app.db.session import SessionLocal
    from app.services import app_setting_service as A

    with SessionLocal() as db:
        A.set_network_mode(db, "velocity")
        A.set_velocity_kick_existing(db, False)

    assert A.get_velocity_config_runtime()["kick_existing"] is False


# ------------------------------------------------------------ Ueber die Oberflaeche

def _login(client):
    antwort = client.post("/login", data={"username": "admin", "password": "admin123!"},
                          follow_redirects=False)
    assert antwort.status_code == 303


def test_seite_zeigt_beide_moeglichkeiten(client):
    _login(client)

    seite = client.get("/settings")

    assert seite.status_code == 200
    assert 'name="velocity_kick_existing"' in seite.text
    assert 'value="ersetzen"' in seite.text
    assert 'value="abweisen"' in seite.text


def test_abweisen_laesst_sich_waehlen(client):
    """Mit einem Kaestchen ginge das nicht: nicht angehakt schickt NICHTS mit, und
    'aus' waere von 'Feld war nicht da' nicht zu unterscheiden."""
    from app.db.session import SessionLocal
    from app.services import app_setting_service as A

    _login(client)

    client.post("/settings/universal-lobby",
                data={"velocity_kick_existing": "abweisen"}, follow_redirects=False)

    with SessionLocal() as db:
        assert A.get_velocity_kick_existing(db) is False


def test_ersetzen_laesst_sich_wieder_waehlen(client):
    from app.db.session import SessionLocal
    from app.services import app_setting_service as A

    _login(client)
    with SessionLocal() as db:
        A.set_velocity_kick_existing(db, False)

    client.post("/settings/universal-lobby",
                data={"velocity_kick_existing": "ersetzen"}, follow_redirects=False)

    with SessionLocal() as db:
        assert A.get_velocity_kick_existing(db) is True


def test_speichern_ohne_das_feld_aendert_nichts(client):
    """Andere Formulare schicken das Feld nicht mit - dann muss der Wert stehen bleiben."""
    from app.db.session import SessionLocal
    from app.services import app_setting_service as A

    _login(client)
    with SessionLocal() as db:
        A.set_velocity_kick_existing(db, False)

    client.post("/settings/universal-lobby", data={"hub_name": "Egal"},
                follow_redirects=False)

    with SessionLocal() as db:
        assert A.get_velocity_kick_existing(db) is False
