from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field

from app.models.job import JobStatus


class UploadResponse(BaseModel):
    job_id: UUID
    status: JobStatus


class JobStatusResponse(BaseModel):
    job_id: UUID
    status: JobStatus
    error_message: str | None = None
    created_at: datetime
    updated_at: datetime


class TimelineSegment(BaseModel):
    second: int
    insight: str
    severity: str


class UploadPackage(BaseModel):
    titles: list[str]
    description: str
    hashtags: list[str]
    pinned_comment: str
    cta: str


class EditingSuggestion(BaseModel):
    timestamp: str
    recommendation: str
    impact: str


class ProxySubScore(BaseModel):
    name: str
    weight: float
    score: float
    rationale: str


class AnalysisResult(BaseModel):
    platform: str
    niche: str
    proxy_score: float = Field(ge=0, le=100)
    scoring_notes: str
    subscores: list[ProxySubScore]
    timeline_diagnosis: list[TimelineSegment]
    upload_package: UploadPackage
    editing_suggestions: list[EditingSuggestion]
    generated_at: datetime


class ResultResponse(BaseModel):
    job_id: UUID
    status: JobStatus
    result: AnalysisResult | None


class JobListItem(BaseModel):
    job_id: UUID
    platform: str
    niche: str
    status: JobStatus
    created_at: datetime


class JobHistoryResponse(BaseModel):
    jobs: list[JobListItem]
