import json
from datetime import datetime, timezone

from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.session import SessionLocal
from app.models.scheduled_job import ScheduledJob
from app.models.server import Server
from app.services import audit_service
from app.services.process_service import (
    queue_restart,
    send_console_command,
    start_server,
    stop_server,
)
from app.tasks.scheduler import get_scheduler


def _scheduler_job_id(job_id: int) -> str:
    return f"scheduled_job_{job_id}"


def _parse_trigger(schedule_expression: str):
    normalized = schedule_expression.strip()
    if normalized.lower().startswith("interval:"):
        raw_value = normalized.split(":", 1)[1].strip()
        seconds = int(raw_value)
        if seconds <= 0:
            raise ValueError("Intervall muss > 0 Sekunden sein.")
        return IntervalTrigger(seconds=seconds)

    try:
        return CronTrigger.from_crontab(normalized)
    except ValueError as exc:
        raise ValueError(
            "Ungueltiger Zeitplan. Erlaubt: 5-feld Cron (z.B. '0 4 * * *') oder interval:<sekunden>."
        ) from exc


def _job_payload(job: ScheduledJob) -> dict[str, object]:
    if not job.command_payload:
        return {}
    try:
        return json.loads(job.command_payload)
    except json.JSONDecodeError:
        return {}


def list_jobs_for_server(db: Session, server_id: int) -> list[ScheduledJob]:
    return list(
        db.scalars(
            select(ScheduledJob)
            .where(ScheduledJob.server_id == server_id)
            .order_by(ScheduledJob.id.asc())
        ).all()
    )


def _sync_single_job(db: Session, job: ScheduledJob) -> None:
    scheduler = get_scheduler()
    scheduler_id = _scheduler_job_id(job.id)

    if not job.is_enabled:
        scheduler.remove_job(scheduler_id) if scheduler.get_job(scheduler_id) else None
        job.next_run_at = None
        db.add(job)
        db.commit()
        return

    trigger = _parse_trigger(job.schedule_expression)
    scheduler.add_job(
        _run_job_by_id,
        trigger=trigger,
        args=[job.id],
        id=scheduler_id,
        replace_existing=True,
        coalesce=True,
        max_instances=1,
    )
    scheduled = scheduler.get_job(scheduler_id)
    job.next_run_at = scheduled.next_run_time if scheduled else None
    db.add(job)
    db.commit()


def sync_all_jobs() -> None:
    with SessionLocal() as db:
        jobs = list(db.scalars(select(ScheduledJob)).all())
        for job in jobs:
            _sync_single_job(db, job)


def create_job(
    db: Session,
    *,
    server_id: int,
    job_type: str,
    schedule_expression: str,
    command_payload: dict[str, object] | None = None,
    is_enabled: bool = True,
) -> ScheduledJob:
    _parse_trigger(schedule_expression)
    job = ScheduledJob(
        server_id=server_id,
        job_type=job_type.strip().lower(),
        schedule_expression=schedule_expression.strip(),
        command_payload=json.dumps(command_payload or {}, ensure_ascii=False),
        is_enabled=is_enabled,
    )
    db.add(job)
    db.commit()
    db.refresh(job)
    _sync_single_job(db, job)
    return job


def delete_job(db: Session, job: ScheduledJob) -> None:
    scheduler = get_scheduler()
    scheduler_id = _scheduler_job_id(job.id)
    if scheduler.get_job(scheduler_id):
        scheduler.remove_job(scheduler_id)
    db.delete(job)
    db.commit()


def get_job(db: Session, job_id: int) -> ScheduledJob | None:
    return db.get(ScheduledJob, job_id)


def set_job_enabled(db: Session, job: ScheduledJob, enabled: bool) -> ScheduledJob:
    job.is_enabled = enabled
    db.add(job)
    db.commit()
    db.refresh(job)
    _sync_single_job(db, job)
    return job


def run_job_now(db: Session, job: ScheduledJob) -> tuple[bool, str]:
    return _execute_job(db, job)


def _run_job_by_id(job_id: int) -> None:
    with SessionLocal() as db:
        job = db.get(ScheduledJob, job_id)
        if not job or not job.is_enabled:
            return
        _execute_job(db, job)
        scheduler_job = get_scheduler().get_job(_scheduler_job_id(job.id))
        job.next_run_at = scheduler_job.next_run_time if scheduler_job else None
        db.add(job)
        db.commit()


def _execute_job(db: Session, job: ScheduledJob) -> tuple[bool, str]:
    server = db.get(Server, job.server_id)
    if server is None:
        return False, "Server nicht gefunden."

    payload = _job_payload(job)
    job_type = job.job_type.lower()

    if job_type == "start":
        ok, message = start_server(db, server, initiated_by_user_id=None)
    elif job_type == "stop":
        ok, message = stop_server(db, server, initiated_by_user_id=None, force=False)
    elif job_type == "restart":
        ok, message = queue_restart(
            db,
            server,
            initiated_by_user_id=None,
            delay_seconds=int(payload.get("delay_seconds", 0) or 0),
            warning_message=str(payload.get("warning_message", "") or ""),
            source="scheduled_job",
        )
    elif job_type == "command":
        command = str(payload.get("command", "") or "")
        ok, message = send_console_command(db, server, command, initiated_by_user_id=None)
    else:
        ok, message = False, f"Unbekannter Job-Typ: {job.job_type}"

    job.last_run_at = datetime.now(timezone.utc)
    db.add(job)
    db.commit()

    audit_service.log_action(
        db,
        action="scheduled_job.execute",
        server_id=server.id,
        details=f"job_id={job.id} type={job.job_type} ok={ok} msg={message}",
    )
    return ok, message
