#!/usr/bin/env python3
"""
process_schedules.py

Project-root wrapper script to process schedule files (PDF -> MD -> JSON)
and regenerate the schedule index.

Usage:
  python3 process_schedules.py               # Interactive mode (local dev)
  python3 process_schedules.py --strict      # Non-interactive strict mode (CI)
  python3 process_schedules.py --md-only     # Process existing MDs without docling
"""

import sys
import os
import subprocess

def main():
    root_dir = os.path.dirname(os.path.abspath(__file__))
    script_dir = os.path.join(root_dir, "schedules", "script")
    
    convert_script = os.path.join(script_dir, "convert_schedule.py")
    index_script = os.path.join(script_dir, "generate_index.py")

    passthrough_args = sys.argv[1:]

    # 1. Run schedule conversion
    cmd_convert = [sys.executable, convert_script] + passthrough_args
    print(f"[Run] {' '.join(cmd_convert)}")
    res = subprocess.run(cmd_convert)
    if res.returncode != 0:
        print("[Error] Schedule conversion failed.")
        sys.exit(res.returncode)

    # 2. Run index generation
    cmd_index = [sys.executable, index_script]
    print(f"\n[Run] {' '.join(cmd_index)}")
    res_index = subprocess.run(cmd_index)
    if res_index.returncode != 0:
        print("[Error] Schedule index generation failed.")
        sys.exit(res_index.returncode)

    print("\n[Done] Schedules and index generated successfully!")

if __name__ == "__main__":
    main()
