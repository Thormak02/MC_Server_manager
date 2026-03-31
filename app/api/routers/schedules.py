from typing import Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services.auth_service import get_current_user_from_session
from app.services.schedule_service import (
    create_job,
    delete_job,
    get_job,
    list_jobs_for_server,
    run_job_now,
    set_job_enabled,
)
from app.services.server_service import can_control_server, can_view_server, get_server_by_id
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)


def _require_user(request: Request, db: Session):
    user = get_current_user_from_session(request, db)
    if user is None:
        return None
    return user


@router.get("/servers/{server_id}/schedules", response_class=HTMLResponse)
def schedules_page(
    request: Request,
    server_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_view_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    jobs = list_jobs_for_server(db, server_id)
    return templates.TemplateResponse(
        request,
        "schedules.html",
        build_context(
            request,
            current_user=current_user,
            page_title=f"Scheduling: {server.name}",
            server=server,
            jobs=jobs,
            can_manage=can_control_server(db, current_user, server),
        ),
    )


@router.post("/servers/{server_id}/schedules")
def create_schedule_action(
    request: Request,
    server_id: int,
    job_type: Annotated[str, Form()],
    schedule_expression: Annotated[str, Form()],
    command: Annotated[str | None, Form()] = None,
    delay_seconds: Annotated[str | None, Form()] = None,
    warning_message: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    payload: dict[str, object] = {}
    if command and command.strip():
        payload["command"] = command.strip()
    if delay_seconds and delay_seconds.strip():
        payload["delay_seconds"] = int(delay_seconds.strip())
    if warning_message and warning_message.strip():
        payload["warning_message"] = warning_message.strip()

    try:
        create_job(
            db,
            server_id=server.id,
            job_type=job_type,
            schedule_expression=schedule_expression,
            command_payload=payload,
            is_enabled=True,
        )
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/schedules", status_code=303)

    push_flash(request, "Job angelegt.", "success")
    return RedirectResponse(url=f"/servers/{server_id}/schedules", status_code=303)


@router.post("/servers/{server_id}/schedules/{job_id}/toggle")
def toggle_schedule_action(
    request: Request,
    server_id: int,
    job_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    job = get_job(db, job_id)
    if not job or job.server_id != server_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Job not found")

    set_job_enabled(db, job, not job.is_enabled)
    push_flash(request, "Job-Status aktualisiert.", "success")
    return RedirectResponse(url=f"/servers/{server_id}/schedules", status_code=303)


@router.post("/servers/{server_id}/schedules/{job_id}/run")
def run_schedule_now_action(
    request: Request,
    server_id: int,
    job_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    job = get_job(db, job_id)
    if not job or job.server_id != server_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Job not found")

    ok, message = run_job_now(db, job)
    push_flash(request, message, "success" if ok else "error")
    return RedirectResponse(url=f"/servers/{server_id}/schedules", status_code=303)


@router.post("/servers/{server_id}/schedules/{job_id}/delete")
def delete_schedule_action(
    request: Request,
    server_id: int,
    job_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    job = get_job(db, job_id)
    if not job or job.server_id != server_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Job not found")

    delete_job(db, job)
    push_flash(request, "Job geloescht.", "success")
    return RedirectResponse(url=f"/servers/{server_id}/schedules", status_code=303)
