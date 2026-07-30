#!/usr/bin/env python3
"""
check_esi_update.py

Scrapes the target ESI schedule webpage (configured via SCHEDULE_SOURCE_URL environment variable
or --url CLI argument) for schedule PDF links, checks if the schedule PDFs have been updated,
downloads new PDFs non-cumulatively (replacing older versions in schedules/pdf/), and updates metadata.
"""

import argparse
import glob
import hashlib
import json
import os
import re
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
PDF_DIR = os.path.join(ROOT_DIR, "schedules", "pdf")
INPUT_DIR = os.path.join(ROOT_DIR, "schedules", "input")
META_FILE = os.path.join(INPUT_DIR, "esi_meta.json")
PDF_META_FILE = os.path.join(INPUT_DIR, "pdf_meta.json")


def compute_sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def compute_sha256_file(filepath: str) -> str:
    sha256_hash = hashlib.sha256()
    with open(filepath, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()


def scrape_pdf_urls(page_url: str) -> dict:
    req = urllib.request.Request(
        page_url,
        headers={"User-Agent": "ClemenTime-UpdateChecker/1.0"},
    )
    with urllib.request.urlopen(req) as resp:
        html = resp.read().decode("utf-8", errors="ignore")

    pdf_urls = re.findall(r'href=["\'](https?://[^"\']+\.pdf)["\']', html)
    schedules = {}
    for u in pdf_urls:
        filename = os.path.basename(u)
        if "1C" in filename and ("GRADO" in filename or "Grupos" in filename):
            schedules["1C"] = u
        elif "2C" in filename and ("GRADO" in filename or "Grupos" in filename):
            schedules["2C"] = u

    return schedules


def purge_old_semester_pdfs(pdf_dir: str, semester: str, keep_filename: str):
    """
    Remove any old PDF files for a given semester ('1C' or '2C') in pdf_dir,
    keeping only keep_filename.
    """
    if not os.path.exists(pdf_dir):
        return

    for pdf_path in glob.glob(os.path.join(pdf_dir, "*.pdf")):
        basename = os.path.basename(pdf_path)
        if basename == keep_filename:
            continue

        if f"_{semester}_" in basename or f"_{semester}." in basename or f"{semester}_" in basename:
            print(f"[Clean] Removing obsolete {semester} PDF: {basename}")
            try:
                os.remove(pdf_path)
            except Exception as e:
                print(f"[Warning] Failed to remove {pdf_path}: {e}", file=sys.stderr)


def main():
    default_url = os.getenv("SCHEDULE_SOURCE_URL", "")

    parser = argparse.ArgumentParser(
        description="Check ESI web page for schedule PDF updates and download fresh PDFs non-cumulatively."
    )
    parser.add_argument(
        "--url",
        default=default_url,
        help="Schedule web page URL (default: SCHEDULE_SOURCE_URL environment variable)",
    )
    parser.add_argument(
        "--mock-url",
        default=None,
        help="Custom URL endpoint alias for testing",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Force download of schedule files even if metadata hasn't changed",
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

    if not target_url:
        print("[Error] SCHEDULE_SOURCE_URL environment variable or --url argument is required.", file=sys.stderr)
        sys.exit(1)

    os.makedirs(PDF_DIR, exist_ok=True)
    os.makedirs(INPUT_DIR, exist_ok=True)

    # Scrape target web page for 1C / 2C PDF URLs
    discovered_schedules = {}
    try:
        discovered_schedules = scrape_pdf_urls(target_url)
    except Exception as e:
        if args.mock_etag is None and args.mock_last_modified is None and not args.force:
            print(f"[Error] Failed to scrape schedule webpage at {target_url}: {e}", file=sys.stderr)
            sys.exit(1)
        else:
            print(f"[Warning] Page fetch failed ({e}), proceeding in test/mock mode.", file=sys.stderr)

    # Load existing metadata
    saved_meta = {}
    if os.path.exists(META_FILE):
        try:
            with open(META_FILE, "r", encoding="utf-8") as f:
                saved_meta = json.load(f)
        except Exception as e:
            print(f"[Warning] Failed to read existing {META_FILE}: {e}", file=sys.stderr)

    mock_provided = (args.mock_etag is not None) or (args.mock_last_modified is not None)
    new_meta = {}
    update_detected = False

    for sem, pdf_url in discovered_schedules.items():
        filename = os.path.basename(pdf_url)
        local_pdf_path = os.path.join(PDF_DIR, filename)

        # Check HTTP HEAD / metadata
        remote_last_modified = ""
        remote_etag = ""
        try:
            req_head = urllib.request.Request(
                pdf_url,
                method="HEAD",
                headers={"User-Agent": "ClemenTime-UpdateChecker/1.0"},
            )
            with urllib.request.urlopen(req_head) as resp:
                headers = dict(resp.headers)
                remote_last_modified = headers.get("Last-Modified", "")
                remote_etag = headers.get("ETag", "")
        except Exception as e:
            print(f"[Warning] HEAD check failed for {pdf_url}: {e}", file=sys.stderr)

        if args.mock_etag is not None:
            remote_etag = args.mock_etag
        if args.mock_last_modified is not None:
            remote_last_modified = args.mock_last_modified

        prev_sem_meta = saved_meta.get(sem, {})
        url_changed = prev_sem_meta.get("url") != pdf_url
        headers_changed = (
            (remote_last_modified and prev_sem_meta.get("last_modified") != remote_last_modified)
            or (remote_etag and prev_sem_meta.get("etag") != remote_etag)
        )
        file_missing = not os.path.exists(local_pdf_path)

        should_download = args.force or mock_provided or url_changed or headers_changed or file_missing

        if should_download:
            print(f"[Download] Fetching updated {sem} PDF from {pdf_url}...")
            try:
                req_get = urllib.request.Request(
                    pdf_url,
                    headers={"User-Agent": "ClemenTime-UpdateChecker/1.0"},
                )
                with urllib.request.urlopen(req_get) as resp_get:
                    pdf_data = resp_get.read()
                
                new_sha256 = compute_sha256_bytes(pdf_data)

                # Check if hash actually changed relative to existing file
                existing_sha = compute_sha256_file(local_pdf_path) if os.path.exists(local_pdf_path) else ""
                if new_sha256 != existing_sha or should_download:
                    # Non-cumulative purge: remove old semester PDFs before writing new one
                    purge_old_semester_pdfs(PDF_DIR, sem, filename)

                    with open(local_pdf_path, "wb") as f:
                        f.write(pdf_data)

                    update_detected = True
                    print(f"[Update] Saved {filename} to {PDF_DIR}")

                new_meta[sem] = {
                    "filename": filename,
                    "url": pdf_url,
                    "last_modified": remote_last_modified,
                    "etag": remote_etag,
                    "sha256": new_sha256,
                }
            except Exception as e:
                print(f"[Error] Downloading {pdf_url} failed: {e}", file=sys.stderr)
                if not args.force:
                    sys.exit(1)
        else:
            new_sha256 = prev_sem_meta.get("sha256", "")
            if not new_sha256 and os.path.exists(local_pdf_path):
                new_sha256 = compute_sha256_file(local_pdf_path)

            new_meta[sem] = {
                "filename": filename,
                "url": pdf_url,
                "last_modified": remote_last_modified or prev_sem_meta.get("last_modified", ""),
                "etag": remote_etag or prev_sem_meta.get("etag", ""),
                "sha256": new_sha256,
            }

    # Save updated metadata
    if new_meta:
        with open(META_FILE, "w", encoding="utf-8") as f:
            json.dump(new_meta, f, indent=2)
            f.write("\n")

        current_pdf_meta = {}
        for pdf_path in sorted(glob.glob(os.path.join(PDF_DIR, "*.pdf"))):
            current_pdf_meta[os.path.basename(pdf_path)] = compute_sha256_file(pdf_path)
        with open(PDF_META_FILE, "w", encoding="utf-8") as f:
            json.dump(current_pdf_meta, f, indent=2)
            f.write("\n")

    if update_detected or args.force or mock_provided:
        print(f"[Update Detected] Schedule PDFs updated or force flag enabled.")
        sys.exit(0)
    else:
        print("[No Update] Schedule files unchanged.")
        sys.exit(0)


if __name__ == "__main__":
    main()
