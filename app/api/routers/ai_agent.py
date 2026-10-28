"""Routen fuer den freien KI-Agenten.

Zwei Endpunkte, und der Unterschied zwischen ihnen ist der ganze Punkt:

* ``/api/ki/auftrag`` laesst den Agenten nachsehen und antworten. Er kann dabei eine
  Aktion VORSCHLAGEN, aber keine ausfuehren.
* ``/api/ki/ausfuehren`` fuehrt eine Aktion aus, die der Nutzer bestaetigt hat. Dieser
  Endpunkt glaubt dem Browser nichts: er prueft Werkzeugname, Server und Recht neu.

Deshalb gibt es keinen "bestaetigt"-Marker, kein Token und keine zwischengespeicherte
Zusage. Wer den Vorschlag im Browser manipuliert, landet an derselben Rechtepruefung wie
bei einem Klick in der Oberflaeche.
"""

from fastapi import APIRouter, Depends, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services import (
    ai_agent_service,
    ai_chat_service,
    ai_provider_service,
    ai_service,
)
from app.services.auth_service import get_current_user_from_session
from app.web.routes.pages import build_context, templates


router = APIRouter(include_in_schema=False)

def _ki_nutzer(request: Request, db: Session):
    """Angemeldeter Nutzer MIT KI-Berechtigung, sonst (None, Antwort).

    Die Rolle wurde vorher erst in ``ai_service.frage`` geprueft - also NACH dem
    Anlegen des Gespraechs und dem Festhalten der Frage. Ein Nutzer ohne jedes KI-Recht
    beschrieb damit bei jedem Versuch drei Tabellen, und die Routen unter
    /api/ki/chats* fragten die Rolle gar nicht ab. Jetzt steht die Pruefung vor dem
    ersten Schreiben, an einer Stelle fuer alle Routen dieses Moduls.
    """
    from app.core.constants import UserRole

    current_user = get_current_user_from_session(request, db)
    if current_user is None:
        return None, JSONResponse(status_code=401,
                                  content={"detail": "Not authenticated"})
    if (current_user.role or "") == UserRole.VIEW_ONLY.value:
        return None, JSONResponse(status_code=403, content={
            "detail": "Fuer KI-Funktionen braucht es mindestens Moderator-Rechte."})
    return current_user, None


def _nutzer(request: Request, db: Session):
    return get_current_user_from_session(request, db)


@router.get("/ki", response_class=HTMLResponse)
def ki_seite(request: Request, db: Session = Depends(get_db)):
    from app.services import ai_settings_service

    current_user = _nutzer(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    einstellungen = ai_settings_service.hole_einstellungen(db)
    return templates.TemplateResponse(
        request,
        "ki_agent.html",
        build_context(
            request,
            current_user=current_user,
            page_title="KI-Assistent",
            ki_aktiv=bool(einstellungen.get("aktiv")),
            ki_schluessel=bool(einstellungen.get("schluessel_hinterlegt")),
            ki_agent_an=bool((einstellungen.get("funktionen") or {}).get("agent", True)),
            werkzeuge=list(ai_agent_service.WERKZEUGE.values()),
        ),
    )


@router.post("/api/ki/auftrag", response_class=JSONResponse)
async def ki_auftrag(request: Request, db: Session = Depends(get_db)):
    """Nachsehen und antworten. Fuehrt nichts aus, was etwas veraendert.

    Der Zusammenhang kommt aus der DATENBANK, nicht aus dem Browser: mitgeschickt wird
    nur eine ``chat_id``. Vorher sandte die Seite den ganzen Verlauf mit und konnte ihn
    damit frei erfinden - ausfuehren liess sich so nichts, aber das Modell liess sich mit
    erfundenen Werkzeugergebnissen fuettern.
    """
    current_user, absage = _ki_nutzer(request, db)
    if current_user is None:
        return absage

    try:
        nutzlast = await request.json()
    except Exception:  # noqa: BLE001
        nutzlast = {}
    nachricht = str((nutzlast or {}).get("nachricht") or "").strip()
    if not nachricht:
        return JSONResponse(status_code=400, content={"detail": "Es fehlt die Frage."})
    if len(nachricht) > 8000:
        return JSONResponse(status_code=400,
                            content={"detail": "Die Frage ist zu lang (max. 8000 Zeichen)."})

    roh_id = (nutzlast or {}).get("chat_id")
    chat = None
    verlauf: list[dict] = []
    if roh_id:
        try:
            chat = ai_chat_service._chat_holen(db, current_user, int(roh_id))
            verlauf = ai_chat_service.verlauf_fuer_ki(db, user=current_user,
                                                      chat_id=chat.id)
        except (ai_chat_service.ChatFehler, TypeError, ValueError):
            return JSONResponse(status_code=404,
                                content={"detail": "Dieses Gespraech gibt es nicht."})
    else:
        chat = ai_chat_service.erstelle(db, user=current_user,
                                        erste_nachricht=nachricht)

    # Die Frage wird VOR dem Aufruf festgehalten. Sonst waere sie bei einem Fehler oder
    # einem Abbruch verloren, und der Nutzer muesste sie neu tippen.
    ai_chat_service.haenge_an(db, chat=chat, rolle="nutzer", inhalt=nachricht)

    try:
        ergebnis = ai_agent_service.beauftrage(
            db, user=current_user, nachricht=nachricht, verlauf=verlauf)
    except ai_service.KiAbgelehnt as exc:
        return JSONResponse(status_code=409, content={
            "detail": str(exc), "abgelehnt": True, "chat_id": chat.id})
    except ai_provider_service.KiFehler as exc:
        return JSONResponse(status_code=502, content={"detail": str(exc),
                                                      "chat_id": chat.id})

    ai_chat_service.haenge_an(
        db, chat=chat, rolle="assistent", inhalt=ergebnis.get("text") or "",
        modell=ergebnis.get("modell"), kosten_cent=ergebnis.get("kosten_cent") or 0.0,
        schritte=ergebnis.get("schritte") or None)

    # Der interne Verlauf enthaelt Inhaltsbloecke (Werkzeugaufrufe und -ergebnisse). Die
    # gehen NICHT an den Browser: dort braucht man sie nicht, sie koennen Serverdaten
    # enthalten, und der Zusammenhang kommt beim naechsten Zug aus der Datenbank.
    ergebnis.pop("verlauf", None)
    ergebnis["chat_id"] = chat.id
    ergebnis["chat_titel"] = chat.titel
    return JSONResponse(ergebnis)


@router.post("/api/ki/ausfuehren", response_class=JSONResponse)
async def ki_ausfuehren(request: Request, db: Session = Depends(get_db)):
    """Eine bestaetigte Aktion ausfuehren - mit vollstaendig neuer Pruefung."""
    current_user, absage = _ki_nutzer(request, db)
    if current_user is None:
        return absage

    try:
        nutzlast = await request.json()
    except Exception:  # noqa: BLE001
        nutzlast = {}
    werkzeug = str((nutzlast or {}).get("werkzeug") or "").strip()
    parameter = (nutzlast or {}).get("parameter")
    if not isinstance(parameter, dict):
        parameter = {}

    try:
        ergebnis = ai_agent_service.fuehre_aus(
            db, user=current_user, werkzeug_name=werkzeug, parameter=parameter)
    except ai_agent_service.AgentFehler as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})
    except Exception as exc:  # noqa: BLE001
        db.rollback()
        return JSONResponse(status_code=500,
                            content={"detail": f"Die Aktion ist fehlgeschlagen: {exc}"})
    return JSONResponse(ergebnis)


# ------------------------------------------------------- Gespraeche aufbewahren

@router.get("/api/ki/chats", response_class=JSONResponse)
def ki_chats(request: Request, db: Session = Depends(get_db)):
    """Die eigenen Gespraeche auflisten."""
    current_user, absage = _ki_nutzer(request, db)
    if current_user is None:
        return absage
    return JSONResponse({"chats": ai_chat_service.liste(db, user=current_user)})


@router.get("/api/ki/chats/{chat_id}", response_class=JSONResponse)
def ki_chat_laden(request: Request, chat_id: int, db: Session = Depends(get_db)):
    """Ein Gespraech mit allen Zuegen laden."""
    current_user, absage = _ki_nutzer(request, db)
    if current_user is None:
        return absage
    try:
        return JSONResponse(ai_chat_service.lade(db, user=current_user,
                                                 chat_id=chat_id))
    except ai_chat_service.ChatFehler as exc:
        return JSONResponse(status_code=404, content={"detail": str(exc)})


@router.post("/api/ki/chats/{chat_id}/loeschen", response_class=JSONResponse)
def ki_chat_loeschen(request: Request, chat_id: int, db: Session = Depends(get_db)):
    current_user, absage = _ki_nutzer(request, db)
    if current_user is None:
        return absage
    try:
        ai_chat_service.loesche(db, user=current_user, chat_id=chat_id)
    except ai_chat_service.ChatFehler as exc:
        return JSONResponse(status_code=404, content={"detail": str(exc)})
    return JSONResponse({"geloescht": chat_id})


@router.post("/api/ki/chats/{chat_id}/titel", response_class=JSONResponse)
async def ki_chat_umbenennen(request: Request, chat_id: int,
                             db: Session = Depends(get_db)):
    current_user, absage = _ki_nutzer(request, db)
    if current_user is None:
        return absage
    try:
        nutzlast = await request.json()
    except Exception:  # noqa: BLE001
        nutzlast = {}
    try:
        chat = ai_chat_service.benenne_um(
            db, user=current_user, chat_id=chat_id,
            titel=str((nutzlast or {}).get("titel") or ""))
    except ai_chat_service.ChatFehler as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})
    return JSONResponse({"id": chat.id, "titel": chat.titel})


@router.post("/api/ki/chats/{chat_id}/anheften", response_class=JSONResponse)
async def ki_chat_anheften(request: Request, chat_id: int,
                           db: Session = Depends(get_db)):
    current_user, absage = _ki_nutzer(request, db)
    if current_user is None:
        return absage
    try:
        nutzlast = await request.json()
    except Exception:  # noqa: BLE001
        nutzlast = {}
    try:
        chat = ai_chat_service.hefte_an(db, user=current_user, chat_id=chat_id,
                                        an=bool((nutzlast or {}).get("an")))
    except ai_chat_service.ChatFehler as exc:
        return JSONResponse(status_code=404, content={"detail": str(exc)})
    return JSONResponse({"id": chat.id, "angeheftet": chat.angeheftet})
