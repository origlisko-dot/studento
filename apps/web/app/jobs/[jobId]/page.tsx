"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import type { JobStatusResponse } from "@contracts";
import { apiFetch } from "@/components/api";

export default function JobPage() {
  const params = useParams<{ jobId: string }>();
  const router = useRouter();
  const [job, setJob] = useState<JobStatusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let interval: NodeJS.Timeout;

    const poll = async () => {
      try {
        const payload = await apiFetch<JobStatusResponse>(`/jobs/${params.jobId}`);
        setJob(payload);
        if (payload.status === "completed") {
          router.push(`/results/${params.jobId}`);
        }
        if (payload.status === "failed") {
          clearInterval(interval);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Polling failed");
      }
    };

    poll();
    interval = setInterval(poll, 2500);
    return () => clearInterval(interval);
  }, [params.jobId, router]);

  return (
    <section className="card">
      <h2 className="mb-3 text-lg font-semibold">Job Status</h2>
      {error && <p className="text-red-400">{error}</p>}
      {!job && <p>Fetching status...</p>}
      {job && (
        <div className="space-y-2">
          <p>Job ID: {job.job_id}</p>
          <p>Status: {job.status}</p>
          {job.error_message && <p className="text-red-400">Error: {job.error_message}</p>}
          <p className="text-sm text-slate-400">Updated: {new Date(job.updated_at).toLocaleString()}</p>
        </div>
      )}
    </section>
  );
}
