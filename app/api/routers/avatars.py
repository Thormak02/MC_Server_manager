"""Ausliefern der Spieler-Skins durch den Manager.

Hinter dem Login, wie alles ausser der Anmeldeseite. Der Browser spricht nur mit
dem Manager - warum das so sein muss, steht in app/services/avatar_service.py.
"""
from fastapi import APIRouter, Depends, Request
from fastapi.responses import RedirectResponse, Response
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services import avatar_service
from app.services.auth_service import get_current_user_from_session


router = APIRouter(include_in_schema=False)


@router.get("/api/avatar/{spieler}")
def spieler_avatar(spieler: str, request: Request, db: Session = Depends(get_db)):
    if get_current_user_from_session(request, db) is None:
        return RedirectResponse(url="/login", status_code=303)

    png, echt = avatar_service.skin_png(spieler)
    # Der Ersatzkopf wird nur kurz gehalten: sobald der Spieler erreichbar ist,
    # soll sein echter Skin erscheinen, ohne dass der Browser tagelang das graue
    # Bild behaelt. Private-Cache, weil die Antwort hinter dem Login liegt.
    sekunden = 86400 if echt else 300
    return Response(
        content=png,
        media_type="image/png",
        headers={"Cache-Control": f"private, max-age={sekunden}"},
    )
