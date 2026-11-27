"""Prepare the catalog and legacy index before replacing any public files."""

import hashlib
import json
import os
from datetime import date
from pathlib import Path
from tempfile import TemporaryDirectory

from .contract import FacultySpec

LEGACY_INDEX = "schedules_index.json"
CATALOG = "schedules_catalog.json"
INDEX_FILES = (LEGACY_INDEX, CATALOG)


def json_bytes(data: object, *, trailing_newline: bool = False) -> bytes:
    text = json.dumps(data, indent=2, ensure_ascii=False)
    if trailing_newline:
        text += "\n"
    return text.replace("\n", os.linesep).encode("utf-8")


def read_json(path: Path, default):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return default


def build_indexes(spec: FacultySpec, dist: Path, today: date, schedules: dict[str, bytes]) -> dict[str, bytes]:
    catalog = read_json(dist / CATALOG, {"version": 2, "universities": []})
    university = next((item for item in catalog["universities"] if item["id"] == spec.key.university_id), None)
    if university is None:
        university = {"id": spec.key.university_id, "name": spec.university_name, "faculties": []}
        catalog["universities"].append(university)
    university["name"] = spec.university_name
    faculties = university["faculties"]
    previous_faculty = next((item for item in faculties if item["id"] == spec.key.faculty_id), None)
    previous_terms = previous_faculty["terms"] if previous_faculty else []
    if spec.legacy_index:
        try:
            previous_terms = read_json(dist / LEGACY_INDEX, [])
        except ValueError:
            previous_terms = []
    previous = {
        item["id"]: item for item in previous_terms
        if isinstance(item, dict) and isinstance(item.get("id"), str)
    } if isinstance(previous_terms, list) else {}

    terms = []
    legacy = []
    for period in sorted(spec.periods, key=lambda item: item.id):
        content = schedules.get(period.path)
        if content is None:
            try:
                content = (dist / period.path).read_bytes()
            except FileNotFoundError:
                continue
        digest = hashlib.sha256(content).hexdigest()
        old = previous.get(period.id, {})
        updated_time = old.get("updatedTime") if old.get("hash") == digest else None
        metadata = {
            "description": period.description,
            "path": period.path,
            "hash": digest,
            "updatedTime": updated_time or today.isoformat(),
        }
        terms.append({"id": period.id, "name": period.name, **metadata})
        legacy.append({"id": period.id, "title": period.name, **metadata})

    if not terms:
        raise ValueError(f"No schedule files available for {spec.key}.")

    faculty = {"id": spec.key.faculty_id, "name": spec.faculty_name}
    for field in ("fcm_topic_prefix", "subgroups_label", "period_type", "period_count"):
        value = getattr(spec, field)
        if value is not None:
            faculty[field] = value
    faculty["terms"] = terms
    if previous_faculty is None:
        faculties.append(faculty)
    else:
        faculties[faculties.index(previous_faculty)] = faculty

    indexes = {}
    if spec.legacy_index:
        indexes[LEGACY_INDEX] = json_bytes(legacy, trailing_newline=True)
    indexes[CATALOG] = json_bytes(catalog, trailing_newline=True)
    return indexes


def publish(spec: FacultySpec, dist: Path, today: date, schedules: dict[str, bytes]) -> set[str]:
    files = {**schedules, **build_indexes(spec, dist, today, schedules)}
    dist.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix=".schedule-", dir=dist) as tmp:
        staging = Path(tmp)
        replacements = []
        for index, (name, content) in enumerate(files.items()):
            target = dist / name
            try:
                original = target.read_bytes()
            except FileNotFoundError:
                original = None
            if original == content:
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            staged = staging / f"new-{index}"
            staged.write_bytes(content)
            backup = None
            if original is not None:
                backup = staging / f"old-{index}"
                backup.write_bytes(original)
            replacements.append((name, target, staged, backup))

        replaced = []
        try:
            for name, target, staged, backup in replacements:
                staged.replace(target)
                replaced.append((name, target, backup))
        except OSError:
            for _, target, backup in reversed(replaced):
                if backup is None:
                    target.unlink()
                else:
                    backup.replace(target)
            raise

    return {name for name, _, _ in replaced}
