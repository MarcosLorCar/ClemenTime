#!/usr/bin/env python3
"""
process_schedules.py

Project-root wrapper script to process PDF schedule files (PDF -> flat JSON),
regenerate the schedule index, and update PDF metadata hashes.

Usage:
  python3 process_schedules.py               # Process all PDFs in schedules/pdf/
  python3 process_schedules.py my.pdf        # Process specific PDF
  python3 process_schedules.py --strict      # Non-interactive mode (CI)
  python3 process_schedules.py --model ...   # Specify Gemini model
  python3 process_schedules.py --clear-cache # Clear AI response cache
"""

import sys
import os
import subprocess
import glob
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

    env_model = os.getenv("GEMINI_MODEL")
    default_model = env_model if env_model else "gemini-2.0-flash"

    parser = argparse.ArgumentParser(description="Process schedule PDF files into flat semester JSONs.")
    parser.add_argument("pdf", nargs="?", help="Specific PDF to process. If omitted, processes all in schedules/pdf/")
    parser.add_argument("--strict", action="store_true", help="Non-interactive mode (CI)")
    parser.add_argument("--model", default=default_model, help=f"Gemini model ID to use (default: {default_model}).")
    parser.add_argument("--clear-cache", action="store_true", help="Clear AI response cache before running.")
    parser.add_argument("--check-esi", action="store_true", help="Check live ESI web page for updated PDFs before processing.")
    parser.add_argument("--page", type=int, action="append", dest="target_pages", help="Specific 1-based page number to re-parse (e.g. --page 10).")
    parser.add_argument("--pages", type=int, nargs="+", dest="target_pages_list", help="Specific 1-based page numbers to re-parse (e.g. --pages 10 11).")

    args, unknown = parser.parse_known_args()

    target_pages = args.target_pages or []
    if args.target_pages_list:
        target_pages.extend(args.target_pages_list)
    target_pages = list(set(target_pages)) if target_pages else None

    root_dir = os.path.dirname(os.path.abspath(__file__))
    script_dir = os.path.join(root_dir, "schedules", "script")
    pdf_dir = os.path.join(root_dir, "schedules", "pdf")

    parse_script = os.path.join(script_dir, "parse_schedule.py")
    index_script = os.path.join(script_dir, "generate_index.py")
    check_esi_script = os.path.join(script_dir, "check_esi_update.py")
    check_meta_script = os.path.join(script_dir, "check_pdf_update.py")

    # 0. Check live ESI web page if requested
    if args.check_esi and os.path.exists(check_esi_script):
        print("\n[Run] Checking live ESI web page for schedule updates...", flush=True)
        sys.stdout.flush()
        res_esi = subprocess.run([sys.executable, "-u", check_esi_script])
        if res_esi.returncode != 0:
            print("[Warning] Live ESI check encountered an error.", flush=True)

    # 1. Determine PDFs to process
    pdfs_to_process = []
    if args.pdf:
        if os.path.exists(args.pdf):
            pdfs_to_process.append(args.pdf)
        else:
            print(f"[Error] File '{args.pdf}' not found.", flush=True)
            sys.exit(1)
    else:
        pdfs_to_process = sorted(glob.glob(os.path.join(pdf_dir, "*.pdf")))
        if not pdfs_to_process:
            print(f"[Warning] No PDFs found in {pdf_dir}", flush=True)

    if not pdfs_to_process:
        print("[Info] No files to process.", flush=True)
    else:
        # 2. Run schedule parsing for each PDF
        for pdf in pdfs_to_process:
            cmd = [sys.executable, "-u", parse_script, pdf]
            if args.strict:
                cmd.append("--non-interactive")
            if args.model:
                cmd.extend(["--model", args.model])
            if args.clear_cache:
                cmd.append("--clear-cache")
            if target_pages:
                for p in target_pages:
                    cmd.extend(["--page", str(p)])

            print(f"\n[Run] Processing {os.path.basename(pdf)} with Gemini ({args.model})...", flush=True)
            sys.stdout.flush()
            res = subprocess.run(cmd)
            if res.returncode != 0:
                print(f"[Error] Parsing failed for {pdf}", flush=True)
                if args.strict:
                    sys.exit(res.returncode)

    # 3. Regenerate index
    if os.path.exists(index_script):
        print("\n[Run] Regenerating index...", flush=True)
        sys.stdout.flush()
        res_index = subprocess.run([sys.executable, "-u", index_script])
        if res_index.returncode != 0:
            print("[Error] Schedule index generation failed.", flush=True)
            sys.exit(res_index.returncode)

    # 4. Update PDF metadata hashes
    if os.path.exists(check_meta_script):
        print("\n[Run] Updating PDF metadata hash file...", flush=True)
        sys.stdout.flush()
        subprocess.run([sys.executable, "-u", check_meta_script])

    print("\n[Done] Pipeline finished successfully!", flush=True)

if __name__ == "__main__":
    main()