"""Die MOTD gehoert in server.properties - lesen, zerlegen, zusammensetzen, speichern.

Zwei Eigenheiten der Datei bestimmen alles weitere:

* Eine Property endet am Zeilenende. Ein echter Zeilenumbruch aus einem Eingabefeld
  wuerde die Datei zerreissen - der Umbruch zwischen den beiden MOTD-Zeilen steht
  deshalb als ``\\n`` (Backslash + n) darin.
* Der Backslash ist dort ein Fluchtzeichen. Wer einen echten Backslash in der MOTD
  haben will, braucht zwei.

Dazu die Regel, die beim Speichern wichtig ist: ``motd=None`` heisst "Feld nicht
mitgeschickt" und laesst den vorhandenen Wert in Ruhe. Ohne das wuerde jedes Speichern
einer anderen Einstellung die MOTD loeschen.
"""

from pathlib import Path

import pytest

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
    (r"oben\nunten", ["oben", "unten"]),
    ("", ["", ""]),
    (r"\nnur unten", ["", "nur unten"]),
    (r"mit \\ Backslash", ["mit \\ Backslash", ""]),
    # Mehr als zwei Zeilen zeigt kein Client - der Rest faellt weg.
    (r"eins\nzwei\ndrei", ["eins", "zwei"]),
])
def test_zerlegen(roh, erwartet):
    assert motd_zerlegen(roh) == erwartet


def test_zerlegen_laesst_unbekannte_flucht_stehen():
    """Ein \\t in der Datei ist keine MOTD-Flucht - es soll sichtbar bleiben,
    statt still zu verschwinden."""
    assert motd_zerlegen(r"a\tb")[0] == r"a\tb"


def test_zerlegen_liefert_immer_zwei_zeilen():
    for roh in ("", "x", r"x\ny", r"a\nb\nc"):
        assert len(motd_zerlegen(roh)) == MOTD_ZEILEN


# ------------------------------------------------------------------ Bauen

def test_bauen_setzt_den_umbruch_als_fluchtzeichen():
    assert motd_bauen("oben", "unten") == r"oben\nunten"


def test_leere_zweite_zeile_erzeugt_keinen_umbruch():
    """Sonst stuende in der Serverliste eine leere zweite Zeile."""
    assert motd_bauen("nur oben", "") == "nur oben"
    assert motd_bauen("", "") == ""


def test_echter_umbruch_zerreisst_die_datei_nicht():
    """Ein Zeilenumbruch im Eingabefeld wuerde die Property beenden - und alles
    danach waere eine kaputte Zeile in server.properties."""
    gebaut = motd_bauen("a\nb", "c\r\nd")

    assert "\n" not in gebaut.replace(r"\n", "")
    assert "\r" not in gebaut
    assert motd_zerlegen(gebaut) == ["a b", "c d"]


def test_backslash_wird_verdoppelt():
    gebaut = motd_bauen(r"Pfad C:\Server", "")

    assert gebaut == r"Pfad C:\\Server"
    assert motd_zerlegen(gebaut)[0] == r"Pfad C:\Server"


@pytest.mark.parametrize("zeile1,zeile2", [
    ("Willkommen", "auf dem Server"),
    ("\u00a7cRot \u00a7lFett", "\u00a7aGruen"),
    (r"mit \ Backslash", "zweite"),
    ("Umlaute aeoeue und Emoji \U0001f9f1", ""),
    ("   Leerzeichen aussen   ", ""),
])
def test_hin_und_zurueck(zeile1, zeile2):
    """Was gespeichert wird, muss im Formular wieder genauso erscheinen."""
    zurueck = motd_zerlegen(motd_bauen(zeile1, zeile2))

    assert zurueck[0] == zeile1.strip()
    assert zurueck[1] == zeile2.strip()


# ------------------------------------------------------------------ Lesen

def test_read_motd_aus_der_datei(tmp_path):
    server = _server(tmp_path, "level-name=world\nmotd=\u00a7cHallo\\nWelt\n")

    assert read_motd(server) == r"§cHallo\nWelt"
    assert motd_zerlegen(read_motd(server)) == ["\u00a7cHallo", "Welt"]


def test_read_motd_ohne_eintrag(tmp_path):
    assert read_motd(_server(tmp_path)) == ""


def test_read_motd_ohne_datei(tmp_path):
    """Ein Server, der noch nie lief, hat keine server.properties - das ist normal."""
    from app.models.server import Server

    server = Server(name="Neu", slug="neu", server_type="paper", mc_version="26.3",
                    base_path=str(tmp_path / "gibtsnicht"), port=25565)

    assert read_motd(server) == ""


# ------------------------------------------------------------------ Speichern

def _login(client):
    antwort = client.post("/login", data={"username": "admin", "password": "admin123!"},
                          follow_redirects=False)
    assert antwort.status_code == 303


def _server_anlegen(client, tmp_path, name="MOTD Server", port=25565):
    import re

    ordner = tmp_path / name.replace(" ", "-").lower()
    ordner.mkdir(parents=True, exist_ok=True)
    (ordner / "start.bat").write_text("@echo off\n", encoding="utf-8")
    (ordner / "server.properties").write_text(
        f"level-name=world\nserver-port={port}\nmotd=Alt\n", encoding="utf-8")
    antwort = client.post("/servers/import/confirm", data={
        "name": name, "base_path": str(ordner), "server_type": "paper",
        "mc_version": "26.3", "start_mode": "bat",
        "start_bat_path": str(ordner / "start.bat"), "start_command": "",
    }, follow_redirects=False)
    assert antwort.status_code == 303
    return int(re.search(r"/servers/(\d+)", antwort.headers["location"]).group(1)), ordner


def _properties(ordner):
    return (ordner / "server.properties").read_text(encoding="utf-8")


def test_speichern_schreibt_die_motd(client, tmp_path):
    _login(client)
    server_id, ordner = _server_anlegen(client, tmp_path)

    antwort = client.post(f"/servers/{server_id}/settings", data={
        "motd_zeile1": "\u00a7cWillkommen", "motd_zeile2": "\u00a77viel Spass",
    }, follow_redirects=False)

    assert antwort.status_code == 303
    assert "motd=\u00a7cWillkommen\\n\u00a77viel Spass" in _properties(ordner)


def test_ohne_die_felder_bleibt_die_motd_stehen(client, tmp_path):
    """Jede andere Einstellung speichert dasselbe Formular - sie darf die MOTD
    nicht loeschen, nur weil die Felder nicht mitgeschickt wurden."""
    _login(client)
    server_id, ordner = _server_anlegen(client, tmp_path, name="MOTD Bleibt", port=25566)

    client.post(f"/servers/{server_id}/settings", data={"max_players": "42"},
                follow_redirects=False)

    assert "motd=Alt" in _properties(ordner)


def test_leere_felder_loeschen_die_motd(client, tmp_path):
    """Wer beide Zeilen leert, will sie weghaben - das muss gehen."""
    _login(client)
    server_id, ordner = _server_anlegen(client, tmp_path, name="MOTD Leer", port=25567)

    client.post(f"/servers/{server_id}/settings",
                data={"motd_zeile1": "", "motd_zeile2": ""}, follow_redirects=False)

    assert "motd=\n" in _properties(ordner) or _properties(ordner).endswith("motd=")


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


def test_echter_umbruch_aus_dem_formular_zerreisst_nichts(client, tmp_path):
    """Ein Umbruch im Feld wuerde server.properties kaputtschreiben."""
    _login(client)
    server_id, ordner = _server_anlegen(client, tmp_path, name="MOTD Umbruch", port=25569)

    client.post(f"/servers/{server_id}/settings", data={
        "motd_zeile1": "oben\nmax-players=999", "motd_zeile2": "",
    }, follow_redirects=False)

    inhalt = _properties(ordner)
    zeilen = [z for z in inhalt.splitlines() if z.startswith("max-players=")]
    assert zeilen == [] or zeilen == ["max-players=20"], zeilen
    assert "motd=oben max-players=999" in inhalt


def test_hex_farbe_nur_fuer_paper_angeboten(client, tmp_path):
    """§x wirkt in der MOTD nur bei Paper und Verwandten - ein Knopf, der nichts tut,
    ist schlimmer als keiner."""
    _login(client)
    server_id, _ = _server_anlegen(client, tmp_path, name="MOTD Paper", port=25570)

    seite = client.get(f"/servers/{server_id}")

    assert 'data-hex="ja"' in seite.text
