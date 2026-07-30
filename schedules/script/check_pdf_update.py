#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import sys
import glob

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT_DIR = os.path.normpath(os.path.join(SCRIPT_DIR, "..", ".."))
PDF_DIR = os.path.join(ROOT_DIR, "schedules", "pdf")
INPUT_DIR = os.path.join(ROOT_DIR, "schedules", "input")
META_FILE = os.path.join(INPUT_DIR, "pdf_meta.json")

def compute_sha256(filepath: str) -> str:
    sha256_hash = hashlib.sha256()
    with open(filepath, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

def main():
    parser = argparse.ArgumentParser(
        description="Check PDF schedule files for updates or changes."
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Force update detection regardless of file hashes",
    )
    args = parser.parse_args()

    os.makedirs(PDF_DIR, exist_ok=True)
    os.makedirs(INPUT_DIR, exist_ok=True)

    pdf_files = sorted(glob.glob(os.path.join(PDF_DIR, "*.pdf")))

    current_meta = {}
    for pdf_path in pdf_files:
        basename = os.path.basename(pdf_path)
        current_meta[basename] = compute_sha256(pdf_path)

    saved_meta = {}
    if os.path.exists(META_FILE):
        try:
            with open(META_FILE, "r", encoding="utf-8") as f:
                saved_meta = json.load(f)
        except Exception as e:
            print(f"[Warning] Failed to read existing {META_FILE}: {e}", file=sys.stderr)

    has_changes = (current_meta != saved_meta)
    should_update = args.force or has_changes or not pdf_files

    if should_update:
        print(f"[Update Detected] PDF schedules modified or force flag enabled ({len(pdf_files)} PDFs found).")
        with open(META_FILE, "w", encoding="utf-8") as f:
            json.dump(current_meta, f, indent=2)
            f.write("\n")
        sys.exit(0)
    else:
        print("[No Update] PDF schedules unchanged.")
        sys.exit(0)

if __name__ == "__main__":
    main()
