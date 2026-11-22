"""Der Freimarken-Bereich: Stammdaten des Plugins lesen und schreiben.

WO DIE STAMMDATEN LIEGEN
In plugins/Freimarken/config.yml des jeweiligen Servers - nicht in einer Tabelle. Plan
12a.2 beschreibt den Weg ueber die Datenbank; gebaut ist er anders, und zwar auf beiden
Seiten: Die 21 Tabellen des Plugins halten ausschliesslich SPIELdaten (Parzellen,
Guthaben, Buchungen), und jede Zahl, die ein Mensch einstellt, liest das Plugin aus der
Datei - beim Start und auf /freimarken neuladen. Die drei Regeln aus 12a.2 gelten
unveraendert, nur steht an der Stelle "Datenbank" jetzt "Datei".

Das ist kein Notbehelf. Die config.yml besteht zu drei Vierteln aus Kommentaren (2327 von
3071 Zeilen), und die sind das Handbuch der Anlage: Sie erklaeren jeden Wert, nennen
Bereiche und sagen, was ein Neuladen erreicht und was nicht. Dieses Dashboard zeigt sie
an, statt sie zu ersetzen.

WARUM NICHT write_text_file
Jene Funktion schreibt ohne newline-Angabe. Unter Windows wird daraus CRLF, und eine
Aenderung an einem einzigen Wert haette alle 3071 Zeilen umgeschrieben - im Diff eine
neue Datei, im Dateidatum ein Vollumbau. Hier wird mit newline="" gelesen UND geschrieben:
Die Zeilenenden der Datei bleiben die, die sie waren, welche auch immer das sind.

DER SPRUNGSCHUTZ WIRD BEDIENT, NICHT NACHGEBAUT
Das Plugin verwirft einen Geldwert, der um mehr als Faktor 2 von der ausgelieferten
Vorgabe abweicht, solange nicht wirtschaft.sprung-bestaetigt.<name> den Text "alt -> neu"
traegt (Sprungregel, Wirtschaftsliste.betrag). Dieses Modul prueft das nicht zum zweiten
Mal - zwei Pruefungen fuer denselben Fall machen sich gegenseitig unpruefbar und laufen
auseinander. Es stellt die Rueckfrage und schreibt danach beide Zeilen. Der Riegel bleibt
im Plugin, das Dashboard ist die Hand, die ihn bedient.
"""
from __future__ import annotations

import functools
import json
import pathlib
import re
from typing import Any

import yaml

from app.models.server import Server
from app.services import yaml_assistant_service as ya

# Die Dateien des Plugins, relativ zum Serverordner.
PFAD_CONFIG = "plugins/Freimarken/config.yml"

# DIE ZWEIDATEIFORM DER SPRACHE - und warum nur eine davon beschrieben wird.
#
# PFAD_VORLAGE schreibt das Plugin bei JEDEM Start neu (saveResource(..., true)). Sie ist
# die vollstaendige Referenz: 770 Saetze mit ihren Erklaerungen. Eine Aenderung darin
# waere beim naechsten Start weg - darum wird sie hier nur GELESEN.
#
# PFAD_SPRACHE legt das Plugin LEER an, genau einmal. Sie enthaelt allein die
# Ueberschreibungen des Betreibers; jeder Satz, der nicht darin steht, kommt aus dem
# Plugin, und der Rueckfall gilt JE SCHLUESSEL. Hierhin wird geschrieben.
PFAD_SPRACHE = "plugins/Freimarken/sprache.yml"
PFAD_VORLAGE = "plugins/Freimarken/sprache-vorlage.yml"

# Der Abschnitt der Sprachdatei, der die Farbrollen fuehrt. Sie sind keine Saetze und
# werden getrennt behandelt: Ein Farbwert ist eine Hexzahl, kein Wortlaut.
ABSCHNITT_FARBEN = "farben"

# Der Abschnitt, in dem die Bestaetigungen fuer grosse Spruenge stehen.
PFAD_BESTAETIGT = "wirtschaft.sprung-bestaetigt"

# Woran erkannt wird, dass ein Abschnitt erst beim Neustart gelesen wird. Die Marke steht
# in der config.yml selbst - 16-mal, von Hand gesetzt, direkt beim betroffenen Abschnitt.
# Sie abzuschreiben waere eine zweite Wahrheit; sie zu lesen ist eine Zeile.
MARKE_START = "NUR BEIM START"

KATALOG_DATEI = (pathlib.Path(__file__).resolve().parents[1]
                 / "assets" / "freimarken_felder.yml")


class FreimarkenFehler(ValueError):
    """Der Text geht an den Nutzer - also deutsch und ohne Innereien."""


# --------------------------------------------------------------------- Katalog


@functools.lru_cache(maxsize=1)
def katalog() -> dict[str, Any]:
    """Der Feldkatalog aus app/assets. Einmal gelesen und dann gehalten.

    Die Datei ist mitgeliefert und aendert sich nur mit einer neuen Fassung des Managers -
    anders als die config.yml eines Servers, die bei jedem Aufruf frisch gelesen wird.
    """
    text = KATALOG_DATEI.read_text(encoding="utf-8")
    geladen = yaml.safe_load(text) or {}
    if not isinstance(geladen, dict):
        raise FreimarkenFehler("Der Feldkatalog ist unlesbar.")
    return geladen


def seiten() -> list[dict[str, Any]]:
    roh = katalog().get("seiten") or []
    return [eintrag for eintrag in roh if isinstance(eintrag, dict)]


def seite(schluessel: str) -> dict[str, Any] | None:
    for eintrag in seiten():
        if eintrag.get("schluessel") == schluessel:
            return eintrag
    return None


def passt_muster(muster: str, pfad: str) -> bool:
    """Ob ``pfad`` auf ``muster`` passt; ``*`` steht fuer GENAU EINEN Abschnitt.

    Nicht fnmatch: dort laeuft ``*`` auch ueber Punkte hinweg, und ``welten.*.art`` traefe
    dann ``welten.marktstadt.tor.art`` mit. Ein Muster fuer eine Ebene soll eine Ebene
    treffen.
    """
    teile_muster = muster.split(".")
    teile_pfad = pfad.split(".")
    if len(teile_muster) != len(teile_pfad):
        return False
    return all(m == "*" or m == p for m, p in zip(teile_muster, teile_pfad))


def _aus_abbildung(name: str, pfad: str) -> str | None:
    """Der Satz aus einer Katalog-Abbildung, deren Schluessel Muster sein duerfen."""
    abbildung = katalog().get(name) or {}
    if not isinstance(abbildung, dict):
        return None
    for muster, satz in abbildung.items():
        if passt_muster(str(muster), pfad):
            return str(satz)
    return None


def sperrgrund(pfad: str) -> str | None:
    """Der Satz, warum dieser Pfad nicht geschrieben werden darf - oder None.

    Zwei Quellen, eine Antwort. Die Muster unter ``gesperrt`` treffen eine Ebene genau
    (Klasse 3 nach Plan 12a.6). Ein Abschnitt unter ``nicht-im-dashboard`` sperrt seinen
    ganzen Unterbaum - diese Haelfte fehlte: ``datenbank`` stand mit Begruendung im
    Katalog, aber kein Programmpfad las die Abbildung. Damit war die Begruendung eine
    Absichtserklaerung: Ein untergeschobenes Feld ``feld:datenbank.passwort`` wurde
    geschrieben, und ``protokoll_text`` legte altes UND neues Kennwort im Klartext ins
    Protokoll, das ab Moderator zu lesen ist.
    """
    grund = _aus_abbildung("gesperrt", pfad)
    if grund:
        return grund
    abschnitte = katalog().get("nicht-im-dashboard") or {}
    if not isinstance(abschnitte, dict):
        return None
    erster = pfad.split(".")[0]
    for name, satz in abschnitte.items():
        if str(name) == erster:
            return str(satz)
    return None


# ----------------------------------------------------------------------- Lesen


def lies_datei(server: Server, relativ: str) -> str:
    """Den Text einer Plugin-Datei, mit ihren Zeilenenden, wie sie sind.

    newline="" heisst: keine Umwandlung. Der Text, der hier herauskommt, ist Zeichen fuer
    Zeichen der Dateiinhalt - nur so stimmen die Positionen, die yaml.compose spaeter
    liefert, mit der Datei ueberein, und nur so laesst sie sich unveraendert
    zurueckschreiben.
    """
    from app.services.file_service import validate_server_relative_path

    ziel = validate_server_relative_path(server, relativ)
    with open(ziel, "r", encoding="utf-8", newline="") as offen:
        return offen.read()


def schreib_datei(server: Server, relativ: str, text: str) -> None:
    from app.services.file_service import validate_server_relative_path

    ziel = validate_server_relative_path(server, relativ)
    with open(ziel, "w", encoding="utf-8", newline="") as offen:
        offen.write(text)


def hat_freimarken(server: Server) -> bool:
    """Ob dieser Server eine Freimarken-Konfiguration hat.

    Faengt alles: Ein Serverordner, der nicht existiert, eine fehlende Datei und ein Pfad,
    den die Pruefung ablehnt, bedeuten dasselbe - hier ist kein Freimarken.
    """
    try:
        lies_datei(server, PFAD_CONFIG)
    except (OSError, ValueError):
        return False
    return True


def _kommentar_ueber(zeilen: list[str], zeile: int) -> list[str]:
    """Die Kommentarzeilen direkt ueber ``zeile`` - das ist die Erklaerung des Feldes.

    Gesammelt wird rueckwaerts bis zur ersten Zeile, die kein Kommentar ist. Eine
    Leerzeile beendet den Block ebenfalls: Was durch eine Leerzeile abgesetzt ist, gehoert
    zum Abschnitt und nicht zu diesem einen Feld.
    """
    raus: list[str] = []
    stelle = zeile - 1
    while stelle >= 0:
        roh = zeilen[stelle].strip()
        if not roh.startswith("#"):
            break
        raus.append(roh.lstrip("#").strip())
        stelle -= 1
    raus.reverse()
    return raus


def _kommentar_am_abschnitt(zeilen: list[str], block: str) -> list[str]:
    """Die Kommentarzeilen unter der Abschnittszeile ``block:`` am Zeilenanfang.

    Dort steht, was fuer den ganzen Abschnitt gilt - unter anderem die Marke
    "NUR BEIM START GELESEN". Beim einzelnen Feld steht sie meist nicht.
    """
    raus: list[str] = []
    gefunden = False
    for roh in zeilen:
        if not gefunden:
            if roh.startswith(block + ":"):
                gefunden = True
            continue
        nackt = roh.strip()
        if nackt.startswith("#"):
            raus.append(nackt.lstrip("#").strip())
            continue
        if not nackt:
            continue
        break
    # Auch die Zeilen VOR dem Abschnitt gehoeren dazu: in der config.yml steht die
    # Start-Marke mehrfach ueber dem Abschnittsnamen und nicht darunter.
    davor: list[str] = []
    for stelle, roh in enumerate(zeilen):
        if roh.startswith(block + ":"):
            davor = _kommentar_ueber(zeilen, stelle)
            break
    return davor + raus


def felder_der_seite(server: Server, schluessel: str) -> dict[str, Any]:
    """Alles, was eine Seite des Dashboards braucht - fertig zum Anzeigen.

    Zurueck kommt je Abschnitt eine Gruppe mit ihren Feldern. Jedes Feld traegt seinen
    Wert, seine Erklaerung aus den Kommentaren und seine Klasse nach Plan 12a.6.
    """
    eintrag = seite(schluessel)
    if eintrag is None:
        raise FreimarkenFehler("Diese Seite gibt es nicht.")

    text = lies_datei(server, PFAD_CONFIG)
    zeilen = text.splitlines()
    try:
        vorhanden = ya.felder_nach_pfad(text)
    except (yaml.YAMLError, RecursionError) as fehler:
        kurz = str(fehler).splitlines()[0] if str(fehler) else "unbekannt"
        raise FreimarkenFehler(
            "Die config.yml des Servers ist zurzeit kein gueltiges YAML (" + kurz
            + "). Solange das so ist, zeigt das Dashboard sie nicht an - zu berichtigen "
            "ist sie im Dateieditor.") from fehler

    sprung_zu = katalog().get("sprung") or {}
    gruppen: list[dict[str, Any]] = []

    for block in eintrag.get("bloecke") or []:
        block = str(block)
        kopf = _kommentar_am_abschnitt(zeilen, block)
        nur_start = any(MARKE_START in satz for satz in kopf)
        felder: list[dict[str, Any]] = []

        for pfad, feld in vorhanden.items():
            if feld["pfad"][0] != block:
                continue
            gesperrt = sperrgrund(pfad)
            erklaerung = _kommentar_ueber(zeilen, feld["zeile"])
            felder.append({
                "pfad": pfad,
                # Ohne den Abschnittsnamen: der steht schon als Gruppenkopf darueber.
                "name": pfad[len(block) + 1:] if pfad.startswith(block + ".") else pfad,
                "wert": feld["wert"],
                "art": feld["art"],
                "erklaerung": erklaerung,
                "gesperrt": gesperrt,
                "bestandsschutz": _aus_abbildung("bestandsschutz", pfad),
                "sprung": sprung_zu.get(pfad),
                # Ein Feld kann die Marke auch selbst tragen, nicht nur sein Abschnitt.
                "nur_start": nur_start or any(MARKE_START in s for s in erklaerung),
            })

        if felder:
            gruppen.append({"block": block, "kopf": kopf, "nur_start": nur_start,
                            "felder": felder})

    return {"seite": eintrag, "gruppen": gruppen,
            "felder_gesamt": sum(len(g["felder"]) for g in gruppen)}


# ------------------------------------------------------------------- Schreiben


def _als_zahl(wert: str) -> int | None:
    try:
        return int(str(wert).strip())
    except (TypeError, ValueError):
        return None


def grosser_sprung(alt: str, neu: str) -> bool:
    """Ob die Aenderung ueber den Faktor 2 hinausgeht - in beide Richtungen.

    Dieselbe Rechnung wie Sprungregel.grosserSprung im Plugin, und bewusst dieselbe:
    Gerechnet wird 2 * neu < alt und nicht neu < alt / 2, weil die Ganzzahldivision
    abrundet und die Grenze damit um eins verschoebe. Hier ENTSCHEIDET sie nichts - sie
    waehlt nur, ob die Rueckfrage kommt. Verworfen oder angenommen wird der Wert im
    Plugin.
    """
    a, n = _als_zahl(alt), _als_zahl(neu)
    if a is None or n is None or a == 0:
        return False
    return n > 2 * a or 2 * n < a


def sprung_text(alt: str, neu: str) -> str:
    """Der Text, den das Plugin als Bestaetigung erwartet: ``"25000 -> 100000"``."""
    return str(alt).strip() + " -> " + str(neu).strip()


def pruefe_aenderungen(server: Server, werte: dict[str, str]) -> dict[str, Any]:
    """Was sich aendern wuerde - OHNE zu schreiben.

    Liefert die Liste der Aenderungen, die gesperrten darunter und die, die eine zweite
    Bestaetigung brauchen. Die Route zeigt das an; erst der zweite Aufruf schreibt.
    """
    text = lies_datei(server, PFAD_CONFIG)
    vorhanden = ya.felder_nach_pfad(text)
    sprung_zu = katalog().get("sprung") or {}

    aenderungen: list[dict[str, Any]] = []
    gesperrt: list[dict[str, str]] = []
    spruenge: list[dict[str, Any]] = []
    unbekannt: list[str] = []

    for pfad, neu in werte.items():
        feld = vorhanden.get(pfad)
        if feld is None:
            unbekannt.append(pfad)
            continue
        alt = str(feld["wert"])
        if str(neu).strip() == alt:
            continue

        grund = sperrgrund(pfad)
        if grund:
            gesperrt.append({"pfad": pfad, "grund": grund})
            continue

        eintrag = {"pfad": pfad, "alt": alt, "neu": str(neu).strip(),
                   "art": feld["art"], "stil": feld["stil"], "roh": feld["roh"],
                   # DER ECHTE PFAD, nicht das Label [Fund 22.11.2026]. In ihm ist eine
                   # Listenstelle eine ZAHL; im Label steht sie als "[0]" ohne Punkt.
                   # Wer ihn aus dem Label zurueckrechnet, erhaelt "spawngruende[0]" als
                   # Schluesselnamen, findet ihn im Mapping nicht und meldet dem Nutzer
                   # eine Dateiaenderung, die nicht stattgefunden hat - 145 der 549
                   # Felder sind davon betroffen.
                   "knotenpfad": list(feld["pfad"])}
        aenderungen.append(eintrag)

        if grosser_sprung(alt, neu):
            name = sprung_zu.get(pfad)
            spruenge.append({
                "pfad": pfad, "alt": alt, "neu": str(neu).strip(),
                "schluessel": (PFAD_BESTAETIGT + "." + name) if name else None,
                "text": sprung_text(alt, neu),
                "bestandsschutz": _aus_abbildung("bestandsschutz", pfad),
            })

    return {"aenderungen": aenderungen, "gesperrt": gesperrt, "spruenge": spruenge,
            "unbekannt": unbekannt}


def speichere(server: Server, werte: dict[str, str], *,
              sprung_bestaetigt: bool = False) -> dict[str, Any]:
    """Setzt die Werte in die config.yml ein. Gibt zurueck, was geschehen ist.

    Ein gesperrtes Feld wird uebergangen und gemeldet, nicht geschrieben. Ist unter den
    Aenderungen ein grosser Sprung und ``sprung_bestaetigt`` ist nicht gesetzt, wird
    NICHTS geschrieben - die Route stellt dann die Rueckfrage.
    """
    lage = pruefe_aenderungen(server, werte)
    if lage["spruenge"] and not sprung_bestaetigt:
        return {"geschrieben": False, "warte_auf_bestaetigung": True, **lage}
    if not lage["aenderungen"]:
        return {"geschrieben": False, "warte_auf_bestaetigung": False, **lage}

    text = lies_datei(server, PFAD_CONFIG)

    # Das Formular des Assistenten erwartet "key" -> Wert und ein meta mit dem Pfad. Wir
    # benutzen den Pfad selbst als key; so bleibt das Schreiben an einer Stelle - samt
    # seiner Pruefung, dass sich die Zeile seit dem Oeffnen nicht geaendert hat und dass
    # der Wert nach dem Schreiben wirklich dasteht.
    formular: dict[str, str] = {}
    meta: list[dict[str, Any]] = []
    for eintrag in lage["aenderungen"]:
        formular[eintrag["pfad"]] = eintrag["neu"]
        meta.append({"key": eintrag["pfad"], "pfad": eintrag["knotenpfad"],
                     "art": eintrag["art"], "stil": eintrag["stil"],
                     "wert": eintrag["alt"], "roh": eintrag["roh"]})

    # Die Bestaetigungszeilen dazu. Sie stehen in derselben Datei und gehen denselben Weg,
    # damit entweder alles oder nichts geschrieben wird: Ein Preis ohne seine Bestaetigung
    # waere ein Wert, den das Plugin beim naechsten Start verwirft.
    vorhanden = ya.felder_nach_pfad(text)
    ohne_schluessel: list[str] = []
    for sprung in lage["spruenge"]:
        schluessel = sprung["schluessel"]
        if not schluessel:
            ohne_schluessel.append(sprung["pfad"])
            continue
        feld = vorhanden.get(schluessel)
        if feld is None:
            ohne_schluessel.append(sprung["pfad"])
            continue
        formular[schluessel] = sprung["text"]
        # Auch hier der echte Pfad: Ein Bestaetigungsschluessel ist heute nie eine
        # Listenstelle, aber ihn aus dem Label zurueckzurechnen waere dieselbe Falle
        # noch einmal - und die naechste Fassung der Datei entscheidet darueber, nicht
        # diese Zeile.
        meta.append({"key": schluessel, "pfad": list(feld["pfad"]),
                     "art": "text", "stil": feld["stil"],
                     "wert": str(feld["wert"]), "roh": feld["roh"]})

    try:
        neu_text = ya.schreibe(text, formular, json.dumps(meta))
    except ya.YamlAssistentFehler as fehler:
        raise FreimarkenFehler(str(fehler)) from fehler

    if neu_text != text:
        schreib_datei(server, PFAD_CONFIG, neu_text)

    return {"geschrieben": neu_text != text, "warte_auf_bestaetigung": False,
            "ohne_schluessel": ohne_schluessel, **lage}


# Die Kennungen, unter denen Aenderungen dieses Bereichs im Audit-Protokoll stehen.
# Beide beginnen mit "freimarken." - daran erkennt die Protokollseite ihre Zeilen, und
# nur daran haengt, dass sie ab Moderator lesbar sein darf.
AKTION_STAMMDATEN = "freimarken.stammdaten"
AKTION_TEXTE = "freimarken.texte"
# Die drei Spieldaten-Taten. Sie beginnen alle mit "freimarken.", und das ist
# tragend: protokollzeilen filtert auf diese Vorsilbe, also erscheinen sie auf der
# Protokollseite von selbst - und das allgemeine Audit-Log bleibt Super-Admin-Sache.
AKTION_BESITZ = "freimarken.besitz"
AKTION_KONTO = "freimarken.konto"
AKTION_MITGLIED = "freimarken.mitglied"

# Woran die Protokollzeile ihre beiden Haelften trennt. EINE Konstante, weil sie beim
# Schreiben UND beim Lesen gebraucht wird: Stuende der Text an zwei Stellen, liesse eine
# Aenderung die Protokollseite stumm bei "Grund: -" stehen.
TRENNER_GRUND = " | Grund: "

OHNE_GRUND = "(kein Grund angegeben)"


def protokoll_text(aenderungen: list[dict[str, Any]], grund: str) -> str:
    """Die Protokollzeile: was von was auf was, und warum.

    Plan 12a.2 Regel 3: Bei Geld und Besitz ist das keine Formalie - es ist der
    Unterschied zwischen "der Admin hat es korrigiert" und "mir fehlen 50.000 Mark".
    """
    teile = [e["pfad"] + ": " + e["alt"] + " -> " + e["neu"] for e in aenderungen]
    satz = "; ".join(teile)
    return satz + TRENNER_GRUND + (grund.strip() or OHNE_GRUND)


def zerlege_protokoll(details: str | None) -> dict[str, Any]:
    """Eine Protokollzeile zum Anzeigen zerlegt: die Aenderungen und der Grund.

    Der Text im Protokoll ist Freitext (die Spalte ist Text, nicht JSON) - nicht aus
    Bequemlichkeit, sondern weil das Protokoll des Managers fuer alle Bereiche so
    aussieht und eine Zeile auch dann noch lesbar sein soll, wenn dieses Modul es nicht
    mehr gibt.

    Was sich nicht zerlegen laesst, wird GANZ gezeigt und nicht verschluckt. Eine Zeile,
    die ein aelterer Stand anders geschrieben hat, ist kein Grund, sie zu verschweigen -
    bei Geld und Besitz ist eine unleserliche Zeile besser als eine fehlende.
    """
    roh = (details or "").strip()
    if not roh:
        # Mit allen Schluesseln, die der normale Weg auch liefert. Fehlte "unlesbar", liefe
        # die Vorlage beim Durchlaufen gegen ein Undefined - eine leere Protokollzeile
        # haette die ganze Seite zerlegt.
        return {"aenderungen": [], "grund": "", "unlesbar": [], "roh": ""}

    vorn, trenner, hinten = roh.partition(TRENNER_GRUND)
    grund = hinten.strip() if trenner else ""
    aenderungen: list[dict[str, str]] = []
    unlesbar: list[str] = []
    for stueck in vorn.split("; "):
        stueck = stueck.strip()
        if not stueck:
            continue
        pfad, doppelpunkt, rest = stueck.partition(": ")
        davor, pfeil, danach = rest.partition(" -> ")
        if doppelpunkt and pfeil:
            aenderungen.append({"pfad": pfad, "alt": davor.strip(),
                                "neu": danach.strip()})
        else:
            unlesbar.append(stueck)

    return {"aenderungen": aenderungen, "grund": grund,
            "unlesbar": unlesbar, "roh": roh}


def protokollzeilen(db: Any, server_id: int, *, grenze: int = 200) -> list[dict[str, Any]]:
    """Was ueber dieses Dashboard an diesem Server geaendert wurde - neueste zuerst.

    Eingeschraenkt auf diesen Server UND auf die Aktionen dieses Bereichs. Das
    allgemeine Protokoll des Managers ist Super-Admin-Sache, weil darin alles steht -
    Anmeldungen, Dateizugriffe, Sicherheitsereignisse. Diese Ansicht zeigt nur, welche
    EINSTELLUNG dieses Servers wer wann warum geaendert hat, und ist damit genau das, was
    Plan 12a.4 ab Moderator lesbar nennt.
    """
    from sqlalchemy import select
    from sqlalchemy.orm import joinedload

    from app.models.audit_log import AuditLog

    abfrage = (select(AuditLog)
               .options(joinedload(AuditLog.user))
               .where(AuditLog.server_id == server_id,
                      AuditLog.action.like("freimarken.%"))
               .order_by(AuditLog.id.desc())
               .limit(max(1, min(int(grenze), 1000))))

    raus: list[dict[str, Any]] = []
    for eintrag in db.scalars(abfrage).all():
        zerlegt = zerlege_protokoll(eintrag.details)
        raus.append({
            "zeit": eintrag.created_at,
            # Der Nutzer kann geloescht sein - die Spalte wird dann auf NULL gesetzt. Dass
            # jemand es getan hat, bleibt trotzdem wahr und wird nicht verschwiegen.
            "nutzer": eintrag.user.username if eintrag.user else "(geloeschter Nutzer)",
            "aktion": eintrag.action,
            **zerlegt,
        })
    return raus


# ------------------------------------------------------------------- Die Texte

# Ein Platzhalter, den das Programm fuellt: {spieler}, {betrag}, {anzahl}.
MUSTER_PLATZHALTER = re.compile(r"\{([a-z0-9_-]+)\}")

# Ein MiniMessage-Tag. Gezaehlt werden nur die FARBROLLEN; die acht stehen im Abschnitt
# farben: derselben Datei. Alles andere kann ein escapter Angabeplatz in einem
# Befehlshinweis sein - gemessen: 8 der 24 Tagnamen der Vorlage sind genau das, etwa
# "/laden mieten \<name>" - oder ein gewoehnliches MiniMessage-Tag wie <bold>. Daraus
# einen Fehler zu machen hiesse, dem Betreiber Dinge zu verbieten, die gehen.
MUSTER_TAG = re.compile(r"<(/?)([a-z0-9_-]+)>")


def _wirksame_saetze(server: Server) -> tuple[dict[str, dict], dict[str, dict], list[str]]:
    """``(vorlage, eigene, farbrollen)`` - was gilt, was der Betreiber geaendert hat.

    Die Vorlage ist die Wahrheit ueber das, was es GIBT; die Betreiberdatei die ueber
    das, was davon abweicht. Fehlt die Betreiberdatei, ist das kein Fehler: Das Plugin
    legt sie beim ersten Start an, und bis dahin gilt ueberall die Vorlage.
    """
    vorlagentext = lies_datei(server, PFAD_VORLAGE)
    vorlage = ya.felder_nach_pfad(vorlagentext)

    eigene: dict[str, dict] = {}
    try:
        eigene = ya.felder_nach_pfad(lies_datei(server, PFAD_SPRACHE))
    except (OSError, ValueError):
        pass   # noch nicht angelegt - dann gilt ueberall die Vorlage
    except (yaml.YAMLError, RecursionError) as fehler:
        kurz = str(fehler).splitlines()[0] if str(fehler) else "unbekannt"
        raise FreimarkenFehler(
            "Die " + PFAD_SPRACHE + " dieses Servers ist zurzeit kein gueltiges YAML ("
            + kurz + "). Solange das so ist, wird hier nichts angezeigt und nichts "
            "geschrieben - zu berichtigen ist sie im Dateieditor.") from fehler

    rollen = sorted(pfad.split(".")[-1] for pfad in vorlage
                    if pfad.startswith(ABSCHNITT_FARBEN + "."))
    return vorlage, eigene, rollen


def sprachabschnitte(server: Server) -> list[dict[str, Any]]:
    """Die Abschnitte der Sprachdatei mit ihrer Satzzahl - 770 Saetze auf einer Seite
    waeren keine Oberflaeche, sondern eine Halde."""
    vorlage, eigene, _rollen = _wirksame_saetze(server)
    zaehler: dict[str, dict[str, int]] = {}
    for pfad in vorlage:
        kopf = pfad.split(".")[0]
        eintrag = zaehler.setdefault(kopf, {"saetze": 0, "eigene": 0})
        eintrag["saetze"] += 1
        if pfad in eigene:
            eintrag["eigene"] += 1
    return [{"name": kopf, **werte} for kopf, werte in zaehler.items()]


def sprachfelder(server: Server, abschnitt: str) -> dict[str, Any]:
    """Die Saetze eines Abschnitts: was gilt, woher es kommt, was es verlangt."""
    vorlagentext = lies_datei(server, PFAD_VORLAGE)
    zeilen = vorlagentext.splitlines()
    vorlage, eigene, rollen = _wirksame_saetze(server)

    felder: list[dict[str, Any]] = []
    for pfad, feld in vorlage.items():
        if pfad.split(".")[0] != abschnitt:
            continue
        eigen = eigene.get(pfad)
        gilt = str(eigen["wert"]) if eigen else str(feld["wert"])
        felder.append({
            "pfad": pfad,
            "name": pfad[len(abschnitt) + 1:] if pfad.startswith(abschnitt + ".") else pfad,
            "wert": gilt,
            "vorgabe": str(feld["wert"]),
            # Die tragende Unterscheidung dieser Seite: Steht der Satz in der Datei des
            # Betreibers, oder kommt er aus dem Plugin? Nur der erste laesst sich
            # zuruecknehmen.
            "eigen": eigen is not None,
            "erklaerung": _kommentar_ueber(zeilen, feld["zeile"]),
            "platzhalter": sorted(set(MUSTER_PLATZHALTER.findall(gilt))),
            "ist_farbe": pfad.startswith(ABSCHNITT_FARBEN + "."),
        })

    return {"abschnitt": abschnitt, "felder": felder, "farbrollen": rollen,
            "eigene_gesamt": sum(1 for f in felder if f["eigen"])}


def pruefe_satz(alt: str, neu: str, rollen: list[str]) -> list[str]:
    """Was an diesem Wortlaut auffaellt - als Hinweis, nicht als Verbot.

    Zwei Dinge werden geprueft, und BEIDE kommen ohne eine zweite Wahrheit aus:

    1. Welche {platzhalter} der alte Wortlaut hatte und der neue nicht mehr. Die Liste der
       erwarteten Namen steht im Plugin (``Sprachliste.fehlendeNamen``), und das Plugin
       lehnt einen Satz ohne sie beim Laden ab - der Manager muss sie also nicht kennen:
       Die Differenz zum bisherigen Wortlaut sagt dasselbe und veraltet nie.
    2. Ob die FARBROLLEN paarig sind. Die acht Namen stehen im Abschnitt farben: derselben
       Datei; gemessen an der Vorlage sind sie dort exakt paarig (339 mal
       <fliesstext> und 339 mal </fliesstext>). Andere Tags werden nicht gezaehlt.

    Verboten wird nichts. Geprueft wird beim Laden, und zwar vom Plugin - zwei Pruefungen
    fuer denselben Fall machen sich gegenseitig unpruefbar.
    """
    hinweise: list[str] = []

    verloren = (set(MUSTER_PLATZHALTER.findall(alt))
                - set(MUSTER_PLATZHALTER.findall(neu)))
    if verloren:
        hinweise.append(
            "Im bisherigen Wortlaut standen " + ", ".join(
                "{" + name + "}" for name in sorted(verloren))
            + " und im neuen nicht mehr. Wenn das Programm diese Werte fuer diesen Satz"
            " einsetzt, lehnt es den neuen Wortlaut beim Laden ab und benutzt weiter den"
            " alten.")

    offen: dict[str, int] = {}
    for schraegstrich, name in MUSTER_TAG.findall(neu):
        if name not in rollen:
            continue
        offen[name] = offen.get(name, 0) + (-1 if schraegstrich else 1)
    schief = sorted(name for name, zahl in offen.items() if zahl != 0)
    if schief:
        hinweise.append(
            "Diese Farbrollen sind nicht paarig: " + ", ".join(
                "<" + name + ">" for name in schief)
            + ". Jede geoeffnete Rolle braucht ihr </" + schief[0] + ">; sonst faerbt sie"
            " den Rest der Zeile mit.")

    return hinweise


def _ist_flach(feld: dict) -> bool:
    """Ob dieser Eintrag als EINE Zeile mit Punktschluessel dasteht.

    Gemessen am 22.11.2026 an einem laufenden Server: Das Plugin baut den Pfad aus der
    Verschachtelung, und darum wirkt ``mark.verlauf.kopf: "..."`` auf oberster Ebene
    genauso wie der dreifach eingerueckte Block - die Startmeldung nannte beide Male
    "deine: mark.verlauf.kopf".

    Der Unterschied steckt in der Pfadliste: flach ist sie EIN Stueck mit Punkten darin,
    geschachtelt sind es drei Stuecke. Daran haengt, ob sich die Zeile wieder entfernen
    laesst, ohne einen leeren Block zu hinterlassen.
    """
    return len(feld.get("pfad") or []) == 1 and "." in str((feld["pfad"] or [""])[0])


def speichere_saetze(server: Server, werte: dict[str, str]) -> dict[str, Any]:
    """Schreibt Wortlaute in die Datei des Betreibers - und nur dorthin.

    Drei Faelle je Satz:
      * gleich der geltenden Fassung  -> nichts zu tun
      * Pfad steht schon in der Datei -> der vorhandene Wert wird ersetzt
      * Pfad steht noch nicht darin   -> eine flache Zeile wird ANGEHAENGT

    Die Vorlage wird nie beschrieben. Sie entsteht bei jedem Start neu; eine Aenderung
    darin waere beim naechsten Start weg, und der Betreiber haette sie fuer gueltig
    gehalten.
    """
    vorlage, eigene, rollen = _wirksame_saetze(server)

    try:
        text = lies_datei(server, PFAD_SPRACHE)
    except (OSError, ValueError):
        # Noch nicht angelegt. Das Plugin tut es beim ersten Start; bis dahin legt der
        # Manager sie an, damit der Betreiber nicht erst starten muss.
        text = ""

    ersetzen: dict[str, str] = {}
    meta: list[dict[str, Any]] = []
    anhaengen: list[tuple[str, str]] = []
    unbekannt: list[str] = []
    hinweise: list[str] = []
    geaendert: list[dict[str, str]] = []

    for pfad, roh in werte.items():
        if pfad not in vorlage:
            unbekannt.append(pfad)
            continue
        neu = str(roh)
        eigen = eigene.get(pfad)
        gilt = str(eigen["wert"]) if eigen else str(vorlage[pfad]["wert"])
        if neu == gilt:
            continue

        for satz in pruefe_satz(gilt, neu, rollen):
            hinweise.append(pfad + ": " + satz)
        geaendert.append({"pfad": pfad, "alt": gilt, "neu": neu})

        if eigen is not None:
            ersetzen[pfad] = neu
            meta.append({"key": pfad, "pfad": eigen["pfad"], "art": "text",
                         "stil": eigen["stil"], "wert": gilt, "roh": eigen["roh"]})
        else:
            anhaengen.append((pfad, neu))

    if not geaendert:
        return {"geschrieben": False, "geaendert": [], "unbekannt": unbekannt,
                "hinweise": hinweise}

    neuer_text = text
    if ersetzen:
        try:
            neuer_text = ya.schreibe(neuer_text, ersetzen, json.dumps(meta))
        except ya.YamlAssistentFehler as fehler:
            raise FreimarkenFehler(str(fehler)) from fehler

    if anhaengen:
        if neuer_text and not neuer_text.endswith(("\n", "\r")):
            neuer_text += "\n"
        for pfad, neu in anhaengen:
            # Immer in doppelte Anfuehrungszeichen: Ein Wortlaut beginnt oft mit einem
            # spitzen Tag (<kopf>...), und YAML liest ein nicht quotiertes > als
            # Blockskalar. Die Flucht ist dieselbe, die YAML selbst verlangt.
            sicher = neu.replace("\\", "\\\\").replace('"', '\\"')
            neuer_text += pfad + ': "' + sicher + '"\n'

    if neuer_text != text:
        schreib_datei(server, PFAD_SPRACHE, neuer_text)

    return {"geschrieben": neuer_text != text, "geaendert": geaendert,
            "unbekannt": unbekannt, "hinweise": hinweise,
            "angelegt": [pfad for pfad, _neu in anhaengen]}


def nimm_satz_zurueck(server: Server, pfad: str) -> dict[str, Any]:
    """Entfernt die Ueberschreibung eines Satzes - danach gilt wieder die Vorlage.

    Nur fuer Zeilen in flacher Form, und das ist keine Bequemlichkeit: Aus einem
    verschachtelten Block die eine Zeile zu nehmen hinterliesse ``verlauf:`` ohne Kind,
    und das ist in YAML nicht nichts, sondern der Wert null - das Plugin klagte dann
    ueber einen Satz, der kein Text ist. Solche Eintraege hat ein Mensch von Hand
    geschrieben; er soll sie von Hand entfernen, und der Hinweis sagt wo.
    """
    text = lies_datei(server, PFAD_SPRACHE)
    eigene = ya.felder_nach_pfad(text)
    feld = eigene.get(pfad)
    if feld is None:
        raise FreimarkenFehler(
            "Dieser Satz steht nicht in " + PFAD_SPRACHE + " - es gilt also schon der "
            "ausgelieferte Wortlaut.")
    if not _ist_flach(feld):
        raise FreimarkenFehler(
            "Dieser Satz steht in " + PFAD_SPRACHE + " als eingerueckter Block (Zeile "
            + str(feld["zeile"] + 1) + "). Ihn hier herauszunehmen liesse einen leeren "
            "Block zurueck, und ein Schluessel ohne Wert ist fuer das Plugin ein Satz, "
            "der kein Text ist. Entferne die Zeile im Dateieditor.")

    zeilen = text.splitlines(keepends=True)
    stelle = feld["zeile"]
    if stelle >= len(zeilen):
        raise FreimarkenFehler("Die Datei hat sich inzwischen geaendert - bitte neu laden.")
    # Gegenprobe: In der Zeile, die entfernt wird, muss der Schluessel auch stehen.
    if not zeilen[stelle].lstrip().startswith(pfad):
        raise FreimarkenFehler(
            "Die Zeile zu " + pfad + " steht nicht mehr dort, wo sie beim Oeffnen stand "
            "- bitte neu laden, damit nichts Falsches entfernt wird.")

    del zeilen[stelle]
    schreib_datei(server, PFAD_SPRACHE, "".join(zeilen))
    return {"pfad": pfad, "entfernt": True}
