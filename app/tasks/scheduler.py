from apscheduler.schedulers.background import BackgroundScheduler

from app.core.config import get_settings


_SCHEDULER: BackgroundScheduler | None = None


def get_scheduler() -> BackgroundScheduler:
    global _SCHEDULER
    if _SCHEDULER is None:
        settings = get_settings()
        _SCHEDULER = BackgroundScheduler(timezone=settings.scheduler_timezone)
    return _SCHEDULER


def start_scheduler() -> None:
    scheduler = get_scheduler()
    if not scheduler.running:
        scheduler.start()


def shutdown_scheduler() -> None:
    scheduler = get_scheduler()
    if scheduler.running:
        scheduler.shutdown(wait=False)
