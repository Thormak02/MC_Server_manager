"""Einen Freimarken-Server fertig einrichten - Plugin bauen, ablegen, Datenbank stellen.

WARUM DAS HIER STEHT
Bis heute war die Einrichtung eine Handreichung aus sieben Schritten: Plugin bauen, Jar
nach plugins/ legen, einmal starten, stoppen, Zugangsdaten eintragen, neu starten. Jeder
Schritt eine Gelegenheit, einen zu vergessen - und der vergessene faellt erst Stunden
spaeter auf, wenn Parzellen nach einem Neustart weg sind.

KEIN PROBESTART NOETIG, und das ist gemessen: Das Plugin legt seine config.yml mit
``saveDefaultConfig()`` an, und das ueberschreibt eine vorhandene Datei NIE
(FreimarkenPlugin.java). Der Manager kann die ausgelieferte Fassung also vorab hinlegen
und die Zugangsdaten eintragen; das Plugin findet sie beim ersten Start vor. Damit
entfaellt die ganze Start-Stopp-Schleife - und die Einstellungsseiten haben sofort Inhalt.

GRADLE OHNE cmd.exe, und das ist der Kern des Bauwegs [gemessen 22.11.2026]:
``gradlew.bat`` braucht cmd.exe, und cmd.exe nimmt keinen UNC-Pfad als Arbeitsverzeichnis -
der Manager sieht das Repo aber genau so. Der Wrapper ist jedoch ein Jar mit einer
Hauptklasse: ``java -cp gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain`` laeuft
ohne Batch, und Python kann UNC als cwd. Gemessen: Exit 0 in 26 Sekunden aus dem
UNC-Pfad.

JEDER SCHRITT SCHEITERT FUER SICH
Die Kette laeuft weiter, wenn einer nicht geht, und jeder hinterlaesst SEINE Zeile. Ein
Fortschritt als Zahl ("3 von 5") sagt nicht, welcher fehlt; eine leere Liste heisst nicht
"fertig", sondern "noch nichts getan". Besonders die Datenbank: Sie braucht
Administratorrechte, und ohne sie ist der Server trotzdem spielbar - nur ohne Speichern.

DAS KENNWORT WIRD NIE PROTOKOLLIERT. Es wandert aus data/freimarken/datenbank.properties
in die config.yml des Servers und sonst nirgendwohin. Die Zeilen dieses Dienstes nennen es
nicht, und der Dashboard-Schreibweg kann es nicht anfassen: ``datenbank`` steht unter
``nicht-im-dashboard`` und ist dort gesperrt. Dass die Einrichtung es trotzdem schreibt,
ist kein Loch in jenem Riegel, sondern seine andere Seite - jener schuetzt vor einem
Menschen, der ein Kennwort in ein Formular tippt und es damit ins Protokoll bringt.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import threading
import time
from pathlib import Path
from typing import Any

# Die Wurzel des Repos: zwei Ebenen ueber app/services.
WURZEL = Path(__file__).resolve().parents[2]

PFAD_FREIMARKEN = WURZEL / "freimarken"
PFAD_WRAPPER = PFAD_FREIMARKEN / "gradle" / "wrapper" / "gradle-wrapper.jar"
PFAD_GEBAUT = PFAD_FREIMARKEN / "paper" / "build" / "libs"
PFAD_CONFIG_VORLAGE = (PFAD_FREIMARKEN / "paper" / "src" / "main" / "resources"
                       / "config.yml")
PFAD_SKRIPT = PFAD_FREIMARKEN / "skripte" / "mariadb_einrichten.ps1"
PFAD_ZUGANG = WURZEL / "data" / "freimarken" / "datenbank.properties"

# Java 25: Das Plugin wird mit --release 25 gebaut (freimarken/build.gradle.kts), und
# Gradle selbst laeuft unter demselben JDK.
JAVA_MINDESTENS = 25

# Gradle baut inkrementell; 26 s waren gemessen, aber ein kalter Lauf laedt erst die
# Verteilung herunter. Fuenfzehn Minuten sind grosszuegig und begrenzen trotzdem.
FRIST_BAU = 900
# Die Datenbankeinrichtung installiert per winget - das dauert.
FRIST_DATENBANK = 1200

PLUGIN_ORDNER = "plugins"
PLUGIN_DATEI = "Freimarken.jar"
CONFIG_ZIEL = "plugins/Freimarken/config.yml"


class EinrichtungFehler(Exception):
    """Der Text geht an den Nutzer - also deutsch und ohne Innereien."""


# Je Server ein Stand. Ein Schloss darum, weil der Faden ihn schreibt und die
# Weboberflaeche ihn liest.
_STAENDE: dict[int, dict[str, Any]] = {}
_SCHLOSS = threading.Lock()

# Der BAU laeuft nie zweimal zugleich - und zwar ueber alle Server hinweg [Fund
# 22.11.2026]. ``starte`` sperrt je Server; werden zwei Freimarken-Server kurz
# nacheinander erstellt, liefen zwei Gradle-Aufrufe im SELBEN Verzeichnis
# (freimarken/), mit denselben Sperren, demselben Konfigurationscache und demselben
# build/libs. Die uebrigen vier Schritte beruehren nur den Ordner ihres Servers und
# bleiben darum parallel.
_BAUSCHLOSS = threading.Lock()


def _leerer_stand() -> dict[str, Any]:
    return {"laeuft": False, "fertig": False, "schritte": [], "begonnen": 0.0}


def stand(server_id: int) -> dict[str, Any]:
    """Was bisher geschehen ist - je Schritt eine Zeile.

    EINE KOPIE, nicht das Original: Die Oberflaeche liest, waehrend der Faden schreibt.
    """
    with _SCHLOSS:
        vorhanden = _STAENDE.get(int(server_id))
        if vorhanden is None:
            return _leerer_stand()
        return {
            "laeuft": vorhanden["laeuft"],
            "fertig": vorhanden["fertig"],
            "begonnen": vorhanden["begonnen"],
            "schritte": [dict(s) for s in vorhanden["schritte"]],
        }


def _zeile(server_id: int, text: str, ok: bool) -> None:
    with _SCHLOSS:
        eintrag = _STAENDE.setdefault(int(server_id), _leerer_stand())
        eintrag["schritte"].append({"text": text, "ok": ok})


# --------------------------------------------------------------- Die fuenf Schritte

def _java_heim(db) -> str | None:
    """Das JAVA_HOME eines JDK, das Gradle und das Plugin bauen kann.

    ``resolve_java_binary`` gibt die ``java.exe`` zurueck und installiert bei Bedarf
    selbst ein passendes JDK - genau wie fuer Velocity. JAVA_HOME ist zwei Ebenen
    darueber (``<heim>/bin/java.exe``).
    """
    from app.services import java_runtime_service

    binaer = java_runtime_service.resolve_java_binary(db, JAVA_MINDESTENS)
    if not binaer:
        return None
    pfad = Path(binaer)
    if pfad.name.lower().startswith("java") and pfad.parent.name.lower() == "bin":
        return str(pfad.parent.parent)
    return None


def baue_plugin(db) -> tuple[bool, str]:
    """``:paper:jar`` bauen. Gibt (ok, Zeile) zurueck und wirft nicht.

    Gebaut wird IMMER, nicht nur wenn das Jar fehlt: Gradle entscheidet selbst, was zu tun
    ist (``UP-TO-DATE``), und ein Altersvergleich auf Dateizeitstempel waere hier
    besonders truegerisch - die Quellen liegen auf einer Netzfreigabe, deren Zeitstempel
    in Ortszeit stehen, waehrend der Manager in UTC rechnet.
    """
    if not PFAD_WRAPPER.is_file():
        return False, ("Der Gradle-Wrapper fehlt (" + str(PFAD_WRAPPER)
                       + ") - das Plugin laesst sich so nicht bauen.")

    heim = _java_heim(db)
    if not heim:
        return False, ("Kein JDK mit Java " + str(JAVA_MINDESTENS) + "+ gefunden, und es "
                       "liess sich auch keines einrichten. Unter Einstellungen -> Java "
                       "eines hinzufuegen; danach geht der Bau.")

    umgebung = dict(os.environ)
    umgebung["JAVA_HOME"] = heim
    java = str(Path(heim) / "bin" / "java.exe")

    # Nacheinander, nicht nebeneinander: siehe _BAUSCHLOSS. Wartet hier ein zweiter
    # Server, kostet ihn das die Dauer des ersten Baus - und der zweite Lauf ist dann
    # ohnehin ein UP-TO-DATE in Sekunden.
    with _BAUSCHLOSS:
        return _baue_mit_gradle(java, umgebung)


def _baue_mit_gradle(java: str, umgebung: dict[str, str]) -> tuple[bool, str]:
    try:
        fertig = subprocess.run(
            [java, "-cp", str(PFAD_WRAPPER),
             "org.gradle.wrapper.GradleWrapperMain", ":paper:jar", "--console=plain"],
            cwd=str(PFAD_FREIMARKEN), env=umgebung, capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=FRIST_BAU)
    except subprocess.TimeoutExpired:
        return False, ("Der Bau des Plugins hat nach " + str(FRIST_BAU // 60)
                       + " Minuten nicht geantwortet und wurde abgebrochen.")
    except OSError as fehler:
        return False, "Der Bau liess sich nicht starten: " + str(fehler)

    if fertig.returncode != 0:
        aus = ((fertig.stdout or "") + (fertig.stderr or "")).strip()
        # Nur der Schwanz: Gradle schreibt viel, und die Ursache steht hinten.
        return False, "Der Bau des Plugins ist gescheitert:\n" + aus[-1500:]

    jar = jar_pfad()
    if jar is None:
        return False, ("Der Bau meldete Erfolg, aber unter "
                       + str(PFAD_GEBAUT) + " liegt kein Jar.")
    return True, "Plugin gebaut: " + jar.name


def jar_pfad() -> Path | None:
    """Das gebaute Plugin-Jar - oder None.

    Das NEUESTE, falls mehrere liegen: Eine alte Fassung bleibt im Ordner stehen, wenn die
    Versionsnummer wechselt, und dann waere die alphabetisch erste die falsche.
    """
    if not PFAD_GEBAUT.is_dir():
        return None
    kandidaten = [p for p in PFAD_GEBAUT.glob("*.jar") if p.is_file()]
    if not kandidaten:
        return None
    return max(kandidaten, key=lambda p: p.stat().st_mtime)


def lege_jar_ab(server) -> tuple[bool, str]:
    """Das Jar nach ``plugins/`` des Servers.

    Windows sperrt eine Jar-Datei, solange der Server laeuft - dieselbe Lage, die
    ``lobby_service`` beim Lobby-Plugin beschreibt. Darum wird der Fehler benannt und
    nicht geworfen: Ein laufender Server ist beim Einrichten der Ausnahmefall, aber er
    darf die Kette nicht abbrechen.
    """
    jar = jar_pfad()
    if jar is None:
        return False, "Kein gebautes Jar vorhanden - der Bau ist nicht durchgelaufen."

    ordner = Path(str(server.base_path)) / PLUGIN_ORDNER
    try:
        ordner.mkdir(parents=True, exist_ok=True)
        shutil.copy2(jar, ordner / PLUGIN_DATEI)
    except (OSError, PermissionError) as fehler:
        return False, ("Das Jar liess sich nicht nach plugins/ kopieren (" + str(fehler)
                       + "). Laeuft der Server gerade? Windows sperrt die Datei dann.")
    return True, "Plugin abgelegt: " + PLUGIN_ORDNER + "/" + PLUGIN_DATEI


def lege_config_ab(server) -> tuple[bool, str]:
    """Die ausgelieferte ``config.yml`` hinlegen - aber keine vorhandene ueberschreiben.

    Genau die Haltung von ``saveDefaultConfig()``: Was da ist, bleibt. Sonst waere ein
    zweiter Lauf dieser Einrichtung der Weg, auf dem ein Betreiber alle seine Preise
    verliert.
    """
    if not PFAD_CONFIG_VORLAGE.is_file():
        return False, ("Die ausgelieferte config.yml fehlt im Repo ("
                       + str(PFAD_CONFIG_VORLAGE) + ").")

    ziel = Path(str(server.base_path)) / CONFIG_ZIEL
    # NICHT nur is_file() [Fund 22.11.2026]: Eine 0-Byte-Datei "ist" eine Datei, traegt
    # aber nichts. Sie entsteht nach einem abgebrochenen Schreibvorgang oder einem
    # Plattenfehler - und dann meldete dieser Schritt gruen, behauptete, die Datei trage
    # die Einstellungen, und legte die ausgelieferte Fassung NIE wieder hin. Auch
    # saveDefaultConfig() im Plugin half nicht: Eine leere Datei fehlt nicht.
    #
    # Gefragt wird nach einem INHALT, nicht nach einer Mindestgroesse: Jede denkbare
    # Fassung dieser Datei hat Text, und eine Zahl waere eine Grenze, die beim naechsten
    # Zuschnitt der Vorgabe falsch ist.
    if ziel.is_file() and ziel.stat().st_size > 0:
        return True, ("config.yml war schon da und bleibt unangetastet - sie traegt die "
                      "Einstellungen dieses Servers.")
    war_leer = ziel.is_file()
    try:
        ziel.parent.mkdir(parents=True, exist_ok=True)
        # newline="" haelt die Zeilenenden der Vorlage. Ohne das macht Windows aus 3071
        # Zeilen 3071 Aenderungen, sobald jemand spaeter einen Wert vergleicht.
        roh = PFAD_CONFIG_VORLAGE.read_text(encoding="utf-8")
        with open(ziel, "w", encoding="utf-8", newline="") as offen:
            offen.write(roh)
    except OSError as fehler:
        return False, "Die config.yml liess sich nicht ablegen: " + str(fehler)
    if war_leer:
        return True, ("config.yml war LEER und wurde durch die ausgelieferte Fassung "
                      "ersetzt - ein frueherer Schreibvorgang ist offenbar abgebrochen. "
                      "Die Einstellungen dieses Servers sind damit auf Vorgabe.")
    return True, "config.yml abgelegt - mit allen Erklaerungen der Vorgabe."


def _alle_zugangswerte() -> dict[str, str]:
    """Alle Zeilen der Zugangsdatei als Karte - mit Javas Regeln gelesen.

    EINE LESUNG FUER BEIDE ADRESSEN, und das ist nicht Bequemlichkeit: Hier
    standen zwei Abschriften derselben zehn Zeilen, und die Waechterprobe
    ``test_kein_neuer_eigener_properties_zerleger`` hat die zweite am
    26.11.2026 gefangen. Zu Recht - sie war ein Nachbau.

    GELESEN WIRD MIT ``properties_service.zerlege_zeile``, also mit Javas
    Regeln. Das ist hier mehr als Ordnung: Diese Datei liest auch das
    PLUGIN, und zwar mit Java (``Datenbank.java``). Wer sie anders liest,
    liest sie falsch - bei einem Kennwort mit Backslash oder einem
    Doppelpunkt als Trenner faellt das auf, und dann zeigt der Manager eine
    Anlage an, mit der das Plugin nicht spricht.

    Der Wert bleibt ROH (nicht entflucht), wie ueberall in diesem Haus: Der
    Schreiber dieser Datei ist das Einrichtungsskript, und es fluchtet
    nicht.
    """
    from app.services import properties_service

    if not PFAD_ZUGANG.is_file():
        return {}
    werte: dict[str, str] = {}
    try:
        text = PFAD_ZUGANG.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return {}
    for zeile in text.split(chr(10)):
        zerlegt = properties_service.zerlege_zeile(zeile.strip())
        if zerlegt is None:
            continue
        name, wert = zerlegt
        if name:
            werte[name] = wert.strip()
    return werte


def lies_zugangsdaten() -> dict[str, str]:
    """``url``, ``benutzer`` und ``passwort`` aus ``datenbank.properties``.

    GELESEN WIRD ``url`` und nicht der erste Schluessel, der passt: Daneben stehen
    ``url.test`` und ``root.passwort``, und das Testschema leeren die Java-Proben vor
    jedem Lauf. Wer dort hineinzeigt, laesst den Betrieb auf einer Anlage laufen, die
    beim naechsten Probelauf leer ist.
    """
    werte = _alle_zugangswerte()
    # LEERE KARTE HEISST "KEINE DATEI", drei leere Werte heissen "Datei da, aber
    # ohne diese Zeilen" - zwei verschiedene Lagen, und eine Probe haelt den
    # Unterschied fest. Beim Zusammenlegen der zwei Lesungen ist er mir
    # verlorengegangen; test_ohne_datei_bleibt_der_zugang_leer hat es gefangen.
    if not werte:
        return {}
    return {"url": werte.get("url", ""), "benutzer": werte.get("benutzer", ""),
            "passwort": werte.get("passwort", "")}


def lies_testadresse() -> dict[str, str]:
    """Die Adresse des TESTSCHEMAS samt Anmeldung - ``url.test`` und sonst nichts.

    EIN EIGENER, SO BENANNTER WEG, und der Grund steht in {@code lies_zugangsdaten}:
    Jene Funktion gibt ``url.test`` mit Absicht nicht heraus, damit niemand den
    Betrieb auf eine Anlage zeigt, die die Java-Proben vor jedem Lauf leeren. Dieser
    Riegel bleibt.

    Wer trotzdem an das Testschema muss - und das ist genau ein Verbraucher, der
    Nachweis, dass sich ein Abzug EINSPIELEN laesst -, nennt diese Funktion beim
    Namen. Dann steht im Quelltext, was geschieht, und eine Suche nach
    "testadresse" findet jede Stelle.

    Benutzer und Kennwort kommen aus denselben zwei Zeilen wie beim Betrieb: Die
    Properties-Datei fuehrt sie nur einmal, und das Einrichtungsskript legt beide
    Schemata fuer denselben Benutzer an.
    """
    werte = _alle_zugangswerte()
    # LEERE KARTE HEISST "KEINE DATEI", drei leere Werte heissen "Datei da, aber
    # ohne diese Zeilen" - zwei verschiedene Lagen, und eine Probe haelt den
    # Unterschied fest. Beim Zusammenlegen der zwei Lesungen ist er mir
    # verlorengegangen; test_ohne_datei_bleibt_der_zugang_leer hat es gefangen.
    if not werte:
        return {}
    return {"url": werte.get("url.test", ""),
            "benutzer": werte.get("benutzer", ""),
            "passwort": werte.get("passwort", "")}


def richte_datenbank_ein() -> tuple[bool, str]:
    """Das Einrichtungsskript rufen - MariaDB als Dienst, Schemata, Benutzer, Firewall.

    BRAUCHT ADMINISTRATORRECHTE. Laeuft der Manager ohne sie, scheitert das Skript, und
    genau das wird hier benannt statt geworfen: Ein Server ohne Datenbank ist spielbar,
    nur haelt er nichts ueber einen Neustart hinaus. Das ist eine Lage, die ein Mensch
    beheben kann - kein Grund, die Einrichtung abzubrechen.

    Uebersprungen, wenn die Zugangsdaten schon dastehen UND die Datenbank antwortet: Eine
    winget-Installation ohne Not ist Minuten fuer nichts.
    """
    if not PFAD_SKRIPT.is_file():
        return False, "Das Einrichtungsskript fehlt (" + str(PFAD_SKRIPT) + ")."

    if _datenbank_antwortet():
        return True, "MariaDB antwortet schon - nichts einzurichten."

    befehl = ["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
              "-File", str(PFAD_SKRIPT),
              "-ZweitAblage", str(PFAD_ZUGANG.parent)]

    # KEINE PIPES [Fund 22.11.2026]. Mit capture_output haengt die Frist nicht am
    # gerufenen Prozess, sondern an seinen ENKELN: powershell.exe gibt die Pipe an winget
    # weiter, winget an den MSI-Installer. Laeuft die Installation laenger als die Frist,
    # toetet subprocess.run nur powershell.exe und ruft danach auf Windows
    # communicate() ERNEUT - ohne Frist. Das liest bis EOF, und EOF kommt erst, wenn der
    # letzte Enkel die Pipe schliesst. Der Faden haengt dann so lange wie winget, der
    # Stand bleibt fuer immer auf "laeuft", und der Wiederholknopf erscheint nie.
    #
    # Mit einer DATEI gibt es kein EOF-Warten: Die Frist greift am Prozess selbst.
    import tempfile

    with tempfile.TemporaryDirectory() as ordner:
        bericht = Path(ordner) / "einrichtung.log"
        try:
            with open(bericht, "w", encoding="utf-8", errors="replace") as mitschrift:
                fertig = subprocess.run(
                    befehl, stdout=mitschrift, stderr=subprocess.STDOUT,
                    stdin=subprocess.DEVNULL, timeout=FRIST_DATENBANK)
        except subprocess.TimeoutExpired:
            return False, (
                "Die Datenbankeinrichtung hat nach " + str(FRIST_DATENBANK // 60)
                + " Minuten nicht geantwortet und wurde abgebrochen. ACHTUNG: Eine "
                "angefangene MariaDB-Installation kann weiterlaufen und den Dienst mit "
                "einem Kennwort anlegen, das danach nirgends steht - dann hilft nur ein "
                "Zuruecksetzen von MariaDB von Hand.")
        except OSError as fehler:
            return False, ("Die Datenbankeinrichtung liess sich nicht starten: "
                           + str(fehler))
        try:
            aus = bericht.read_text(encoding="utf-8", errors="replace").strip()
        except OSError:
            aus = ""

    if fertig.returncode != 0:
        return False, (
            "Die Datenbankeinrichtung ist gescheitert. Sie braucht "
            "Administratorrechte - ohne sie installiert winget nichts und der Dienst "
            "laesst sich nicht anlegen. Von Hand geht es mit "
            "freimarken\\skripte\\mariadb_einrichten.bat als Administrator.\n"
            + aus[-1200:])
    return True, "MariaDB eingerichtet: Dienst, Schemata, Benutzer, Firewallregel."


def zerlege_url(url: str) -> dict[str, str] | None:
    """``rechner``, ``port`` und ``schema`` einer JDBC-Adresse - oder ``None``.

    DIE EINE STELLE, an der diese Adresse gelesen wird. Dieser Dienst hatte zwei
    eigene ``re.match`` dafuer, und die Datenbanksicherung haette den dritten
    gebraucht - genau das Muster, das heute bei den .properties-Zerlegern aufgefallen
    ist (vierzehn Nachbauten derselben Regel, jeder ein wenig anders).

    Erkannt wird ``jdbc:mariadb://`` und ``jdbc:mysql://``, mit oder ohne Port, mit
    oder ohne Parameter dahinter. Alles andere gibt ``None`` - auch eine
    PostgreSQL-Adresse, denn fuer die taugt weder der Vorgabeport noch mysqldump.

    GEWORFEN WIRD NIE: Jeder Aufrufer beantwortet damit die Frage "gibt es hier
    ueberhaupt eine Datenbank", und die darf an einer kaputten Zeile in einer
    Einstellungsdatei nicht scheitern.
    """
    import re

    treffer = re.match(
        r"^jdbc:(?:mariadb|mysql)://([^:/?]+)(?::(\d+))?/([^?/]+)",
        (url or "").strip())
    if treffer is None:
        return None
    return {
        "rechner": treffer.group(1),
        "port": treffer.group(2) or "3306",
        "schema": treffer.group(3),
    }


def _datenbank_antwortet() -> bool:
    """Steht auf dem Rechner der Zugangsdaten ein Horcher auf dem Port?

    Eine Netzprobe und kein Anmeldeversuch: Fuer die Frage "muss ich einrichten" genuegt
    sie, und sie braucht kein Kennwort. Dasselbe Verfahren wie ``mc_ping``.
    """
    import socket

    teile = zerlege_url(lies_zugangsdaten().get("url", ""))
    if teile is None:
        return False
    try:
        with socket.create_connection((teile["rechner"], int(teile["port"])),
                                      timeout=3.0):
            return True
    except OSError:
        return False


def trage_zugangsdaten_ein(server) -> tuple[bool, str]:
    """Die drei ``datenbank``-Zeilen in die config.yml des Servers.

    GESCHRIEBEN WIRD ueber den kommentarerhaltenden Assistenten und nicht mit einer
    Textersetzung: Die Datei besteht zu drei Vierteln aus Erklaerungen, und sie sind das
    Handbuch der Anlage.

    DAS KENNWORT STEHT IN KEINER ZEILE, die dieser Dienst zurueckgibt. Es wandert aus den
    Properties in die config.yml und sonst nirgendwohin.
    """
    from app.services import freimarken_service as fs
    from app.services import yaml_assistant_service as ya

    zugang = lies_zugangsdaten()
    if not zugang.get("url"):
        return False, (
            "In data/freimarken/datenbank.properties steht keine url - ohne sie bleibt "
            "datenbank.url leer, und der Server speichert nichts ueber einen Neustart "
            "hinaus. Das Einrichtungsskript schreibt die Datei.")

    try:
        aktuell = fs.lies_datei(server, fs.PFAD_CONFIG)
    except (OSError, ValueError) as fehler:
        return False, "Die config.yml des Servers ist nicht lesbar: " + str(fehler)

    try:
        vorhanden = ya.felder_nach_pfad(aktuell)
    except Exception as fehler:  # noqa: BLE001 - jede Ursache fuehrt zur gleichen Antwort
        # NUR DIE ERSTE ZEILE [Fund 22.11.2026]: Eine MarkedYAMLError traegt ueber
        # Mark.get_snippet() den Quelltext der betroffenen Zeile mit sich. Liegt die
        # Fehlermarke im datenbank-Block, stuende dort die passwort-Zeile eines frueheren
        # Laufs - und diese Meldung erscheint auf jeder Freimarken-Seite, die ab
        # Moderator zu lesen ist.
        kurz = str(fehler).splitlines()[0] if str(fehler) else "unbekannt"
        return False, ("Die config.yml ist zurzeit kein gueltiges YAML (" + kurz
                       + ") - zu berichtigen ist sie im Dateieditor.")

    import json

    formular: dict[str, str] = {}
    meta: list[dict[str, Any]] = []
    fehlend: list[str] = []
    schon_gesetzt: list[str] = []
    for schluessel, wert in (("datenbank.url", zugang["url"]),
                             ("datenbank.benutzer", zugang.get("benutzer", "")),
                             ("datenbank.passwort", zugang.get("passwort", ""))):
        feld = vorhanden.get(schluessel)
        if feld is None:
            fehlend.append(schluessel)
            continue
        # WAS SCHON DASTEHT, BLEIBT [Fund 22.11.2026]. Vorher wurden alle drei Werte
        # bedingungslos gesetzt. Hat jemand datenbank.url von Hand auf einen anderen
        # Rechner gestellt - ueber das Dashboard geht das gar nicht, denn der Abschnitt
        # ist dort gesperrt -, drehte eine Wiederholung sie auf das zurueck, was in den
        # Properties steht, und das Einrichtungsskript schreibt dort immer 127.0.0.1.
        # Der Server haengt danach an einem leeren Schema, waehrend die echten Daten
        # woanders liegen. Dieselbe Haltung wie bei der config.yml: Was da ist, bleibt.
        if str(feld["wert"]).strip():
            schon_gesetzt.append(schluessel)
            continue
        formular[schluessel] = wert
        # art="text" und NICHT feld["art"] [Fund 22.11.2026]: Die Art kommt sonst aus dem
        # Tag des ALTEN Werts. Stand dort eine unquotierte Ziffernfolge (jemand hat ein
        # Zahlenkennwort von Hand eingetragen), gilt das Feld als "int" - und der
        # Assistent ruft dann int() auf dem NEUEN Wert und wirft mit einer Meldung, die
        # das Kennwort im Klartext nennt. Ein Kennwort, ein Benutzername und eine Adresse
        # sind immer Text, auch wenn sie nur aus Ziffern bestehen.
        meta.append({"key": schluessel, "pfad": list(feld["pfad"]), "art": "text",
                     "stil": feld["stil"], "wert": feld["wert"], "roh": feld["roh"]})

    if fehlend:
        return False, ("In der config.yml fehlen diese Schluessel: "
                       + ", ".join(fehlend)
                       + ". Passt die Fassung des Plugins zum Manager?")

    if not formular:
        # Alle drei standen schon. Das ist Erfolg und nicht Fehlschlag: Der Server ist
        # eingerichtet, nur hat diese Einrichtung daran nichts geaendert.
        return True, ("Die Zugangsdaten standen schon in der config.yml und bleiben "
                      "unangetastet (" + ", ".join(schon_gesetzt) + ").")

    try:
        neuer_text = ya.schreibe(aktuell, formular, json.dumps(meta))
        fs.schreib_datei(server, fs.PFAD_CONFIG, neuer_text)
    except Exception as fehler:  # noqa: BLE001
        # DER FREMDTEXT BLEIBT DRINNEN [Fund 22.11.2026]. Der Assistent nennt in zwei
        # Meldungen den GESCHRIEBENEN Wert ("'...': '<wert>' ist keine Zahl" und "...
        # haette nach dem Schreiben einen anderen Wert gehabt"). Diese Zeile erscheint auf
        # jeder Freimarken-Seite und ist ab Moderator zu lesen - ein durchgereichter
        # str(fehler) traegt das Kennwort dorthin. Genannt wird darum nur die ART des
        # Fehlers; die Ursache steht fuer den Betreiber im Serverprotokoll des Managers.
        import logging

        logging.getLogger(__name__).warning(
            "Zugangsdaten nicht eintragbar (%s)", type(fehler).__name__, exc_info=True)
        return False, (
            "Die Zugangsdaten liessen sich nicht eintragen ("
            + type(fehler).__name__ + "). Der Grund steht im Protokoll des Managers - "
            "er wird hier nicht genannt, weil eine solche Meldung das Kennwort "
            "enthalten kann.")

    # Die Zeile nennt den RECHNER und das Schema, nie den Benutzer und nie das
    # Kennwort. Zerlegt wird mit zerlege_url - dieselbe Regel wie ueberall.
    teile = zerlege_url(zugang["url"])
    ziel = ("(unleserlich)" if teile is None
            else teile["rechner"] + ":" + teile["port"] + "/" + teile["schema"])
    satz = "Zugangsdaten eingetragen: " + ziel
    if schon_gesetzt:
        satz += (" (unangetastet, weil schon gesetzt: " + ", ".join(schon_gesetzt)
                 + ")")
    return True, satz


# ------------------------------------------------------------------- Die Kette

SCHRITTE = ("Plugin bauen", "Plugin ablegen", "config.yml ablegen",
            "MariaDB einrichten", "Zugangsdaten eintragen")


def richte_ein(db, server) -> list[dict[str, Any]]:
    """Alle fuenf Schritte, jeder mit seiner Zeile. Wirft nicht.

    DIE REIHENFOLGE IST TRAGEND: Die Datenbank kommt VOR dem Eintragen, denn das Skript
    schreibt die Properties, aus denen eingetragen wird. Und die config.yml kommt vor der
    Datenbank, damit auch bei fehlgeschlagener Einrichtung eine vollstaendige Datei
    daliegt - die Einstellungsseiten haben dann Inhalt, nur die Spieldatenseiten nicht.
    """
    raus: list[dict[str, Any]] = []

    def tue(name: str, arbeit) -> bool:
        try:
            ok, text = arbeit()
        except Exception as fehler:  # noqa: BLE001 - ein Schritt reisst nie die Kette mit
            ok, text = False, "Unerwartet gescheitert: " + str(fehler)
        raus.append({"name": name, "ok": ok, "text": text})
        _zeile(int(server.id), name + ": " + text, ok)
        return ok

    gebaut = tue(SCHRITTE[0], lambda: baue_plugin(db))
    if gebaut:
        tue(SCHRITTE[1], lambda: lege_jar_ab(server))
    else:
        # Ohne Jar kein Ablegen - aber die Zeile muss trotzdem dastehen, sonst sieht die
        # Liste aus, als waere der Schritt gelungen.
        raus.append({"name": SCHRITTE[1], "ok": False,
                     "text": "Uebersprungen, weil der Bau nicht durchlief."})
        _zeile(int(server.id),
               SCHRITTE[1] + ": Uebersprungen, weil der Bau nicht durchlief.", False)

    tue(SCHRITTE[2], lambda: lege_config_ab(server))
    tue(SCHRITTE[3], richte_datenbank_ein)
    tue(SCHRITTE[4], lambda: trage_zugangsdaten_ein(server))
    return raus


def starte(server_id: int) -> bool:
    """Die Einrichtung in einem eigenen Faden - der Klick blockiert nicht.

    ``False``, wenn fuer diesen Server schon eine laeuft. Der Faden oeffnet seine EIGENE
    Datenbanksitzung: Die des Aufrufers ist nach der Antwort geschlossen, und ein Zugriff
    darauf wuerde aus einem fremden Faden kommen.
    """
    nummer = int(server_id)
    with _SCHLOSS:
        vorhanden = _STAENDE.get(nummer)
        if vorhanden is not None and vorhanden["laeuft"]:
            return False
        _STAENDE[nummer] = {"laeuft": True, "fertig": False, "schritte": [],
                            "begonnen": time.time()}

    def arbeite() -> None:
        from app.db.session import SessionLocal
        from app.models.server import Server

        try:
            with SessionLocal() as db:
                server = db.get(Server, nummer)
                if server is None:
                    _zeile(nummer, "Diesen Server gibt es nicht mehr.", False)
                    return
                richte_ein(db, server)
        except Exception as fehler:  # noqa: BLE001
            _zeile(nummer, "Die Einrichtung ist unerwartet gescheitert: "
                   + str(fehler), False)
        finally:
            with _SCHLOSS:
                eintrag = _STAENDE.setdefault(nummer, _leerer_stand())
                eintrag["laeuft"] = False
                eintrag["fertig"] = True

    threading.Thread(target=arbeite, name="freimarken-einrichtung-" + str(nummer),
                     daemon=True).start()
    return True


def vergiss(server_id: int) -> None:
    """Den Stand abraeumen - fuer die Proben und wenn ein Server geloescht wird."""
    with _SCHLOSS:
        _STAENDE.pop(int(server_id), None)
