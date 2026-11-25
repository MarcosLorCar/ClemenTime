#!/usr/bin/env python3
"""
Release notes and changelog generator for ClemenTime.
Synthesizes raw git commits between versions into:
  1. GitHub Release Notes (Markdown)
  2. Google Play 'What's New' in Spanish (es-ES, <= 500 chars)
  3. Google Play 'What's New' in English (en-US, <= 500 chars)

Uses the Gemini API (via credentials in .env or environment) with zero external dependencies.
Falls back gracefully to standard git log categorization if offline or unauthenticated.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Dict, List, Optional, Tuple

PLAY_STORE_MAX_CHARS = 500
GITHUB_REPO_URL = "https://github.com/MarcosLorCar/ClemenTime"
COMMIT_BODY_MAX_CHARS = 300


def load_env(env_path: Optional[Path] = None) -> Dict[str, str]:
    """Parse key=value pairs from .env without external dependencies."""
    env_vars: Dict[str, str] = {}
    if env_path is not None:
        candidates = [env_path]
    else:
        candidates = [
            Path.cwd() / ".env",
            Path(__file__).resolve().parent.parent / ".env",
        ]
    target_path = next((p for p in candidates if p and p.is_file()), None)
    if not target_path:
        return env_vars

    with target_path.open("r", encoding="utf-8") as f:
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            if "=" in stripped:
                key, val = stripped.split("=", 1)
                key = key.strip()
                val = val.strip().strip("'\"")
                env_vars[key] = val
    return env_vars


def run_git(args: List[str]) -> str:
    """Run a git command and return stripped stdout."""
    res = subprocess.run(
        ["git"] + args,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
    if res.returncode != 0:
        return ""
    return res.stdout.strip()


def resolve_tags(from_tag: Optional[str], to_ref: str) -> Tuple[Optional[str], str]:
    """Resolve the base tag and target ref."""
    # Verify if to_ref exists
    rev = run_git(["rev-parse", "--verify", to_ref])
    if not rev:
        to_ref = "HEAD"

    if not from_tag:
        is_stable_release = to_ref != "HEAD" and "-" not in to_ref
        raw_tags = run_git(["tag", "--merged", f"{to_ref}^", "--sort=-v:refname", "v*"])
        if not raw_tags:
            raw_tags = run_git(["tag", "--merged", to_ref, "--sort=-v:refname", "v*"])

        candidates = [t.strip() for t in raw_tags.splitlines() if t.strip() and t.strip() != to_ref]
        if is_stable_release:
            # For stable releases (e.g. v1.5.0), compare against the previous stable tag
            # so changes tested in internal pre-releases (v1.5.0-rc.1) are included.
            stable_candidates = [t for t in candidates if "-" not in t]
            from_tag = stable_candidates[0] if stable_candidates else (candidates[0] if candidates else None)
        else:
            from_tag = candidates[0] if candidates else None

    return from_tag, to_ref


def get_git_commits(from_tag: Optional[str], to_ref: str) -> List[Dict[str, str]]:
    """Retrieve commits between from_tag and to_ref."""
    if from_tag:
        range_spec = f"{from_tag}..{to_ref}"
    else:
        # If no base tag, look at the last 20 commits
        range_spec = f"-n 20 {to_ref}"

    format_str = "%h%x1f%s%x1f%an%x1f%b%x1e"
    raw = run_git(["log", f"--format={format_str}", range_spec])
    commits: List[Dict[str, str]] = []
    if not raw:
        return commits

    for entry in raw.split("\x1e"):
        entry = entry.strip()
        if not entry:
            continue
        parts = entry.split("\x1f")
        if len(parts) >= 3:
            commits.append({
                "hash": parts[0].strip(),
                "subject": parts[1].strip(),
                "author": parts[2].strip(),
                "body": parts[3].strip() if len(parts) > 3 else "",
            })
    return commits


def enforce_char_limit(text: str, max_chars: int = PLAY_STORE_MAX_CHARS) -> str:
    """Ensure plain text notes fit within Google Play's 500-char limit."""
    text = text.strip()
    if len(text) <= max_chars:
        return text

    lines = [l.strip() for l in text.split("\n") if l.strip()]
    fitted_lines: List[str] = []
    current_len = 0

    for line in lines:
        line_len = len(line) + (1 if fitted_lines else 0)
        if current_len + line_len <= max_chars:
            fitted_lines.append(line)
            current_len += line_len
        else:
            break

    if fitted_lines:
        return "\n".join(fitted_lines)

    # Fallback: slice directly if a single line was already too long
    return text[: max_chars - 3].rstrip() + "..."


DEFAULT_ROLLING_MODELS = [
    "gemini-flash-latest",
    "gemini-flash-lite-latest",
]
STATIC_FALLBACK_MODELS = [
    "gemini-3.6-flash",
    "gemini-3.5-flash-lite",
    "gemini-2.5-flash",
]
EXCLUDED_MODEL_TOKENS = (
    "embedding",
    "imagen",
    "image",
    "tts",
    "audio",
    "live",
    "veo",
    "robotics",
    "computer-use",
    "aqa",
    "gemma",
    "learnlm",
)


def _model_rank(name: str) -> Tuple[int, Tuple[int, ...], int, int]:
    """Rank discovered Gemini models: prefer stable flash models with highest version."""
    lower = name.lower()
    is_flash = 1 if "flash" in lower else 0
    is_stable = 0 if any(tok in lower for tok in ("exp", "preview", "experimental")) else 1
    is_lite = 0 if "lite" in lower else 1
    version_nums = tuple(int(n) for n in re.findall(r"\d+", lower)[:3]) or (0,)
    return (is_flash, version_nums, is_stable, is_lite)


def discover_gemini_models(api_key: str, limit: int = 5) -> List[str]:
    """Query Gemini ListModels API to dynamically discover working text-generation models."""
    url = f"https://generativelanguage.googleapis.com/v1beta/models?key={api_key}"
    req = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"Warning: Could not list Gemini models dynamically: {e}", file=sys.stderr)
        return []

    discovered: List[str] = []
    for entry in data.get("models", []):
        methods = entry.get("supportedGenerationMethods", [])
        if "generateContent" not in methods:
            continue
        raw_name = entry.get("name", "")
        short_name = raw_name.removeprefix("models/")
        lower = short_name.lower()
        if not lower.startswith("gemini-"):
            continue
        if any(tok in lower for tok in EXCLUDED_MODEL_TOKENS):
            continue
        discovered.append(short_name)

    discovered.sort(key=_model_rank, reverse=True)
    return discovered[:limit]


def call_gemini(
    prompt: str,
    api_key: str,
    model: str = "gemini-flash-latest",
) -> Optional[dict]:
    """Call Gemini REST API requesting JSON response."""
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "responseMimeType": "application/json",
            "temperature": 0.2,
        },
    }

    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    for attempt in range(2):
        try:
            with urllib.request.urlopen(req, timeout=25) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                candidates = data.get("candidates", [])
                if candidates:
                    raw_text = candidates[0]["content"]["parts"][0]["text"].strip()
                    # Clean markdown JSON block if present
                    if raw_text.startswith("```json"):
                        raw_text = raw_text[7:]
                    if raw_text.startswith("```"):
                        raw_text = raw_text[3:]
                    if raw_text.endswith("```"):
                        raw_text = raw_text[:-3]
                    return json.loads(raw_text.strip())
                return None
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503, 504) and attempt == 0:
                print(f"Transient HTTP {e.code} from {model}; retrying in 2s...", file=sys.stderr)
                time.sleep(2)
                continue
            print(f"Warning: Gemini API error with model {model}: {e}", file=sys.stderr)
            return None
        except Exception as e:
            print(f"Warning: Gemini API error with model {model}: {e}", file=sys.stderr)
            return None

    return None


def generate_fallback_notes(commits: List[Dict[str, str]], tag_name: str) -> dict:
    """Fallback generator when offline or no API key is available."""
    features = []
    fixes = []
    others = []

    for c in commits:
        subj = c["subject"]
        h = c["hash"]
        if subj.startswith("feat"):
            features.append(f"- {subj} ({h})")
        elif subj.startswith("fix"):
            fixes.append(f"- {subj} ({h})")
        else:
            others.append(f"- {subj} ({h})")

    gh_md = [f"## What's Changed in {tag_name}\n"]
    if features:
        gh_md.append("### ✨ Features\n" + "\n".join(features))
    if fixes:
        gh_md.append("### 🐛 Bug Fixes\n" + "\n".join(fixes))
    if others:
        gh_md.append("### 🛠️ Other Changes\n" + "\n".join(others))

    gh_markdown = "\n\n".join(gh_md)

    # Simple bullet points for Play Store
    play_en_bullets = []
    for c in (features + fixes)[:4]:
        clean = re.sub(r"^(feat|fix)(\(.*?\))?:\s*", "", c.split(" (")[0].lstrip("- "))
        play_en_bullets.append(f"• {clean}")

    if not play_en_bullets:
        play_en_bullets = ["• Bug fixes and performance improvements."]

    # Avoid leaking raw English commit subjects into Spanish Play Store notes
    play_es_bullets = []
    if features:
        play_es_bullets.append("• Nuevas funcionalidades y mejoras en la gestión de horarios.")
    if fixes:
        play_es_bullets.append("• Corrección de errores y mejoras de estabilidad.")
    if not play_es_bullets:
        play_es_bullets = ["• Mejoras de estabilidad y rendimiento."]

    return {
        "github_markdown": gh_markdown,
        "google_play_es": enforce_char_limit("\n".join(play_es_bullets)),
        "google_play_en": enforce_char_limit("\n".join(play_en_bullets)),
    }


def append_compare_link(gh_markdown: str, from_tag: Optional[str], to_ref: str) -> str:
    """Append a GitHub compare link footer when a base tag is known."""
    if not from_tag or to_ref == "HEAD" or "/compare/" in gh_markdown:
        return gh_markdown
    compare_url = f"{GITHUB_REPO_URL}/compare/{from_tag}...{to_ref}"
    return f"{gh_markdown.rstrip()}\n\n**Full Changelog**: {compare_url}"


def format_commit_for_prompt(commit: Dict[str, str]) -> str:
    """Format commit subject and a compact body excerpt for LLM context."""
    line = f"- {commit['hash']}: {commit['subject']}"
    body = commit.get("body", "").strip()
    if body:
        compact = re.sub(r"\s+", " ", body)
        if len(compact) > COMMIT_BODY_MAX_CHARS:
            compact = compact[: COMMIT_BODY_MAX_CHARS - 3].rstrip() + "..."
        line += f"\n  Details: {compact}"
    return line


def synthesize_changelog(
    commits: List[Dict[str, str]],
    from_tag: Optional[str],
    to_ref: str,
    api_key: Optional[str],
    api_key_alt: Optional[str],
    model: str,
) -> dict:
    """Synthesize changelogs using Gemini, or fallback if unavailable."""
    tag_label = to_ref if to_ref != "HEAD" else (from_tag or "Latest")

    if not commits:
        return {
            "github_markdown": f"## {tag_label}\n\nNo commit changes detected.",
            "google_play_es": "• Mejoras de estabilidad y rendimiento.",
            "google_play_en": "• Stability and performance improvements.",
        }

    if not api_key and not api_key_alt:
        print("Note: No GEMINI_API_KEY found. Generating rule-based fallback changelog.", file=sys.stderr)
        res = generate_fallback_notes(commits, tag_label)
        res["github_markdown"] = append_compare_link(res["github_markdown"], from_tag, to_ref)
        return res

    # Format commits (including compact body excerpts) for prompt
    commit_lines = [format_commit_for_prompt(c) for c in commits]
    commit_summary = "\n".join(commit_lines)

    prompt = f"""
You are the release manager and technical copywriter for ClemenTime, an offline-first Android timetable and schedule app for university students (supporting UCLM / ESI and a multi-faculty schedule catalog).

Here is the git commit history between {from_tag or 'initial'} and {to_ref}:
{commit_summary}

Task:
Synthesize these commits into release notes.
1. "github_markdown": Detailed, developer-friendly Markdown for GitHub Releases.
   - Categorize into sections with emojis: `### ✨ Features`, `### 🐛 Bug Fixes`, `### ⚡ Polish & Improvements`, and `### 🛠️ Under the Hood`.
   - Preserve commit hashes or PR references where helpful.
   - Filter out pure CI, README, or chore noise unless meaningful.
   - In English.

2. "google_play_es": Release notes for Google Play in Spanish (Spain).
   - Tailored for university students. Friendly, natural tone.
   - Focus on user benefits and app behavior (e.g. horarios, catálogo de facultades, visor, widget, notificaciones).
   - Use bullet points starting with '• '.
   - STRICT CONSTRAINT: Must NOT exceed 450 characters (absolute limit is 500 characters).

3. "google_play_en": Release notes for Google Play in English.
   - Clear, concise bullets ('• ').
   - Focus on user benefits.
   - STRICT CONSTRAINT: Must NOT exceed 450 characters (absolute limit is 500 characters).

Respond ONLY with a valid JSON object matching this schema:
{{
  "github_markdown": "...",
  "google_play_es": "...",
  "google_play_en": "..."
}}
"""

    keys_to_try = [k for k in [api_key, api_key_alt] if k]

    for k in keys_to_try:
        # 1. Try explicit model (if given) and Google's rolling aliases first
        fast_models = [m for m in ([model] if model else []) + DEFAULT_ROLLING_MODELS if m]
        seen_models = set()
        fast_models = [m for m in fast_models if not (m in seen_models or seen_models.add(m))]

        for m in fast_models:
            res = call_gemini(prompt, k, m)
            if res and "github_markdown" in res and "google_play_es" in res and "google_play_en" in res:
                res["github_markdown"] = append_compare_link(res["github_markdown"], from_tag, to_ref)
                res["google_play_es"] = enforce_char_limit(res["google_play_es"])
                res["google_play_en"] = enforce_char_limit(res["google_play_en"])
                return res

        # 2. If aliases/explicit model failed, dynamically discover live models from the API
        dynamic_models = discover_gemini_models(k) + STATIC_FALLBACK_MODELS
        fallback_models = [m for m in dynamic_models if not (m in seen_models or seen_models.add(m))]

        for m in fallback_models:
            res = call_gemini(prompt, k, m)
            if res and "github_markdown" in res and "google_play_es" in res and "google_play_en" in res:
                res["github_markdown"] = append_compare_link(res["github_markdown"], from_tag, to_ref)
                res["google_play_es"] = enforce_char_limit(res["google_play_es"])
                res["google_play_en"] = enforce_char_limit(res["google_play_en"])
                return res

    print("Warning: Gemini synthesis failed or rate limited across all keys/models. Falling back to rule-based notes.", file=sys.stderr)
    res = generate_fallback_notes(commits, tag_label)
    res["github_markdown"] = append_compare_link(res["github_markdown"], from_tag, to_ref)
    return res


def main():
    parser = argparse.ArgumentParser(description="Synthesize release notes and Google Play changelogs using Gemini.")
    parser.add_argument("--from-tag", help="Starting tag (defaults to previous release tag)")
    parser.add_argument("--to-ref", default="HEAD", help="Ending git ref or tag (default: HEAD)")
    parser.add_argument("--output-dir", default="build/release_notes", help="Output directory for notes")
    parser.add_argument("--model", help="Gemini model name")
    parser.add_argument("--preview", action="store_true", help="Print preview to stdout")
    parser.add_argument("--no-ai", action="store_true", help="Bypass AI and generate rule-based notes directly")
    parser.add_argument("--env-file", help="Path to custom .env file")
    args = parser.parse_args()

    # Load environment variables
    env = load_env(Path(args.env_file) if args.env_file else None)
    if args.no_ai:
        api_key = None
        api_key_alt = None
    else:
        api_key = os.environ.get("GEMINI_API_KEY") or env.get("GEMINI_API_KEY")
        api_key_alt = os.environ.get("GEMINI_API_KEY_ALT") or env.get("GEMINI_API_KEY_ALT")
    model = args.model or os.environ.get("GEMINI_MODEL") or env.get("GEMINI_MODEL") or ""

    # Resolve git range
    from_tag, to_ref = resolve_tags(args.from_tag, args.to_ref)
    print(f"Synthesizing changelog for range: {from_tag or '(initial)'}..{to_ref}", file=sys.stderr)

    commits = get_git_commits(from_tag, to_ref)
    print(f"Found {len(commits)} commits.", file=sys.stderr)

    result = synthesize_changelog(commits, from_tag, to_ref, api_key, api_key_alt, model)

    out_dir = Path(args.output_dir)
    whatsnew_dir = out_dir / "whatsnew"
    whatsnew_dir.mkdir(parents=True, exist_ok=True)

    gh_path = out_dir / "github_release.md"
    es_path = whatsnew_dir / "whatsnew-es-ES"
    en_path = whatsnew_dir / "whatsnew-en-US"

    gh_path.write_text(result["github_markdown"], encoding="utf-8")
    es_path.write_text(result["google_play_es"], encoding="utf-8")
    en_path.write_text(result["google_play_en"], encoding="utf-8")

    es_chars = len(result["google_play_es"])
    en_chars = len(result["google_play_en"])

    print(f"Successfully generated release notes:", file=sys.stderr)
    print(f"  - GitHub Release: {gh_path} ({len(result['github_markdown'])} chars)", file=sys.stderr)
    print(f"  - Google Play es-ES: {es_path} ({es_chars}/{PLAY_STORE_MAX_CHARS} chars)", file=sys.stderr)
    print(f"  - Google Play en-US: {en_path} ({en_chars}/{PLAY_STORE_MAX_CHARS} chars)", file=sys.stderr)

    if args.preview:
        print("\n" + "=" * 60)
        print("GITHUB RELEASE NOTES (MARKDOWN):")
        print("=" * 60)
        print(result["github_markdown"])
        print("\n" + "=" * 60)
        print(f"GOOGLE PLAY (es-ES) [{es_chars}/{PLAY_STORE_MAX_CHARS} chars]:")
        print("=" * 60)
        print(result["google_play_es"])
        print("\n" + "=" * 60)
        print(f"GOOGLE PLAY (en-US) [{en_chars}/{PLAY_STORE_MAX_CHARS} chars]:")
        print("=" * 60)
        print(result["google_play_en"])
        print("=" * 60)


if __name__ == "__main__":
    main()
