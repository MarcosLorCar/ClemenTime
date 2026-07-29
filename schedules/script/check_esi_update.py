#!/usr/bin/env python3
import argparse
import json
import os
import sys
import urllib.request

DEFAULT_ESI_URL = "https://esi.uclm.es/TV/hall/horarios.json"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
INPUT_DIR = os.path.normpath(os.path.join(SCRIPT_DIR, "..", "input"))
META_FILE = os.path.join(INPUT_DIR, "esi_meta.json")
INPUT_JSON = os.path.join(INPUT_DIR, "schedules.json")


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
        "--mock-url",
        default=None,
        help="Custom URL endpoint alias for testing",
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

    target_url = args.mock_url if args.mock_url is not None else args.url

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
        if (
            args.mock_etag is None
            and args.mock_last_modified is None
            and not args.force
        ):
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

    headers_changed = (last_modified != saved_last_modified) or (etag != saved_etag)
    mock_provided = (args.mock_etag is not None) or (args.mock_last_modified is not None)

    should_update = args.force or headers_changed or mock_provided

    if should_update:
        print(f"[Update Detected] Downloading {target_url}...")
        os.makedirs(INPUT_DIR, exist_ok=True)
        try:
            req_get = urllib.request.Request(
                target_url,
                headers={"User-Agent": "ClemenTime-UpdateChecker/1.0"},
            )
            with urllib.request.urlopen(req_get) as resp_get, open(INPUT_JSON, "wb") as out_file:
                out_file.write(resp_get.read())
        except Exception as e:
            print(f"[Error] Failed to download schedule from {target_url}: {e}", file=sys.stderr)
            sys.exit(1)

        # Update metadata file
        meta_data = {
            "last_modified": last_modified,
            "etag": etag,
        }
        with open(META_FILE, "w", encoding="utf-8") as f:
            json.dump(meta_data, f, indent=2)
            f.write("\n")

        sys.exit(0)
    else:
        print("[No Update] ESI schedule headers unchanged.")
        sys.exit(0)


if __name__ == "__main__":
    main()
