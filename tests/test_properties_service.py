"""server.properties - EINE Umsetzung von Javas Regeln, und die muss stimmen.

Minecraft liest die Datei mit ``java.util.Properties``. Jede eigene Auslegung geht
frueher oder spaeter auseinander, und zwar STILL: der Manager zeigt einen Wert an, der
Server benutzt einen anderen. Die Tests hier sind deshalb an Javas Verhalten geschrieben,
nicht an dem, was praktisch waere.

Der Anlass: die erste Fassung des MOTD-Editors hatte eine eigene Fluchtlogik. Sie kannte
``\\n`` und ``\\\\``, liess alles andere stehen - und fluchtete es beim naechsten Speichern
erneut. Nach vier Runden stand im Spiel ``Preis \\\\\\\\= 0``. Schlimmer: sie entfernte nur
``\\r`` und ``\\n``, waehrend Python an acht weiteren Zeichen trennt; ein ``U+2028`` aus
einer Textverarbeitung genuegte, um aus einem Wert zwei Eintraege zu machen.
"""

from pathlib import Path

import pytest

from app.services import properties_service as P


# ------------------------------------------------------------------ Entfluchten

@pytest.mark.parametrize("roh,erwartet", [
    (r"Willkommen\! Treff\: 20 Uhr", "Willkommen! Treff: 20 Uhr"),
    (r"Preis \= 0", "Preis = 0"),
    (r"Raute \# eins", "Raute # eins"),
    (r"oben\nunten", "oben\nunten"),
    (r"Tab\there", "Tab\there"),
    (r"§cRot", "§cRot"),
    (r"doppelt \\ Backslash", "doppelt \\ Backslash"),
    # Backslash vor irgendetwas anderem heisst "dieses Zeichen literal".
    (r"\y", "y"),
    ("ohne alles", "ohne alles"),
    ("", ""),
])
def test_entfluchten_wie_java(roh, erwartet):
    assert P.entflucht(roh) == erwartet


def test_entfluchten_haelt_halbes_uXXXX_aus():
    """Eine abgeschnittene Folge darf nicht knallen."""
    assert P.entflucht(r"\u00a") == "u00a"


# ------------------------------------------------------------------ Fluchten

@pytest.mark.parametrize("wert", [
    "Willkommen! Treff: 20 Uhr",
    "Preis = 0",
    "Raute # eins",
    "oben\nunten",
    "mit \\ Backslash",
    "§cRot §lFett",
    " fuehrendes Leerzeichen",
    "Umlaute aeoeue",
])
def test_hin_und_zurueck(wert):
    """Was wir schreiben, muss Java genauso wieder herausgeben."""
    assert P.entflucht(P.fluchte(wert)) == wert


@pytest.mark.parametrize("zeichen", P.TRENNT_PYTHON_NICHT_JAVA)
def test_zeilentrenner_werden_gefluchtet(zeichen):
    """Python trennt an diesen Zeichen, Java nicht. Ungefluchtet macht unsere eigene
    Schreibroutine beim naechsten Mal zwei Eintraege daraus."""
    gefluchtet = P.fluchte(f"a{zeichen}b")

    assert zeichen not in gefluchtet
    assert len(gefluchtet.splitlines()) == 1
    assert P.entflucht(gefluchtet) == f"a{zeichen}b"


def test_fuehrendes_leerzeichen_bleibt_erhalten():
    """Ungefluchtet schluckt Javas Leser es - es gehoert dort zum Trennzeichen."""
    assert P.entflucht(P.fluchte("  zwei")) == "  zwei"


def test_nur_ascii_schreibt_unicode_als_flucht():
    gefluchtet = P.fluchte("§cRot", nur_ascii=True)

    assert gefluchtet.isascii()
    assert P.entflucht(gefluchtet) == "§cRot"


# ------------------------------------------------------------------ Zeilen zerlegen

@pytest.mark.parametrize("zeile,erwartet", [
    ("motd=Hallo", ("motd", "Hallo")),
    ("motd = Hallo", ("motd", "Hallo")),
    ("motd:Hallo", ("motd", "Hallo")),
    ("  motd  =  Hallo", ("motd", "Hallo")),
    ("leer=", ("leer", "")),
    # Ein gefluchtetes Trennzeichen im Schluessel trennt nicht.
    (r"a\=b=wert", (r"a\=b", "wert")),
])
def test_zerlege_zeile(zeile, erwartet):
    assert P.zerlege_zeile(zeile) == erwartet


@pytest.mark.parametrize("zeile", ["", "   ", "# Kommentar", "! auch Kommentar"])
def test_kommentare_und_leerzeilen(zeile):
    assert P.zerlege_zeile(zeile) is None


# ------------------------------------------------------------------ Datei lesen

def _datei(tmp_path, inhalt, kodierung="utf-8"):
    pfad = tmp_path / "server.properties"
    if isinstance(inhalt, bytes):
        pfad.write_bytes(inhalt)
    else:
        pfad.write_text(inhalt, encoding=kodierung)
    return pfad


def test_lies_alle(tmp_path):
    pfad = _datei(tmp_path, "# Kopf\nmotd=§cHallo\\nWelt\nmax-players = 20\n")

    werte = P.lies_alle(pfad)

    assert werte["motd"] == "§cHallo\nWelt"
    assert werte["max-players"] == "20"


def test_doppelter_schluessel_der_letzte_gewinnt(tmp_path):
    """Genau wie bei Java - ein Leser, der den ersten nimmt, zeigt etwas anderes an
    als der Server benutzt."""
    pfad = _datei(tmp_path, "level-name=world\nlevel-name=backup\n")

    assert P.lies_wert(pfad, "level-name") == "backup"


def test_fortgesetzte_zeile(tmp_path):
    """Ein Backslash am Zeilenende haengt die naechste Zeile an."""
    pfad = _datei(tmp_path, "motd=oben \\\nunten\n")

    assert P.lies_wert(pfad, "motd") == "oben unten"


def test_crlf_datei(tmp_path):
    pfad = _datei(tmp_path, "motd=Hallo\r\nmax-players=20\r\n")

    assert P.lies_wert(pfad, "motd") == "Hallo"
    assert P.lies_wert(pfad, "max-players") == "20"


def test_latin1_datei_wird_nicht_zerstoert(tmp_path):
    """Frueher las der Manager stur UTF-8 mit errors='ignore' - und loeschte beim
    naechsten Schreiben jedes Nicht-ASCII-Zeichen der GANZEN Datei."""
    pfad = _datei(tmp_path, b"motd=\xa7cWillkommen\nlevel-name=Gr\xfcne Welt\n")

    assert P.lies_wert(pfad, "motd") == "§cWillkommen"
    assert P.lies_wert(pfad, "level-name") == "Grüne Welt"


def test_fehlende_datei(tmp_path):
    assert P.lies_alle(tmp_path / "gibtsnicht.properties") == {}
    assert P.lies_wert(tmp_path / "gibtsnicht.properties", "motd") is None


# ------------------------------------------------------------------ Datei schreiben

def test_schreiben_laesst_den_rest_in_ruhe(tmp_path):
    pfad = _datei(tmp_path, "difficulty=easy\nmotd=Alt\nmax-players=20\n")

    assert P.schreibe_wert(pfad, "motd", "Neu") is None

    werte = P.lies_alle(pfad)
    assert werte == {"difficulty": "easy", "motd": "Neu", "max-players": "20"}


def test_neuer_schluessel_wird_angehaengt(tmp_path):
    pfad = _datei(tmp_path, "difficulty=easy\n")

    P.schreibe_wert(pfad, "motd", "Neu")

    assert P.lies_wert(pfad, "motd") == "Neu"
    assert P.lies_wert(pfad, "difficulty") == "easy"


def test_schluessel_mit_leerzeichen_wird_ersetzt_nicht_verdoppelt(tmp_path):
    """Das Prefix-Matching von frueher fand 'motd = x' nicht und haengte einen
    ZWEITEN Eintrag an - danach gewann bei Java der neue, im Manager der alte."""
    pfad = _datei(tmp_path, "motd = Alt\nmax-players=20\n")

    P.schreibe_wert(pfad, "motd", "Neu")

    zeilen = [z for z in pfad.read_text(encoding="utf-8").splitlines() if z.startswith("motd")]
    assert len(zeilen) == 1
    assert P.lies_wert(pfad, "motd") == "Neu"


def test_doppelte_eintraege_werden_zusammengefuehrt(tmp_path):
    pfad = _datei(tmp_path, "motd=A\nmax-players=20\nmotd=B\n")

    P.schreibe_wert(pfad, "motd", "C")

    zeilen = [z for z in pfad.read_text(encoding="utf-8").splitlines() if z.startswith("motd")]
    assert len(zeilen) == 1
    assert P.lies_wert(pfad, "motd") == "C"


@pytest.mark.parametrize("zeichen", P.TRENNT_PYTHON_NICHT_JAVA + ("\n", "\r"))
def test_kein_wert_kann_einen_zweiten_schluessel_erzeugen(tmp_path, zeichen):
    """Der schwerste Fund: ueber einen Wert liess sich ein beliebiger anderer
    Schluessel setzen - sichtbar erst beim ZWEITEN Schreiben."""
    pfad = _datei(tmp_path, "level-name=world\nmotd=Alt\n")

    P.schreibe_wert(pfad, "motd", f"Hallo{zeichen}level-name=backup")
    P.schreibe_wert(pfad, "max-players", "30")   # hier brach es vorher auf

    assert P.lies_wert(pfad, "level-name") == "world"
    schluessel = [z[0] for z in (P.zerlege_zeile(r)
                                 for r in pfad.read_text(encoding="utf-8").splitlines()) if z]
    assert schluessel.count("level-name") == 1


def test_latin1_datei_bleibt_latin1(tmp_path):
    """Die Kodierung der Datei zu wechseln waere ein stiller Eingriff in fremde Werte."""
    pfad = _datei(tmp_path, b"motd=Alt\nlevel-name=Gr\xfcne Welt\n")

    P.schreibe_wert(pfad, "motd", "§cNeu")

    assert P.lies_wert(pfad, "level-name") == "Grüne Welt"
    assert P.lies_wert(pfad, "motd") == "§cNeu"
    # Das Nicht-ASCII-Zeichen steht als Flucht drin, die Datei bleibt lesbar.
    assert b"\xfc" in pfad.read_bytes()


def test_schreibfehler_wird_gemeldet_statt_geworfen(tmp_path, monkeypatch):
    """Eine schreibgeschuetzte Datei hat vorher die GANZE Einstellungsaenderung mit
    einem HTTP 500 beendet - samt der Werte, die schon gespeichert waren."""
    pfad = _datei(tmp_path, "motd=Alt\n")

    def _nicht_schreibbar(self, data):
        raise OSError("Zugriff verweigert")

    monkeypatch.setattr(Path, "write_bytes", _nicht_schreibbar)

    warnung = P.schreibe_wert(pfad, "motd", "Neu")

    assert warnung and "nicht schreibbar" in warnung


def test_hin_und_zurueck_ueber_die_datei(tmp_path):
    """Die Runde, auf die es ankommt: schreiben, lesen, wieder schreiben."""
    pfad = _datei(tmp_path, "motd=Alt\n")
    wert = "§cTreff: 20 Uhr! Preis = 0\nZweite \\ Zeile"

    for _ in range(4):
        P.schreibe_wert(pfad, "motd", wert)
        assert P.lies_wert(pfad, "motd") == wert
