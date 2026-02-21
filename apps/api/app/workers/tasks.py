import logging
from uuid import UUID

from app.db.session import SessionLocal
from app.models.job import AnalysisJob, JobStatus
from app.services.analysis_proxy import build_mock_analysis
from app.workers.celery_app import celery_app

logger = logging.getLogger(__name__)


@celery_app.task(name="app.workers.tasks.process_video_job")
def process_video_job(job_id: str) -> None:
    db = SessionLocal()
    try:
        job = db.get(AnalysisJob, UUID(job_id))
        if not job:
            logger.error("Job %s not found", job_id)
            return

        job.status = JobStatus.processing
        db.commit()

        result_payload = build_mock_analysis(job_id=job_id, platform=job.platform, niche=job.niche)
        job.result_payload = result_payload
        job.status = JobStatus.completed
        db.commit()
        logger.info("Job %s completed", job_id)
    except Exception as exc:  # noqa: BLE001
        logger.exception("Job %s failed", job_id)
        job = db.get(AnalysisJob, UUID(job_id))
        if job:
            job.status = JobStatus.failed
            job.error_message = str(exc)
            db.commit()
    finally:
        db.close()
