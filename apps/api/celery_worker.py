from app.workers.celery_app import celery_app
from app.workers import tasks as _tasks  # noqa: F401

__all__ = ("celery_app",)
