import calendar as pycalendar
from datetime import date, datetime, timedelta
from typing import Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services.auth_service import get_current_user_from_session
from app.services.schedule_service import (
    _scheduler_job_id,
    create_job,
    delete_job,
    get_job,
    list_job_history_for_server,
    list_jobs_for_server,
    run_job_now,
    set_job_enabled,
)
from app.services.server_service import can_control_server, can_view_server, get_server_by_id
from app.tasks.scheduler import get_scheduler
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)


def _require_user(request: Request, db: Session):
    user = get_current_user_from_session(request, db)
    if user is None:
        return None
    return user


def _parse_optional_int(raw: str | None, *, field_name: str) -> int | None:
    if raw is None:
        return None
    stripped = raw.strip()
    if not stripped:
        return None
    try:
        return int(stripped)
    except ValueError as exc:
        raise ValueError(f"Ungueltiger Integer fuer {field_name}.") from exc


def _parse_required_date(raw: str | None) -> date:
    value = (raw or "").strip()
    if not value:
        raise ValueError("Bitte ein Datum waehlen.")
    try:
        return datetime.strptime(value, "%Y-%m-%d").date()
    except ValueError as exc:
        raise ValueError("Ungueltiges Datum. Erwartet: YYYY-MM-DD.") from exc


def _parse_required_time(raw: str | None) -> tuple[int, int]:
    value = (raw or "").strip()
    if not value:
        raise ValueError("Bitte eine Uhrzeit waehlen.")
    try:
        parsed = datetime.strptime(value, "%H:%M")
        return parsed.hour, parsed.minute
    except ValueError as exc:
        raise ValueError("Ungueltige Uhrzeit. Erwartet: HH:MM.") from exc


def _build_schedule_expression(
    *,
    schedule_mode: str | None,
    schedule_expression: str | None,
    planner_date: str | None,
    planner_time: str | None,
    weekday: str | None,
    day_of_month: str | None,
    interval_minutes: str | None,
) -> str:
    mode = (schedule_mode or "advanced").strip().lower()
    if mode in {"advanced", "cron", "raw"}:
        expression = (schedule_expression or "").strip()
        if not expression:
            raise ValueError("Zeitplan fehlt. Bitte Cron/Interval eintragen.")
        return expression

    if mode == "interval":
        minutes = _parse_optional_int(interval_minutes, field_name="interval_minutes")
        if minutes is None or minutes <= 0:
            raise ValueError("Intervall muss mindestens 1 Minute sein.")
        return f"interval:{minutes * 60}"

    target_date = _parse_required_date(planner_date)
    hour, minute = _parse_required_time(planner_time)

    if mode == "once":
        run_at = datetime(
            target_date.year,
            target_date.month,
            target_date.day,
            hour,
            minute,
        )
        return f"once:{run_at.strftime('%Y-%m-%dT%H:%M')}"

    if mode == "daily":
        return f"{minute} {hour} * * *"

    if mode == "weekly":
        selected_weekday = (weekday or "").strip()
        if selected_weekday:
            try:
                cron_weekday = int(selected_weekday)
            except ValueError as exc:
                raise ValueError("Ungueltiger Wochentag.") from exc
            if cron_weekday < 0 or cron_weekday > 6:
                raise ValueError("Wochentag muss zwischen 0 (So) und 6 (Sa) liegen.")
        else:
            # Python weekday: Mo=0..So=6 -> cron weekday: So=0..Sa=6
            cron_weekday = (target_date.weekday() + 1) % 7
        return f"{minute} {hour} * * {cron_weekday}"

    if mode == "monthly":
        parsed_day = _parse_optional_int(day_of_month, field_name="day_of_month")
        month_day = parsed_day if parsed_day is not None else target_date.day
        if month_day < 1 or month_day > 31:
            raise ValueError("Monatstag muss zwischen 1 und 31 liegen.")
        return f"{minute} {hour} {month_day} * *"

    raise ValueError(f"Unbekannter Scheduling-Modus: {mode}")


def _parse_calendar_month(request: Request) -> tuple[int, int]:
    today = datetime.now().date()
    raw_year = request.query_params.get("year")
    raw_month = request.query_params.get("month")

    try:
        year = int(raw_year) if raw_year is not None else today.year
    except ValueError:
        year = today.year
    try:
        month = int(raw_month) if raw_month is not None else today.month
    except ValueError:
        month = today.month

    if year < 1970 or year > 2100:
        year = today.year
    if month < 1 or month > 12:
        month = today.month
    return year, month


def _localize(tz, naive_dt: datetime) -> datetime:
    """Naive datetime in die Scheduler-Zeitzone heben (pytz oder zoneinfo)."""
    localize = getattr(tz, "localize", None)
    if callable(localize):
        try:
            return localize(naive_dt)
        except Exception:
            return naive_dt.replace(tzinfo=tz)
    return naive_dt.replace(tzinfo=tz)


def _start_of_next_day(dt: datetime) -> datetime:
    nd = (dt + timedelta(days=1)).date()
    return datetime(nd.year, nd.month, nd.day, tzinfo=dt.tzinfo)


def _collect_fire_times(
    trigger,
    window_start: datetime,
    window_end: datetime,
    *,
    per_day_cap: int = 6,
    hard_cap: int = 1500,
) -> dict[date, list[datetime]]:
    """Alle Feuerzeitpunkte eines Triggers im Fenster, pro Tag gebuendelt.

    Pro Tag werden hoechstens ``per_day_cap`` Zeitpunkte gesammelt; danach
    wird zum Folgetag gesprungen, damit hochfrequente Intervalle den Kalender
    nicht sprengen. ``hard_cap`` begrenzt die Gesamtiteration als Notbremse.
    """
    per_day: dict[date, list[datetime]] = {}
    prev: datetime | None = None
    now = window_start
    iterations = 0
    while iterations < hard_cap:
        iterations += 1
        try:
            fire = trigger.get_next_fire_time(prev, now)
        except Exception:
            break
        if fire is None or fire > window_end:
            break
        bucket = per_day.setdefault(fire.date(), [])
        if len(bucket) < per_day_cap:
            bucket.append(fire)
            prev = fire
            now = fire + timedelta(microseconds=1)
        else:
            # Tag ist voll -> zum Anfang des Folgetags springen.
            prev = None
            now = _start_of_next_day(fire)
    return per_day


def _build_calendar_view(
    jobs: list,
    *,
    year: int,
    month: int,
) -> dict[str, object]:
    first_day = date(year, month, 1)
    prev_day = first_day - timedelta(days=1)
    next_month_first = (first_day.replace(day=28) + timedelta(days=4)).replace(day=1)

    cal = pycalendar.Calendar(firstweekday=0)  # Monday
    month_weeks = cal.monthdatescalendar(year, month)
    grid_start_date = month_weeks[0][0]
    grid_end_date = month_weeks[-1][-1]

    scheduler = get_scheduler()
    tz = getattr(scheduler, "timezone", None)
    events_by_day: dict[str, list[dict[str, object]]] = {}

    if tz is not None:
        window_start = _localize(
            tz, datetime(grid_start_date.year, grid_start_date.month, grid_start_date.day)
        )
        window_end = _localize(
            tz,
            datetime(grid_end_date.year, grid_end_date.month, grid_end_date.day)
            + timedelta(days=1),
        ) - timedelta(microseconds=1)

        for job in jobs:
            if not getattr(job, "is_enabled", False):
                continue
            scheduler_job = scheduler.get_job(_scheduler_job_id(job.id))
            if scheduler_job is None or scheduler_job.trigger is None:
                continue
            per_day = _collect_fire_times(scheduler_job.trigger, window_start, window_end)
            for day, fires in per_day.items():
                day_key = day.isoformat()
                target = events_by_day.setdefault(day_key, [])
                for fire in fires:
                    target.append(
                        {
                            "time": fire.strftime("%H:%M"),
                            "job_type": getattr(job, "job_type", "-"),
                            "job_id": getattr(job, "id", 0),
                            "_sort": fire,
                        }
                    )

    for events in events_by_day.values():
        events.sort(key=lambda item: item["_sort"])
        for item in events:
            item.pop("_sort", None)

    weeks: list[list[dict[str, object]]] = []
    today = datetime.now().date()
    for week in month_weeks:
        cells: list[dict[str, object]] = []
        for day in week:
            day_key = day.isoformat()
            day_events = events_by_day.get(day_key, [])
            cells.append(
                {
                    "iso_date": day_key,
                    "day_number": day.day,
                    "in_month": day.month == month,
                    "is_today": day == today,
                    "events": day_events[:3],
                    "more_count": max(0, len(day_events) - 3),
                }
            )
        weeks.append(cells)

    month_label = f"{pycalendar.month_name[month]} {year}"
    return {
        "month_label": month_label,
        "year": year,
        "month": month,
        "prev_year": prev_day.year,
        "prev_month": prev_day.month,
        "next_year": next_month_first.year,
        "next_month": next_month_first.month,
        "weekday_labels": ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"],
        "weeks": weeks,
    }


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
    history = list_job_history_for_server(db, server_id)
    job_types = {job.id: job.job_type for job in jobs}
    now_local = datetime.now()
    calendar_year, calendar_month = _parse_calendar_month(request)
    calendar_view = _build_calendar_view(jobs, year=calendar_year, month=calendar_month)
    return templates.TemplateResponse(
        request,
        "schedules.html",
        build_context(
            request,
            current_user=current_user,
            page_title=f"Scheduling: {server.name}",
            server=server,
            jobs=jobs,
            history=history,
            job_types=job_types,
            can_manage=can_control_server(db, current_user, server),
            schedule_calendar=calendar_view,
            schedule_defaults={
                "date": now_local.date().isoformat(),
                "time": now_local.replace(second=0, microsecond=0).strftime("%H:%M"),
            },
            weekday_options=[
                {"value": "1", "label": "Montag"},
                {"value": "2", "label": "Dienstag"},
                {"value": "3", "label": "Mittwoch"},
                {"value": "4", "label": "Donnerstag"},
                {"value": "5", "label": "Freitag"},
                {"value": "6", "label": "Samstag"},
                {"value": "0", "label": "Sonntag"},
            ],
        ),
    )


@router.post("/servers/{server_id}/schedules")
def create_schedule_action(
    request: Request,
    server_id: int,
    job_type: Annotated[str, Form()],
    schedule_expression: Annotated[str | None, Form()] = None,
    schedule_mode: Annotated[str | None, Form()] = None,
    planner_date: Annotated[str | None, Form()] = None,
    planner_time: Annotated[str | None, Form()] = None,
    weekday: Annotated[str | None, Form()] = None,
    day_of_month: Annotated[str | None, Form()] = None,
    interval_minutes: Annotated[str | None, Form()] = None,
    command: Annotated[str | None, Form()] = None,
    delay_seconds: Annotated[str | None, Form()] = None,
    warning_message: Annotated[str | None, Form()] = None,
    backup_scope: Annotated[str | None, Form()] = None,
    pre_action: Annotated[str | None, Form()] = None,
    backup_name: Annotated[str | None, Form()] = None,
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

    normalized_type = (job_type or "").strip().lower()
    payload: dict[str, object] = {}
    if command and command.strip():
        payload["command"] = command.strip()
    try:
        parsed_delay = _parse_optional_int(delay_seconds, field_name="delay_seconds")
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/schedules", status_code=303)
    if parsed_delay is not None:
        payload["delay_seconds"] = parsed_delay
    if warning_message and warning_message.strip():
        payload["warning_message"] = warning_message.strip()
    if normalized_type == "backup":
        payload["backup_scope"] = (backup_scope or "full").strip().lower() or "full"
        payload["pre_action"] = (pre_action or "none").strip().lower() or "none"
        if backup_name and backup_name.strip():
            payload["backup_name"] = backup_name.strip()
    if normalized_type == "command" and not payload.get("command"):
        push_flash(request, "Bei job_type=command ist ein Command erforderlich.", "error")
        return RedirectResponse(url=f"/servers/{server_id}/schedules", status_code=303)

    try:
        computed_schedule_expression = _build_schedule_expression(
            schedule_mode=schedule_mode,
            schedule_expression=schedule_expression,
            planner_date=planner_date,
            planner_time=planner_time,
            weekday=weekday,
            day_of_month=day_of_month,
            interval_minutes=interval_minutes,
        )
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/schedules", status_code=303)

    try:
        create_job(
            db,
            server_id=server.id,
            job_type=normalized_type,
            schedule_expression=computed_schedule_expression,
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
