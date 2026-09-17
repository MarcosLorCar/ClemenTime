#!/usr/bin/env python3
"""
generate_index.py

Generates schedules_index.json for the distribution files in schedules/dist/.
Computes per-semester SHA256 hashes and deterministic academic year metadata
without relying on PDF filename regex scraping.
"""

import json
import os
import sys
import hashlib
from datetime import datetime


def get_file_hash(file_path: str) -> str:
    sha256_hash = hashlib.sha256()
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()


def get_academic_year(date: datetime = None) -> str:
    if date is None:
        date = datetime.now()
    # Spanish university academic year starts in September; July+ belongs to the new academic year
    if date.month >= 7:
        return f"{date.year}-{date.year + 1}"
    else:
        return f"{date.year - 1}-{date.year}"


def generate_index(dist_dir: str = None) -> str:
    if dist_dir is None:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        dist_dir = os.path.abspath(os.path.join(script_dir, "..", "dist"))

    if not os.path.exists(dist_dir):
        print(f"[Info] Output directory '{dist_dir}' does not exist. Creating it.")
        os.makedirs(dist_dir, exist_ok=True)

    index_entries = []
    files = sorted(os.listdir(dist_dir))
    json_files = [f for f in files if f.endswith(".json") and f != "schedules_index.json"]

    if not json_files:
        print(f"[Warning] No schedule JSON files found in {dist_dir}")

    acad_year = get_academic_year()

    # Pre-defined deterministic semester metadata
    metadata = {
        "1C": {
            "title": "Primer Cuatrimestre",
            "description": f"Horario oficial ESI UCLM - 1º Cuatrimestre ({acad_year})"
        },
        "2C": {
            "title": "Segundo Cuatrimestre",
            "description": f"Horario oficial ESI UCLM - 2º Cuatrimestre ({acad_year})"
        }
    }

    output_path = os.path.join(dist_dir, "schedules_index.json")
    existing_entries = {}
    if os.path.exists(output_path):
        try:
            with open(output_path, "r", encoding="utf-8") as f:
                loaded = json.load(f)
                if isinstance(loaded, list):
                    for item in loaded:
                        if isinstance(item, dict) and "id" in item:
                            existing_entries[item["id"]] = item
        except Exception:
            pass

    for filename in json_files:
        file_path = os.path.join(dist_dir, filename)
        schedule_id = os.path.splitext(filename)[0].upper()

        if schedule_id in metadata:
            title = metadata[schedule_id]["title"]
            description = metadata[schedule_id]["description"]
        else:
            title = f"Cuatrimestre {schedule_id}"
            description = f"Horario oficial ({acad_year})"

        file_hash = get_file_hash(file_path)
        prev_entry = existing_entries.get(schedule_id, {})

        # Retain previous updatedTime if the content hash has not changed
        if prev_entry.get("hash") == file_hash and prev_entry.get("updatedTime"):
            updated_time = prev_entry["updatedTime"]
        else:
            updated_time = datetime.now().strftime("%Y-%m-%d")

        index_entries.append({
            "id": schedule_id,
            "title": title,
            "description": description,
            "path": filename,
            "hash": file_hash,
            "updatedTime": updated_time
        })

    # Ensure 1C is listed before 2C
    index_entries.sort(key=lambda e: e["id"])

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(index_entries, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"Successfully generated {output_path} with {len(index_entries)} schedules.")
    return output_path


if __name__ == "__main__":
    generate_index()
