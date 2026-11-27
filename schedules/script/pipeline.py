"""Validate a faculty result and publish the existing flat schedule format."""

import re
from collections.abc import Iterable
from datetime import date
from pathlib import Path, PurePosixPath

from .contract import FacultyAdapter, FacultySchedule, FacultySpec, ParseFailure, Payload, SourceFailure, Unchanged
from .publication import INDEX_FILES, json_bytes, publish

DAY_NAMES = ("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
PERIOD_COUNTS = {"ANNUAL": 1, "SEMESTERS": 2, "TRIMESTERS": 3, "QUARTERS": 4, "CUSTOM": 2}


def term_index(term_id: str) -> int | None:
    # Supported wire IDs are a subset of Android's TermCodeParser.
    token = term_id.upper().rsplit("_", 1)[-1]
    if token in {"ANNUAL", "ANUAL", "YEAR"}:
        return 1
    match = re.fullmatch(r"([1-8])[CTSQPA]?|[CTSQPA]([1-8])", token)
    return int(match[1] or match[2]) if match else None


def validate_specs(specs: Iterable[FacultySpec], dist: Path) -> None:
    keys = set()
    paths = {dist / name for name in INDEX_FILES}
    if any(path.is_symlink() for path in paths):
        raise ValueError("Index paths must be regular files in the distribution directory.")
    legacy_owner = None
    for spec in specs:
        key = spec.key
        if any(not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", part) for part in (key.university_id, key.faculty_id)):
            raise ValueError(f"Invalid faculty key: {key}")
        if key in keys:
            raise ValueError(f"Duplicate faculty key: {key}")
        keys.add(key)
        if spec.legacy_index:
            if legacy_owner is not None:
                raise ValueError("Only one faculty can publish the legacy index.")
            legacy_owner = key

        if not spec.periods:
            raise ValueError(f"{key} has no configured periods.")
        ids = set()
        indices = set()
        for period in spec.periods:
            if period.id in ids or period.index in indices or term_index(period.id) != period.index:
                raise ValueError(f"Invalid or duplicate period {period.id!r} (index {period.index}) for {key}.")
            ids.add(period.id)
            indices.add(period.index)

            path = PurePosixPath(period.path)
            if (
                path.is_absolute() or ".." in path.parts or path.suffix != ".json"
                or not all(re.fullmatch(r"[a-zA-Z0-9_.-]+", part) for part in path.parts)
            ):
                raise ValueError(f"Invalid output path for {key}: {period.path}")
            target = dist / path
            if target.resolve() != target or any(
                target.is_relative_to(known) or known.is_relative_to(target) for known in paths
            ):
                raise ValueError(f"Conflicting or unsafe output path for {key}: {period.path}")
            paths.add(target)

        count = spec.period_count
        if count is not None and not 1 <= count <= 8:
            raise ValueError(f"Invalid period count for {key}: {count}")
        if spec.period_type is not None:
            if spec.period_type not in PERIOD_COUNTS:
                raise ValueError(f"Invalid period type for {key}: {spec.period_type}")
            if spec.period_type != "CUSTOM" or count is None:
                count = PERIOD_COUNTS[spec.period_type]
        if count is not None and max(indices) > count:
            raise ValueError(f"The period configuration for {key} does not cover all declared indices.")


def serialize_schedule(spec: FacultySpec, schedule: FacultySchedule) -> dict[str, bytes]:
    if schedule.key != spec.key:
        raise ValueError(f"Expected schedule for {spec.key}, got {schedule.key}.")
    periods = {period.id: period for period in spec.periods}
    output = {}
    for period_id, sessions in schedule.sessions_by_period.items():
        if period_id not in periods:
            raise ValueError(f"Undeclared period {period_id!r} for {spec.key}.")
        period = periods[period_id]
        if not sessions and not period.allow_empty:
            raise ValueError(f"Empty period {period_id!r} for {spec.key}.")
        for session in sessions:
            if (
                not 1 <= session.day <= 7
                or any(value.second or value.microsecond or value.tzinfo is not None for value in (session.start, session.end))
            ):
                raise ValueError(f"Invalid day or time for {session.subject_code!r} in {period_id}.")
        slots = sorted(sessions, key=lambda session: (session.group, session.day, session.start, session.subject_code))
        output[period.path] = json_bytes([
            {
                "grupo": slot.group,
                "cuatrimestre": period_id,
                "dia": DAY_NAMES[slot.day - 1],
                "hora_inicio": slot.start.strftime("%H:%M"),
                "hora_fin": slot.end.strftime("%H:%M"),
                "codigo": slot.subject_code,
                "asignatura": slot.subject_name,
                "tipo": slot.kind,
                "aula": slot.room,
                "profesor": slot.professor,
                "es_laboratorio": slot.is_lab,
                "grupo_practicas": slot.subgroup,
            }
            for slot in slots
        ])
    if not output:
        raise ValueError(f"No publishable periods for {spec.key}.")
    return output


def run_pipeline(
    spec: FacultySpec,
    adapter: FacultyAdapter[Payload],
    dist: Path,
    today: date,
    *,
    fixture: Path | None = None,
    remote: bool = False,
    force: bool = False,
    strict: bool = False,
) -> set[str] | SourceFailure | ParseFailure:
    dist = dist.resolve()
    validate_specs((spec,), dist)
    source = adapter.acquire(force=force) if remote else adapter.read_local(fixture)
    if isinstance(source, Unchanged):
        print("[Source] Unchanged. Reprocessing the cached input with the current pipeline.")
        source = adapter.read_local(None)
    if isinstance(source, SourceFailure):
        return source

    schedule = adapter.normalize(source.payload, strict=strict)
    if isinstance(schedule, ParseFailure):
        return schedule
    try:
        files = serialize_schedule(spec, schedule)
    except ValueError as error:
        return ParseFailure(str(error))
    return publish(spec, dist, today, files)
