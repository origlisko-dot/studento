# Viral Proxy Lab (V1 Foundation)

Production-minded monorepo foundation for uploading short-form videos and running an **explainable Algorithm Proxy** analysis pipeline.

> This app **does not** claim to replicate TikTok/YouTube ranking algorithms. It provides transparent proxy scoring and diagnostics.

## Repository tree

```text
.
├── apps
│   ├── api
│   │   ├── app
│   │   │   ├── api/routes.py
│   │   │   ├── core/config.py
│   │   │   ├── db/{base.py,session.py}
│   │   │   ├── models/job.py
│   │   │   ├── schemas/job.py
│   │   │   ├── services/analysis_proxy.py
│   │   │   ├── workers/{celery_app.py,tasks.py}
│   │   │   └── main.py
│   │   ├── requirements.txt
│   │   └── celery_worker.py
│   └── web
│       ├── app/{upload,jobs/[jobId],results/[jobId],history}
│       ├── components/api.ts
│       └── package.json
├── infra/docker-compose.yml
├── packages/contracts/index.ts
└── storage/uploads
```

## Tech stack
- Frontend: Next.js App Router + TypeScript + Tailwind
- Backend: FastAPI + SQLAlchemy
- Jobs: Celery + Redis
- DB: PostgreSQL
- Storage: local filesystem (`storage/uploads/<job_id>/`)

## Exact local run steps

### 1) Start infra
```bash
cd /workspace/studento
docker compose -f infra/docker-compose.yml up -d
```

### 2) Run backend API
```bash
cd /workspace/studento/apps/api
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --port 8000
```

### 3) Run Celery worker
```bash
cd /workspace/studento/apps/api
source .venv/bin/activate
celery -A celery_worker.celery_app worker --loglevel=info
```

### 4) Run frontend
```bash
cd /workspace/studento/apps/web
npm install
cp .env.example .env.local
npm run dev
```

Frontend URL: `http://localhost:3000`

## API endpoints summary
- `POST /api/upload`
  - multipart form: `file`, `platform` (`tiktok|youtube_shorts`), `niche`
  - persists job record + file, enqueues Celery task
- `GET /api/jobs/{job_id}`
  - status polling (`queued|processing|completed|failed`)
- `GET /api/results/{job_id}`
  - returns result payload when available
- `GET /api/history`
  - returns recent jobs

## What is real vs mocked

### Real now
- End-to-end upload flow from Next.js to FastAPI
- File validation (platform/content type/file size)
- PostgreSQL persistence for jobs/status/results
- Asynchronous background processing via Celery + Redis
- Status polling + result/history UI
- Shared TypeScript contracts aligned to backend responses

### Mocked for Phase 1
- Analysis internals are deterministic mock generation (`analysis_proxy.py`) using seeded pseudo-random values per `job_id/platform/niche`
- Timeline diagnosis and editing suggestions are template-based

## Phase 2 integration plan (file-level TODOs)
- `apps/api/app/services/analysis_proxy.py`
  - TODO: replace mock signals with real features from ffmpeg cuts, ASR transcript, OCR overlays, audio dynamics
- `apps/api/app/workers/tasks.py`
  - TODO: split into pipeline stages (extract -> transcribe -> score -> package copy), add retries and dead-letter handling
- `apps/api/app/api/routes.py`
  - TODO: add signed upload URLs/object storage abstraction and antivirus scan hook
- `apps/api/app/models/job.py`
  - TODO: add detailed stage timings + artifact pointers
- `apps/web/app/results/[jobId]/page.tsx`
  - TODO: richer charts for timeline/subscore visualization and downloadable report

