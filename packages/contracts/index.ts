export type JobStatus = "queued" | "processing" | "completed" | "failed";

export interface UploadResponse {
  job_id: string;
  status: JobStatus;
}

export interface JobStatusResponse {
  job_id: string;
  status: JobStatus;
  error_message?: string | null;
  created_at: string;
  updated_at: string;
}

export interface ProxySubScore {
  name: string;
  weight: number;
  score: number;
  rationale: string;
}

export interface TimelineSegment {
  second: number;
  insight: string;
  severity: string;
}

export interface UploadPackage {
  titles: string[];
  description: string;
  hashtags: string[];
  pinned_comment: string;
  cta: string;
}

export interface EditingSuggestion {
  timestamp: string;
  recommendation: string;
  impact: string;
}

export interface AnalysisResult {
  platform: string;
  niche: string;
  proxy_score: number;
  scoring_notes: string;
  subscores: ProxySubScore[];
  timeline_diagnosis: TimelineSegment[];
  upload_package: UploadPackage;
  editing_suggestions: EditingSuggestion[];
  generated_at: string;
}

export interface ResultResponse {
  job_id: string;
  status: JobStatus;
  result?: AnalysisResult | null;
}

export interface JobListItem {
  job_id: string;
  platform: string;
  niche: string;
  status: JobStatus;
  created_at: string;
}

export interface JobHistoryResponse {
  jobs: JobListItem[];
}
