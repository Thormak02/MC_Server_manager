from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.db.session import get_db
from app.services.auth_service import get_current_user_from_session
from app.services.security_service import list_security_events
from app.web.routes.pages import build_context, templates


router = APIRouter(include_in_schema=False)


def _require_super_admin(request: Request, db: Session):
    user = get_current_user_from_session(request, db)
    if user is None:
        return None
    if user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")
    return user


@router.get("/security-events", response_class=HTMLResponse)
def security_events_page(
    request: Request,
    limit: int = 200,
    user_id: int | None = None,
    username: str | None = None,
    event_type: str | None = None,
    ip_address: str | None = None,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    entries = list_security_events(
        db,
        limit=limit,
        user_id=user_id,
        username=username,
        event_type=event_type,
        ip_address=ip_address,
    )
    return templates.TemplateResponse(
        request,
        "security_events.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Security Events",
            entries=entries,
            limit=max(1, min(limit, 2000)),
            filter_user_id=user_id,
            filter_username=username or "",
            filter_event_type=event_type or "",
            filter_ip=ip_address or "",
        ),
    )


@router.get("/api/security-events", response_class=JSONResponse)
def security_events_api(
    request: Request,
    limit: int = 200,
    user_id: int | None = None,
    username: str | None = None,
    event_type: str | None = None,
    ip_address: str | None = None,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    entries = list_security_events(
        db,
        limit=limit,
        user_id=user_id,
        username=username,
        event_type=event_type,
        ip_address=ip_address,
    )
    payload = [
        {
            "id": row.id,
            "created_at": row.created_at.isoformat() if row.created_at else None,
            "user_id": row.user_id,
            "username": row.username,
            "event_type": row.event_type,
            "ip_address": row.ip_address,
            "details": row.details,
        }
        for row in entries
    ]
    return JSONResponse({"items": payload, "count": len(payload)})

