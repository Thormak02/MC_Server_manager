"""server.properties lesen und schreiben - so, wie Java es tut.

Minecraft liest diese Datei mit ``java.util.Properties``. Jede eigene Auslegung geht
frueher oder spaeter auseinander, und zwar still: der Manager zeigt einen Wert an, der
Server benutzt einen anderen. Deshalb steht hier EINE Umsetzung von Javas Regeln, und
alles im Manager fragt sie.

Javas Regeln, auf die es ankommt:

* **Trennzeichen** ist das erste ``=`` oder ``:``; Leerzeichen davor und dahinter
  gehoeren nicht dazu (``motd = x`` ist derselbe Schluessel wie ``motd=x``).
* **Fluchtzeichen**: ``\\t \\n \\r \\f`` sind die bekannten Steuerzeichen, ``\\uXXXX`` ein
  beliebiges Zeichen, und ein Backslash vor irgendetwas anderem heisst schlicht
  "dieses Zeichen literal" - ``\\:`` ist ein Doppelpunkt, ``\\\\`` ein Backslash.
  ``Properties.store`` schuetzt beim Schreiben immer ``= : # !`` und fuehrende
  Leerzeichen; wer das beim Lesen nicht aufloest, sammelt mit jeder Runde Backslashes.
* **Fortsetzung**: ein Backslash am Zeilenende haengt die naechste Zeile an.

Dazu die Kodierung: Paper schreibt UTF-8, aeltere Server und der Windows-Editor schreiben
Latin-1/CP1252. Wer stur UTF-8 mit ``errors="ignore"`` liest, verliert beim naechsten
Schreiben jedes Nicht-ASCII-Zeichen der GANZEN Datei - auch in Werten, die er gar nicht
anfasst.

DREI TRENNZEICHEN, nicht zwei: ``=``, ``:`` UND Zwischenraum. Hier stand bis zum
23.11.2026 "das erste = oder :", und das Leerzeichen fehlte - siehe
``SCHLUESSEL_ENDET_AN``.
"""

from __future__ import annotations

import re
from pathlib import Path

# Zeichen, an denen Pythons ``str.splitlines()`` trennt, Javas Properties-Leser aber
# NICHT. Stehen sie in einem Wert, zerreissen sie die Datei beim naechsten Schreiben -
# aus einem Wert werden zwei Eintraege. Genau damit liess sich ueber das MOTD-Feld ein
# beliebiger anderer Schluessel setzen.
TRENNT_PYTHON_NICHT_JAVA = (
    "\x0b",      # Zeilentabulator
    "\x0c",      # Seitenvorschub
    "\x1c", "\x1d", "\x1e",   # Datei-, Gruppen-, Satztrenner
    "\x85",      # NEL
    " ",    # LINE SEPARATOR - faengt man sich beim Einfuegen aus Word/PDF ein
    " ",    # PARAGRAPH SEPARATOR
)

# ``Properties.store`` schuetzt in einem Wert auch ``= : # !``. Beim LESEN muessen wir
# das aufloesen - beim SCHREIBEN aber nicht nachmachen: in einem Wert sind diese Zeichen
# gewoehnlich, weil das erste Trennzeichen den Schluessel schon beendet hat, und ``#``
# leitet nur am ZEILENANFANG einen Kommentar ein. Sie zu fluchten macht aus
# ``resource-pack=http://cdn/pack.zip`` ein ``http\://cdn/pack.zip``: fuer Java dasselbe,
# fuer jeden Menschen und jedes andere Werkzeug aber kaputt.

_STEUERZEICHEN = {"t": "\t", "n": "\n", "r": "\r", "f": "\f"}


def entflucht(roh: str) -> str:
    """Einen Wert aus der Datei in den Text verwandeln, den Java daraus macht."""
    raus: list[str] = []
    i = 0
    text = roh or ""
    while i < len(text):
        zeichen = text[i]
        if zeichen != "\\":
            raus.append(zeichen)
            i += 1
            continue
        if i + 1 >= len(text):
            break   # Backslash am Ende: Java verschluckt ihn (Fortsetzungszeichen)
        folgt = text[i + 1]
        if folgt == "u" and re.match(r"[0-9a-fA-F]{4}", text[i + 2:i + 6] or ""):
            raus.append(chr(int(text[i + 2:i + 6], 16)))
            i += 6
            continue
        raus.append(_STEUERZEICHEN.get(folgt, folgt))
        i += 2
    return "".join(raus)


def fluchte(wert: str, *, nur_ascii: bool = False) -> str:
    """Einen Text so schreiben, dass Java genau ihn wieder herausliest.

    ``nur_ascii`` schreibt alles ausserhalb von ASCII als ``\\uXXXX``. Das braucht es
    nur, wenn die Datei in einer Kodierung vorliegt, die das Zeichen nicht darstellen
    kann - sonst bliebe die MOTD in der Datei unleserlich.
    """
    raus: list[str] = []
    for index, zeichen in enumerate(wert or ""):
        if zeichen == "\\":
            raus.append("\\\\")
        elif zeichen == "\n":
            raus.append("\\n")
        elif zeichen == "\r":
            raus.append("\\r")
        elif zeichen == "\t":
            raus.append("\\t")
        elif zeichen == "\f":
            raus.append("\\f")
        elif zeichen == " " and index == 0:
            raus.append("\\ ")   # fuehrende Leerzeichen gehoeren sonst zum Trenner
        elif zeichen in TRENNT_PYTHON_NICHT_JAVA:
            # Java wuerde sie durchlassen, aber unsere eigene Schreibroutine (und jeder
            # Editor, der splitlines benutzt) macht daraus eine neue Zeile.
            raus.append(f"\\u{ord(zeichen):04x}")
        elif nur_ascii and ord(zeichen) > 126:
            raus.append(f"\\u{ord(zeichen):04x}")
        else:
            raus.append(zeichen)
    return "".join(raus)


# Zeichen, an denen Javas ``Properties.load`` einen Schluessel beendet. Das Leerzeichen
# und der Tabulator gehoeren dazu, und sie haben am 23.11.2026 gefehlt: Eine
# handgeschriebene Zeile ``level-name lobby`` liest Java als ``lobby``, dieser Leser las
# den ganzen Rest als Schluessel und gab darum nichts zurueck - der Rueckfall war "world".
# Das ist nicht nur ein falscher Wert: ``deploy_lobby_world`` raeumt ``base / <weltname>``
# per ``shutil.rmtree`` aus, haette also den Ordner ``world`` geloescht, waehrend der
# Server in ``lobby`` spielt.
# Javas ``load0`` beendet den Schluessel am ersten ungefluchteten "=", ":" oder
# Zwischenraum, und Zwischenraum heisst dort GENAU diese drei Zeichen: Leerzeichen,
# Tabulator, Seitenvorschub. Nicht mehr (ein Zeilentabulator beendet keinen Schluessel)
# und nicht weniger - der Seitenvorschub fehlte in der ersten Fassung vom 23.11.2026.
ZWISCHENRAUM_WIE_JAVA = " " + chr(9) + chr(12)
SCHLUESSEL_ENDET_AN = "=:" + ZWISCHENRAUM_WIE_JAVA


def zerlege_zeile(zeile: str) -> tuple[str, str] | None:
    """``(Schluessel, roher Wert)`` einer Zeile - oder None fuer Kommentar/leer.

    Javas Regel in drei Schritten: Der Schluessel endet am ersten ungefluchteten Zeichen
    aus ``SCHLUESSEL_ENDET_AN``. Danach wird Zwischenraum uebersprungen, dann HOECHSTENS
    EIN ``=`` oder ``:``, dann wieder Zwischenraum. Der Rest ist der Wert.

    Daraus folgt, was auf den ersten Blick verwundert: ``a b = c`` hat den Schluessel
    ``a`` und den Wert ``b = c`` - das zweite Trennzeichen ist schon Teil des Wertes,
    weil der Zwischenraum den Schluessel bereits beendet hat. Genau so liest es Java, und
    uebereinstimmen ist der ganze Zweck dieses Moduls.
    """
    text = (zeile or "").lstrip()
    if not text or text[0] in "#!":
        return None
    i = 0
    while i < len(text):
        if text[i] == "\\":
            i += 2
            continue
        if text[i] in SCHLUESSEL_ENDET_AN:
            break
        i += 1
    schluessel = text[:i]
    if i >= len(text):
        return schluessel, ""
    rest = text[i:]
    j = 0
    while j < len(rest) and rest[j] in ZWISCHENRAUM_WIE_JAVA:
        j += 1
    if j < len(rest) and rest[j] in "=:":
        j += 1
        while j < len(rest) and rest[j] in ZWISCHENRAUM_WIE_JAVA:
            j += 1
    return schluessel, rest[j:]


def dekodiere(rohdaten: bytes) -> tuple[str, str]:
    """Bytes plus erkannte Kodierung. Latin-1 ist der Rueckfall, nicht Datenverlust.

    Oeffentlich, weil es einen zweiten Leser gibt (admin_path_service): zwei Kaskaden
    waeren zwei Meinungen darueber, was in einer Datei steht.
    """
    try:
        return rohdaten.decode("utf-8"), "utf-8"
    except UnicodeDecodeError:
        # cp1252 bildet jedes Byte ausser fuenf auf ein Zeichen ab; latin-1 kann alle.
        try:
            return rohdaten.decode("cp1252"), "cp1252"
        except UnicodeDecodeError:
            return rohdaten.decode("latin-1"), "latin-1"


def _lies_text(pfad: Path) -> tuple[str, str]:
    """Dateiinhalt plus erkannte Kodierung."""
    return dekodiere(pfad.read_bytes())


def _zeilen(text: str) -> list[str]:
    """Nur an ECHTEN Zeilenenden trennen - nicht an allem, was splitlines() kennt."""
    return text.replace("\r\n", "\n").replace("\r", "\n").split("\n")


def _setzt_fort(zeile: str) -> bool:
    """Endet die Zeile auf einer UNGERADEN Zahl Backslashes? Dann geht sie weiter."""
    return (len(zeile) - len(zeile.rstrip("\\"))) % 2 == 1


def _ist_kommentar(zeile: str) -> bool:
    """Javas Pruefung: Zwischenraum ueberspringen, dann '#' oder '!'."""
    nackt = zeile.lstrip(ZWISCHENRAUM_WIE_JAVA)
    return bool(nackt) and nackt[0] in "#!"


def _bloecke(zeilen: list[str]) -> list[list[str]]:
    """Physische Zeilen zu LOGISCHEN gruppieren - je Block die Zeilen, die Java als EINE
    liest.

    Der Block behaelt seine physischen Zeilen, damit ein Schreiber unangetastet
    zurueckschreiben kann, was er nicht anfasst.

    EINE KOMMENTARZEILE SETZT NICHTS FORT, und das ist der Unterschied zur Fassung
    bis zum 23.11.2026: Javas ``load0`` liest eine Kommentarzeile bis zum Ende der
    NATUERLICHEN Zeile. Ein Kommentar, der auf einen Backslash endet - ein
    Windows-Pfad genuegt, ``# Welt liegt unter C:\\minecraft\\lobby\\`` -, verschluckte
    sonst die Zeile darunter. War das ``level-name=lobby``, fiel der Leser auf "world"
    zurueck, und ``deploy_lobby_world`` raeumte mit ``shutil.rmtree`` den falschen Ordner
    aus. Eine Fortsetzungszeile, die mit '#' BEGINNT, ist dagegen kein Kommentar, sondern
    Teil des Wertes - darum wird nur am ANFANG eines Blocks gefragt.
    """
    bloecke: list[list[str]] = []
    aktuell: list[str] = []
    for zeile in zeilen:
        if not aktuell and _ist_kommentar(zeile):
            bloecke.append([zeile])
            continue
        aktuell.append(zeile)
        if not _setzt_fort(zeile):
            bloecke.append(aktuell)
            aktuell = []
    if aktuell:
        bloecke.append(aktuell)
    return bloecke


def _zusammen(block: list[str]) -> str:
    """Die logische Zeile eines Blocks - je Fortsetzung faellt ihr Backslash weg."""
    if len(block) == 1 and _ist_kommentar(block[0]):
        return block[0]
    return "".join(z[:-1] if _setzt_fort(z) else z for z in block)


def _offener_block(block: list[str]) -> bool:
    """Ein Block, dessen letzte Zeile noch fortsetzen WILL - am Dateiende.

    Wer dahinter etwas anhaengt, haengt es in den Wert darueber: Java liest die neue
    Zeile als Fortsetzung. Der Schreiber muss den Backslash darum vorher wegnehmen.
    """
    if not block or (len(block) == 1 and _ist_kommentar(block[0])):
        return False
    return _setzt_fort(block[-1])


def _mit_fortsetzung(zeilen: list[str]) -> list[str]:
    """Die logischen Zeilen - EIN Begriff, und der Schreiber nutzt denselben."""
    return [_zusammen(b) for b in _bloecke(zeilen)]


def lies_alle(pfad: str | Path) -> dict[str, str]:
    """Alle Schluessel mit entfluchteten Werten. Bei doppeltem Schluessel gewinnt der
    LETZTE - genau wie bei Java."""
    pfad = Path(pfad)
    try:
        text, _kodierung = _lies_text(pfad)
    except OSError:
        return {}
    raus: dict[str, str] = {}
    for zeile in _mit_fortsetzung(_zeilen(text)):
        zerlegt = zerlege_zeile(zeile)
        if zerlegt is not None:
            raus[zerlegt[0]] = entflucht(zerlegt[1])
    return raus


def lies_wert(pfad: str | Path, schluessel: str) -> str | None:
    """Ein einzelner Wert, entfluchtet - oder None."""
    return lies_alle(pfad).get(schluessel)


def schreibe_wert(pfad: str | Path, schluessel: str, wert: str) -> str | None:
    """Einen Wert setzen und alles andere unangetastet lassen.

    Rueckgabe: eine Warnung als Text, oder None bei Erfolg. Ein Schreibfehler darf das
    Speichern der uebrigen Einstellungen nicht mit in den Abgrund reissen.
    """
    pfad = Path(pfad)
    try:
        text, kodierung = _lies_text(pfad) if pfad.is_file() else ("", "utf-8")
    except OSError as exc:
        return f"server.properties nicht lesbar ({exc}) - '{schluessel}' nicht gesetzt."

    # In einer Nicht-UTF-8-Datei alles ausserhalb von ASCII als \\uXXXX schreiben: so
    # bleibt der Wert verlustfrei, ohne die Kodierung der Datei zu wechseln.
    neuer_wert = fluchte(wert, nur_ascii=(kodierung != "utf-8"))
    neue_zeile = f"{schluessel}={neuer_wert}"

    # UEBER LOGISCHE ZEILEN und nicht ueber physische [23.11.2026]: Eine
    # Fortsetzungszeile ist fuer Java kein eigener Eintrag, sondern ein Stueck des Wertes
    # darueber. Wer sie als Zeile mit Schluessel behandelt, ersetzt oder loescht einen
    # Teil eines fremden Wertes - und der haengende Backslash davor verschluckt danach,
    # was neu an ihrer Stelle steht. Gemessen mit echtem Java: Der Schluessel kommt dann
    # gar nicht in der Datei an, und diese Funktion meldete trotzdem Erfolg.
    bloecke = _bloecke(_zeilen(text))
    ersetzt = False
    raus: list[str] = []
    for block in bloecke:
        zerlegt = zerlege_zeile(_zusammen(block))
        if zerlegt is not None and zerlegt[0] == schluessel:
            # Der GANZE Block faellt weg, auch seine Fortsetzungszeilen: Sie gehoerten
            # zum alten Wert. Beim zweiten Treffer bleibt nichts stehen - doppelte
            # Eintraege desselben Schluessels fallen weg.
            if not ersetzt:
                raus.append(neue_zeile)
                ersetzt = True
            continue
        raus.extend(block)
    if not ersetzt:
        while raus and not raus[-1].strip():
            raus.pop()
        # NACH dem Wegnehmen der Leerzeilen neu fragen und nicht vorher: Eine Leerzeile
        # hinter der Fortsetzung schliesst deren Block, der Block ist also nicht
        # offen - aber sobald die Leerzeile weg ist, steht der haengende Backslash
        # wieder am Dateiende und verschluckt die neue Zeile. Gemessen mit echtem
        # Java am 23.11.2026, als genau dieser Fall als einziger von acht noch fiel.
        if raus and _offener_block(_bloecke(raus)[-1]):
            raus[-1] = raus[-1][:-1]
        raus.append(neue_zeile)

    inhalt = "\n".join(raus).strip("\n") + "\n"
    # EIN Schreibfehler ist noch keine Warnung: Steht der richtige Wert schon in der
    # Datei - zweiter Durchlauf, oder ein Betreiber hat sie von Hand gesetzt und
    # schreibgeschuetzt -, dann IST der Wert gesetzt, und eine Warnung schickte den
    # Betreiber einem Fehler nach, den es nicht gibt. Darum wird der Fehler gemerkt und
    # erst nach der Nachkontrolle gemeldet.
    schreibfehler: str | None = None
    try:
        pfad.parent.mkdir(parents=True, exist_ok=True)
        pfad.write_bytes(inhalt.encode(kodierung, errors="strict"))
    except UnicodeEncodeError:
        # Kann die Datei das Zeichen nicht - dann alles Nicht-ASCII fluchten.
        try:
            sicher = inhalt.replace(neue_zeile, f"{schluessel}={fluchte(wert, nur_ascii=True)}")
            pfad.write_bytes(sicher.encode(kodierung, errors="strict"))
        except (OSError, UnicodeEncodeError) as exc:
            schreibfehler = f"nicht schreibbar ({exc})"
    except OSError as exc:
        schreibfehler = f"nicht schreibbar ({exc})"

    # NACHKONTROLLE: Der LESER desselben Moduls muss den Wert danach sehen. Ohne sie
    # meldet jede Gestalt dieses Fehlers stumm Erfolg - am 23.11.2026 gemessen: vier
    # gruene Schreibaufrufe, und bei Java kam kein einziger der vier Schluessel an,
    # waehrend der Betreiber "Adventure + friedlich gesetzt" las. Eine Zahl kostet das
    # einen zweiten Lesevorgang auf eine Datei von wenigen Kilobyte, einmal je Knopfdruck.
    gelesen = lies_alle(pfad).get(schluessel)
    if gelesen == wert:
        return None
    if schreibfehler:
        return f"server.properties {schreibfehler} - '{schluessel}' nicht gesetzt."
    return (f"'{schluessel}' steht nach dem Schreiben nicht so in server.properties "
            f"(gelesen: {gelesen!r}) - bitte die Datei von Hand pruefen.")
