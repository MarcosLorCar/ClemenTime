#!/usr/bin/env python3
"""
process_schedules.py

Project-root wrapper script to fetch ESI schedule data from the TV hall endpoint,
process it into flat semester JSON files (dist/1C.json and dist/2C.json),
and regenerate schedules_index.json with per-semester SHA256 hashes.

Usage:
  python3 process_schedules.py               # Process schedules/input/schedules.json
  python3 process_schedules.py --check-esi   # Check live ESI API for updates first
  python3 process_schedules.py --force       # Force redownload from ESI API
  python3 process_schedules.py --strict      # Non-interactive mode (fails on unknown mappings)
  python3 process_schedules.py custom.json   # Process a specific JSON file
"""

import sys
import os
import subprocess
import argparse


def main():
    try:
        sys.stdout.reconfigure(line_buffering=True)
        sys.stderr.reconfigure(line_buffering=True)
    except Exception:
        pass

    try:
        from dotenv import load_dotenv
        root_dir = os.path.dirname(os.path.abspath(__file__))
        load_dotenv(os.path.join(root_dir, ".env"))
    except ImportError:
        pass

    parser = argparse.ArgumentParser(description="Process schedule files into flat semester JSONs.")
    parser.add_argument("file", nargs="?", help="Specific JSON or PDF file to process (defaults to schedules/input/schedules.json).")
    parser.add_argument("--strict", action="store_true", help="Non-interactive mode (halts on unresolved mappings)")
    parser.add_argument("--check-esi", action="store_true", help="Check live ESI API for updates before processing.")
    parser.add_argument("--force", action="store_true", help="Force redownload from ESI API during check.")

    args, unknown = parser.parse_known_args()

    root_dir = os.path.dirname(os.path.abspath(__file__))
    script_dir = os.path.join(root_dir, "schedules", "script")
    input_dir = os.path.join(root_dir, "schedules", "input")
    default_json = os.path.join(input_dir, "schedules.json")

    check_esi_script = os.path.join(script_dir, "check_esi_update.py")
    parse_script = os.path.join(script_dir, "parse_schedule.py")
    index_script = os.path.join(script_dir, "generate_index.py")

    # 1. Check live ESI API if requested or if default input is missing
    if (args.check_esi or (not args.file and not os.path.exists(default_json))) and os.path.exists(check_esi_script):
        print("\n[Run] Checking live ESI API endpoint for updates...", flush=True)
        check_cmd = [sys.executable, "-u", check_esi_script]
        if args.force:
            check_cmd.append("--force")
        res_esi = subprocess.run(check_cmd)
        if res_esi.returncode != 0:
            print(f"[Error] Live ESI check failed with exit code {res_esi.returncode}.", file=sys.stderr)
            if args.strict or not os.path.exists(default_json):
                sys.exit(res_esi.returncode)

    # 2. Determine file to process
    target_file = args.file if args.file else default_json
    if not os.path.exists(target_file):
        print(f"[Error] Schedule input file '{target_file}' not found.", file=sys.stderr)
        sys.exit(1)

    # 3. Run parse_schedule.py
    print(f"\n[Run] Processing {os.path.basename(target_file)}...", flush=True)
    parse_cmd = [sys.executable, "-u", parse_script, target_file]
    if args.strict:
        parse_cmd.append("--non-interactive")

    res_parse = subprocess.run(parse_cmd)
    if res_parse.returncode != 0:
        print(f"[Error] Schedule parsing failed with exit code {res_parse.returncode}", file=sys.stderr)
        sys.exit(res_parse.returncode)

    # 4. Regenerate schedule index
    if os.path.exists(index_script):
        print("\n[Run] Regenerating schedule index...", flush=True)
        res_index = subprocess.run([sys.executable, "-u", index_script])
        if res_index.returncode != 0:
            print("[Error] Schedule index generation failed.", file=sys.stderr)
            sys.exit(res_index.returncode)

    print("\n[Done] Pipeline finished successfully!", flush=True)


if __name__ == "__main__":
    main()