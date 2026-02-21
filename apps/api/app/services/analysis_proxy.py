"""Algorithm Proxy scoring service.

TODO(phase2): replace deterministic heuristics with real multimodal extraction pipeline
using ffmpeg, ASR transcription, OCR, and audio feature modeling.
"""

from datetime import datetime
import hashlib
import random


def _seed_from(job_id: str, platform: str, niche: str) -> int:
    digest = hashlib.sha256(f"{job_id}:{platform}:{niche}".encode("utf-8")).hexdigest()
    return int(digest[:8], 16)


def build_mock_analysis(job_id: str, platform: str, niche: str) -> dict:
    rng = random.Random(_seed_from(job_id, platform, niche))

    factors = [
        ("Hook Strength", 0.3),
        ("Retention Shape", 0.25),
        ("Content Clarity", 0.2),
        ("CTA Timing", 0.15),
        ("Platform Fit", 0.1),
    ]

    subscores = []
    total = 0.0
    for name, weight in factors:
        score = round(rng.uniform(50, 95), 1)
        total += score * weight
        subscores.append(
            {
                "name": name,
                "weight": weight,
                "score": score,
                "rationale": f"Proxy estimate for {name.lower()} based on deterministic mock signal.",
            }
        )

    timeline = [
        {"second": 0, "insight": "Opening frame is clear but could start with stronger conflict.", "severity": "medium"},
        {"second": 7, "insight": "Engagement dip risk around transition; tighten pacing.", "severity": "high"},
        {"second": 18, "insight": "Value explanation lands well.", "severity": "low"},
    ]

    return {
        "platform": platform,
        "niche": niche,
        "proxy_score": round(total, 1),
        "scoring_notes": "Transparent Algorithm Proxy score (not the real platform ranking algorithm).",
        "subscores": subscores,
        "timeline_diagnosis": timeline,
        "upload_package": {
            "titles": [
                f"{niche.title()} shortcut most creators miss",
                f"{platform.title()} growth playbook for {niche}",
                f"I tested this {niche} format so you don't have to",
            ],
            "description": "Short-form optimization package generated from proxy diagnostics.",
            "hashtags": ["#viralproxylab", f"#{platform}", f"#{niche.replace(' ', '')}"],
            "pinned_comment": "Want part 2 with exact shot list? Comment 'BLUEPRINT'.",
            "cta": "Save this and test the revised cut today.",
        },
        "editing_suggestions": [
            {"timestamp": "00:00-00:02", "recommendation": "Swap opener to strongest claim first", "impact": "high"},
            {"timestamp": "00:06-00:09", "recommendation": "Remove filler phrase and jump cut", "impact": "medium"},
            {"timestamp": "00:16-00:20", "recommendation": "Overlay CTA text with contrast", "impact": "medium"},
        ],
        "generated_at": datetime.utcnow().isoformat(),
    }
