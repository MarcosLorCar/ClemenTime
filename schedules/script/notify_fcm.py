#!/usr/bin/env python3
"""
notify_fcm.py

Dispatches data-only FCM HTTP v1 push messages to school-scoped topics
(e.g., sched_uclm_esi) using schedules/dist/schedules_catalog.json and
the FCM_SERVICE_ACCOUNT_KEY JSON secret.
"""

import argparse
import base64
from datetime import datetime, timezone
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

FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"
DEFAULT_CATALOG_PATH = os.path.abspath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dist", "schedules_catalog.json")
)


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


def build_fcm_messages(catalog: dict, updated_at: str | None = None) -> list[dict]:
    timestamp = updated_at or datetime.now(timezone.utc).isoformat()
    messages = []
    for uni in catalog.get("universities", []):
        uni_id = uni.get("id", "")
        for faculty in uni.get("faculties", []):
            fac_id = faculty.get("id", "")
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


def fetch_oauth_access_token(service_account: dict) -> str:
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
    signing_input = f"{_b64url(json.dumps(header, separators=(',', ':')).encode('utf-8'))}." \
                    f"{_b64url(json.dumps(claims, separators=(',', ':')).encode('utf-8'))}".encode("ascii")
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
    with urllib.request.urlopen(req, timeout=20) as resp:
        token_data = json.loads(resp.read().decode("utf-8"))
    return token_data["access_token"]


def send_fcm_message(project_id: str, access_token: str, payload: dict) -> dict:
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
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser(description="Send FCM topic notifications from schedules_catalog.json")
    parser.add_argument("--catalog", default=DEFAULT_CATALOG_PATH, help="Path to schedules_catalog.json")
    parser.add_argument("--dry-run", action="store_true", help="Print payloads without calling FCM")
    args = parser.parse_args()

    if not os.path.exists(args.catalog):
        print(f"[Error] Catalog file not found: {args.catalog}", file=sys.stderr)
        return 1

    with open(args.catalog, "r", encoding="utf-8") as f:
        catalog = json.load(f)

    messages = build_fcm_messages(catalog)
    if not messages:
        print("[Info] No faculties found in catalog; nothing to dispatch.")
        return 0

    if args.dry_run:
        print(json.dumps(messages, indent=2))
        return 0

    raw_sa = os.environ.get("FCM_SERVICE_ACCOUNT_KEY", "").strip()
    if not raw_sa:
        print("[Info] FCM_SERVICE_ACCOUNT_KEY not set; skipping FCM push dispatch.")
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

    access_token = fetch_oauth_access_token(service_account)

    for payload in messages:
        topic = payload["message"]["topic"]
        try:
            res = send_fcm_message(project_id, access_token, payload)
            print(f"[FCM] Dispatched topic '{topic}' -> {res.get('name', 'ok')}")
        except urllib.error.HTTPError as exc:
            err_body = exc.read().decode("utf-8", errors="replace")
            print(f"[Error] FCM send failed for topic '{topic}': HTTP {exc.code} {err_body}", file=sys.stderr)
            return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
