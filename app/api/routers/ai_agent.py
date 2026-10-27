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
from app.services import ai_agent_service, ai_provider_service, ai_service
from app.services.auth_service import get_current_user_from_session
from app.web.routes.pages import build_context, templates


router = APIRouter(include_in_schema=False)

# Wie viele Zuege des Gespraechs hoechstens zurueckgeschickt werden duerfen. Der Verlauf
# kommt aus dem Browser und wird bei jedem Zug mitgesendet - ohne Grenze waechst er und
# mit ihm der Preis jeder Anfrage.
MAX_VERLAUF = 24


def _nutzer(request: Request, db: Session):
    return get_current_user_from_session(request, db)


def _verlauf_lesen(roh) -> list[dict]:
    """Den mitgeschickten Verlauf auf das Erlaubte beschneiden.

    Er kommt aus dem Browser, also wird nur uebernommen, was Form hat: Rolle und Text.
    Inhaltsbloecke (Werkzeugergebnisse) nimmt diese Route NICHT an - die entstehen nur
    innerhalb eines Auftrags und haben ausserhalb nichts zu suchen.
    """
    if not isinstance(roh, list):
        return []
    verlauf: list[dict] = []
    for zug in roh[-MAX_VERLAUF:]:
        if not isinstance(zug, dict):
            continue
        inhalt = zug.get("inhalt")
        if not isinstance(inhalt, str) or not inhalt.strip():
            continue
        verlauf.append({
            "rolle": "assistent" if zug.get("rolle") == "assistent" else "nutzer",
            "inhalt": inhalt[:12_000]})
    return verlauf


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
    """Nachsehen und antworten. Fuehrt nichts aus, was etwas veraendert."""
    current_user = _nutzer(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

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

    try:
        ergebnis = ai_agent_service.beauftrage(
            db, user=current_user, nachricht=nachricht,
            verlauf=_verlauf_lesen((nutzlast or {}).get("verlauf")))
    except ai_service.KiAbgelehnt as exc:
        return JSONResponse(status_code=409, content={"detail": str(exc), "abgelehnt": True})
    except ai_provider_service.KiFehler as exc:
        return JSONResponse(status_code=502, content={"detail": str(exc)})

    # Der interne Verlauf enthaelt Inhaltsbloecke (Werkzeugaufrufe und -ergebnisse). Die
    # gehen NICHT an den Browser zurueck: dort braucht man sie nicht, sie koennen
    # Serverdaten enthalten, und sie wuerden beim naechsten Zug ohnehin verworfen.
    ergebnis["verlauf"] = [z for z in (ergebnis.get("verlauf") or [])
                           if isinstance(z.get("inhalt"), str)][-MAX_VERLAUF:]
    return JSONResponse(ergebnis)


@router.post("/api/ki/ausfuehren", response_class=JSONResponse)
async def ki_ausfuehren(request: Request, db: Session = Depends(get_db)):
    """Eine bestaetigte Aktion ausfuehren - mit vollstaendig neuer Pruefung."""
    current_user = _nutzer(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

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
