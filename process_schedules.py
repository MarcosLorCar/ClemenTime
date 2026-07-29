#!/usr/bin/env python3
"""
process_schedules.py

Wrapper script to process JSON schedule files in schedules/input/
and generate output JSON files per cuatrimestre in dist/.
"""

import sys
import os
import subprocess
import glob
import argparse

def main():
    parser = argparse.ArgumentParser(description="Process schedule JSON files into semester output JSONs.")
    parser.add_argument("json_file", nargs="?", help="Specific JSON file to process. If omitted, processes all in schedules/input/")
    parser.add_argument("--strict", action="store_true", help="Non-interactive mode")

    args = parser.parse_args()

    root_dir = os.path.dirname(os.path.abspath(__file__))
    script_dir = os.path.join(root_dir, "schedules", "script")
    input_dir = os.path.join(root_dir, "schedules", "input")

    parse_script = os.path.join(script_dir, "parse_schedule.py")
    index_script = os.path.join(script_dir, "generate_index.py")

    # 1. Determine JSON files to process
    files_to_process = []
    if args.json_file:
        if os.path.exists(args.json_file):
            files_to_process.append(args.json_file)
        else:
            print(f"[Error] File '{args.json_file}' not found.")
            sys.exit(1)
    else:
        files_to_process = [
            f for f in glob.glob(os.path.join(input_dir, "*.json"))
            if os.path.basename(f) != "esi_meta.json"
        ]
        if not files_to_process:
            print(f"[Warning] No JSON files found in {input_dir}")

    if not files_to_process:
        print("[Info] No files to process.")
    else:
        # 2. Run parsing script for each JSON file
        for json_path in files_to_process:
            cmd = [sys.executable, parse_script, json_path]
            if args.strict:
                cmd.append("--non-interactive")

            print(f"\n[Run] Processing {os.path.basename(json_path)}...")
            res = subprocess.run(cmd)
            if res.returncode != 0:
                print(f"[Error] Processing failed for {json_path}")
                if args.strict:
                    sys.exit(res.returncode)

    # 3. Regenerate index if index script exists
    if os.path.exists(index_script):
        print("\n[Run] Regenerating index...")
        res_index = subprocess.run([sys.executable, index_script])
        if res_index.returncode != 0:
            print("[Error] Schedule index generation failed.")
            sys.exit(res_index.returncode)

    print("\n[Done] Pipeline finished successfully!")

if __name__ == "__main__":
    main()