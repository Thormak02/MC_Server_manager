"""Der Freimarken-Bereich: Seiten und JSON-Zwilling.

RECHTE - ZWEI FRAGEN, NICHT EINE
Plan 12a.4 nennt eine Abstufung nach ROLLE: lesen ab Moderator, Stammdaten aendern nur
Admin. Dazu kommt, was der Manager ohnehin kennt, die Frage nach dem SERVER: wer eine
Datei dieses Servers aendern darf (can_edit_server_files). Beide muessen gefragt werden,
denn dieser Bereich ist ein zweiter Weg zu derselben Tat - er schreibt eine Datei im
Serverordner. Fragte er nur die Rolle, waere er der Weg um das Dateirecht herum: ein
Admin ohne Dateirecht fuer diesen Server koennte hier die config.yml aendern.

Die Rangordnung der Rollen kommt aus app/core/permissions.py und wird nicht neu gebaut.
Die Funktion lag bis heute unbenutzt da; eine vierte Rangordnung danebenzustellen waere
die schlechtere Haelfte beider Moeglichkeiten.

WARUM KEIN Depends
Das Haus prueft die Anmeldung im Funktionsrumpf und nicht als Abhaengigkeit; die
Waechterprobe tests/test_login_barrier.py geht jede registrierte Route durch und verlangt
im erreichbaren Quelltext einen der Markernamen. Dieses Modul haelt sich daran, damit die
Probe greift - und _require_stufe ruft get_current_user_from_session, also steht der
Marker in jeder Route.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.core.permissions import has_minimum_role
from app.db.session import get_db
from app.models.server import Server
from app.models.user import User
from app.services import (
    audit_service,
    freimarken_db_service,
    freimarken_mitglied_service,
    freimarken_service,
    freimarken_spieldaten_service,
)
from app.services.auth_service import get_current_user_from_session
from app.services.server_service import (
    can_edit_server_files,
    can_view_server,
    get_server_by_id,
    list_servers_for_user,
)
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)


def _require_stufe(request: Request, db: Session, mindestens: str) -> User | None:
    """``None`` heisst "nicht angemeldet" (die Route leitet auf /login), 403 heisst "zu
    niedrige Rolle". Zwei verschiedene Antworten fuer zwei verschiedene Lagen."""
    user = get_current_user_from_session(request, db)
    if user is None:
        return None
    if not has_minimum_role(user.role, mindestens):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")
    return user


def _fuer_korrektur(request: Request, db: Session,
                    server_id: int) -> tuple[User | None, Server | None]:
    """Die Tore fuer eine KORREKTUR an Spieldaten - Plan 12a.4: nur Super-Admin.

    Eine dritte Stufe neben Lesen (Moderator) und Stammdaten (Admin), und sie hat einen
    eigenen Grund: Eine Buchung aendert, was einem Spieler gehoert.

    ES WIRD NUR DIE ROLLE GEFRAGT, und das ist eine Entscheidung [22.11.2026]. Hier stand
    zusaetzlich ``can_control_server`` - "wer die Konsole dieses Servers nicht bedienen
    darf, soll es auch hier nicht". Gemessen: Jene Funktion gibt fuer einen Super-Admin
    IMMER true zurueck (server_service.py, "SUPER_ADMIN immer"). Die zweite Frage konnte
    also nie "nein" antworten - sie war der doppelte Riegel, der sich selbst unpruefbar
    macht, und eine Probe dafuer waere mit UND ohne ihn gruen.

    Der tragende Riegel liegt ohnehin nicht hier, sondern in ``process_service`` an der
    Sendestelle, durch die alle fuenf Wege gehen. Was hier steht, ist die Oberflaeche:
    ein Formular, das ein Moderator gar nicht sehen soll.
    """
    current_user = _require_stufe(request, db, UserRole.SUPER_ADMIN.value)
    if current_user is None:
        return None, None
    return current_user, _server_oder_404(db, server_id)


def _server_oder_404(db: Session, server_id: int) -> Server:
    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND,
                            detail="Server not found")
    return server


def _fuer_schreiben(request: Request, db: Session, server_id: int,
                    seite: str) -> tuple[User | None, Server | None]:
    """Die Tore, die JEDER schreibende Weg dieses Bereichs durchlaufen muss.

    An einer Stelle, weil es inzwischen zwei Wege gibt (Einstellungen und Texte) und ein
    dritter kommen wird. Eine Kopie der drei Pruefungen in jeder Route ist genau die
    Stelle, an der die vierte sie vergisst.

    ``(None, None)`` heisst "nicht angemeldet" - die Route leitet dann auf /login. Zu
    niedrige Rolle, fehlendes Dateirecht und eine unbekannte Seite werfen.
    """
    current_user = _require_stufe(request, db, UserRole.ADMIN.value)
    if current_user is None:
        return None, None
    server = _server_oder_404(db, server_id)
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")
    if freimarken_service.seite(seite) is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Unknown page")
    return current_user, server


def _sichtbare_server(db: Session, user: User) -> list[Server]:
    """Die Server mit einer Freimarken-Konfiguration, die dieser Nutzer sehen darf.

    Die Sichtbarkeit beantwortet list_servers_for_user - in SQL und einmal fuer alle, statt
    hier Server fuer Server zu fragen. Die Reihenfolge ist die von dort (nach Namen), damit
    die Uebersicht nicht bei jedem Aufruf anders aussieht.
    """
    return [server for server in list_servers_for_user(db, user)
            if freimarken_service.hat_freimarken(server)]


@router.get("/freimarken", response_class=HTMLResponse)
def freimarken_uebersicht(request: Request, db: Session = Depends(get_db)):
    current_user = _require_stufe(request, db, UserRole.MODERATOR.value)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server_liste = _sichtbare_server(db, current_user)
    # Ein einziger Server mit Freimarken ist der haeufige Fall - dann ist die Uebersicht
    # eine Seite mit einem Link darauf, und das ist ein Klick zu viel.
    if len(server_liste) == 1:
        erste = freimarken_service.seiten()[0]["schluessel"]
        return RedirectResponse(
            url="/freimarken/" + str(server_liste[0].id) + "/" + erste,
            status_code=303)

    return templates.TemplateResponse(
        "freimarken_uebersicht.html",
        build_context(request, current_user=current_user, server_liste=server_liste,
                      seiten=freimarken_service.seiten()),
    )


@router.get("/freimarken/{server_id}/{seite}", response_class=HTMLResponse)
def freimarken_seite(request: Request, server_id: int, seite: str,
                     abschnitt: str = "", db: Session = Depends(get_db)):
    current_user = _require_stufe(request, db, UserRole.MODERATOR.value)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = _server_oder_404(db, server_id)
    if not can_view_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    if freimarken_service.seite(seite) is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Unknown page")

    beschreibung = freimarken_service.seite(seite)
    fehler: str | None = None
    inhalt: dict = {"seite": beschreibung, "gruppen": [], "felder_gesamt": 0}
    zeilen: list = []
    sprache: dict = {}
    # ALLE Behaelter werden vorbelegt, auch die, die diese Seite nicht braucht. Jinja
    # laeuft sonst ueber Undefined, und eine einzige leere Zelle reisst die ganze Seite
    # mit - derselbe Grund, aus dem zerlege_protokoll in jedem Zweig dieselben
    # Schluessel zurueckgibt.
    spieldaten: dict = {}
    mitglieder: dict = {}

    if beschreibung.get("quelle") == "audit":
        # Diese Seite liest nicht die config.yml, sondern das Protokoll des Managers.
        zeilen = freimarken_service.protokollzeilen(db, server.id)
    elif beschreibung.get("quelle") == "sprache":
        # Die Sprachdatei hat 770 Saetze. Alle auf einer Seite waeren keine Oberflaeche,
        # darum erst die Abschnitte und dann einer davon.
        try:
            if abschnitt:
                sprache = freimarken_service.sprachfelder(server, abschnitt)
            else:
                sprache = {"abschnitte": freimarken_service.sprachabschnitte(server)}
        except freimarken_service.FreimarkenFehler as problem:
            fehler = str(problem)
        except (OSError, ValueError):
            fehler = ("Die " + freimarken_service.PFAD_VORLAGE + " ist auf diesem Server "
                      "nicht lesbar. Das Plugin schreibt sie bei jedem Start neu - "
                      "starte den Server einmal.")
    elif beschreibung.get("quelle") == "spieldaten":
        # Besitz und Konten kommen aus der Datenbank des PLUGINS. Sie zu lesen kann
        # scheitern, ohne dass etwas kaputt ist: Ein Server ohne datenbank.url speichert
        # nichts, und ein nicht erreichbarer Rechner ist kein Programmfehler. Darum eine
        # Meldung und keine Ausnahme - die Seite bleibt bedienbar.
        # Die ART steht VOR dem Lesen [Fund 22.11.2026]. Haengt sie am Erfolg, faellt
        # die Seite bei einem Lesefehler durch alle Zweige der Vorlage bis zum letzten -
        # und der sagt "kein einziger Wert aus der config.yml", was fuer diese Seite
        # ohnehin nie stimmt. Das Korrekturformular verschwaende mit, obwohl genau dann
        # jemand eingreifen will.
        spieldaten = {"art": seite, "zeilen": [], "zusammen": {}}
        try:
            if seite == "besitz":
                spieldaten["zeilen"] = freimarken_spieldaten_service.besitz(server)
            else:
                spieldaten["zeilen"] = freimarken_spieldaten_service.konten(server)
                spieldaten["zusammen"] = \
                    freimarken_spieldaten_service.zusammenfassung(server)
        except freimarken_db_service.FreimarkenDbFehler as problem:
            fehler = str(problem)
    elif beschreibung.get("quelle") == "mitglieder":
        # Diese Zeilen stehen im MANAGER. Sie koennen nicht unlesbar sein, und darum gibt
        # es hier auch keinen Fehlerzweig.
        mitglieder = {
            "zeilen": freimarken_mitglied_service.liste(db, server.id),
            "rechte": freimarken_mitglied_service.rechtezeilen(db, server.id),
            "zuschlaege": freimarken_mitglied_service.zuschlaege(),
        }
    elif beschreibung.get("quelle"):
        pass   # eine Quelle, die es noch nicht gibt - die Vorlage sagt es dem Nutzer
    else:
        try:
            inhalt = freimarken_service.felder_der_seite(server, seite)
        except freimarken_service.FreimarkenFehler as problem:
            fehler = str(problem)
        except (OSError, ValueError):
            fehler = ("Die Datei " + freimarken_service.PFAD_CONFIG + " ist auf diesem "
                      "Server nicht lesbar. Laeuft dort ueberhaupt Freimarken?")

    return templates.TemplateResponse(
        "freimarken_seite.html",
        build_context(request, current_user=current_user, server=server,
                      seiten=freimarken_service.seiten(), aktiv=seite,
                      inhalt=inhalt, lesefehler=fehler, zeilen=zeilen,
                      sprache=sprache, abschnitt=abschnitt,
                      spieldaten=spieldaten, mitglieder=mitglieder,
                      darf_aendern=(has_minimum_role(current_user.role,
                                                     UserRole.ADMIN.value)
                                    and can_edit_server_files(db, current_user, server)),
                      # Die dritte Stufe, und sie wird GETRENNT gefragt: Ein Admin mit
                      # Dateirecht darf Preise stellen, aber kein Konto berichtigen.
                      darf_korrigieren=has_minimum_role(
                          current_user.role, UserRole.SUPER_ADMIN.value)),
    )


def _sende(db, server, befehl: str, user_id: int, grund: str,
           aktion: str) -> tuple[bool, str]:
    """Einen Korrekturbefehl an den laufenden Server - und die WAHRHEIT darueber.

    Der Weg geht durch ``send_console_command``, also durch dieselbe Engstelle, die die
    Rollenpruefung, den Umbruchriegel und das Protokoll traegt. Ein zweiter Sendeweg
    waere der Weg daran vorbei.

    DIE RUECKMELDUNG IST EHRLICH: ``send_console_command`` kennt den Ausgang nicht - es
    schreibt auf stdin und gibt "Befehl gesendet." zurueck. Antwortet das Plugin
    UNBEKANNTER_SPIELER oder NICHT_GEDECKT, hat es trotzdem gesendet. Darum steht in der
    Meldung "gesendet" und nicht "gebucht", und sie sagt, wo der Ausgang zu lesen ist.

    Der GRUND steht im Manager-Audit und nicht im Befehl: Das Plugin nimmt keinen. Damit
    faellt Plan 12a.2 Regel 3 auf zwei Buecher auseinander - das Was ins Serverlog, das
    Wer und das Warum hierher. Die Protokollzeile nennt beides, damit sie zusammenfuehrbar
    bleiben.
    """
    from app.services.process_service import send_console_command

    ok, meldung = send_console_command(db, server, befehl, user_id)
    audit_service.log_action(
        db, action=aktion, user_id=user_id, server_id=server.id,
        details=(befehl + freimarken_service.TRENNER_GRUND
                 + (grund.strip() or freimarken_service.OHNE_GRUND)
                 + " | gesendet: " + ("ja" if ok else "nein - " + meldung)))
    if not ok:
        return False, meldung
    return True, ("Gesendet: " + befehl + ". Was daraus geworden ist, steht in der "
                  "Serverkonsole hinter dem Wort \"Ausgang\" - diese Seite erfaehrt es "
                  "nicht.")


@router.post("/freimarken/{server_id}/konten")
async def freimarken_konto_korrigieren(request: Request, server_id: int,
                                       db: Session = Depends(get_db)):
    """Mark gutschreiben oder abziehen - ueber /freimarken geld, nicht ueber die Tabelle.

    Plan 12a.2 Regel 2: Der Manager liest die Spieldaten direkt, aendert sie aber nur
    ueber das Plugin. Eine Zeile, die der Manager in ``kassen`` schriebe, stuende in
    keinem Buch und braeche die tragende Zusage der Kasse ("fuer ein eroeffnetes Konto
    ist die Summe aller Buchungen sein Stand").
    """
    current_user, server = _fuer_korrektur(request, db, server_id)
    if current_user is None or server is None:
        return RedirectResponse(url="/login", status_code=303)

    form = await request.form()
    spieler = str(form.get("spieler") or "").strip()
    betrag = str(form.get("betrag") or "").strip()
    grund = str(form.get("grund") or "")

    ziel = "/freimarken/" + str(server_id) + "/konten"
    if not spieler or not betrag:
        push_flash(request, "Spieler und Betrag muessen beide dastehen.", "error")
        return RedirectResponse(url=ziel, status_code=303)
    # Der Name ist ein Brigadier-Wort: Ein Leerzeichen darin erreicht den Zweig nicht,
    # sondern faellt in Brigadiers englische Fehlerzeile - und die liest hier niemand.
    if " " in spieler:
        push_flash(request, "Ein Spielername hat kein Leerzeichen.", "error")
        return RedirectResponse(url=ziel, status_code=303)
    try:
        zahl = int(betrag)
    except ValueError:
        push_flash(request, "Der Betrag muss eine ganze Zahl sein; ein Minus zieht ab.",
                   "error")
        return RedirectResponse(url=ziel, status_code=303)
    # Dieselbe Schranke wie Betreibergeld.HOECHSTENS im Plugin. Sie steht hier, weil eine
    # groessere Zahl den Zweig gar nicht erreicht: Das Argument ist ein LongArgumentType
    # mit Grenzen, und darueber antwortet Brigadier englisch.
    if abs(zahl) > 1_000_000_000:
        push_flash(request, "Hoechstens eine Milliarde - darueber nimmt das Plugin den "
                            "Befehl nicht an.", "error")
        return RedirectResponse(url=ziel, status_code=303)

    ok, meldung = _sende(db, server, "freimarken geld " + spieler + " " + str(zahl),
                         current_user.id, grund, freimarken_service.AKTION_KONTO)
    push_flash(request, meldung, "success" if ok else "error")
    return RedirectResponse(url=ziel, status_code=303)


@router.post("/freimarken/{server_id}/besitz")
async def freimarken_besitz_korrigieren(request: Request, server_id: int,
                                        db: Session = Depends(get_db)):
    """Besitz uebertragen oder freigeben - ueber /freimarken besitz.

    Freigeben schickt das Wort ``bestaetigen`` mit, weil der Befehl es verlangt: Es
    loescht Besitz ohne Rueckzahlung, und ein Zahlendreher traefe sonst die Parzelle
    eines Unbeteiligten.
    """
    current_user, server = _fuer_korrektur(request, db, server_id)
    if current_user is None or server is None:
        return RedirectResponse(url="/login", status_code=303)

    form = await request.form()
    aktion = str(form.get("aktion") or "").strip()
    x = str(form.get("x") or "").strip()
    z = str(form.get("z") or "").strip()
    spieler = str(form.get("spieler") or "").strip()
    grund = str(form.get("grund") or "")

    ziel = "/freimarken/" + str(server_id) + "/besitz"
    if aktion not in {"uebertragen", "freigeben"}:
        push_flash(request, "Es gibt nur uebertragen und freigeben.", "error")
        return RedirectResponse(url=ziel, status_code=303)
    try:
        zahl_x = int(x)
        zahl_z = int(z)
    except ValueError:
        push_flash(request, "Die Stelle sind zwei ganze Zahlen - die Parzellennummern, "
                            "wie sie /parzelle info nennt.", "error")
        return RedirectResponse(url=ziel, status_code=303)

    if aktion == "uebertragen":
        if not spieler or " " in spieler:
            push_flash(request, "Fuer das Uebertragen braucht es einen Spielernamen "
                                "ohne Leerzeichen.", "error")
            return RedirectResponse(url=ziel, status_code=303)
        befehl = ("freimarken besitz uebertragen " + str(zahl_x) + " " + str(zahl_z)
                  + " " + spieler)
    else:
        befehl = ("freimarken besitz freigeben " + str(zahl_x) + " " + str(zahl_z)
                  + " bestaetigen")

    ok, meldung = _sende(db, server, befehl, current_user.id, grund,
                         freimarken_service.AKTION_BESITZ)
    push_flash(request, meldung, "success" if ok else "error")
    return RedirectResponse(url=ziel, status_code=303)


@router.post("/freimarken/{server_id}/mitglieder/beenden")
async def freimarken_mitglied_beenden(request: Request, server_id: int,
                                      db: Session = Depends(get_db)):
    """Eine Mitgliedschaft beenden - mit Datum, nicht durch Loeschen.

    Die Zeile ist der Beleg dafuer, dass jemand bezahlt hat. Wer sie entfernt, kann in
    drei Monaten nicht mehr beantworten, warum dieser Spieler damals drei Parzellen mehr
    hatte.
    """
    current_user, server = _fuer_korrektur(request, db, server_id)
    if current_user is None or server is None:
        return RedirectResponse(url="/login", status_code=303)

    form = await request.form()
    ziel = "/freimarken/" + str(server_id) + "/mitglieder"
    try:
        nummer = int(str(form.get("eintrag") or "").strip())
    except ValueError:
        push_flash(request, "Ohne die Nummer der Zeile geht es nicht.", "error")
        return RedirectResponse(url=ziel, status_code=303)

    try:
        eintrag = freimarken_mitglied_service.beende(db, server_id=server.id,
                                                    eintrag_id=nummer)
    except freimarken_mitglied_service.MitgliedFehler as problem:
        push_flash(request, str(problem), "error")
        return RedirectResponse(url=ziel, status_code=303)

    audit_service.log_action(
        db, action=freimarken_service.AKTION_MITGLIED, user_id=current_user.id, server_id=server.id,
        details=("beendet: " + eintrag.spieler_kennung + " " + eintrag.zuschlag_name
                 + "." + str(eintrag.zuschlag_zahl)))
    push_flash(request, "Beendet. Die Zeile bleibt als Beleg stehen - das Recht "
                        + freimarken_mitglied_service.rechtename(
                            eintrag.zuschlag_name, eintrag.zuschlag_zahl)
                        + " muss in der Rechteverwaltung des Servers entzogen werden.",
               "success")
    return RedirectResponse(url=ziel, status_code=303)


@router.post("/freimarken/{server_id}/mitglieder")
async def freimarken_mitglied_anlegen(request: Request, server_id: int,
                                      db: Session = Depends(get_db)):
    """Eine Mitgliedschaft eintragen.

    Der Manager VERGIBT das Recht nicht, er NENNT es: ``/lp`` waere ein Konsolenbefehl an
    ein fremdes Plugin, dessen Form sich mit jeder Fassung aendert. Ein Knopf, der bei
    einer anderen Rechteverwaltung stumm nichts tut, waere schlimmer als eine Zeile zum
    Kopieren.
    """
    current_user, server = _fuer_korrektur(request, db, server_id)
    if current_user is None or server is None:
        return RedirectResponse(url="/login", status_code=303)

    form = await request.form()
    ziel = "/freimarken/" + str(server_id) + "/mitglieder"
    try:
        eintrag = freimarken_mitglied_service.lege_an(
            db,
            server_id=server.id,
            spieler_kennung=str(form.get("kennung") or ""),
            spieler_name=str(form.get("name") or ""),
            zuschlag_name=str(form.get("zuschlag") or ""),
            zuschlag_zahl=str(form.get("zahl") or ""),
            bis=None,
            zahlung_herkunft=str(form.get("herkunft") or ""),
            zahlung_beleg=str(form.get("beleg") or ""),
            grund=str(form.get("grund") or ""),
            angelegt_von_user_id=current_user.id,
        )
    except freimarken_mitglied_service.MitgliedFehler as problem:
        push_flash(request, str(problem), "error")
        return RedirectResponse(url=ziel, status_code=303)

    audit_service.log_action(
        db, action=freimarken_service.AKTION_MITGLIED, user_id=current_user.id, server_id=server.id,
        details=("angelegt: " + eintrag.spieler_kennung + " " + eintrag.zuschlag_name
                 + "." + str(eintrag.zuschlag_zahl)
                 + freimarken_service.TRENNER_GRUND
                 + (eintrag.grund or freimarken_service.OHNE_GRUND)))
    push_flash(request, "Eingetragen. Damit es wirkt, braucht der Spieler in der "
                        "Rechteverwaltung des Servers das Recht "
                        + freimarken_mitglied_service.rechtename(
                            eintrag.zuschlag_name, eintrag.zuschlag_zahl)
                        + " - der Manager vergibt es nicht.", "success")
    return RedirectResponse(url=ziel, status_code=303)


# Die beiden Texterouten stehen VOR der allgemeinen Speicherroute, und das ist keine
# Geschmacksfrage: Starlette nimmt die erste Route, deren Muster passt, und
# "{seite}" passt auch auf "texte". Stuenden sie danach, landete jedes Speichern von
# Saetzen in der Einstellungsroute - die wuerde sie als unbekannte Pfade der config.yml
# melden. Die Probe test_texte_landet_in_der_texteroute haelt das fest, denn eine
# Reihenfolge im Quelltext ist kein Riegel, den man sieht.
@router.post("/freimarken/{server_id}/texte")
async def freimarken_texte_speichern(request: Request, server_id: int,
                                     db: Session = Depends(get_db)):
    """Wortlaute in die sprache.yml des Betreibers - und nur dorthin.

    Eigene Route und nicht der Weg der Einstellungen: Es ist eine andere Datei, und sie
    wird anders geschrieben. Ein Satz, der noch nicht darin steht, wird ANGELEGT; bei den
    Einstellungen gibt es diesen Fall nicht, dort steht jeder Schluessel schon da. Die
    drei Tore davor sind dieselben und stehen darum an einer Stelle (_fuer_schreiben).
    """
    current_user, server = _fuer_schreiben(request, db, server_id, "texte")
    if current_user is None or server is None:
        return RedirectResponse(url="/login", status_code=303)

    form = await request.form()
    abschnitt = str(form.get("abschnitt") or "").strip()
    ziel = ("/freimarken/" + str(server_id) + "/texte"
            + ("?abschnitt=" + abschnitt if abschnitt else ""))
    grund = str(form.get("grund") or "").strip()
    werte = {schluessel[len("feld:"):]: str(wert)
             for schluessel, wert in form.items()
             if schluessel.startswith("feld:")}

    if not werte:
        push_flash(request, "Es war nichts zu speichern.", "info")
        return RedirectResponse(url=ziel, status_code=303)

    try:
        ergebnis = freimarken_service.speichere_saetze(server, werte)
    except freimarken_service.FreimarkenFehler as problem:
        push_flash(request, str(problem), "error")
        return RedirectResponse(url=ziel, status_code=303)
    except (OSError, ValueError) as problem:
        push_flash(request, "Gespeichert wurde nichts: " + str(problem), "error")
        return RedirectResponse(url=ziel, status_code=303)

    teile: list[str] = []
    art = "success"

    if ergebnis["geschrieben"]:
        audit_service.log_action(
            db,
            action=freimarken_service.AKTION_TEXTE,
            user_id=current_user.id,
            server_id=server.id,
            details=freimarken_service.protokoll_text(ergebnis["geaendert"], grund),
        )
        anzahl = len(ergebnis["geaendert"])
        teile.append("Gespeichert: " + str(anzahl)
                     + (" Satz" if anzahl == 1 else " Saetze") + " in "
                     + freimarken_service.PFAD_SPRACHE + ".")
        if ergebnis.get("angelegt"):
            teile.append(str(len(ergebnis["angelegt"]))
                         + " davon standen noch nicht in deiner Datei und wurden"
                         " angelegt.")
        teile.append("Wirksam mit /freimarken neuladen.")

    if ergebnis["hinweise"]:
        # Hinweise und keine Absagen: Geprueft wird beim Laden, und zwar vom Plugin.
        # Der Manager sagt nur vorher, was ihm auffaellt.
        art = "error"
        teile.extend(ergebnis["hinweise"])

    if ergebnis["unbekannt"]:
        art = "error"
        teile.append("Diese Saetze gibt es in der Vorlage nicht: "
                     + ", ".join(sorted(ergebnis["unbekannt"]))
                     + " - nichts davon wurde geschrieben.")

    if not teile:
        teile.append("Es hat sich nichts geaendert.")
        art = "info"

    push_flash(request, " ".join(teile), art)
    return RedirectResponse(url=ziel, status_code=303)


@router.post("/freimarken/{server_id}/texte/zuruecknehmen")
async def freimarken_satz_zuruecknehmen(request: Request, server_id: int,
                                        db: Session = Depends(get_db)):
    """Eine Ueberschreibung entfernen - danach gilt wieder der ausgelieferte Wortlaut.

    Das ist die Gegenrichtung zum Speichern und gehoert dazu: Ohne sie waere jede
    Aenderung endgueltig, und der Betreiber muesste den alten Wortlaut von Hand aus der
    Vorlage zurueckkopieren - also genau das tun, was diese Oberflaeche ihm ersparen soll.
    """
    current_user, server = _fuer_schreiben(request, db, server_id, "texte")
    if current_user is None or server is None:
        return RedirectResponse(url="/login", status_code=303)

    form = await request.form()
    pfad = str(form.get("pfad") or "").strip()
    abschnitt = str(form.get("abschnitt") or "").strip()
    ziel = ("/freimarken/" + str(server_id) + "/texte"
            + ("?abschnitt=" + abschnitt if abschnitt else ""))

    if not pfad:
        push_flash(request, "Kein Satz angegeben.", "error")
        return RedirectResponse(url=ziel, status_code=303)

    try:
        freimarken_service.nimm_satz_zurueck(server, pfad)
    except freimarken_service.FreimarkenFehler as problem:
        push_flash(request, str(problem), "error")
        return RedirectResponse(url=ziel, status_code=303)
    except (OSError, ValueError) as problem:
        push_flash(request, "Nichts geaendert: " + str(problem), "error")
        return RedirectResponse(url=ziel, status_code=303)

    audit_service.log_action(
        db,
        action=freimarken_service.AKTION_TEXTE,
        user_id=current_user.id,
        server_id=server.id,
        details=pfad + ": zurueckgenommen"
                + freimarken_service.TRENNER_GRUND
                + "wieder der ausgelieferte Wortlaut",
    )
    push_flash(request, "Zurueckgenommen: " + pfad
               + ". Es gilt wieder der ausgelieferte Wortlaut, wirksam mit"
               " /freimarken neuladen.", "success")
    return RedirectResponse(url=ziel, status_code=303)


@router.post("/freimarken/{server_id}/{seite}")
async def freimarken_speichern(request: Request, server_id: int, seite: str,
                               db: Session = Depends(get_db)):
    # Stammdaten aendern ist Admin-Sache (Plan 12a.4) - lesen reicht hier nicht.
    current_user, server = _fuer_schreiben(request, db, server_id, seite)
    if current_user is None or server is None:
        return RedirectResponse(url="/login", status_code=303)

    ziel = "/freimarken/" + str(server_id) + "/" + seite
    form = await request.form()

    # Nur Felder mit dem Vorsatz sind Einstellungen. Alles andere ist Steuerung des
    # Formulars und darf nicht als Schluessel in der config.yml landen.
    werte = {schluessel[len("feld:"):]: str(wert)
             for schluessel, wert in form.items()
             if schluessel.startswith("feld:")}
    grund = str(form.get("grund") or "").strip()
    bestaetigt = str(form.get("sprung_bestaetigt") or "").strip() in {"1", "true", "on"}

    if not werte:
        push_flash(request, "Es war nichts zu speichern.", "info")
        return RedirectResponse(url=ziel, status_code=303)

    try:
        ergebnis = freimarken_service.speichere(server, werte,
                                                sprung_bestaetigt=bestaetigt)
    except freimarken_service.FreimarkenFehler as problem:
        push_flash(request, str(problem), "error")
        return RedirectResponse(url=ziel, status_code=303)
    except (OSError, ValueError) as problem:
        push_flash(request, "Gespeichert wurde nichts: " + str(problem), "error")
        return RedirectResponse(url=ziel, status_code=303)

    if ergebnis["warte_auf_bestaetigung"]:
        # Die Rueckfrage. Sie kommt als Seite auf diese POST-Antwort und nicht ueber eine
        # Umleitung mit Sitzungszustand: Was der Nutzer eingetippt hat, reist in verborgenen
        # Feldern weiter. So gibt es keinen halben Vorgang, der in einer Sitzung liegen
        # bleibt, und zwei Leute koennen gleichzeitig verschiedene Werte bestaetigen.
        gesprungen = {eintrag["pfad"] for eintrag in ergebnis["spruenge"]}
        return templates.TemplateResponse(
            "freimarken_rueckfrage.html",
            build_context(request, current_user=current_user, server=server,
                          seiten=freimarken_service.seiten(), aktiv=seite,
                          inhalt={"seite": freimarken_service.seite(seite)},
                          spruenge=ergebnis["spruenge"],
                          andere=[eintrag for eintrag in ergebnis["aenderungen"]
                                  if eintrag["pfad"] not in gesprungen],
                          werte=werte, grund=grund),
        )

    # Es gibt genau EINEN Platz fuer eine Rueckmeldung (die Sitzung haelt einen Flash;
    # ein zweites push_flash im selben Durchlauf ueberschreibt das erste). Darum wird der
    # Satz zusammengesetzt und nicht in Teilen gemeldet - sonst verdeckt der letzte
    # Hinweis den wichtigsten.
    teile: list[str] = []
    art = "success"

    if ergebnis["geschrieben"]:
        # Protokoll VOR der Meldung und ohne Bedingung: Was geschrieben wurde, steht im
        # Buch, auch wenn daneben etwas schiefging. Plan 12a.2 Regel 3.
        audit_service.log_action(
            db,
            action=freimarken_service.AKTION_STAMMDATEN,
            user_id=current_user.id,
            server_id=server.id,
            details=freimarken_service.protokoll_text(ergebnis["aenderungen"], grund),
        )
        anzahl = len(ergebnis["aenderungen"])
        teile.append("Gespeichert: " + str(anzahl)
                     + (" Wert" if anzahl == 1 else " Werte") + " in "
                     + freimarken_service.PFAD_CONFIG + ".")
        if ergebnis["spruenge"]:
            teile.append("Die Bestaetigung fuer den grossen Sprung wurde mitgeschrieben.")
        teile.append("Wirksam wird das beim naechsten Start oder mit "
                     "/freimarken neuladen.")

    if ergebnis["gesperrt"]:
        # Ein gesperrtes Feld ist kein Fehler des Nutzers: Das Formular zeigt es, weil
        # eine gesperrte Einstellung mit Begruendung freundlicher ist als eine fehlende.
        # Geschrieben wird es nie.
        teile.append("Nicht aenderbar und darum uebergangen: "
                     + ", ".join(eintrag["pfad"] for eintrag in ergebnis["gesperrt"])
                     + ".")

    if ergebnis["unbekannt"]:
        # Ein Pfad, den die Datei nicht (mehr) hat - etwa nach einem Plugin-Update, das
        # einen Schluessel umbenannt hat, waehrend im Browser noch die alte Seite stand.
        # Der Dienst sammelt diese Pfade; sie nicht zu melden hiesse, den Nutzer "es hat
        # sich nichts geaendert" lesen zu lassen, wo in Wahrheit ein Name nicht mehr
        # stimmt - und dann sucht er den Fehler bei sich.
        art = "error"
        teile.append("Diese Einstellungen gibt es in der Datei nicht: "
                     + ", ".join(sorted(ergebnis["unbekannt"]))
                     + " - nichts davon wurde geschrieben. Lade die Seite neu;"
                     " womoeglich hat ein Plugin-Update den Namen geaendert.")

    if ergebnis.get("ohne_schluessel"):
        # Eine Luecke im Feldkatalog und keine des Nutzers - darum deutlich und rot.
        art = "error"
        teile.append("ACHTUNG: fuer "
                     + ", ".join(sorted(ergebnis["ohne_schluessel"]))
                     + " ist kein Bestaetigungsschluessel bekannt. Das Plugin wird diesen"
                     " Wert beim naechsten Start verwerfen.")

    if not teile:
        teile.append("Es hat sich nichts geaendert.")
        art = "info"

    push_flash(request, " ".join(teile), art)
    return RedirectResponse(url=ziel, status_code=303)


@router.get("/api/freimarken/{server_id}/{seite}")
def api_freimarken_seite(request: Request, server_id: int, seite: str,
                         db: Session = Depends(get_db)):
    """Derselbe Inhalt als JSON - fuer die KI-Werkzeuge und zum Nachsehen.

    Absichtlich dieselbe Rechtefrage wie die HTML-Seite: Ein API-Pfad, der mehr zeigt als
    die Seite, ist die Luecke, die niemand sucht. Der Unterschied ist nur die Antwort auf
    eine Absage - hier 403, dort eine Umleitung auf /login.
    """
    current_user = _require_stufe(request, db, UserRole.MODERATOR.value)
    if current_user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED,
                            detail="Not authenticated")

    server = _server_oder_404(db, server_id)
    if not can_view_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")
    if freimarken_service.seite(seite) is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Unknown page")

    try:
        return freimarken_service.felder_der_seite(server, seite)
    except freimarken_service.FreimarkenFehler as problem:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT,
                            detail=str(problem)) from problem
    except (OSError, ValueError) as problem:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND,
                            detail="Keine Freimarken-Konfiguration auf diesem Server"
                            ) from problem
