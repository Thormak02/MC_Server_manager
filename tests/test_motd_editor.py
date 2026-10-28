"""Die MOTD gehoert in server.properties - lesen, zerlegen, zusammensetzen, speichern.

Massgeblich ist nicht, was der Manager sich denkt, sondern was ``java.util.Properties``
aus der Datei liest - denn damit liest Minecraft sie. Deshalb macht das Fluchten der
properties_service, und hier geht es nur noch um die beiden Zeilen:

* ``read_motd`` liefert den fertigen Text, mit einem ECHTEN Umbruch zwischen den Zeilen.
* ``motd_bauen`` setzt die Zeilen mit einem echten Umbruch zusammen; gefluchtet wird
  beim Schreiben.

Die erste Fassung hatte eine eigene Fluchtlogik, und sie war an zwei Stellen falsch:
sie kannte nur ``\\n`` und ``\\\\`` (alle anderen Java-Fluchtzeichen wuchsen mit jeder
Runde zu mehr Backslashes an), und sie entfernte nur ``\\r`` und ``\\n`` - waehrend Python
an acht weiteren Zeichen trennt. Ein ``U+2028`` aus einer Textverarbeitung genuegte, um
aus der MOTD-Zeile einen ZWEITEN Eintrag in server.properties zu machen und damit jede
andere Einstellung zu setzen.

Dazu die Regel beim Speichern: ``motd=None`` heisst "Feld nicht mitgeschickt" und laesst
den vorhandenen Wert in Ruhe.
"""

import re

import pytest

from app.services.properties_service import TRENNT_PYTHON_NICHT_JAVA
from app.services.server_service import (
    MOTD_ZEILEN,
    motd_bauen,
    motd_zerlegen,
    read_motd,
)


def _server(tmp_path, inhalt="level-name=world\n"):
    from app.models.server import Server

    ordner = tmp_path / "srv"
    ordner.mkdir(parents=True, exist_ok=True)
    (ordner / "server.properties").write_text(inhalt, encoding="utf-8")
    return Server(name="Test", slug="test", server_type="paper", mc_version="26.3",
                  base_path=str(ordner), port=25565)


# ------------------------------------------------------------------ Zerlegen

@pytest.mark.parametrize("roh,erwartet", [
    ("Willkommen", ["Willkommen", ""]),
    ("oben\nunten", ["oben", "unten"]),
    ("", ["", ""]),
    ("\nnur unten", ["", "nur unten"]),
    ("mit \\ Backslash", ["mit \\ Backslash", ""]),
    # Mehr als zwei Zeilen zeigt kein Client - der Rest faellt weg.
    ("eins\nzwei\ndrei", ["eins", "zwei"]),
])
def test_zerlegen(roh, erwartet):
    assert motd_zerlegen(roh) == erwartet


def test_zerlegen_liefert_immer_zwei_zeilen():
    for roh in ("", "x", "x\ny", "a\nb\nc"):
        assert len(motd_zerlegen(roh)) == MOTD_ZEILEN


# ------------------------------------------------------------------ Bauen

def test_bauen_setzt_einen_echten_umbruch():
    """Das Fluchten macht der properties_service - hier steht der Text, nicht die Datei."""
    assert motd_bauen("oben", "unten") == "oben\nunten"


def test_leere_zweite_zeile_erzeugt_keinen_umbruch():
    """Sonst stuende in der Serverliste eine leere zweite Zeile."""
    assert motd_bauen("nur oben", "") == "nur oben"
    assert motd_bauen("", "") == ""


@pytest.mark.parametrize("trenner", list(TRENNT_PYTHON_NICHT_JAVA) + ["\n", "\r", "\r\n"])
def test_kein_zeilentrenner_ueberlebt(trenner):
    """DER Fund der Pruefung: Python trennt an acht Zeichen mehr als nur \\n und \\r.

    Blieb eines davon im Wert stehen, machte die Schreibroutine beim NAECHSTEN Speichern
    zwei Eintraege daraus - und wer die MOTD setzen durfte, konnte damit jede andere
    Einstellung setzen.
    """
    gebaut = motd_bauen(f"Hallo{trenner}level-name=backup", "")

    assert gebaut.count("\n") == 0
    for zeichen in TRENNT_PYTHON_NICHT_JAVA + ("\r",):
        assert zeichen not in gebaut
    assert len(gebaut.splitlines()) == 1, gebaut


def test_backslash_bleibt_ein_backslash():
    """Verdoppelt wird erst beim Schreiben - sonst waechst er mit jeder Runde."""
    assert motd_bauen(r"Pfad C:\Server", "") == r"Pfad C:\Server"


@pytest.mark.parametrize("zeile1,zeile2", [
    ("Willkommen", "auf dem Server"),
    ("\u00a7cRot \u00a7lFett", "\u00a7aGruen"),
    (r"mit \ Backslash", "zweite"),
    ("Preis = 0", "Treff: 20 Uhr"),
    ("Achtung! #1", "Server"),
    ("Umlaute aeoeue und Emoji \U0001f9f1", ""),
])
def test_hin_und_zurueck(zeile1, zeile2):
    """Was gespeichert wird, muss im Formular wieder genauso erscheinen."""
    zurueck = motd_zerlegen(motd_bauen(zeile1, zeile2))

    assert zurueck[0] == zeile1.strip()
    assert zurueck[1] == zeile2.strip()


# ------------------------------------------------------------------ Lesen

def test_read_motd_loest_die_fluchtzeichen_auf(tmp_path):
    """So steht es in einer Datei, die java.util.Properties geschrieben hat."""
    server = _server(tmp_path,
                     "level-name=world\nmotd=\u00a7cWillkommen\\! Treff\\: 20\\nZweite\n")

    assert read_motd(server) == "\u00a7cWillkommen! Treff: 20\nZweite"
    assert motd_zerlegen(read_motd(server)) == ["\u00a7cWillkommen! Treff: 20", "Zweite"]


def test_read_motd_ohne_eintrag(tmp_path):
    assert read_motd(_server(tmp_path)) == ""


def test_read_motd_ohne_datei(tmp_path):
    """Ein Server, der noch nie lief, hat keine server.properties - das ist normal."""
    from app.models.server import Server

    server = Server(name="Neu", slug="neu", server_type="paper", mc_version="26.3",
                    base_path=str(tmp_path / "gibtsnicht"), port=25565)

    assert read_motd(server) == ""


def test_read_motd_mit_leerzeichen_um_das_gleichheitszeichen(tmp_path):
    """java.util.Properties erlaubt das, der Freitext-Editor des Managers auch."""
    server = _server(tmp_path, "level-name=world\nmotd = \u00a76Mein Server\n")

    assert read_motd(server) == "\u00a76Mein Server"


# ------------------------------------------------------------------ Speichern

def _login(client):
    antwort = client.post("/login", data={"username": "admin", "password": "admin123!"},
                          follow_redirects=False)
    assert antwort.status_code == 303


def _server_anlegen(client, tmp_path, name="MOTD Server", port=25565, extra=""):
    ordner = tmp_path / name.replace(" ", "-").lower()
    ordner.mkdir(parents=True, exist_ok=True)
    (ordner / "start.bat").write_text("@echo off\n", encoding="utf-8")
    (ordner / "server.properties").write_text(
        f"difficulty=easy\nlevel-name=world\nmax-players=20\nmotd=Alt\n"
        f"server-port={port}\n{extra}", encoding="utf-8")
    antwort = client.post("/servers/import/confirm", data={
        "name": name, "base_path": str(ordner), "server_type": "paper",
        "mc_version": "26.3", "start_mode": "bat",
        "start_bat_path": str(ordner / "start.bat"), "start_command": "",
    }, follow_redirects=False)
    assert antwort.status_code == 303
    return int(re.search(r"/servers/(\d+)", antwort.headers["location"]).group(1)), ordner


def _properties(ordner):
    return (ordner / "server.properties").read_text(encoding="utf-8")


def _wert(ordner, schluessel):
    from app.services import properties_service

    return properties_service.lies_wert(ordner / "server.properties", schluessel)


def test_speichern_schreibt_die_motd(client, tmp_path):
    _login(client)
    server_id, ordner = _server_anlegen(client, tmp_path)

    antwort = client.post(f"/servers/{server_id}/settings", data={
        "motd_zeile1": "\u00a7cWillkommen", "motd_zeile2": "\u00a77viel Spass",
    }, follow_redirects=False)

    assert antwort.status_code == 303
    assert _wert(ordner, "motd") == "\u00a7cWillkommen\n\u00a77viel Spass"


def test_ohne_die_felder_bleibt_die_motd_stehen(client, tmp_path):
    """Jede andere Einstellung speichert dasselbe Formular - sie darf die MOTD
    nicht loeschen, nur weil die Felder nicht mitgeschickt wurden."""
    _login(client)
    server_id, ordner = _server_anlegen(client, tmp_path, name="MOTD Bleibt", port=25566)

    client.post(f"/servers/{server_id}/settings", data={"max_players": "42"},
                follow_redirects=False)

    assert _wert(ordner, "motd") == "Alt"


def test_nur_eine_zeile_mitgeschickt_laesst_die_andere_stehen(client, tmp_path):
    """Sonst leert ein Formular, das nur ein Feld kennt, still die zweite Zeile."""
    _login(client)
    server_id, ordner = _server_anlegen(client, tmp_path, name="MOTD Halb", port=25571)
    client.post(f"/servers/{server_id}/settings",
                data={"motd_zeile1": "Oben", "motd_zeile2": "Unten"},
                follow_redirects=False)

    client.post(f"/servers/{server_id}/settings", data={"motd_zeile2": "Neu unten"},
                follow_redirects=False)

    assert _wert(ordner, "motd") == "Oben\nNeu unten"


def test_leere_felder_loeschen_die_motd(client, tmp_path):
    """Wer beide Zeilen leert, will sie weghaben - das muss gehen."""
    _login(client)
    server_id, ordner = _server_anlegen(client, tmp_path, name="MOTD Leer", port=25567)

    client.post(f"/servers/{server_id}/settings",
                data={"motd_zeile1": "", "motd_zeile2": ""}, follow_redirects=False)

    assert _wert(ordner, "motd") == ""


def test_editor_zeigt_die_aktuelle_motd(client, tmp_path):
    _login(client)
    server_id, ordner = _server_anlegen(client, tmp_path, name="MOTD Anzeige", port=25568)
    (ordner / "server.properties").write_text(
        "level-name=world\nmotd=\u00a7aOben\\n\u00a7bUnten\n", encoding="utf-8")

    seite = client.get(f"/servers/{server_id}")

    assert seite.status_code == 200
    assert 'id="motd_zeile1"' in seite.text
    assert "\u00a7aOben" in seite.text
    assert "\u00a7bUnten" in seite.text


# ---------------------------------------------- Einschleusen fremder Einstellungen

@pytest.mark.parametrize("trenner", TRENNT_PYTHON_NICHT_JAVA)
def test_keine_fremde_einstellung_ueber_die_motd(client, tmp_path, trenner):
    """Der schwerste Fund der Pruefung, als Test.

    Mit einem Zeilentrenner, den Python kennt und Java nicht, liess sich ueber das
    MOTD-Feld eine zweite Zeile in server.properties erzeugen - und damit level-name,
    difficulty oder gamemode setzen. Ausloeser war das ZWEITE Speichern.
    """
    _login(client)
    name = f"MOTD Angriff {ord(trenner)}"
    server_id, ordner = _server_anlegen(client, tmp_path, name=name,
                                        port=25700 + ord(trenner) % 90)

    client.post(f"/servers/{server_id}/settings", data={
        "motd_zeile1": f"Willkommen!{trenner}level-name=backup", "motd_zeile2": "",
    }, follow_redirects=False)
    # Erst beim naechsten Schreiben trennte die alte Fassung die Zeile auf.
    client.post(f"/servers/{server_id}/settings", data={"max_players": "30"},
                follow_redirects=False)

    from app.services.properties_service import zerlege_zeile

    inhalt = _properties(ordner)
    assert _wert(ordner, "level-name") == "world", inhalt
    # Nur ECHTE Schluesselzeilen zaehlen - im gefluchteten MOTD-Wert darf der Text
    # "level-name" ruhig vorkommen, dort ist er harmlos.
    schluessel = [z[0] for z in (zerlege_zeile(r) for r in inhalt.splitlines()) if z]
    assert schluessel.count("level-name") == 1, inhalt
    assert _wert(ordner, "max-players") == "30"


def test_motd_mit_gleichheitszeichen_erzeugt_keinen_schluessel(client, tmp_path):
    _login(client)
    server_id, ordner = _server_anlegen(client, tmp_path, name="MOTD Gleich", port=25572)

    client.post(f"/servers/{server_id}/settings", data={
        "motd_zeile1": "difficulty=hard", "motd_zeile2": "",
    }, follow_redirects=False)
    client.post(f"/servers/{server_id}/settings", data={"max_players": "30"},
                follow_redirects=False)

    assert _wert(ordner, "difficulty") == "easy"
    assert _wert(ordner, "motd") == "difficulty=hard"


def test_backslashes_wachsen_nicht_mit_jeder_runde(client, tmp_path):
    """Die alte Fassung liess unbekannte Fluchtzeichen stehen und fluchtete sie erneut."""
    _login(client)
    server_id, ordner = _server_anlegen(client, tmp_path, name="MOTD Runden", port=25573)

    for _ in range(4):
        client.post(f"/servers/{server_id}/settings", data={
            "motd_zeile1": "Treff: 20 Uhr! Preis = 0", "motd_zeile2": "",
        }, follow_redirects=False)

    assert _wert(ordner, "motd") == "Treff: 20 Uhr! Preis = 0"


def test_fremde_werte_ueberleben_eine_nicht_utf8_datei(client, tmp_path):
    """errors='ignore' loeschte frueher jedes Nicht-ASCII-Zeichen der GANZEN Datei."""
    _login(client)
    server_id, ordner = _server_anlegen(client, tmp_path, name="MOTD Latin", port=25574)
    (ordner / "server.properties").write_bytes(
        b"max-players=20\nmotd=\xa7cWillkommen\nlevel-name=Gr\xfcne Welt\n"
        b"resource-pack-prompt=Bitte best\xe4tigen\n")

    client.post(f"/servers/{server_id}/settings",
                data={"motd_zeile1": "Neu", "motd_zeile2": ""}, follow_redirects=False)

    assert _wert(ordner, "level-name") == "Gr\u00fcne Welt"
    assert _wert(ordner, "resource-pack-prompt") == "Bitte best\u00e4tigen"
    assert _wert(ordner, "motd") == "Neu"


def test_hex_farbe_nur_fuer_paper_angeboten(client, tmp_path):
    """§x wirkt in der MOTD nur bei Paper und Verwandten - ein Knopf, der nichts tut,
    ist schlimmer als keiner."""
    _login(client)
    server_id, _ = _server_anlegen(client, tmp_path, name="MOTD Paper", port=25575)

    seite = client.get(f"/servers/{server_id}")

    assert 'data-hex="ja"' in seite.text
