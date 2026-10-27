"""Log- und Crash-Analyse: die Ursache benennen statt den Log vorzulesen.

Ein Serverlog hat bei einem Absturz schnell 50.000 Zeilen, von denen 200 zaehlen. Diese
Funktion schickt deshalb nicht das ganze Log, sondern den Teil, der die Ursache enthaelt -
das spart Token (und damit Guthaben) und macht die Antwort praeziser.

Ausgewaehlt wird nach dem Aufbau eines Minecraft-Logs:

* Der **Kopf** nennt Server-Software, Version, Java und die geladenen Mods/Plugins. Ohne
  ihn kann die KI nicht sagen, WELCHE Mod schuld ist.
* Das **Ende** enthaelt den Absturz. Ein Java-Stacktrace gehoert zusammen und wird komplett
  mitgenommen, nicht in der Mitte abgeschnitten.
* **Fehlerzeilen** dazwischen (ERROR, SEVERE, Exception, Caused by) kommen mit ihrem
  Umfeld dazu, denn die eigentliche Ursache steht oft weit vor dem letzten Fehler.

Der ausgewaehlte Text geht durch ``ai_privacy_service``, bevor er das Haus verlaesst.
"""

from __future__ import annotations

import re

from sqlalchemy.orm import Session

from app.models.server import Server
from app.models.user import User
from app.services import ai_service

# Zeilen, die auf das eigentliche Problem zeigen. Zwei Ausdruecke, weil die
# Gross-/Kleinschreibung unterschiedlich zaehlt:
#
# Schluesselwoerter sollen in jeder Schreibweise greifen ("ERROR", "error", "Failed").
_FEHLER_WORT = re.compile(
    r"(?i)\b(?:ERROR|SEVERE|FATAL|crash|failed|could not|unable to|missing|"
    r"incompatible|watchdog|did the system time change|caused by)\b")

# Java-Klassennamen dagegen GROSS geschrieben - und ohne \b am Anfang. "\bException\b"
# findet "NullPointerException" NICHT, weil vor dem E kein Wortanfang steht: zwischen "r"
# und "E" sind beide Zeichen Wortzeichen. Genau diese Zeile ist aber die wichtigste im
# ganzen Log. Die Gross-/Kleinschreibung bleibt hier bedeutsam, damit harmlose Woerter,
# die "error" enthalten (z.B. "terror", "mirror"), keine Fehlerstelle vortaeuschen.
_FEHLER_KLASSE = re.compile(r"\b[\w$.]*(?:Exception|Error|Throwable)\b")

# Fortsetzungszeilen eines Java-Stacktrace.
_STACK = re.compile(r"^\s+(?:at\s|\.\.\.\s*\d+\s+more|Caused by:|Suppressed:)")

# Die Kopfzeile einer Ausnahme. Sie steht meist NACH der Meldung und ist nicht eingerueckt:
#   [11:00:00] [Server/ERROR]: Exception ticking entity
#   java.lang.NullPointerException: Cannot invoke getOwner()
#           at net.example...
# Ohne diese Zeile als Teil des Stacktrace bricht der Lauf sofort ab und der eigentliche
# Stacktrace - der die schuldige Klasse nennt - bleibt draussen.
_AUSNAHME_KOPF = re.compile(
    r"^\s*(?:Caused by:\s*|Suppressed:\s*)?[\w$.]*(?:Exception|Error|Throwable)\b")


def _ist_fehler(zeile: str) -> bool:
    return bool(_FEHLER_WORT.search(zeile) or _FEHLER_KLASSE.search(zeile))


def _gehoert_zum_stacktrace(zeile: str) -> bool:
    return bool(_STACK.match(zeile) or _AUSNAHME_KOPF.match(zeile))


# Ein Stacktrace ist tief, aber nicht endlos. Die Grenze verhindert, dass ein Log mit einer
# langen Leerzeilenstrecke hinter einem Fehler die ganze Zeilengrenze auffrisst - dann
# bliebe fuer Kopf und Ende nichts uebrig.
STACK_MAX = 400

# Zeilen aus dem Kopf, die der Diagnose dienen: Software, Version, Java, geladene Inhalte.
_KOPF = re.compile(
    r"(?i)(?:starting minecraft server|this server is running|java version|"
    r"loading \d+ mods|preparing level|default game type|loaded \d+ recipes|"
    r"enabling |loading [A-Za-z0-9_-]+ v|\[main/|system info|operating system|"
    r"memory:|jvm |max memory)")

KOPF_ZEILEN = 120
ENDE_ZEILEN = 250
UMFELD = 6

# Zeichengrenze fuer den Ausschnitt. Sie liegt unter der Grenze von
# ai_privacy_service.kuerze_fuer_anfrage (60.000): sonst schneidet die danach blind die
# MITTE heraus - und damit genau die Fehlerstellen, die hier mit Aufwand gesucht wurden.
MAX_ZEICHEN = 48_000


def _fehler_indizes(zeilen: list[str]) -> list[int]:
    return [i for i, z in enumerate(zeilen) if _ist_fehler(z)]


def _kopf_kandidaten(zeilen: list[str], kopf_bereich: int) -> list[int]:
    """Die Zeilen am Anfang, die eine Diagnose tragen: Software, Version, Java, Mods."""
    treffer = [i for i in range(kopf_bereich) if _KOPF.search(zeilen[i])]
    if len(treffer) >= 10:
        return treffer[:KOPF_ZEILEN]
    # Notfalls der Anfang - aber ohne Leerzeilen, die helfen bei keiner Diagnose.
    return [i for i in range(min(40, len(zeilen))) if zeilen[i].strip()]


def _mitte_kandidaten(zeilen: list[str], fehler: list[int]) -> list[int]:
    """Fehlerstellen mit Umfeld und vollstaendigem Stacktrace.

    Der Nachlauf merkt sich, wie weit er schon gelaufen ist. Ohne das wird bei einem Log,
    dessen Zeilen gleichzeitig Fehler- UND Stacktrace-Zeilen sind, derselbe Abschnitt
    hunderte Male durchlaufen.
    """
    gesamt = len(zeilen)
    gewaehlt: set[int] = set()
    bis_gelaufen = -1
    for i in fehler:
        gewaehlt.update(range(max(0, i - UMFELD), min(gesamt, i + UMFELD + 1)))
        j = max(i + 1, bis_gelaufen + 1)
        leer_hintereinander = 0
        while j < gesamt and j - i <= STACK_MAX:
            if _gehoert_zum_stacktrace(zeilen[j]):
                leer_hintereinander = 0
            elif not zeilen[j].strip() and leer_hintereinander < 2:
                # Eine einzelne Leerzeile mitten im Stacktrace darf ihn nicht beenden,
                # eine lange Leerstrecke beendet ihn aber sehr wohl.
                leer_hintereinander += 1
            else:
                break
            gewaehlt.add(j)
            j += 1
        bis_gelaufen = max(bis_gelaufen, j - 1)
    return sorted(gewaehlt)


def waehle_ausschnitt(zeilen: list[str], *, max_zeilen: int = 600,
                      max_zeichen: int = MAX_ZEICHEN) -> tuple[str, dict]:
    """Die Zeilen auswaehlen, die fuer eine Diagnose zaehlen.

    Rueckgabe: (Text, Angaben). Die Angaben beschreiben, was TATSAECHLICH im Text steht -
    sie werden dem Nutzer angezeigt, bevor er senden laesst, und muessen deshalb zum Text
    passen und nicht zu einem Zwischenstand.

    Die Auswahl laeuft nach festen Quoten, damit keine Gruppe die anderen verdraengt:

    * **Ende** (die letzten Zeilen) - dort steht, woran der Server gestorben ist.
    * **Kopf** (Software, Version, geladene Mods) - ohne ihn kann niemand sagen, WELCHE
      Mod schuld ist, und das ist die Frage.
    * **Mitte** (Fehlerstellen mit Stacktrace), die neuesten zuerst - die letzte
      Fehlerstelle vor dem Absturz sagt mehr als die erste.

    Bleibt nach den Quoten Platz, wird er wieder verteilt. Eine fruehere Fassung liess
    Zeilen, die beide Quoten verfehlten, stillschweigend wegfallen - mit freiem Budget.
    """
    gesamt = len(zeilen)
    if gesamt == 0:
        return "", {"gesamt": 0, "gesendet": 0, "vollstaendig": True,
                    "fehlerstellen": 0, "zeichen": 0}

    fehler = _fehler_indizes(zeilen)
    laenge_gesamt = sum(len(z) + 1 for z in zeilen)
    if gesamt <= max_zeilen and laenge_gesamt <= max_zeichen:
        text = "\n".join(zeilen)
        return text, {"gesamt": gesamt, "gesendet": gesamt, "vollstaendig": True,
                      "fehlerstellen": len(fehler), "zeichen": len(text)}

    kopf_bereich = min(KOPF_ZEILEN * 4, gesamt)
    ende_ab = max(0, gesamt - ENDE_ZEILEN)

    kopf_kand = [i for i in _kopf_kandidaten(zeilen, kopf_bereich) if i < ende_ab]
    ende_kand = list(range(ende_ab, gesamt))
    mitte_kand = [i for i in _mitte_kandidaten(zeilen, fehler)
                  if i < ende_ab and i not in set(kopf_kand)]

    # Quoten. Der Kopf ist klein und unverzichtbar, bekommt aber hoechstens ein Viertel -
    # sonst verdraengt er bei kleinem max_zeilen alles andere.
    kopf_platz = min(len(kopf_kand), KOPF_ZEILEN, max(5, max_zeilen // 4))
    gewaehlt_kopf = kopf_kand[:kopf_platz]
    rest = max(0, max_zeilen - len(gewaehlt_kopf))
    ende_platz = min(len(ende_kand), rest if not mitte_kand else max(1, rest * 2 // 3))
    gewaehlt_ende = ende_kand[len(ende_kand) - ende_platz:] if ende_platz else []
    rest -= len(gewaehlt_ende)
    gewaehlt_mitte = mitte_kand[len(mitte_kand) - rest:] if rest > 0 else []

    # Platz zurueckverteilen, solange noch Kandidaten uebrig sind.
    frei = max_zeilen - len(gewaehlt_kopf) - len(gewaehlt_ende) - len(gewaehlt_mitte)
    if frei > 0:
        for pool, gewaehlt in ((mitte_kand, gewaehlt_mitte), (ende_kand, gewaehlt_ende),
                              (kopf_kand, gewaehlt_kopf)):
            if frei <= 0:
                break
            offen = [i for i in pool if i not in set(gewaehlt)]
            nachgelegt = offen[len(offen) - min(frei, len(offen)):]
            gewaehlt.extend(nachgelegt)
            frei -= len(nachgelegt)

    # Immer noch Platz? Dann das Ende weiter zurueck ausdehnen. Zeilen zwischen Kopf und
    # Endfenster gehoeren zu keinem Kandidatenpool; sie einfach wegzulassen, obwohl
    # Budget frei ist, waere verschenkt - naeher am Absturz ist naeher an der Ursache.
    if frei > 0 and ende_ab > 0:
        zusatz = list(range(max(0, ende_ab - frei), ende_ab))
        gewaehlt_ende.extend(i for i in zusatz if i not in set(gewaehlt_kopf))

    # Zeichengrenze: zuerst die Mitte von vorne (aelteste Fehlerstellen), dann das Ende
    # von vorne, zuletzt der Kopf von hinten. Was wegfaellt, wird gezaehlt.
    reihen = {"mitte": sorted(set(gewaehlt_mitte)), "ende": sorted(set(gewaehlt_ende)),
              "kopf": sorted(set(gewaehlt_kopf))}

    def _zeichen(auswahl: set[int]) -> int:
        return sum(len(zeilen[i]) + 1 for i in auswahl)

    auswahl = set(reihen["mitte"]) | set(reihen["ende"]) | set(reihen["kopf"])
    for name, von_hinten in (("mitte", False), ("ende", False), ("kopf", True)):
        folge = reihen[name][::-1] if von_hinten else reihen[name]
        for i in folge:
            if _zeichen(auswahl) <= max_zeichen:
                break
            auswahl.discard(i)

    sortiert = sorted(auswahl)
    teile: list[str] = []
    vorher: int | None = None
    for i in sortiert:
        if vorher is not None and i > vorher + 1:
            teile.append(f"[... {i - vorher - 1} Zeilen ausgelassen ...]")
        teile.append(zeilen[i])
        vorher = i
    text = "\n".join(teile)

    # Die Zahl der Fehlerstellen zaehlt, was im AUSSCHNITT steht - nicht, was im ganzen
    # Log steht. Die frueher gemeldete Gesamtzahl liess glauben, die KI habe alle
    # Fehlerstellen gesehen, obwohl sie einen Bruchteil sah.
    return text, {
        "gesamt": gesamt,
        "gesendet": len(sortiert),
        "vollstaendig": False,
        "fehlerstellen": sum(1 for i in sortiert if _ist_fehler(zeilen[i])),
        "fehlerstellen_gesamt": len(fehler),
        "zeichen": len(text),
    }


def vorschau(zeilen: list[str], db, *, gesamt_im_log: int | None = None) -> dict:
    """Was wuerde gesendet? Fuer die Anzeige, bevor etwas das Haus verlaesst.

    ``gesamt_im_log`` ist die ECHTE Zahl der Zeilen in der Datei. Sie kann groesser sein
    als ``len(zeilen)``, weil fuer die Analyse nur Kopf und Ende eingelesen werden. Ohne
    sie wuerde die Seite "von 20000 Zeilen" behaupten, wo es 200000 sind.

    Die Zahlen beschreiben den TATSAECHLICH gesendeten Text, nach Auswahl und Saeuberung.
    Eine frueher gemeldete Zeichenzahl stammte aus der ungekuerzten Auswahl und lag bis um
    den Faktor hundert daneben - bei einer Anzeige, deren ganzer Zweck Verlaesslichkeit
    ist.
    """
    from app.services import ai_privacy_service, ai_settings_service

    ausschnitt, angaben = waehle_ausschnitt(zeilen)
    einstellungen = ai_settings_service.hole_einstellungen(db)
    sicht = ai_privacy_service.vorschau(
        ausschnitt, namen_ersetzen=bool(einstellungen.get("namen_ersetzen")))
    if gesamt_im_log is not None and gesamt_im_log > angaben["gesamt"]:
        angaben = {**angaben, "gesamt": int(gesamt_im_log),
                   "nur_kopf_und_ende_gelesen": True,
                   "vollstaendig": False}
    sicht["auswahl"] = angaben
    return sicht


def analysiere(db: Session, *, user: User, server: Server, zeilen: list[str],
               frage: str = "", gesamt_im_log: int | None = None) -> dict:
    """Den Log analysieren lassen. Wirft KiAbgelehnt oder KiFehler."""
    ausschnitt, angaben = waehle_ausschnitt(zeilen)
    if not ausschnitt.strip():
        raise ai_service.KiAbgelehnt("Der Log ist leer - es gibt nichts zu analysieren.")

    if gesamt_im_log is not None and gesamt_im_log > angaben["gesamt"]:
        angaben = {**angaben, "gesamt": int(gesamt_im_log),
                   "nur_kopf_und_ende_gelesen": True, "vollstaendig": False}

    anliegen = (frage or "").strip() or (
        "Was ist die Ursache? Nenne die verantwortliche Mod, das Plugin oder die "
        "Einstellung und sage, was konkret zu tun ist.")

    ergebnis = ai_service.frage(
        db, user=user, funktion="crash_analyse", nachricht=anliegen,
        kontext=ausschnitt, server_id=server.id,
        zusatzauftrag=(
            f"Der Server ist {server.server_type} {server.mc_version}. "
            f"Beziehe dich auf Zeilen aus dem Log, wenn du eine Ursache nennst, damit sie "
            f"nachpruefbar ist. Steht die Ursache nicht im Ausschnitt, sage das - der Log "
            f"wurde gekuerzt."),
        max_tokens=2048)

    ergebnis["auswahl"] = angaben
    if not angaben["vollstaendig"]:
        text = (f"Vom Log wurden {angaben['gesendet']} von {angaben['gesamt']} Zeilen "
                f"gesendet: Kopf, {angaben['fehlerstellen']} Fehlerstellen mit Umfeld und "
                f"das Ende. Das spart Guthaben und macht die Antwort genauer.")
        # Wenn im Log mehr Fehlerstellen stehen als im Ausschnitt, muss das dastehen -
        # sonst haelt man eine Antwort fuer vollstaendig, die auf einem Bruchteil beruht.
        gesamt_fehler = angaben.get("fehlerstellen_gesamt")
        if gesamt_fehler and gesamt_fehler > angaben["fehlerstellen"]:
            text += (f" Im Log stehen {gesamt_fehler} Fehlerstellen; die KI hat "
                     f"{angaben['fehlerstellen']} davon gesehen.")
        if angaben.get("nur_kopf_und_ende_gelesen"):
            text += (" Der Log ist so lang, dass nur Kopf und Ende eingelesen wurden - "
                     "was in der Mitte stand, konnte nicht geprueft werden.")
        ergebnis.setdefault("hinweise", []).append(text)
    return ergebnis
