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


def zerlege_zeile(zeile: str) -> tuple[str, str] | None:
    """``(Schluessel, roher Wert)`` einer Zeile - oder None fuer Kommentar/leer."""
    text = (zeile or "").lstrip()
    if not text or text[0] in "#!":
        return None
    # Das erste UNGEFLUCHTETE = oder : trennt.
    i = 0
    while i < len(text):
        if text[i] == "\\":
            i += 2
            continue
        if text[i] in "=:":
            return text[:i].strip(), text[i + 1:].lstrip()
        i += 1
    return text.strip(), ""


def _lies_text(pfad: Path) -> tuple[str, str]:
    """Dateiinhalt plus erkannte Kodierung. Latin-1 ist der Rueckfall, nicht Datenverlust."""
    rohdaten = pfad.read_bytes()
    try:
        return rohdaten.decode("utf-8"), "utf-8"
    except UnicodeDecodeError:
        # cp1252 bildet jedes Byte ausser fuenf auf ein Zeichen ab; latin-1 kann alle.
        try:
            return rohdaten.decode("cp1252"), "cp1252"
        except UnicodeDecodeError:
            return rohdaten.decode("latin-1"), "latin-1"


def _zeilen(text: str) -> list[str]:
    """Nur an ECHTEN Zeilenenden trennen - nicht an allem, was splitlines() kennt."""
    return text.replace("\r\n", "\n").replace("\r", "\n").split("\n")


def _mit_fortsetzung(zeilen: list[str]) -> list[str]:
    """Zeilen, die auf einen ungeraden Backslash enden, mit der naechsten verbinden."""
    raus: list[str] = []
    puffer = ""
    for zeile in zeilen:
        kandidat = puffer + zeile
        backslashes = len(kandidat) - len(kandidat.rstrip("\\"))
        if backslashes % 2 == 1:
            puffer = kandidat[:-1]
            continue
        raus.append(kandidat)
        puffer = ""
    if puffer:
        raus.append(puffer)
    return raus


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

    zeilen = _zeilen(text)
    ersetzt = False
    raus: list[str] = []
    for zeile in zeilen:
        zerlegt = zerlege_zeile(zeile)
        if zerlegt is not None and zerlegt[0] == schluessel:
            if ersetzt:
                continue      # Doppelte Eintraege desselben Schluessels fallen weg
            raus.append(neue_zeile)
            ersetzt = True
            continue
        raus.append(zeile)
    if not ersetzt:
        while raus and not raus[-1].strip():
            raus.pop()
        raus.append(neue_zeile)

    inhalt = "\n".join(raus).strip("\n") + "\n"
    try:
        pfad.parent.mkdir(parents=True, exist_ok=True)
        pfad.write_bytes(inhalt.encode(kodierung, errors="strict"))
    except UnicodeEncodeError:
        # Kann die Datei das Zeichen nicht - dann alles Nicht-ASCII fluchten.
        try:
            sicher = inhalt.replace(neue_zeile, f"{schluessel}={fluchte(wert, nur_ascii=True)}")
            pfad.write_bytes(sicher.encode(kodierung, errors="strict"))
        except (OSError, UnicodeEncodeError) as exc:
            return f"server.properties nicht schreibbar ({exc}) - '{schluessel}' nicht gesetzt."
    except OSError as exc:
        return f"server.properties nicht schreibbar ({exc}) - '{schluessel}' nicht gesetzt."
    return None
