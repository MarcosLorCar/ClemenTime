#!/usr/bin/env python3
"""
notify_fcm.py

Dispatches data-only FCM HTTP v1 push messages to school-scoped topics
(e.g., sched_uclm_esi) using schedules/dist/schedules_catalog.json and
the FCM_SERVICE_ACCOUNT_KEY JSON secret.

Supports selective dispatch by faculty key (--faculty) or from a pipeline
publication report (--report), plus bounded transient retries.
"""

import argparse
import base64
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from collections.abc import Collection
from datetime import UTC, datetime
from pathlib import Path

FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"
DEFAULT_CATALOG_PATH = os.path.abspath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dist", "schedules_catalog.json")
)
RETRYABLE_HTTP_CODES = frozenset({429, 500, 502, 503, 504})
DEFAULT_MAX_ATTEMPTS = 3


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


_INVALID_TOPIC_CHAR_RE = re.compile(r"[^a-z0-9_-]")


def build_topic_name(university_id: str, faculty_id: str, fcm_topic_prefix: str | None = None) -> str:
    if fcm_topic_prefix and fcm_topic_prefix.strip():
        clean_prefix = fcm_topic_prefix.strip().lower().removeprefix("sched_")
        clean_prefix = _INVALID_TOPIC_CHAR_RE.sub("_", clean_prefix)
        if clean_prefix:
            return f"sched_{clean_prefix}"
    uni = _INVALID_TOPIC_CHAR_RE.sub("_", university_id.strip().lower())
    fac = _INVALID_TOPIC_CHAR_RE.sub("_", faculty_id.strip().lower())
    if not uni or not fac:
        raise ValueError("university_id and faculty_id must be non-empty when fcm_topic_prefix is blank")
    return f"sched_{uni}_{fac}"


def _normalize_faculty_filter(faculties: Collection[str]) -> set[str]:
    normalized = set()
    for raw in faculties:
        cleaned = raw.strip().lower()
        if cleaned:
            normalized.add(cleaned)
    return normalized


def build_fcm_messages(
    catalog: dict,
    updated_at: str | None = None,
    faculties: Collection[str] | None = None,
) -> list[dict]:
    timestamp = updated_at or datetime.now(UTC).isoformat()
    selected = _normalize_faculty_filter(faculties) if faculties is not None else None
    messages = []
    for uni in catalog.get("universities", []):
        uni_id = uni.get("id", "")
        for faculty in uni.get("faculties", []):
            fac_id = faculty.get("id", "")
            key = f"{uni_id}/{fac_id}".lower()
            if selected is not None and key not in selected:
                continue
            topic = build_topic_name(uni_id, fac_id, faculty.get("fcm_topic_prefix"))
            data = {
                "university": str(uni_id),
                "faculty": str(fac_id),
                "updated_at": timestamp,
            }
            for term in faculty.get("terms", []):
                term_id = str(term.get("id", "")).strip()
                term_hash = term.get("hash")
                if term_id and term_hash:
                    data[f"hash_{term_id}"] = str(term_hash)

            messages.append({
                "message": {
                    "topic": topic,
                    "data": data,
                    "android": {
                        "priority": "high",
                    },
                }
            })
    return messages


def _sign_rs256_with_openssl(signing_input: bytes, private_key_pem: str) -> bytes:
    with tempfile.NamedTemporaryFile(mode="w", suffix=".pem", delete=False, encoding="utf-8") as tmp:
        tmp.write(private_key_pem)
        key_path = tmp.name
    try:
        os.chmod(key_path, 0o600)
        proc = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", key_path],
            input=signing_input,
            check=True,
            capture_output=True,
        )
        return proc.stdout
    finally:
        if os.path.exists(key_path):
            os.remove(key_path)


def _urlopen_json_with_retry(
    req: urllib.request.Request,
    *,
    timeout: int = 20,
    max_attempts: int = DEFAULT_MAX_ATTEMPTS,
) -> dict:
    attempts = max(1, max_attempts)
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            if exc.code in RETRYABLE_HTTP_CODES and attempt < attempts:
                time.sleep(attempt)
                continue
            raise
        except (urllib.error.URLError, TimeoutError, OSError):
            if attempt < attempts:
                time.sleep(attempt)
                continue
            raise
    raise RuntimeError("Unreachable retry exhaustion")


def fetch_oauth_access_token(
    service_account: dict,
    *,
    max_attempts: int = DEFAULT_MAX_ATTEMPTS,
) -> str:
    token_uri = service_account.get("token_uri") or DEFAULT_TOKEN_URI
    now = int(time.time())
    header = {"alg": "RS256", "typ": "JWT"}
    claims = {
        "iss": service_account["client_email"],
        "scope": FCM_SCOPE,
        "aud": token_uri,
        "iat": now,
        "exp": now + 3600,
    }
    signing_input = (
        f"{_b64url(json.dumps(header, separators=(',', ':')).encode('utf-8'))}."
        f"{_b64url(json.dumps(claims, separators=(',', ':')).encode('utf-8'))}"
    ).encode("ascii")
    signature = _sign_rs256_with_openssl(signing_input, service_account["private_key"])
    assertion = f"{signing_input.decode('ascii')}.{_b64url(signature)}"

    body = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": assertion,
    }).encode("utf-8")

    req = urllib.request.Request(
        token_uri,
        data=body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    token_data = _urlopen_json_with_retry(req, timeout=20, max_attempts=max_attempts)
    return token_data["access_token"]


def send_fcm_message(
    project_id: str,
    access_token: str,
    payload: dict,
    *,
    max_attempts: int = DEFAULT_MAX_ATTEMPTS,
) -> dict:
    url = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json; charset=UTF-8",
        },
        method="POST",
    )
    return _urlopen_json_with_retry(req, timeout=20, max_attempts=max_attempts)


def _load_report_faculties(report_path: str) -> list[str]:
    with open(report_path, encoding="utf-8") as handle:
        report = json.load(handle)
    changed = report.get("changed_schedule_faculties")
    if not isinstance(changed, list) or not all(isinstance(item, str) for item in changed):
        raise ValueError(f"Report {report_path} is missing a valid 'changed_schedule_faculties' list.")
    return changed


def _append_summary(summary_path: str | None, lines: list[str]) -> None:
    if not summary_path:
        return
    path = Path(summary_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="Send FCM topic notifications from schedules_catalog.json")
    parser.add_argument("--catalog", default=DEFAULT_CATALOG_PATH, help="Path to schedules_catalog.json")
    parser.add_argument(
        "--faculty",
        action="append",
        dest="faculties",
        help="Faculty key (<university>/<faculty>) to notify. Repeatable.",
    )
    parser.add_argument(
        "--report",
        help="Path to a process_schedules.py JSON report; notifies only changed_schedule_faculties.",
    )
    parser.add_argument(
        "--max-attempts",
        type=int,
        default=DEFAULT_MAX_ATTEMPTS,
        help="Maximum HTTP attempts for transient FCM/OAuth failures (default: %(default)s).",
    )
    parser.add_argument("--summary-file", help="Optional Markdown file to append FCM dispatch status.")
    parser.add_argument("--dry-run", action="store_true", help="Print payloads without calling FCM")
    args = parser.parse_args()

    if args.faculties and args.report:
        parser.error("Choose either --faculty or --report, not both.")
    if args.max_attempts < 1:
        parser.error("--max-attempts must be at least 1.")

    if not os.path.exists(args.catalog):
        print(f"[Error] Catalog file not found: {args.catalog}", file=sys.stderr)
        return 1

    with open(args.catalog, encoding="utf-8") as f:
        catalog = json.load(f)

    selected_faculties: list[str] | None = None
    if args.report:
        try:
            selected_faculties = _load_report_faculties(args.report)
        except (OSError, ValueError) as exc:
            print(f"[Error] Could not read publication report {args.report}: {exc}", file=sys.stderr)
            return 1
        if not selected_faculties:
            print("[Info] Report lists no changed faculty schedules; skipping FCM push dispatch.")
            _append_summary(args.summary_file, ["### FCM Notifications", "", "Skipped: no published schedule hashes changed.", ""])
            return 0
    elif args.faculties:
        selected_faculties = args.faculties

    messages = build_fcm_messages(catalog, faculties=selected_faculties)
    if selected_faculties is not None:
        matched = {f"{item['message']['data']['university']}/{item['message']['data']['faculty']}".lower() for item in messages}
        missing = sorted({fac.strip() for fac in selected_faculties if fac.strip().lower() not in matched})
        if missing:
            print(f"[Error] Requested faculties not found in catalog: {', '.join(missing)}", file=sys.stderr)
            return 1

    if not messages:
        print("[Info] No faculties matched in catalog; nothing to dispatch.")
        return 0

    if args.dry_run:
        print(json.dumps(messages, indent=2))
        return 0

    raw_sa = os.environ.get("FCM_SERVICE_ACCOUNT_KEY", "").strip()
    if not raw_sa:
        print("[Info] FCM_SERVICE_ACCOUNT_KEY not set; skipping FCM push dispatch.")
        _append_summary(args.summary_file, ["### FCM Notifications", "", "Skipped: `FCM_SERVICE_ACCOUNT_KEY` is not configured.", ""])
        return 0

    try:
        service_account = json.loads(raw_sa)
    except json.JSONDecodeError as exc:
        print(f"[Error] Invalid FCM_SERVICE_ACCOUNT_KEY JSON: {exc}", file=sys.stderr)
        return 1

    project_id = service_account.get("project_id")
    if not project_id or not service_account.get("client_email") or not service_account.get("private_key"):
        print("[Error] FCM_SERVICE_ACCOUNT_KEY missing required fields (project_id, client_email, private_key)", file=sys.stderr)
        return 1

    try:
        access_token = fetch_oauth_access_token(service_account, max_attempts=args.max_attempts)
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, OSError, KeyError, ValueError) as exc:
        print(
            f"[Error] Failed to obtain FCM OAuth token after {args.max_attempts} attempt(s): {exc}. "
            "Published schedule files remain live and were not reverted.",
            file=sys.stderr,
        )
        failed_keys = [f"{m['message']['data']['university']}/{m['message']['data']['faculty']}" for m in messages]
        retry_args = " ".join(f"--faculty {key}" for key in failed_keys)
        _append_summary(
            args.summary_file,
            [
                "### FCM Notifications",
                "",
                f"- **Failed** obtaining OAuth token: `{exc}`",
                "- Published schedule data on `master` remains live and was **not** reverted.",
                f"- Retry with workflow dispatch `retry_fcm=true` or run `python3 schedules/script/notify_fcm.py {retry_args}`.",
                "",
            ],
        )
        return 1

    succeeded: list[tuple[str, str]] = []
    failed: list[tuple[str, str, str]] = []
    for payload in messages:
        topic = payload["message"]["topic"]
        faculty_key = f"{payload['message']['data']['university']}/{payload['message']['data']['faculty']}"
        try:
            res = send_fcm_message(project_id, access_token, payload, max_attempts=args.max_attempts)
            print(f"[FCM] Dispatched topic '{topic}' ({faculty_key}) -> {res.get('name', 'ok')}")
            succeeded.append((faculty_key, topic))
        except urllib.error.HTTPError as exc:
            err_body = exc.read().decode("utf-8", errors="replace")
            detail = f"HTTP {exc.code} {err_body}".strip()
            print(f"[Error] FCM send failed for topic '{topic}' ({faculty_key}): {detail}", file=sys.stderr)
            failed.append((faculty_key, topic, detail))
        except (urllib.error.URLError, TimeoutError, OSError, ValueError) as exc:
            detail = str(exc)
            print(f"[Error] FCM send failed for topic '{topic}' ({faculty_key}): {detail}", file=sys.stderr)
            failed.append((faculty_key, topic, detail))

    summary_lines = ["### FCM Notifications", ""]
    for faculty_key, topic in succeeded:
        summary_lines.append(f"- Dispatched `{faculty_key}` -> `{topic}`")
    for faculty_key, topic, detail in failed:
        summary_lines.append(f"- **Failed** `{faculty_key}` (`{topic}`): `{detail}`")

    if failed:
        retry_args = " ".join(f"--faculty {faculty_key}" for faculty_key, _, _ in failed)
        note = (
            "Published schedule files remain live and were not reverted. "
            f"Retry failed notifications with: python3 schedules/script/notify_fcm.py {retry_args}"
        )
        print(f"[Warning] {note}", file=sys.stderr)
        summary_lines.extend([
            "",
            "> Published schedule files on `master` remain live and were **not** reverted.",
            f"> Retry via workflow dispatch (`retry_fcm=true`) or `python3 schedules/script/notify_fcm.py {retry_args}`.",
        ])
    summary_lines.append("")
    _append_summary(args.summary_file, summary_lines)

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
