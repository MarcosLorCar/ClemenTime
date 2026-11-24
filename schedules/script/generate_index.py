#!/usr/bin/env python3
"""
generate_index.py

Generates schedules_index.json and schedules_catalog.json for the distribution
files in schedules/dist/. Supports N semesters/terms per year with deterministic
metadata and natural term ordering.
"""

import hashlib
import json
import os
import re
from datetime import datetime


SPANISH_ORDINALS = {
    1: ("Primer", "1º"),
    2: ("Segundo", "2º"),
    3: ("Tercer", "3º"),
    4: ("Cuarto", "4º"),
    5: ("Quinto", "5º"),
    6: ("Sexto", "6º"),
}


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


def term_sort_key(schedule_id: str) -> tuple[int, str]:
    upper = schedule_id.strip().upper()
    if upper in ("ANUAL", "ANNUAL", "YEAR"):
        return (0, upper)
    m = re.search(r"([1-9]\d*)", upper)
    if m:
        return (int(m.group(1)), upper)
    return (999, upper)


def resolve_term_metadata(schedule_id: str, acad_year: str, school_prefix: str = "ESI UCLM") -> tuple[str, str]:
    upper = schedule_id.strip().upper()
    if upper in ("ANUAL", "ANNUAL", "YEAR"):
        return ("Curso Anual", f"Horario oficial {school_prefix} - Curso Anual ({acad_year})")

    m_cuat = re.match(r"^([1-9]\d*)C$", upper)
    if m_cuat:
        n = int(m_cuat.group(1))
        word, short_ord = SPANISH_ORDINALS.get(n, (f"{n}º", f"{n}º"))
        return (f"{word} Cuatrimestre", f"Horario oficial {school_prefix} - {short_ord} Cuatrimestre ({acad_year})")

    m_trim = re.match(r"^(?:([1-9]\d*)T|T([1-9]\d*))$", upper)
    if m_trim:
        n = int(m_trim.group(1) or m_trim.group(2))
        word, short_ord = SPANISH_ORDINALS.get(n, (f"{n}º", f"{n}º"))
        return (f"{word} Trimestre", f"Horario oficial {school_prefix} - {short_ord} Trimestre ({acad_year})")

    m_quart = re.match(r"^(?:([1-9]\d*)Q|Q([1-9]\d*))$", upper)
    if m_quart:
        n = int(m_quart.group(1) or m_quart.group(2))
        word, short_ord = SPANISH_ORDINALS.get(n, (f"{n}º", f"{n}º"))
        return (f"{word} Periodo", f"Horario oficial {school_prefix} - {short_ord} Periodo ({acad_year})")

    return (f"Periodo {schedule_id}", f"Horario oficial ({acad_year})")


def generate_index(dist_dir: str = None) -> str:
    if dist_dir is None:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        dist_dir = os.path.abspath(os.path.join(script_dir, "..", "dist"))

    if not os.path.exists(dist_dir):
        print(f"[Info] Output directory '{dist_dir}' does not exist. Creating it.")
        os.makedirs(dist_dir, exist_ok=True)

    index_entries = []
    files = sorted(os.listdir(dist_dir))
    json_files = [
        f for f in files
        if f.endswith(".json") and f not in ("schedules_index.json", "schedules_catalog.json")
    ]

    if not json_files:
        print(f"[Warning] No schedule JSON files found in {dist_dir}")

    acad_year = get_academic_year()

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

        title, description = resolve_term_metadata(schedule_id, acad_year)
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

    # Natural term ordering (1C, 2C, 3C, ..., 10C)
    index_entries.sort(key=lambda e: term_sort_key(e["id"]))

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(index_entries, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"Successfully generated {output_path} with {len(index_entries)} schedules.")

    catalog_path = os.path.join(dist_dir, "schedules_catalog.json")
    terms = [
        {
            "id": entry["id"],
            "name": entry["title"],
            "description": entry["description"],
            "path": entry["path"],
            "hash": entry["hash"],
            "updatedTime": entry["updatedTime"]
        }
        for entry in index_entries
    ]

    existing_catalog = None
    if os.path.exists(catalog_path):
        try:
            with open(catalog_path, "r", encoding="utf-8") as f:
                loaded_catalog = json.load(f)
                if isinstance(loaded_catalog, dict) and isinstance(loaded_catalog.get("universities"), list):
                    existing_catalog = loaded_catalog
        except Exception:
            existing_catalog = None

    esi_faculty_entry = {
        "id": "esi",
        "name": "Escuela Superior de Informática (Ciudad Real)",
        "fcm_topic_prefix": "uclm_esi",
        "subgroups_label": "Laboratorios",
        "terms": terms
    }

    if existing_catalog is not None:
        universities = existing_catalog.get("universities", [])
        uclm_uni = next((u for u in universities if str(u.get("id", "")).lower() == "uclm"), None)
        if uclm_uni is not None:
            faculties = uclm_uni.setdefault("faculties", [])
            esi_idx = next((i for i, fac in enumerate(faculties) if str(fac.get("id", "")).lower() == "esi"), None)
            if esi_idx is not None:
                faculties[esi_idx]["terms"] = terms
            else:
                faculties.insert(0, esi_faculty_entry)
        else:
            universities.insert(0, {
                "id": "uclm",
                "name": "Universidad de Castilla-La Mancha",
                "faculties": [esi_faculty_entry]
            })
        catalog_data = {
            "version": existing_catalog.get("version", 2),
            "universities": universities
        }
    else:
        catalog_data = {
            "version": 2,
            "universities": [
                {
                    "id": "uclm",
                    "name": "Universidad de Castilla-La Mancha",
                    "faculties": [esi_faculty_entry]
                }
            ]
        }

    with open(catalog_path, "w", encoding="utf-8") as f:
        json.dump(catalog_data, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"Successfully generated {catalog_path} (catalog schema v2).")
    return output_path


if __name__ == "__main__":
    generate_index()
