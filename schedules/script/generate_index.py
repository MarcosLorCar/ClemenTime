#!/usr/bin/env python3
import json
import os
import sys
import hashlib
from datetime import datetime

def get_file_hash(file_path):
    sha256_hash = hashlib.sha256()
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

def generate_index(dist_dir=None):
    if dist_dir is None:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        dist_dir = os.path.abspath(os.path.join(script_dir, "..", "dist"))

    if not os.path.exists(dist_dir):
        print(f"[Info] Output directory '{dist_dir}' does not exist. Creating it.")
        os.makedirs(dist_dir, exist_ok=True)

    index_entries = []

    for filename in sorted(os.listdir(dist_dir)):
        if filename.endswith(".json") and filename != "schedules_index.json":
            file_path = os.path.join(dist_dir, filename)
            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    data = json.load(f)

                schedule_id = os.path.splitext(filename)[0]
                title = data.get("title", filename)
                file_hash = get_file_hash(file_path)

                index_entries.append({
                    "id": schedule_id,
                    "title": title,
                    "description": "Horario oficial",
                    "path": filename,
                    "hash": file_hash,
                    "updatedTime": datetime.now().strftime("%Y-%m-%d")
                })
            except Exception as e:
                print(f"Skipping {filename}: {e}")

    output_path = os.path.join(dist_dir, "schedules_index.json")

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(index_entries, f, indent=2, ensure_ascii=False)

    print(f"Successfully generated {output_path} with {len(index_entries)} schedules.")

if __name__ == "__main__":
    generate_index()
