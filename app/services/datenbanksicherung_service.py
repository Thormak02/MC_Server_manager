"""Einen Abzug der Plugin-Datenbank ziehen - und ihn wieder einspielen koennen.

WARUM ES DAS GIBT [26.11.2026]: Der Sicherungsdienst zippte Dateien aus dem
Serverordner. Die Plugin-Datenbank liegt nicht dort - sie laeuft als MariaDB-Dienst -, und
damit lagen Parzellen, Guthaben, Laeden, Freistaedte, Inseln, Straftaten, Meldungen,
Teamrollen, Mitgliedschaften und das Blockbuch in KEINER Sicherung. Ein Plattenschaden
haette alles gekostet, und zwar unwiederbringlich: Weltdateien lassen sich notfalls neu
generieren, ein Guthaben nicht.

DREI ENTSCHEIDUNGEN TRAGEN DIESES MODUL.

**Der Abzug enthaelt KEIN ``CREATE DATABASE``** - ``mysqldump`` wird ohne
``--databases`` gerufen. Das ist die wichtigste Zeile hier, und zwar aus einem
handfesten Grund: Mit jener Option traegt der Abzug den Namen seines Schemas in sich,
und ein Einspielen landet IMMER dort. Die Wiederherstellungsprobe dieses Moduls haette
damit das Betriebsschema ueberschrieben - also genau den Schaden angerichtet, gegen den
die Sicherung gebaut ist. Ohne die Option bestimmt der EINSPIELBEFEHL das Ziel, und ein
Abzug aus dem Betrieb laesst sich gefahrlos in ein Testschema legen.

**Das Kennwort steht nie auf der Kommandozeile.** Ein ``-p<kennwort>`` ist auf diesem
Rechner fuer jeden sichtbar, der die Prozessliste lesen kann - und bei einem Dienst, der
laufend sichert, steht es dort laufend. Gereicht wird es darum ueber eine
``--defaults-extra-file``, die unmittelbar danach geloescht wird. Dieselbe Haltung wie in
``freimarken_einrichtung_service``: Eine Meldung von dort nennt den Rechner und das
Schema, nie den Benutzer und nie das Kennwort.

**Eingespielt wird NICHT automatisch.** Ein Abzug dieses Schemas gehoert ALLEN Servern
gemeinsam - es gibt genau eine Plugin-Datenbank, und mehrere Server koennen sie teilen.
Ein Wiederherstellen, das sie stillschweigend ueberschreibt, nimmt dem zweiten Server
seinen Stand, waehrend der Betreiber den ersten zurueckholt. Die Wiederherstellung legt
den Abzug darum als DATEI zurueck und nennt die fertige Zeile; einspielen muss der
Betreiber. Dieselbe Haltung wie bei den Rechten der Mitgliedschaften: Der Manager NENNT,
was zu tun ist, und tut es nicht hinter dem Ruecken.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

# Der Name, unter dem der Abzug im Sicherungsarchiv liegt.
#
# Der Ordner mit dem Unterstrich davor sagt, dass er nicht vom Spielserver kommt:
# Beim Wiederherstellen wird das Archiv in den Serverordner entpackt, und dort soll
# niemand eine .sql-Datei fuer eine Minecraft-Datei halten. Minecraft selbst sieht
# unbekannte Ordner nicht an.
ORDNER_IM_ARCHIV = "_manager"
NAME_IM_ARCHIV = ORDNER_IM_ARCHIV + "/freimarken-datenbank.sql"

# Wo nach dem Abzugwerkzeug gesucht wird, in dieser Reihenfolge.
#
# ``mariadb-dump`` zuerst: Es ist der neue Name, ``mysqldump`` die Bruecke zurueck. Beide
# koennen ueber die Umgebungsvariable ueberschrieben werden - damit braucht dieser Dienst
# keine eigene Einstellungsdatei, und ein Betreiber mit einer Anlage an ungewohnter Stelle
# ist nicht ausgeschlossen.
UMGEBUNGSVARIABLE = "MC_MANAGER_MARIADB_DUMP"
UMGEBUNGSVARIABLE_EINSPIELEN = "MC_MANAGER_MARIADB_CLIENT"
WERKZEUGNAMEN = ("mariadb-dump", "mysqldump")
WERKZEUGNAMEN_EINSPIELEN = ("mariadb", "mysql")
SUCHORTE = (
    r"C:\Program Files\MariaDB*\bin",
    r"C:\Program Files\MySQL\*\bin",
    r"C:\xampp\mysql\bin",
    "/usr/bin",
    "/usr/local/bin",
    "/opt/homebrew/bin",
)


def _gefunden(namen: tuple[str, ...], variable: str) -> Path | None:
    """Das erste Werkzeug aus ``namen`` - Umgebungsvariable, PATH, dann die Suchorte."""
    eigen = (os.environ.get(variable) or "").strip().strip('"')
    if eigen:
        pfad = Path(eigen)
        # Ein gesetzter, aber falscher Pfad gibt None und keine stille PATH-Suche: Wer
        # die Variable setzt, soll den Fehler sehen und nicht ein anderes Werkzeug
        # bekommen, als er gemeint hat.
        return pfad if pfad.is_file() else None
    for name in namen:
        imPfad = shutil.which(name)
        if imPfad:
            return Path(imPfad)
    for ort in SUCHORTE:
        for name in namen:
            # glob auf dem ELTERNTEIL, weil die Suchorte einen Stern tragen koennen
            # (MariaDB legt je Fassung einen eigenen Ordner an).
            try:
                for ordner in Path(ort).parent.glob(Path(ort).name):
                    for endung in ("", ".exe"):
                        kandidat = ordner / (name + endung)
                        if kandidat.is_file():
                            return kandidat
            except (OSError, ValueError, IndexError):
                continue
    return None


def werkzeug() -> Path | None:
    """Womit ein Abzug gezogen wird - oder ``None``."""
    return _gefunden(WERKZEUGNAMEN, UMGEBUNGSVARIABLE)


def werkzeug_einspielen() -> Path | None:
    """Womit ein Abzug eingespielt wird - oder ``None``."""
    return _gefunden(WERKZEUGNAMEN_EINSPIELEN, UMGEBUNGSVARIABLE_EINSPIELEN)


def _zugang(*, test: bool = False) -> dict[str, str] | None:
    """Rechner, Port, Schema, Benutzer und Kennwort - oder ``None``.

    ZWEI GETRENNTE QUELLEN und kein Schluessel als Parameter: ``lies_zugangsdaten``
    gibt ``url.test`` mit Absicht nicht heraus (sonst zeigte der Betrieb womoeglich
    auf die Anlage, die die Java-Proben vor jedem Lauf leeren), und
    ``lies_testadresse`` nennt im Namen, was sie liefert. Ein Zeichenkettenparameter
    dazwischen waere die Stelle, an der ein Tippfehler den Betrieb trifft.
    """
    from app.services.freimarken_einrichtung_service import (lies_testadresse,
                                                             lies_zugangsdaten,
                                                             zerlege_url)

    zugang = lies_testadresse() if test else lies_zugangsdaten()
    teile = zerlege_url(zugang.get("url", ""))
    if teile is None or not zugang.get("benutzer"):
        return None
    return {
        "rechner": teile["rechner"],
        "port": teile["port"],
        "schema": teile["schema"],
        "benutzer": zugang["benutzer"],
        "passwort": zugang.get("passwort", ""),
    }


def _kennwortdatei(benutzer: str, passwort: str) -> Path:
    """Eine Wegwerfdatei mit den Anmeldedaten - fuer ``--defaults-extra-file``.

    DAS KENNWORT GEHOERT NICHT AUF DIE KOMMANDOZEILE: Dort ist es fuer jeden sichtbar,
    der die Prozessliste lesen kann, und bei einem Dienst, der laufend sichert, steht es
    dort laufend. Die Datei entsteht im privaten Temp-Verzeichnis des Benutzers und wird
    unmittelbar nach dem Aufruf geloescht.
    """
    griff, name = tempfile.mkstemp(prefix="mcm-db-", suffix=".cnf", text=True)
    with os.fdopen(griff, "w", encoding="utf-8", newline="\n") as datei:
        datei.write("[client]\n")
        datei.write("user=" + benutzer + "\n")
        datei.write("password=" + passwort + "\n")
    return Path(name)


def _lauf(werkzeugpfad: Path, zugang: dict[str, str], weitere: list[str], *,
          hinein: Path | None = None,
          heraus: Path | None = None) -> tuple[bool, str]:
    """Ruft das Werkzeug mit der Kennwortdatei. Gibt (gelungen, Meldung OHNE Kennwort).

    ``--defaults-extra-file`` MUSS die erste Option sein - MariaDB und MySQL lesen sie
    nur dort. Steht sie hinter einer anderen, wird sie mit einer Warnung uebergangen,
    und der Aufruf fragt nach einem Kennwort, das niemand tippt: Er haengt, bis die
    Zeitgrenze greift.
    """
    datei = _kennwortdatei(zugang["benutzer"], zugang["passwort"])
    befehl = [
        str(werkzeugpfad),
        "--defaults-extra-file=" + str(datei),
        "--host=" + zugang["rechner"],
        "--port=" + zugang["port"],
        *weitere,
    ]
    try:
        with open(heraus, "wb") if heraus else _nichts() as ausgabe, \
                open(hinein, "rb") if hinein else _nichts() as eingabe:
            ergebnis = subprocess.run(
                befehl,
                stdin=eingabe if hinein else subprocess.DEVNULL,
                stdout=ausgabe if heraus else subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=900)
    except (OSError, subprocess.SubprocessError) as fehler:
        return False, (type(fehler).__name__
                       + " beim Aufruf des Datenbankwerkzeugs - der Grund steht im"
                       + " Protokoll des Managers")
    finally:
        # AUCH BEI EINEM WURF: Eine liegengebliebene Kennwortdatei ist genau das, was
        # dieser Umweg vermeiden soll.
        try:
            datei.unlink()
        except OSError:
            pass

    if ergebnis.returncode == 0:
        return True, ""
    # Die Fehlerausgabe KANN den Benutzernamen nennen (nie das Kennwort - das stand in
    # der Datei). Durchgegeben wird sie trotzdem: Ohne sie raet der Betreiber, und ein
    # "es ging nicht" ist bei einer Sicherung die unbrauchbarste aller Meldungen.
    text = (ergebnis.stderr or b"").decode("utf-8", errors="replace").strip()
    # rpartition: Gesucht ist DIE LETZTE ZEILE, und das sagt rpartition direkt -
    # der Umweg ueber eine ganze Liste von Zeilen braucht es hier nicht.
    #
    # Nebenbei faellt diese Funktion damit nicht mehr in
    # test_kein_neuer_eigener_properties_zerleger: Jene Tafel sucht Funktionen, die
    # Zeilen SELBST trennen und ein "=" mitbringen, und beides traf hier zufaellig
    # zusammen (die Fehlerausgabe und die Kommandozeilen-Optionen). Geschaerft wurde
    # nicht die fremde Tafel, sondern diese Zeile.
    #
    # UND DIESER KOMMENTAR NENNT DIE ALTE FORM NICHT MEHR: Die Tafel sucht mit einer
    # TEXTSUCHE im Quelltext, und Kommentare nimmt sie nicht aus - der erste Entwurf
    # dieser Zeilen schrieb das alte Verfahren hin und wurde darum selbst getroffen.
    # Dasselbe war am 26.11.2026 schon einem Javadoc passiert, das die Abwesenheit
    # einer mysqldump-Option begruendete.
    letzte = text.rpartition(chr(10))[2].strip() or text
    return False, (letzte[:300] if text
                   else "Abbruch mit Code " + str(ergebnis.returncode))


class _nichts:
    """Ein Kontextwaechter, der nichts tut - damit der ``with``-Satz einheitlich bleibt."""

    def __enter__(self):
        return None

    def __exit__(self, *_):
        return False


def schreibe_abzug(ziel: Path) -> tuple[bool, str]:
    """Zieht einen Abzug des BETRIEBSSCHEMAS nach ``ziel``.

    Gibt ``(gelungen, Meldung)``. Eine Meldung kommt in BEIDEN Faellen: Beim Erfolg
    nennt sie die Groesse, beim Misserfolg den Grund - ein Betreiber, der "Sicherung
    fertig" liest, soll wissen, ob die Datenbank dabei war.

    OHNE ``--databases``, und das ist die tragende Zeile dieses Moduls - siehe den Kopf.
    Dazu:
      * ``--single-transaction`` gibt einen in sich stimmigen Abzug, ohne die Tabellen zu
        sperren. Der Spielserver laeuft waehrend der Sicherung weiter, und ein Abzug mit
        halb gebuchtem Geld waere schlimmer als keiner.
      * ``--add-drop-table`` macht den Abzug in ein NICHT leeres Schema einspielbar.
        Ohne ihn scheitert das Einspielen an der ersten vorhandenen Tabelle - also genau
        dann, wenn man es braucht.
      * KEINE ``--routines``/``--events``: Dieses Schema hat keine (``Datenbank.SCHRITTE``
        legt nur Tabellen und Spalten an), und der Plugin-Benutzer hat die Rechte dafuer
        womoeglich nicht - ein Abzug, der an einer fehlenden Berechtigung scheitert,
        waere ein Fehlschlag ohne Gegenstand.
    """
    zugang = _zugang()
    if zugang is None:
        return False, ("Keine Plugin-Datenbank eingerichtet (keine lesbare Adresse in"
                       " datenbank.properties) - es gibt nichts zu sichern.")
    pfad = werkzeug()
    if pfad is None:
        return False, ("Kein mariadb-dump oder mysqldump gefunden. Gesucht wurde in"
                       " PATH und in " + ", ".join(SUCHORTE) + ". Wo es liegt, sagt die"
                       " Umgebungsvariable " + UMGEBUNGSVARIABLE + ".")
    ziel.parent.mkdir(parents=True, exist_ok=True)
    gelungen, meldung = _lauf(pfad, zugang, [
        "--single-transaction",
        "--quick",
        "--add-drop-table",
        "--default-character-set=utf8mb4",
        zugang["schema"],
    ], heraus=ziel)
    if not gelungen:
        # Eine halbe Datei ist schlimmer als keine: Sie sieht wie eine Sicherung aus.
        try:
            ziel.unlink()
        except OSError:
            pass
        return False, "Abzug der Plugin-Datenbank fehlgeschlagen: " + meldung
    groesse = ziel.stat().st_size if ziel.is_file() else 0
    if groesse <= 0:
        try:
            ziel.unlink()
        except OSError:
            pass
        return False, ("Der Abzug der Plugin-Datenbank blieb leer - das Werkzeug hat"
                       " nichts geschrieben.")
    return True, ("Plugin-Datenbank gesichert: Schema " + zugang["schema"] + " von "
                  + zugang["rechner"] + ", " + str(groesse) + " Bytes.")


def einspielbefehl(pfad_im_serverordner: str) -> str:
    """Die fertige Zeile, die diesen Abzug einspielt - zum Kopieren.

    Der BENUTZER steht darin, das Kennwort nicht: Es wird abgefragt (``-p`` ohne Wert).
    Eine Zeile mit Kennwort waere eine, die in der Zwischenablage und in der
    Befehlsgeschichte landet.
    """
    zugang = _zugang()
    if zugang is None:
        return ""
    werkzeugname = (werkzeug_einspielen() or Path("mariadb")).name
    return (werkzeugname + " --host=" + zugang["rechner"] + " --port=" + zugang["port"]
            + " -u " + zugang["benutzer"] + " -p " + zugang["schema"]
            + ' < "' + pfad_im_serverordner + '"')


def spiele_ein_ins_testschema(quelle: Path) -> tuple[bool, str]:
    """Spielt einen Abzug in das TESTSCHEMA ein - und kann nichts anderes.

    DIESE FUNKTION KANN DEN BETRIEB NICHT UEBERSCHREIBEN, und das ist ihr ganzer Zweck:
    Das Ziel kommt aus ``url.test`` und aus keinem Parameter. Eine allgemeine
    Einspielfunktion waere ein Weg, mit einem falschen Argument die Spielerdaten aller
    Server zu loeschen - und sie wuerde genau einmal falsch aufgerufen.

    Gebraucht wird sie fuer den Nachweis, auf den es bei einer Sicherung ankommt: dass
    der Abzug sich EINSPIELEN laesst. Ein Abzug, der nicht zurueckkommt, ist keine
    Sicherung, sondern eine Datei.

    Der zweite Riegel sitzt am NAMEN: Endet das Schema aus ``url.test`` nicht auf
    ``_test``, wird abgesagt. Dieselbe Regel wie in den Java-Proben, und aus demselben
    Grund - wer dort versehentlich die Betriebsadresse eintraegt, verliert sonst alles.
    """
    if not quelle.is_file():
        return False, "Den Abzug gibt es nicht: " + str(quelle)
    zugang = _zugang(test=True)
    if zugang is None:
        return False, ("Keine Testadresse in datenbank.properties (url.test) - ohne sie"
                       " gibt es kein Schema, in das gefahrlos eingespielt werden"
                       " kann.")
    if not zugang["schema"].endswith("_test"):
        return False, ("Das Schema aus url.test heisst \"" + zugang["schema"]
                       + "\" und nicht auf _test - abgesagt. Diese Funktion spielt nur"
                       " in ein Testschema ein, und der Name ist der Riegel dafuer.")
    pfad = werkzeug_einspielen()
    if pfad is None:
        return False, ("Kein mariadb oder mysql gefunden. Wo es liegt, sagt die"
                       " Umgebungsvariable " + UMGEBUNGSVARIABLE_EINSPIELEN + ".")
    gelungen, meldung = _lauf(pfad, zugang, [zugang["schema"]], hinein=quelle)
    if not gelungen:
        return False, "Einspielen ins Testschema fehlgeschlagen: " + meldung
    return True, "In " + zugang["schema"] + " eingespielt."


def zaehle_im_testschema(tabelle: str) -> int | None:
    """Wie viele Zeilen diese Tabelle im TESTSCHEMA hat - oder ``None``.

    Nur fuer den Nachweis, dass ein eingespielter Abzug wirklich Zeilen zurueckbringt.
    Dieselben zwei Riegel wie beim Einspielen, und der Tabellenname wird gegen eine
    Weissliste von Zeichen geprueft: Er kommt aus dem Programm und nicht von aussen,
    aber eine Zeichenkette, die in ein SQL wandert, wird in diesem Haus geprueft.
    """
    if not tabelle or not all(z.isalnum() or z == "_" for z in tabelle):
        return None
    zugang = _zugang(test=True)
    if zugang is None or not zugang["schema"].endswith("_test"):
        return None
    pfad = werkzeug_einspielen()
    if pfad is None:
        return None
    datei = _kennwortdatei(zugang["benutzer"], zugang["passwort"])
    try:
        ergebnis = subprocess.run(
            [str(pfad), "--defaults-extra-file=" + str(datei),
             "--host=" + zugang["rechner"], "--port=" + zugang["port"],
             "--batch", "--skip-column-names", "--execute",
             "SELECT COUNT(*) FROM `" + tabelle + "`", zugang["schema"]],
            stdin=subprocess.DEVNULL, capture_output=True, timeout=120)
    except (OSError, subprocess.SubprocessError):
        return None
    finally:
        try:
            datei.unlink()
        except OSError:
            pass
    if ergebnis.returncode != 0:
        return None
    text = (ergebnis.stdout or b"").decode("utf-8", errors="replace").strip()
    try:
        return int(text)
    except ValueError:
        return None
