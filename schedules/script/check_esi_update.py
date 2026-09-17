#!/usr/bin/env python3
"""
check_esi_update.py

Checks the official ESI schedule JSON endpoint (https://esi.uclm.es/TV/hall/horarios.json)
for updates using HTTP HEAD headers (Last-Modified, ETag) and content SHA256.
When an update is detected or --force is supplied, downloads schedules.json into schedules/input/
and updates schedules/input/esi_meta.json.
"""

import argparse
import hashlib
import json
import os
import sys
import urllib.request

try:
    from dotenv import load_dotenv
    SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
    ROOT_DIR = os.path.normpath(os.path.join(SCRIPT_DIR, "..", ".."))
    load_dotenv(os.path.join(ROOT_DIR, ".env"))
except ImportError:
    pass

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT_DIR = os.path.normpath(os.path.join(SCRIPT_DIR, "..", ".."))
INPUT_DIR = os.path.join(ROOT_DIR, "schedules", "input")
META_FILE = os.path.join(INPUT_DIR, "esi_meta.json")
INPUT_JSON = os.path.join(INPUT_DIR, "schedules.json")

env_url = os.getenv("SCHEDULE_API_URL") or os.getenv("SCHEDULE_SOURCE_URL")
if env_url and env_url.endswith(".json"):
    DEFAULT_ESI_URL = env_url
else:
    DEFAULT_ESI_URL = "https://esi.uclm.es/TV/hall/horarios.json"


def compute_sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(
        description="Check ESI schedule URL headers for updates and download fresh schedules.json."
    )
    parser.add_argument(
        "--url",
        default=DEFAULT_ESI_URL,
        help=f"Custom URL endpoint (default: {DEFAULT_ESI_URL})",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Force download of schedule file even if headers haven't changed",
    )
    parser.add_argument(
        "--mock-etag",
        default=None,
        help="Simulate a new ETag string for testing without remote server changes",
    )
    parser.add_argument(
        "--mock-last-modified",
        default=None,
        help="Simulate a new Last-Modified string for testing",
    )

    args = parser.parse_args()
    target_url = args.url

    # Perform HEAD request
    last_modified = ""
    etag = ""

    req = urllib.request.Request(
        target_url,
        method="HEAD",
        headers={"User-Agent": "ClemenTime-UpdateChecker/1.0"},
    )
    try:
        with urllib.request.urlopen(req) as resp:
            headers = dict(resp.headers)
            last_modified = headers.get("Last-Modified", "")
            etag = headers.get("ETag", "")
    except Exception as e:
        if args.mock_etag is None and args.mock_last_modified is None and not args.force:
            print(f"[Error] Failed to fetch HEAD from {target_url}: {e}", file=sys.stderr)
            sys.exit(1)
        else:
            print(f"[Warning] HEAD request failed ({e}), proceeding with mocks/force mode.", file=sys.stderr)

    # Apply mock headers if provided
    if args.mock_etag is not None:
        etag = args.mock_etag
    if args.mock_last_modified is not None:
        last_modified = args.mock_last_modified

    # Load existing metadata if available
    saved_meta = {}
    if os.path.exists(META_FILE):
        try:
            with open(META_FILE, "r", encoding="utf-8") as f:
                saved_meta = json.load(f)
        except Exception as e:
            print(f"[Warning] Failed to read existing {META_FILE}: {e}", file=sys.stderr)

    saved_last_modified = saved_meta.get("last_modified", "")
    saved_etag = saved_meta.get("etag", "")
    saved_url = saved_meta.get("url", "")

    headers_changed = (last_modified != saved_last_modified) or (etag != saved_etag)
    url_changed = (target_url != saved_url)
    mock_provided = (args.mock_etag is not None) or (args.mock_last_modified is not None)
    file_missing = not os.path.exists(INPUT_JSON)

    should_download = args.force or headers_changed or url_changed or mock_provided or file_missing

    if should_download:
        print(f"[Download] Fetching schedule data from {target_url}...")
        os.makedirs(INPUT_DIR, exist_ok=True)
        try:
            req_get = urllib.request.Request(
                target_url,
                headers={"User-Agent": "ClemenTime-UpdateChecker/1.0"},
            )
            with urllib.request.urlopen(req_get) as resp_get:
                content_bytes = resp_get.read()
        except Exception as e:
            print(f"[Error] Failed to download schedule from {target_url}: {e}", file=sys.stderr)
            sys.exit(1)

        sha256_val = compute_sha256(content_bytes)
        prev_sha256 = saved_meta.get("sha256", "")
        content_changed = (sha256_val != prev_sha256) or file_missing

        # Write downloaded content
        with open(INPUT_JSON, "wb") as out_file:
            out_file.write(content_bytes)

        # Update metadata file with latest headers and hash
        meta_data = {
            "url": target_url,
            "last_modified": last_modified,
            "etag": etag,
            "sha256": sha256_val
        }
        with open(META_FILE, "w", encoding="utf-8") as f:
            json.dump(meta_data, f, indent=2)
            f.write("\n")

        print(f"[Success] Downloaded {len(content_bytes)} bytes. Saved to {INPUT_JSON}.")

        if content_changed or args.force or mock_provided:
            print("[Update Detected] ESI schedule content changed or force flag enabled.")
        else:
            print("[No Change] Remote headers changed but file content is identical; skipping.")
        sys.exit(0)
    else:
        print("[No Update] ESI schedule headers unchanged.")
        sys.exit(0)


if __name__ == "__main__":
    main()
