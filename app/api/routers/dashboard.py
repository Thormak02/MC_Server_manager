from fastapi import APIRouter, Depends, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services.auth_service import get_current_user_from_session
from app.services.process_service import refresh_runtime_states
from app.services.server_service import list_servers_for_user
from app.services.server_service import get_dashboard_summary
from app.web.routes.pages import build_context, templates


router = APIRouter(include_in_schema=False)


@router.get("/dashboard", response_class=HTMLResponse)
def dashboard_page(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = get_current_user_from_session(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    refresh_runtime_states(db, list_servers_for_user(db, current_user))
    summary = get_dashboard_summary(db, current_user)
    return templates.TemplateResponse(
        request,
        "dashboard.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Dashboard",
            summary=summary,
            servers=summary["servers"],
        ),
    )
