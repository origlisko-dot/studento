"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { UploadResponse } from "@contracts";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8000/api";

export default function UploadPage() {
  const router = useRouter();
  const [platform, setPlatform] = useState("tiktok");
  const [niche, setNiche] = useState("fitness");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!file) {
      setError("Please choose a video file.");
      return;
    }

    setLoading(true);
    setError(null);

    const formData = new FormData();
    formData.append("platform", platform);
    formData.append("niche", niche);
    formData.append("file", file);

    try {
      const response = await fetch(`${API_BASE}/upload`, { method: "POST", body: formData });
      if (!response.ok) {
        throw new Error(await response.text());
      }
      const payload = (await response.json()) as UploadResponse;
      router.push(`/jobs/${payload.job_id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="card">
      <h2 className="mb-4 text-lg font-semibold">Upload a short-form video</h2>
      <form onSubmit={submit} className="space-y-4">
        <label className="block">
          <span className="mb-1 block text-sm">Platform</span>
          <select className="w-full rounded bg-slate-800 p-2" value={platform} onChange={(e) => setPlatform(e.target.value)}>
            <option value="tiktok">TikTok</option>
            <option value="youtube_shorts">YouTube Shorts</option>
          </select>
        </label>

        <label className="block">
          <span className="mb-1 block text-sm">Niche</span>
          <input className="w-full rounded bg-slate-800 p-2" value={niche} onChange={(e) => setNiche(e.target.value)} required />
        </label>

        <label className="block">
          <span className="mb-1 block text-sm">Vertical video file</span>
          <input type="file" accept="video/mp4,video/quicktime,video/webm" onChange={(e) => setFile(e.target.files?.[0] ?? null)} required />
        </label>

        {error && <p className="text-sm text-red-400">{error}</p>}
        <button className="rounded bg-indigo-500 px-4 py-2 font-medium disabled:opacity-50" disabled={loading} type="submit">
          {loading ? "Uploading..." : "Analyze Video"}
        </button>
      </form>
    </section>
  );
}
