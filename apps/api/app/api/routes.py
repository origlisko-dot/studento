import logging
from pathlib import Path
import shutil
from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from sqlalchemy import desc, select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.db.session import get_db
from app.models.job import AnalysisJob, JobStatus
from app.schemas.job import (
    JobHistoryResponse,
    JobListItem,
    JobStatusResponse,
    ResultResponse,
    UploadResponse,
)
from app.workers.tasks import process_video_job

router = APIRouter()
logger = logging.getLogger(__name__)

ALLOWED_CONTENT_TYPES = {"video/mp4", "video/quicktime", "video/webm"}
ALLOWED_PLATFORMS = {"tiktok", "youtube_shorts"}


@router.post("/upload", response_model=UploadResponse)
def upload_video(
    platform: str = Form(...),
    niche: str = Form(..., min_length=2, max_length=64),
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
):
    settings = get_settings()

    if platform not in ALLOWED_PLATFORMS:
        raise HTTPException(status_code=422, detail="Unsupported platform")

    if file.content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(status_code=415, detail="Unsupported file type")

    file.file.seek(0, 2)
    size_bytes = file.file.tell()
    file.file.seek(0)

    if size_bytes > settings.max_upload_size_mb * 1024 * 1024:
        raise HTTPException(status_code=413, detail="File too large")

    job = AnalysisJob(
        platform=platform,
        niche=niche.strip().lower(),
        file_path="",
        original_filename=file.filename or "upload.mp4",
        status=JobStatus.queued,
    )
    db.add(job)
    db.commit()
    db.refresh(job)

    job_dir = Path(settings.upload_dir) / str(job.id)
    job_dir.mkdir(parents=True, exist_ok=True)
    saved_path = job_dir / job.original_filename
    with saved_path.open("wb") as out_file:
        shutil.copyfileobj(file.file, out_file)

    job.file_path = str(saved_path)
    db.commit()

    process_video_job.delay(str(job.id))
    logger.info("Queued job %s", job.id)
    return UploadResponse(job_id=job.id, status=job.status)


@router.get("/jobs/{job_id}", response_model=JobStatusResponse)
def get_job_status(job_id: UUID, db: Session = Depends(get_db)):
    job = db.get(AnalysisJob, job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    return JobStatusResponse(
        job_id=job.id,
        status=job.status,
        error_message=job.error_message,
        created_at=job.created_at,
        updated_at=job.updated_at,
    )


@router.get("/results/{job_id}", response_model=ResultResponse)
def get_result(job_id: UUID, db: Session = Depends(get_db)):
    job = db.get(AnalysisJob, job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")

    return ResultResponse(job_id=job.id, status=job.status, result=job.result_payload)


@router.get("/history", response_model=JobHistoryResponse)
def get_history(db: Session = Depends(get_db)):
    jobs = db.scalars(select(AnalysisJob).order_by(desc(AnalysisJob.created_at)).limit(50)).all()
    return JobHistoryResponse(
        jobs=[
            JobListItem(
                job_id=job.id,
                platform=job.platform,
                niche=job.niche,
                status=job.status,
                created_at=job.created_at,
            )
            for job in jobs
        ]
    )
