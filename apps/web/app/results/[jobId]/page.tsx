import type { ResultResponse } from "@contracts";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8000/api";

async function fetchResult(jobId: string): Promise<ResultResponse> {
  const response = await fetch(`${API_BASE}/results/${jobId}`, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(await response.text());
  }
  return response.json();
}

export default async function ResultPage({ params }: { params: { jobId: string } }) {
  const payload = await fetchResult(params.jobId);

  return (
    <section className="space-y-4">
      <div className="card">
        <h2 className="text-lg font-semibold">Result for {payload.job_id}</h2>
        <p>Status: {payload.status}</p>
        {!payload.result && <p className="text-yellow-400">Result not ready yet.</p>}
      </div>

      {payload.result && (
        <>
          <div className="card">
            <h3 className="font-semibold">Algorithm Proxy Score: {payload.result.proxy_score}</h3>
            <p className="text-sm text-slate-300">{payload.result.scoring_notes}</p>
            <ul className="mt-3 list-disc pl-5 text-sm">
              {payload.result.subscores.map((s) => (
                <li key={s.name}>
                  {s.name}: {s.score} (weight {s.weight})
                </li>
              ))}
            </ul>
          </div>

          <div className="card">
            <h3 className="mb-2 font-semibold">Timeline Diagnosis</h3>
            <ul className="space-y-2 text-sm">
              {payload.result.timeline_diagnosis.map((t) => (
                <li key={`${t.second}-${t.insight}`}>
                  <strong>{t.second}s</strong> [{t.severity}] - {t.insight}
                </li>
              ))}
            </ul>
          </div>

          <div className="card text-sm">
            <h3 className="mb-2 font-semibold">Upload Package</h3>
            <p><strong>Titles:</strong> {payload.result.upload_package.titles.join(" | ")}</p>
            <p><strong>Description:</strong> {payload.result.upload_package.description}</p>
            <p><strong>Hashtags:</strong> {payload.result.upload_package.hashtags.join(" ")}</p>
            <p><strong>Pinned:</strong> {payload.result.upload_package.pinned_comment}</p>
            <p><strong>CTA:</strong> {payload.result.upload_package.cta}</p>
          </div>

          <div className="card text-sm">
            <h3 className="mb-2 font-semibold">Editing Suggestions</h3>
            <ul className="space-y-1">
              {payload.result.editing_suggestions.map((e) => (
                <li key={e.timestamp}>
                  {e.timestamp} — {e.recommendation} ({e.impact} impact)
                </li>
              ))}
            </ul>
          </div>
        </>
      )}
    </section>
  );
}
