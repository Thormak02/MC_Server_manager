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


def frage(db: Session, *, user: User, funktion: str, nachricht: str,
          kontext: str = "", server_id: int | None = None,
          zusatzauftrag: str = "", werkzeuge: list[dict] | None = None,
          max_tokens: int = 2048) -> dict:
    """Eine KI-Anfrage stellen. Wirft KiAbgelehnt oder KiFehler.

    ``kontext`` sind die mitgeschickten Daten (Logauszug, Config) - sie werden gesaeubert.
    ``nachricht`` ist die Frage des Nutzers.
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
    #    fehlenden Schluesselwort scheitern. Wer Daten mitschickt, hat ohnehin Serverbezug.
    wortfilter_noetig = (funktion == "agent" and not kontext.strip())
    if (einstellungen.get("themensperre", True) and wortfilter_noetig
            and not _thema_passt(nachricht)):
        hinweis = ("Die Anfrage hat keinen erkennbaren Bezug zur Serververwaltung. Die "
                   "KI-Funktionen sind auf dieses Thema beschraenkt.")
        ai_budget_service.protokolliere(
            db, user_id=getattr(user, "id", None), server_id=server_id,
            funktion=funktion, modell="-", status="abgelehnt", hinweis=hinweis)
        raise KiAbgelehnt(hinweis)

    # 3. Was hinausgeht, wird gesaeubert.
    namen_ersetzen = bool(einstellungen.get("namen_ersetzen"))
    gesaeubert, bericht = ai_privacy_service.saeubere(kontext,
                                                      namen_ersetzen=namen_ersetzen)
    gesaeubert, gekuerzt = ai_privacy_service.kuerze_fuer_anfrage(gesaeubert)

    inhalt = nachricht.strip()
    if gesaeubert.strip():
        inhalt = (f"{inhalt}\n\n--- Mitgelieferte Daten "
                  f"{'(Mitte gekuerzt) ' if gekuerzt else ''}---\n{gesaeubert}")

    # 4. Aufruf.
    modell = ai_settings_service.modell_fuer(einstellungen, funktion)
    anbieter = hole_anbieter(einstellungen.get("anbieter") or "anthropic")
    try:
        antwort = anbieter.frage(
            KiAnfrage(auftrag=_baue_auftrag(funktion, zusatzauftrag),
                      nachrichten=[{"rolle": "nutzer", "inhalt": inhalt}],
                      modell=modell, max_tokens=max_tokens,
                      werkzeuge=werkzeuge or []),
            ai_settings_service.hole_schluessel(db))
    except KiFehler as exc:
        ai_budget_service.protokolliere(
            db, user_id=getattr(user, "id", None), server_id=server_id,
            funktion=funktion, modell=modell, status="fehler", hinweis=str(exc))
        raise

    # 5. Protokoll mit den ECHTEN Token-Zahlen - und erst danach die Antwort auswerten,
    #    damit ein Aufruf auch dann gezaehlt wird, wenn die Auswertung scheitert.
    cent = kosten_cent(antwort.modell, antwort.tokens_ein, antwort.tokens_aus,
                       einstellungen.get("preise"))
    text = antwort.text or ""
    abgelehnt = ABLEHNUNG in text
    ai_budget_service.protokolliere(
        db, user_id=getattr(user, "id", None), server_id=server_id, funktion=funktion,
        modell=antwort.modell, tokens_ein=antwort.tokens_ein,
        tokens_aus=antwort.tokens_aus, kosten_cent=cent,
        status="abgelehnt" if abgelehnt else "ok",
        hinweis="Themen-Sperre (Modell)" if abgelehnt else None)

    if abgelehnt:
        raise KiAbgelehnt("Die KI hat die Anfrage als themenfremd abgelehnt. Die "
                          "KI-Funktionen sind auf die Serververwaltung beschraenkt.")

    if namen_ersetzen and bericht.get("namen"):
        text = ai_privacy_service.uebersetze_zurueck(text, bericht["namen"])

    hinweise: list[str] = []
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
