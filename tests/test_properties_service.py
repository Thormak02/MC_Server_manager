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


def test_der_schluessel_endet_auch_am_zwischenraum():
    """Javas drittes Trennzeichen - gefehlt bis zum 23.11.2026.

    ``java.util.Properties.load`` beendet den Schluessel am ersten ungefluchteten ``=``,
    ``:`` ODER Zwischenraum; danach wird Zwischenraum uebersprungen, dann HOECHSTENS EIN
    ``=`` oder ``:``, dann wieder Zwischenraum. Der Modulkopf sagte "das erste = oder :",
    und der Behebungstext in ``transfer_service`` stuetzt sich darauf, dass dieser Leser
    mit dem uebereinstimmt, was der Server selbst liest.
    """
    from app.services.properties_service import zerlege_zeile

    # Nur Zwischenraum als Trenner.
    assert zerlege_zeile("level-name lobby") == ("level-name", "lobby")
    assert zerlege_zeile("level-name" + chr(9) + "lobby") == ("level-name", "lobby")
    # Der Seitenvorschub gehoert dazu - Javas load0 prueft GENAU Leerzeichen, Tabulator
    # und Seitenvorschub. Er fehlte in der ersten Fassung dieser Behebung.
    assert zerlege_zeile("level-name" + chr(12) + "lobby") == ("level-name", "lobby")
    # Und der ZEILENtabulator gehoert NICHT dazu: Java beendet daran keinen Schluessel.
    # Er steht in TRENNT_PYTHON_NICHT_JAVA, weil PYTHON dort eine Zeile trennt.
    assert zerlege_zeile("a" + chr(11) + "b=c") == ("a" + chr(11) + "b", "c")
    # Zwischenraum UND Trennzeichen, in jeder Mischung - alles einer Wert.
    assert zerlege_zeile("level-name = lobby") == ("level-name", "lobby")
    assert zerlege_zeile("level-name   :   lobby") == ("level-name", "lobby")
    assert zerlege_zeile("level-name=lobby") == ("level-name", "lobby")
    # HOECHSTENS EIN Trennzeichen wird verschluckt: Das zweite gehoert zum Wert.
    assert zerlege_zeile("a == b") == ("a", "= b")
    # Und der Zwischenraum, der den Schluessel beendet hat, macht den Rest zum Wert -
    # auch wenn darin noch ein = steht. Das verwundert und ist genau Javas Regel.
    assert zerlege_zeile("a b = c") == ("a", "b = c")
    # Zwischenraum IM Wert bleibt, und der Wert behaelt sein Inneres unangetastet.
    assert zerlege_zeile("motd=Hallo Welt") == ("motd", "Hallo Welt")
    assert zerlege_zeile("motd = Hallo  Welt") == ("motd", "Hallo  Welt")
    # Ein Schluessel ohne jeden Wert bleibt ein Schluessel mit leerem Wert.
    assert zerlege_zeile("white-list") == ("white-list", "")
    assert zerlege_zeile("white-list ") == ("white-list", "")
    # Kommentar und Leerzeile bleiben, was sie waren.
    assert zerlege_zeile("# level-name lobby") is None
    assert zerlege_zeile("   ") is None


def test_ein_gefluchteter_zwischenraum_beendet_den_schluessel_nicht():
    """Ein geschuetztes Leerzeichen gehoert noch zum Schluessel.

    Ohne diese Zusicherung waere die einfachste Behebung - an JEDEM Leerzeichen trennen -
    gruen, und ein gefluchteter Schluessel zerfiele in zwei.
    """
    from app.services.properties_service import zerlege_zeile

    backslash = chr(92)

    assert zerlege_zeile("a" + backslash + " b=c") == ("a" + backslash + " b", "c")


def test_ein_kommentar_setzt_nichts_fort(tmp_path):
    """Javas load0 liest eine Kommentarzeile bis zum Ende der NATUERLICHEN Zeile.

    Bis zum 23.11.2026 haengte ``_mit_fortsetzung`` jede Zeile mit ungeradem Backslash an
    die naechste - auch einen Kommentar. Ein Windows-Pfad in einem Kommentar genuegte, und
    die Zeile darunter war verschwunden. War das ``level-name=lobby``, fiel
    ``weltname_aus_ordner`` auf "world" zurueck, und ``deploy_lobby_world`` raeumte mit
    ``shutil.rmtree`` den FALSCHEN Ordner aus und meldete dabei gruen.

    Die Erwartungen hier sind gemessen - mit ``java.util.Properties.load`` unter Java 25.
    """
    backslash = chr(92)
    datei = tmp_path / "server.properties"
    datei.write_text("# Welt liegt unter C:" + backslash + "minecraft" + backslash
                     + "lobby" + backslash + chr(10)
                     + "level-name=lobby" + chr(10)
                     + "gamemode=survival" + chr(10), encoding="utf-8")

    alle = P.lies_alle(datei)

    assert alle == {"level-name": "lobby", "gamemode": "survival"}


def test_eine_fortsetzung_mit_raute_ist_kein_kommentar(tmp_path):
    """DIE GEGENRICHTUNG: Gefragt wird nur am ANFANG einer logischen Zeile.

    Ohne sie waere die einfachste Behebung - jede Zeile mit '#' als Kommentar zu nehmen -
    gruen, und eine Raute im umgebrochenen Wert zerriss die Datei.
    """
    backslash = chr(92)
    datei = tmp_path / "server.properties"
    datei.write_text("motd=Raute folgt " + backslash + chr(10)
                     + "#eins zwei" + chr(10)
                     + "level-name=lobby" + chr(10), encoding="utf-8")

    alle = P.lies_alle(datei)

    assert alle == {"motd": "Raute folgt #eins zwei", "level-name": "lobby"}


def test_schreiben_laesst_eine_fremde_fortsetzung_in_ruhe(tmp_path):
    """Eine Fortsetzungszeile ist kein Eintrag, sondern ein Stueck eines fremden Wertes.

    Seit der Schluessel am ersten Zwischenraum endet (23.11.2026), war das ERSTE WORT
    einer Fortsetzungszeile ihr Schluessel - und traf. Die Zeile wurde ersetzt, und der
    haengende Backslash darueber verschluckte, was neu an ihrer Stelle stand: Gemessen mit
    echtem Java kam ``difficulty`` gar nicht in der Datei an, und ``schreibe_wert`` gab
    None zurueck, also "gesetzt".
    """
    backslash = chr(92)
    datei = tmp_path / "server.properties"
    datei.write_text("motd=Hardcore-Server, " + backslash + chr(10)
                     + "difficulty hard und kein Respawn" + chr(10)
                     + "online-mode=false" + chr(10), encoding="utf-8")

    warnung = P.schreibe_wert(datei, "difficulty", "peaceful")

    assert warnung is None, warnung
    alle = P.lies_alle(datei)
    assert alle["difficulty"] == "peaceful", alle
    # Der fremde Wert bleibt, wie Java ihn vorher las - samt der Fortsetzung.
    assert alle["motd"] == "Hardcore-Server, difficulty hard und kein Respawn"
    assert alle["online-mode"] == "false"


def test_schreiben_haengt_nicht_hinter_einen_offenen_backslash(tmp_path):
    """Ein haengender Backslash am Dateiende verschluckt die angehaengte Zeile.

    Java liest sie als Fortsetzung des Wertes darueber - aus ``motd=Willkommen`` plus
    ``gamemode=adventure`` wurde ``motd=Willkommengamemode=adventure`` und KEIN gamemode.
    Die Lobby lief damit im Standard Survival, und normale Spieler bauten sie ab.
    """
    backslash = chr(92)
    datei = tmp_path / "server.properties"
    datei.write_text("level-name=lobby" + chr(10)
                     + "motd=Willkommen" + backslash + chr(10), encoding="utf-8")

    warnung = P.schreibe_wert(datei, "gamemode", "adventure")

    assert warnung is None, warnung
    alle = P.lies_alle(datei)
    assert alle["gamemode"] == "adventure", alle
    assert alle["motd"] == "Willkommen", "der offene Backslash gehoerte zu niemandem"
    assert alle["level-name"] == "lobby"


def test_auch_mit_leerzeile_hinter_dem_offenen_backslash(tmp_path):
    """Derselbe Fall mit einer Leerzeile dahinter - und er fiel als EINZIGER von acht.

    Die Leerzeile schliesst den Block, der Block ist also nicht offen; sobald die
    Leerzeile beim Anhaengen weggenommen wird, steht der Backslash wieder am Dateiende.
    Gefragt werden muss darum NACH dem Wegnehmen.
    """
    backslash = chr(92)
    datei = tmp_path / "server.properties"
    datei.write_text("level-name=lobby" + chr(10)
                     + "motd=Willkommen" + backslash + chr(10)
                     + chr(10) + chr(10), encoding="utf-8")

    warnung = P.schreibe_wert(datei, "gamemode", "adventure")

    assert warnung is None, warnung
    assert P.lies_alle(datei)["gamemode"] == "adventure"


def test_doppelter_schluessel_mit_fortsetzung_faellt_ganz_weg(tmp_path):
    """Beim Ersetzen faellt der GANZE Block weg, auch seine Fortsetzungszeilen.

    Sie gehoerten zum alten Wert. Bliebe eine stehen, haengte sie sich an den neuen.
    """
    backslash = chr(92)
    datei = tmp_path / "server.properties"
    datei.write_text("gamemode=creative" + chr(10)
                     + "gamemode=sur" + backslash + chr(10)
                     + "vival" + chr(10)
                     + "pvp=true" + chr(10), encoding="utf-8")

    warnung = P.schreibe_wert(datei, "gamemode", "adventure")

    assert warnung is None, warnung
    alle = P.lies_alle(datei)
    assert alle == {"gamemode": "adventure", "pvp": "true"}, alle
    assert "vival" not in datei.read_text(encoding="utf-8")


def test_ein_schreibfehler_allein_ist_keine_warnung(tmp_path, monkeypatch):
    """Steht der richtige Wert schon da, ist er GESETZT - auch ohne Schreibzugriff.

    Zweiter Deploy derselben Lobby, server.properties vom Betreiber schreibgeschuetzt und
    inhaltlich schon richtig: Vorher gab es vier Warnungen, und deploy_lobby_world
    behauptete daraufhin, die Lobby laufe in Survival mit PvP und Monstern - waehrend
    genau diese Zeilen unveraendert in der Datei standen. Der Betreiber sucht dann einen
    Fehler, den es nicht gibt.
    """
    import pathlib as _pathlib

    datei = tmp_path / "server.properties"
    datei.write_text("gamemode=adventure" + chr(10), encoding="utf-8")

    def wirft(*_a, **_k):
        raise OSError("schreibgeschuetzt (Probe)")

    monkeypatch.setattr(_pathlib.Path, "write_bytes", wirft)

    assert P.schreibe_wert(datei, "gamemode", "adventure") is None


def test_ein_schreibfehler_MIT_falschem_wert_warnt_doch(tmp_path, monkeypatch):
    """DIE GEGENRICHTUNG: Steht der Wert nicht da, muss der Schreibfehler gemeldet werden -
    samt seinem Grund, damit der Betreiber weiss, dass es am Schreibschutz liegt."""
    import pathlib as _pathlib

    datei = tmp_path / "server.properties"
    datei.write_text("gamemode=survival" + chr(10), encoding="utf-8")

    def wirft(*_a, **_k):
        raise OSError("schreibgeschuetzt (Probe)")

    monkeypatch.setattr(_pathlib.Path, "write_bytes", wirft)

    warnung = P.schreibe_wert(datei, "gamemode", "adventure")

    assert warnung is not None
    assert "schreibgeschuetzt (Probe)" in warnung, warnung
    assert "nicht gesetzt" in warnung


def test_die_nachkontrolle_warnt_wenn_der_wert_nicht_ankommt(tmp_path, monkeypatch):
    """Die Nachkontrolle ist der Riegel gegen die NAECHSTE Gestalt dieses Fehlers.

    Ohne sie meldete jeder Schreibweg, den eine Fortsetzungszeile kaputtmacht, stumm
    Erfolg - vier gruene Aufrufe, und bei Java kam kein einziger Schluessel an, waehrend
    der Betreiber "Adventure + friedlich gesetzt" las.
    """
    datei = tmp_path / "server.properties"
    datei.write_text("level-name=lobby" + chr(10), encoding="utf-8")
    # Ein Leser, der den Wert nicht sieht - gleich welche Gestalt der Fehler haette.
    monkeypatch.setattr(P, "lies_alle", lambda _pfad: {"level-name": "lobby"})

    warnung = P.schreibe_wert(datei, "gamemode", "adventure")

    assert warnung is not None, "ein nicht angekommener Wert muss gemeldet werden"
    assert "gamemode" in warnung
    assert "von Hand" in warnung


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
