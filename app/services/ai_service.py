"""Der einzige Weg zu einem KI-Aufruf - hier laufen alle Schranken zusammen.

Keine Funktion ruft den Anbieter selbst auf. Alles geht durch ``frage()``, und zwar in
dieser Reihenfolge:

1. **Darf das sein?** Abschaltung, Rolle, Rate Limit, Monatsdeckel
   (``ai_budget_service.pruefe_erlaubt``). Scheitert das, kostet der Aufruf nichts.
2. **Gehoert das zum Thema?** Die Themen-Sperre prueft die Anfrage, bevor sie Geld kostet.
3. **Was geht hinaus?** Logs und Configs werden gesaeubert
   (``ai_privacy_service``), IP-Adressen immer, Spielernamen auf Wunsch.
4. **Aufruf** beim Anbieter mit dem fuer diese Funktion eingestellten Modell.
5. **Antwort pruefen** - haelt sie sich ans Thema, wurde sie abgeschnitten.
6. **Protokollieren** mit den echten Token-Zahlen des Anbieters.

Werkzeuge (der freie Agent) werden hier als Absicht zurueckgegeben, nicht ausgefuehrt:
Entscheidung des Nutzers ist "Vorschlagen, du bestaetigst". ``frage()`` liefert in
``werkzeug_aufrufe``, was die KI TUN will; ausgefuehrt wird das erst von der Oberflaeche,
nach einem Klick und durch dieselben Rechtepruefungen wie ein normaler Klick.
"""

from __future__ import annotations

import re

from sqlalchemy.orm import Session

from app.models.user import User
from app.services import ai_budget_service, ai_privacy_service, ai_settings_service
from app.services.ai_provider_service import (
    KiAnfrage,
    KiFehler,
    hole_anbieter,
    kosten_cent,
)

# Die Rolle, auf die der Manager die KI festlegt. Steht in jedem Aufruf im Systemauftrag -
# das ist die eine Haelfte der Themen-Sperre.
GRUNDAUFTRAG = """Du bist der Assistent eines Minecraft-Server-Managers. Du hilfst beim
Betrieb von Minecraft-Servern: Absturzursachen finden, Logs deuten, Konfiguration und
JVM-Einstellungen beurteilen, Mods und Plugins einschaetzen, Welten und Backups verwalten.

Regeln:
- Antworte auf Deutsch, sachlich und knapp. Nenne konkrete Dateien, Werte und Schritte.
- Wenn du etwas nicht aus den mitgelieferten Daten belegen kannst, sage das. Rate nicht.
- Fragen ausserhalb der Serververwaltung beantwortest du nicht. Antworte dann genau mit:
  THEMA_ABGELEHNT
- Du aenderst nichts selbst. Wenn eine Aenderung noetig ist, beschreibe sie so genau, dass
  der Mensch sie bestaetigen kann."""

ABLEHNUNG = "THEMA_ABGELEHNT"

# Woran der Vorfilter einen Serverbezug erkennt. Bewusst breit: ein Fehlalarm aergert mehr
# als eine durchgelassene Frage, die das Modell dann selbst ablehnt.
_THEMENWOERTER = re.compile(
    r"(?i)\b(?:server|minecraft|mod|mods|modpack|plugin|plugins|welt|world|nether|end|"
    r"spigot|paper|bukkit|purpur|folia|forge|neoforge|fabric|vanilla|velocity|bungee|"
    r"crash|absturz|fehler|exception|log|logs|konsole|console|tick|lag|ram|speicher|"
    r"jvm|java|heap|gc|port|whitelist|operator|op|ban|backup|sicherung|datapack|"
    r"config|konfiguration|properties|yml|yaml|toml|spieler|player|chunk|region|"
    r"seed|dimension|portal|respawn|inventar|uuid|rcon|proxy|lobby|gateway|"
    r"start|stop|neustart|restart|update|version|performance|tps)\b")


class KiAbgelehnt(Exception):
    """Die Anfrage wurde abgelehnt - Thema, Rechte, Budget oder Rate Limit."""


def _saeubere_block(block: dict, namen_ersetzen: bool) -> dict:
    """Einen Inhaltsblock saeubern, ohne seine Struktur zu veraendern.

    ``tool_use`` stammt vom Modell selbst und geht unveraendert zurueck - er enthaelt nur
    das, was das Modell ohnehin schon formuliert hat. ``tool_result`` dagegen ist eine
    Antwort DES MANAGERS mit echten Serverdaten und wird gesaeubert wie jeder andere
    Kontext.
    """
    if not isinstance(block, dict):
        return {"type": "text", "text": str(block)}
    if block.get("type") != "tool_result":
        return block
    roh = block.get("content")
    text = roh if isinstance(roh, str) else str(roh or "")
    sauber, _ = ai_privacy_service.saeubere(text, namen_ersetzen=namen_ersetzen)
    sauber, _ = ai_privacy_service.kuerze_fuer_anfrage(sauber, max_zeichen=12_000)
    neu = dict(block)
    neu["content"] = sauber
    return neu


def _thema_passt(text: str) -> bool:
    """Grober Vorfilter: hat die Anfrage ueberhaupt Serverbezug?

    Laeuft VOR dem Aufruf, damit eine themenfremde Frage kein Geld kostet. Bewusst
    grosszuegig - wer einen Log mitschickt, kommt ohnehin durch (siehe frage()).
    """
    return bool(_THEMENWOERTER.search(text or ""))


def _baue_auftrag(funktion: str, zusatz: str = "") -> str:
    besonders = {
        "crash_analyse": "Deine Aufgabe: die Ursache des Absturzes benennen und sagen, "
                         "welche Mod oder Einstellung dahintersteckt und was zu tun ist.",
        "config_assistent": "Deine Aufgabe: die Konfiguration beurteilen und konkrete "
                            "Werte vorschlagen. Nenne immer Schluessel und Wert.",
        "changelog": "Deine Aufgabe: knapp zusammenfassen, was sich aendert, und "
                     "ausdruecklich sagen, ob es Breaking Changes gibt.",
        "agent": "Du kannst Aktionen VORSCHLAGEN. Ausgefuehrt werden sie erst, wenn der "
                 "Mensch zustimmt - beschreibe also genau, was passieren soll und warum.",
    }
    teile = [GRUNDAUFTRAG]
    if funktion in besonders:
        teile.append(besonders[funktion])
    if zusatz.strip():
        teile.append(zusatz.strip())
    return "\n\n".join(teile)


# Wie lang die FRAGE des Nutzers hoechstens sein darf. Nicht aus Bequemlichkeit, sondern
# weil die Eingabe bezahlt wird: 400 KB Frage sind rund 100.000 Token und damit ein
# Vielfaches dessen, was eine Analyse kosten soll. Die mitgelieferten Daten haben ihre
# eigene, groessere Grenze (kuerze_fuer_anfrage) - dort ist viel Text der Zweck.
MAX_FRAGE_ZEICHEN = 8_000

THEMA_FREMD = ("Die Anfrage hat keinen erkennbaren Bezug zur Serververwaltung. Die "
               "KI-Funktionen sind auf dieses Thema beschraenkt.")


def pruefe_thema(db: Session, *, user: User, nachricht: str,
                 verlauf: list[dict] | None = None,
                 einstellungen: dict | None = None) -> None:
    """Den Wortfilter von aussen anwenden. Wirft KiAbgelehnt.

    Fuer Aufrufer, die mehrere Anfragen zu EINEM Auftrag stellen (der freie Agent): sie
    pruefen die Frage des Nutzers einmal und setzen danach ``thema_geprueft=True``, damit
    ihre internen Folgefragen nicht an einem fehlenden Schluesselwort scheitern.

    Geprueft wird das GESPRAECH, nicht nur der letzte Satz. In einer laufenden
    Unterhaltung sind Rueckfragen kurz und ohne Schluesselwort ("und jetzt der andere?",
    "warum?"); am Wortfilter zu scheitern waere dort unverstaendlich. Das Thema ist
    gesetzt, sobald es in diesem Gespraech einmal genannt wurde.
    """
    werte = einstellungen if einstellungen is not None else \
        ai_settings_service.hole_einstellungen(db)
    if not werte.get("themensperre", True):
        return
    teile = [nachricht, *(str(z.get("inhalt") or "") for z in (verlauf or [])
                          if isinstance(z.get("inhalt"), str))]
    if _thema_passt(" ".join(teile)):
        return
    ai_budget_service.protokolliere(
        db, user_id=getattr(user, "id", None), server_id=None, funktion="agent",
        modell="-", status="abgelehnt", hinweis=THEMA_FREMD)
    raise KiAbgelehnt(THEMA_FREMD)


def _baue_nachrichten(verlauf: list[dict], inhalt: str) -> list[dict]:
    """Verlauf und neue Nachricht zu einer gueltigen Folge zusammensetzen.

    Der Anbieter verlangt abwechselnde Rollen. Endet der Verlauf mit einem Nutzerzug aus
    Inhaltsbloecken - so sieht ein zurueckgespieltes Werkzeugergebnis aus -, waere die
    neue Nachricht ein ZWEITER Nutzerzug und die Anfrage wuerde abgewiesen. Dann gehoert
    der Text als weiterer Block in denselben Zug.
    """
    nachrichten = list(verlauf)
    text = (inhalt or "").strip()
    if not nachrichten:
        return [{"rolle": "nutzer", "inhalt": text}]

    letzter = nachrichten[-1]
    if letzter["rolle"] == "nutzer" and isinstance(letzter.get("inhalt"), list):
        if text:
            nachrichten[-1] = {"rolle": "nutzer",
                               "inhalt": [*letzter["inhalt"], {"type": "text",
                                                               "text": text}]}
        return nachrichten
    if letzter["rolle"] == "nutzer" and isinstance(letzter.get("inhalt"), str):
        nachrichten[-1] = {"rolle": "nutzer",
                           "inhalt": (letzter["inhalt"] + "\n\n" + text).strip()}
        return nachrichten
    return [*nachrichten, {"rolle": "nutzer", "inhalt": text}]


def frage(db: Session, *, user: User, funktion: str, nachricht: str,
          kontext: str = "", server_id: int | None = None,
          zusatzauftrag: str = "", werkzeuge: list[dict] | None = None,
          werkzeugzwang: str | None = None, verlauf: list[dict] | None = None,
          thema_geprueft: bool = False, max_tokens: int = 2048) -> dict:
    """Eine KI-Anfrage stellen. Wirft KiAbgelehnt oder KiFehler.

    ``kontext`` sind die mitgeschickten Daten (Logauszug, Config) - sie werden gesaeubert.
    ``nachricht`` ist die Frage des Nutzers.
    ``verlauf`` sind fruehere Zuege desselben Gespraechs, aelteste zuerst, je Eintrag
    {"rolle": "nutzer"|"assistent", "inhalt": "..."}. Ohne ihn waere jede Frage die erste,
    und ein Agent koennte sich nicht auf "mach das fuer den Server von vorhin" beziehen.
    Der Verlauf wird genauso gesaeubert wie der Kontext - er kommt aus dem Browser und
    kann denselben Inhalt tragen.
    """
    einstellungen = ai_settings_service.hole_einstellungen(db)

    # 1. Schranken, die nichts kosten.
    try:
        ai_budget_service.pruefe_erlaubt(db, user=user, funktion=funktion,
                                         einstellungen=einstellungen)
    except ai_budget_service.BudgetGesperrt as exc:
        ai_budget_service.protokolliere(
            db, user_id=getattr(user, "id", None), server_id=server_id,
            funktion=funktion, modell="-", status="abgelehnt", hinweis=str(exc))
        raise KiAbgelehnt(str(exc)) from exc

    if not einstellungen["schluessel_hinterlegt"]:
        raise KiAbgelehnt("Es ist kein API-Schluessel hinterlegt. Einstellungen -> KI.")

    # 2. Themen-Sperre auf die Eingabe.
    #    Der Wortfilter gilt nur fuer den FREIEN Agenten: dort wird beliebig gefragt, also
    #    muss der Bezug aus der Frage selbst kommen. Bei den gebundenen Funktionen
    #    (Crash-Analyse, Config, Changelog) steht der Zweck schon durch den Aufruf fest -
    #    "Was aendert sich?" ist dort eine voellig normale Frage und darf nicht an einem
    #    fehlenden Schluesselwort scheitern.
    #
    #    ``thema_geprueft`` setzt nur, wer den Wortfilter selbst schon angewandt hat.
    #    Der Agent tut das: er arbeitet in mehreren Runden, und seine internen
    #    Folgefragen ("mach weiter mit dem, was du jetzt weisst") tragen kein
    #    Schluesselwort. Wuerde hier jede Runde geprueft, braeche ab der zweiten jedes
    #    Gespraech ab - und wuerde die Pruefung stattdessen an einem mitgeschickten
    #    Kontext haengen, waere sie beim Agenten voellig aus, weil der immer die
    #    Lage-Uebersicht mitschickt. Deshalb: einmal pruefen, beim Aufrufer, an der
    #    echten Frage des Nutzers.
    if (einstellungen.get("themensperre", True) and funktion == "agent"
            and not thema_geprueft and not _thema_passt(nachricht)):
        ai_budget_service.protokolliere(
            db, user_id=getattr(user, "id", None), server_id=server_id,
            funktion=funktion, modell="-", status="abgelehnt", hinweis=THEMA_FREMD)
        raise KiAbgelehnt(THEMA_FREMD)

    # 3. Was hinausgeht, wird gesaeubert - ALLES, nicht nur die mitgelieferten Daten.
    #
    #    Die Frage selbst wurde hier lange unveraendert weitergegeben. Das war falsch in
    #    zwei Richtungen: wer eine Fehlermeldung oder Configzeile in das Fragefeld
    #    kopiert - der naheliegendste Gebrauch -, haette damit IP-Adressen und
    #    Geheimnisse ungesaeubert hinausgeschickt, obwohl die Zusage lautet, dass sie
    #    entfernt werden. Und sie war unbegrenzt: eine 12-MB-Frage ging vollstaendig an
    #    den Anbieter, waehrend alle Sparsamkeit nur fuer den Log galt. Ein solcher
    #    Aufruf kostet ein Vielfaches des Eingeplanten, und der Monatsdeckel prueft nur
    #    den VERGANGENEN Verbrauch - er kann einen einzelnen grossen Aufruf nicht
    #    aufhalten.
    namen_ersetzen = bool(einstellungen.get("namen_ersetzen"))
    nachricht, frage_gekuerzt = ai_privacy_service.kuerze_fuer_anfrage(
        nachricht or "", max_zeichen=MAX_FRAGE_ZEICHEN)
    nachricht, frage_bericht = ai_privacy_service.saeubere(
        nachricht, namen_ersetzen=namen_ersetzen)

    gesaeubert, bericht = ai_privacy_service.saeubere(kontext,
                                                      namen_ersetzen=namen_ersetzen)
    gesaeubert, gekuerzt = ai_privacy_service.kuerze_fuer_anfrage(gesaeubert)

    # Die Berichte zusammenfuehren, damit die Hinweise auch nennen, was aus der FRAGE
    # entfernt wurde - sonst bliebe das unsichtbar.
    for schluessel in ("ips", "geheimnisse"):
        bericht[schluessel] = int(bericht.get(schluessel) or 0) + int(
            frage_bericht.get(schluessel) or 0)
    if frage_bericht.get("namen"):
        zusammen = dict(bericht.get("namen") or {})
        zusammen.update(frage_bericht["namen"])
        bericht["namen"] = zusammen

    verlauf_rein: list[dict] = []
    for zug in (verlauf or []):
        if isinstance(zug.get("inhalt"), list):
            # Inhaltsbloecke eines Agenten-Zuges. Der Werkzeugaufruf selbst stammt vom
            # Modell und geht unveraendert zurueck; das ERGEBNIS kommt aus dem Manager und
            # enthaelt Serverdaten - es wird genauso gesaeubert wie alles andere, was
            # hinausgeht.
            verlauf_rein.append({
                "rolle": "assistent" if zug.get("rolle") == "assistent" else "nutzer",
                "inhalt": [_saeubere_block(b, namen_ersetzen) for b in zug["inhalt"]]})
            continue
        roh = str(zug.get("inhalt") or "").strip()
        if not roh:
            continue
        sauber, _ = ai_privacy_service.saeubere(roh, namen_ersetzen=namen_ersetzen)
        sauber, _ = ai_privacy_service.kuerze_fuer_anfrage(sauber, max_zeichen=8_000)
        rolle = "assistent" if zug.get("rolle") == "assistent" else "nutzer"
        # Der Anbieter verlangt abwechselnde Rollen, beginnend beim Nutzer. Der Verlauf
        # kommt aus dem Browser und kann beides verletzen - dann wuerde die Anfrage mit
        # einem Fehler abgewiesen, der nach einem Fehler des Managers aussieht. Also hier
        # geradeziehen: ein fuehrender Assistenten-Zug faellt weg, gleiche Rollen
        # hintereinander werden zusammengefasst.
        if not verlauf_rein and rolle == "assistent":
            continue
        if (verlauf_rein and verlauf_rein[-1]["rolle"] == rolle
                and isinstance(verlauf_rein[-1]["inhalt"], str)):
            verlauf_rein[-1]["inhalt"] = (verlauf_rein[-1]["inhalt"] + "\n\n"
                                          + sauber)
            continue
        verlauf_rein.append({"rolle": rolle, "inhalt": sauber})

    if (verlauf_rein and verlauf_rein[-1]["rolle"] == "nutzer"
            and isinstance(verlauf_rein[-1]["inhalt"], str)):
        # Zwei Nutzerzuege hintereinander waeren der neue und der letzte alte. Den alten
        # anhaengen statt wegwerfen - sonst verliert das Gespraech eine Frage.
        nachricht = verlauf_rein.pop()["inhalt"] + "\n\n" + nachricht

    inhalt = nachricht.strip()
    if gesaeubert.strip():
        inhalt = (f"{inhalt}\n\n--- Mitgelieferte Daten "
                  f"{'(Mitte gekuerzt) ' if gekuerzt else ''}---\n{gesaeubert}")

    # 4. Vorbuchen und aufrufen.
    #
    #    Gebucht wird VOR dem Aufruf, mit einer Schaetzung. Vorher wurde erst nach der
    #    Antwort protokolliert - und waehrend ein Aufruf lief (beim echten Anbieter
    #    Sekunden), sah ihn keine andere Pruefung. Zwanzig gleichzeitige Aufrufe kamen so
    #    alle durch, obwohl das Stundenkontingent fuenf erlaubte, und der Monatsdeckel
    #    wurde um ein Mehrfaches ueberfahren.
    modell = ai_settings_service.modell_fuer(einstellungen, funktion)
    nachrichten = _baue_nachrichten(verlauf_rein, inhalt)
    zeichen_ein = len(_baue_auftrag(funktion, zusatzauftrag)) + sum(
        len(n["inhalt"]) if isinstance(n.get("inhalt"), str)
        else len(str(n.get("inhalt"))) for n in nachrichten)

    try:
        buchung = ai_budget_service.pruefe_und_reserviere(
            db, user=user, funktion=funktion, einstellungen=einstellungen, modell=modell,
            geschaetzt_cent=ai_budget_service.schaetze_cent(
                modell, zeichen_ein, max_tokens, einstellungen.get("preise")))
    except ai_budget_service.BudgetGesperrt as exc:
        ai_budget_service.protokolliere(
            db, user_id=getattr(user, "id", None), server_id=server_id,
            funktion=funktion, modell=modell, status="abgelehnt", hinweis=str(exc))
        raise KiAbgelehnt(str(exc)) from exc

    anbieter = hole_anbieter(einstellungen.get("anbieter") or "anthropic")
    try:
        antwort = anbieter.frage(
            KiAnfrage(auftrag=_baue_auftrag(funktion, zusatzauftrag),
                      nachrichten=nachrichten,
                      modell=modell, max_tokens=max_tokens,
                      werkzeuge=werkzeuge or [], werkzeugzwang=werkzeugzwang),
            ai_settings_service.hole_schluessel(db))
    except KiFehler as exc:
        ai_budget_service.verwirf_reservierung(db, buchung, hinweis=str(exc))
        raise

    # 5. Protokoll mit den ECHTEN Token-Zahlen - und erst danach die Antwort auswerten,
    #    damit ein Aufruf auch dann gezaehlt wird, wenn die Auswertung scheitert.
    cent = kosten_cent(antwort.modell, antwort.tokens_ein, antwort.tokens_aus,
                       einstellungen.get("preise"))
    text = antwort.text or ""
    abgelehnt = ABLEHNUNG in text
    ai_budget_service.buche_ab(
        db, buchung, modell=antwort.modell, server_id=server_id,
        tokens_ein=antwort.tokens_ein, tokens_aus=antwort.tokens_aus, kosten_cent=cent,
        status="abgelehnt" if abgelehnt else "ok",
        hinweis="Themen-Sperre (Modell)" if abgelehnt else None)

    if abgelehnt:
        raise KiAbgelehnt("Die KI hat die Anfrage als themenfremd abgelehnt. Die "
                          "KI-Funktionen sind auf die Serververwaltung beschraenkt.")

    if namen_ersetzen and bericht.get("namen"):
        text = ai_privacy_service.uebersetze_zurueck(text, bericht["namen"])

    hinweise: list[str] = []
    if frage_gekuerzt:
        hinweise.append(f"Deine Frage war laenger als {MAX_FRAGE_ZEICHEN} Zeichen - die "
                        f"Mitte wurde weggelassen. Lange Texte gehoeren in die "
                        f"mitgelieferten Daten, nicht in die Frage.")
    if gekuerzt:
        hinweise.append("Die mitgelieferten Daten waren sehr lang - die Mitte wurde "
                        "weggelassen, Anfang und Ende sind drin.")
    if antwort.abbruchgrund == "laenge":
        hinweise.append("Die Antwort wurde abgeschnitten, weil die Laengengrenze erreicht "
                        "war. Bitte gezielter fragen.")
    if bericht.get("ips"):
        hinweise.append(f"{bericht['ips']} IP-Adresse(n) wurden vor dem Senden entfernt.")

    return {
        "text": text,
        "modell": antwort.modell,
        "tokens_ein": antwort.tokens_ein,
        "tokens_aus": antwort.tokens_aus,
        "kosten_cent": cent,
        "werkzeug_aufrufe": antwort.werkzeug_aufrufe,
        "hinweise": hinweise,
        "budget": ai_budget_service.budget_stand(db, einstellungen),
    }


def pruefe_verbindung(db: Session, *, user: User) -> tuple[bool, str]:
    """Schluessel und Erreichbarkeit pruefen - mit dem guenstigsten Modell."""
    from app.core.constants import UserRole

    if getattr(user, "role", "") not in {UserRole.SUPER_ADMIN.value, UserRole.ADMIN.value}:
        return False, "Nur Admins koennen die Verbindung pruefen."
    einstellungen = ai_settings_service.hole_einstellungen(db)
    if not einstellungen["schluessel_hinterlegt"]:
        return False, "Es ist kein API-Schluessel hinterlegt."
    anbieter = hole_anbieter(einstellungen.get("anbieter") or "anthropic")
    try:
        schluessel = ai_settings_service.hole_schluessel(db)
    except Exception as exc:  # noqa: BLE001
        return False, str(exc)
    ok, meldung = anbieter.pruefe_schluessel(schluessel)
    ai_budget_service.protokolliere(
        db, user_id=getattr(user, "id", None), server_id=None, funktion="verbindung",
        modell="claude-haiku-5-5", status="ok" if ok else "fehler",
        hinweis=None if ok else meldung)
    return ok, meldung
