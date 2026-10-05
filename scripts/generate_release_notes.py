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
import urllib.error
import urllib.request
from pathlib import Path
from typing import Dict, List, Optional, Tuple

PLAY_STORE_MAX_CHARS = 500


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
    if not from_tag:
        # Try to find the most recent tag before to_ref
        prev_tag = run_git(["describe", "--tags", "--abbrev=0", f"{to_ref}^"])
        if not prev_tag:
            # Fallback to the latest tag reachable
            prev_tag = run_git(["describe", "--tags", "--abbrev=0", to_ref])
        from_tag = prev_tag if prev_tag else None

    # Verify if to_ref exists
    rev = run_git(["rev-parse", "--verify", to_ref])
    if not rev:
        to_ref = "HEAD"

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


def call_gemini(
    prompt: str,
    api_key: str,
    model: str = "gemini-3.6-flash",
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
    play_es_bullets = []
    play_en_bullets = []

    for c in (features + fixes)[:4]:
        clean = re.sub(r"^(feat|fix)(\(.*?\))?:\s*", "", c.split(" (")[0].lstrip("- "))
        play_es_bullets.append(f"• {clean}")
        play_en_bullets.append(f"• {clean}")

    if not play_es_bullets:
        play_es_bullets = ["• Corrección de errores y mejoras de rendimiento."]
        play_en_bullets = ["• Bug fixes and performance improvements."]

    return {
        "github_markdown": gh_markdown,
        "google_play_es": enforce_char_limit("\n".join(play_es_bullets)),
        "google_play_en": enforce_char_limit("\n".join(play_en_bullets)),
    }


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
        return generate_fallback_notes(commits, tag_label)

    # Format commits for prompt
    commit_lines = [f"- {c['hash']}: {c['subject']}" for c in commits]
    commit_summary = "\n".join(commit_lines)

    prompt = f"""
You are the release manager and technical copywriter for ClemenTime, an Android timetable and schedule app for university students at Escuela Superior de Informática (ESI) in Ciudad Real, Spain (UCLM).

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
   - Tailored specifically for ESI university students. Friendly, natural tone.
   - Focus on user benefits and app behavior (e.g. horarios, visor, widget, notificaciones).
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
    models_to_try = [model, "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite"]
    # Deduplicate models preserving order
    seen_models = set()
    models_to_try = [m for m in models_to_try if not (m in seen_models or seen_models.add(m))]

    for k in keys_to_try:
        for m in models_to_try:
            res = call_gemini(prompt, k, m)
            if res and "github_markdown" in res and "google_play_es" in res and "google_play_en" in res:
                res["google_play_es"] = enforce_char_limit(res["google_play_es"])
                res["google_play_en"] = enforce_char_limit(res["google_play_en"])
                return res

    print("Warning: Gemini synthesis failed or rate limited across all keys/models. Falling back to rule-based notes.", file=sys.stderr)
    return generate_fallback_notes(commits, tag_label)


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
    model = args.model or os.environ.get("GEMINI_MODEL") or env.get("GEMINI_MODEL") or "gemini-3.6-flash"

    # Resolve git range
    from_tag, to_ref = resolve_tags(args.from_tag, args.to_ref)
    print(f"Synthesizing changelog for range: {from_tag or '(initial)'}..{to_ref}", file=sys.stderr)

    commits = get_git_commits(from_tag, to_ref)
    print(f"Found {len(commits)} commits.", file=sys.stderr)

    result = synthesize_changelog(commits, from_tag, to_ref, api_key, api_key_alt, model)

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    gh_path = out_dir / "github_release.md"
    es_path = out_dir / "whatsnew-es-ES"
    en_path = out_dir / "whatsnew-en-US"

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
