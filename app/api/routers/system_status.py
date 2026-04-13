from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.db.session import get_db
from app.services.auth_service import get_current_user_from_session
from app.services.system_service import get_host_processes, get_managed_processes, get_system_summary
from app.web.routes.pages import build_context, templates


router = APIRouter(include_in_schema=False)


def _require_super_admin(request: Request, db: Session):
    user = get_current_user_from_session(request, db)
    if user is None:
        return None
    if user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")
    return user


@router.get("/system-status", response_class=HTMLResponse)
def system_status_page(
    request: Request,
    limit: int = 30,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    summary = get_system_summary(db, current_user)
    managed = get_managed_processes(db, current_user)
    processes = get_host_processes(limit=limit)
    return templates.TemplateResponse(
        request,
        "system_status.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Systemstatus",
            summary=summary,
            managed=managed,
            processes=processes,
            process_limit=max(1, min(limit, 500)),
            now=datetime.now(),
        ),
    )


@router.get("/api/system/summary", response_class=JSONResponse)
def api_system_summary(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})
    return JSONResponse(get_system_summary(db, current_user))


@router.get("/api/system/processes", response_class=JSONResponse)
def api_system_processes(
    request: Request,
    limit: int = 50,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    return JSONResponse(
        {
            "managed": get_managed_processes(db, current_user),
            "host": get_host_processes(limit=limit),
            "count": max(1, min(limit, 500)),
        }
    )

