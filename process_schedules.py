#!/usr/bin/env python3
"""
process_schedules.py

Project-root wrapper script to process PDF schedule files (PDF -> flat JSON)
and regenerate the schedule index.

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

    args, unknown = parser.parse_known_args()

    root_dir = os.path.dirname(os.path.abspath(__file__))
    script_dir = os.path.join(root_dir, "schedules", "script")
    pdf_dir = os.path.join(root_dir, "schedules", "pdf")

    parse_script = os.path.join(script_dir, "parse_schedule.py")
    index_script = os.path.join(script_dir, "generate_index.py")

    # 1. Determine PDFs to process
    pdfs_to_process = []
    if args.pdf:
        if os.path.exists(args.pdf):
            pdfs_to_process.append(args.pdf)
        else:
            print(f"[Error] File '{args.pdf}' not found.")
            sys.exit(1)
    else:
        pdfs_to_process = sorted(glob.glob(os.path.join(pdf_dir, "*.pdf")))
        if not pdfs_to_process:
            print(f"[Warning] No PDFs found in {pdf_dir}")

    if not pdfs_to_process:
        print("[Info] No files to process.")
    else:
        # 2. Run schedule parsing for each PDF
        for pdf in pdfs_to_process:
            cmd = [sys.executable, parse_script, pdf]
            if args.strict:
                cmd.append("--non-interactive")
            if args.model:
                cmd.extend(["--model", args.model])
            if args.clear_cache:
                cmd.append("--clear-cache")

            print(f"\n[Run] Processing {os.path.basename(pdf)} with Gemini ({args.model})...")
            res = subprocess.run(cmd)
            if res.returncode != 0:
                print(f"[Error] Parsing failed for {pdf}")
                if args.strict:
                    sys.exit(res.returncode)

    # 3. Regenerate index
    if os.path.exists(index_script):
        print("\n[Run] Regenerating index...")
        res_index = subprocess.run([sys.executable, index_script])
        if res_index.returncode != 0:
            print("[Error] Schedule index generation failed.")
            sys.exit(res_index.returncode)

    print("\n[Done] Pipeline finished successfully!")

if __name__ == "__main__":
    main()