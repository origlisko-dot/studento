import Link from "next/link";
import type { JobHistoryResponse } from "@contracts";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8000/api";

async function fetchHistory(): Promise<JobHistoryResponse> {
  const response = await fetch(`${API_BASE}/history`, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(await response.text());
  }
  return response.json();
}

export default async function HistoryPage() {
  const history = await fetchHistory();

  return (
    <section className="card">
      <h2 className="mb-4 text-lg font-semibold">Recent Analyses</h2>
      <ul className="space-y-2 text-sm">
        {history.jobs.map((job) => (
          <li key={job.job_id} className="rounded border border-slate-800 p-3">
            <p>{job.platform} / {job.niche}</p>
            <p>Status: {job.status}</p>
            <p>{new Date(job.created_at).toLocaleString()}</p>
            <div className="mt-1 flex gap-3">
              <Link href={`/jobs/${job.job_id}`} className="text-indigo-300">Status</Link>
              <Link href={`/results/${job.job_id}`} className="text-indigo-300">Results</Link>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
