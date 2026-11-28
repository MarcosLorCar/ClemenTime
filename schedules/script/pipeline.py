"""Validate faculty results and publish the existing flat schedule format."""

import re
from collections.abc import Callable, Collection, Iterable, Mapping, Sequence
from dataclasses import dataclass
from datetime import date
from pathlib import Path, PurePosixPath
from typing import Any

from .contract import (
    BatchResult,
    FacultyAdapter,
    FacultyKey,
    FacultyOutcome,
    FacultySchedule,
    FacultySpec,
    ParseFailure,
    Payload,
    SourceFailure,
    Unchanged,
)
from .notify_fcm import build_topic_name
from .publication import INDEX_FILES, json_bytes, publish_batch

DAY_NAMES = ("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
PERIOD_COUNTS = {"ANNUAL": 1, "SEMESTERS": 2, "TRIMESTERS": 3, "QUARTERS": 4, "CUSTOM": 2}


def term_index(term_id: str) -> int | None:
    # Supported wire IDs are a subset of Android's TermCodeParser.
    token = term_id.upper().rsplit("_", 1)[-1]
    if token in {"ANNUAL", "ANUAL", "YEAR"}:
        return 1
    match = re.fullmatch(r"([1-8])[CTSQPA]?|[CTSQPA]([1-8])", token)
    return int(match[1] or match[2]) if match else None


def _ensure_no_symlinks_under(dist_resolved: Path, relative: PurePosixPath, key: FacultyKey, raw_path: str) -> None:
    cursor = dist_resolved
    for part in relative.parts:
        cursor = cursor / part
        if cursor.is_symlink():
            raise ValueError(f"Symlink path not allowed for {key}: {raw_path}")


def validate_specs(specs: Iterable[FacultySpec], dist: Path) -> None:
    dist_resolved = dist.resolve()
    keys_ci: set[str] = set()
    university_names: dict[str, str] = {}
    topics: dict[str, FacultyKey] = {}
    paths = {dist_resolved / name for name in INDEX_FILES}
    paths_ci = {path.as_posix().lower() for path in paths}
    if any(path.is_symlink() for path in paths):
        raise ValueError("Index paths must be regular files in the distribution directory.")
    legacy_owner = None
    for spec in specs:
        key = spec.key
        if any(not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", part) for part in (key.university_id, key.faculty_id)):
            raise ValueError(f"Invalid faculty key: {key}")
        key_ci = str(key).lower()
        if key_ci in keys_ci:
            raise ValueError(f"Duplicate faculty key: {key}")
        keys_ci.add(key_ci)

        existing_uni_name = university_names.get(key.university_id)
        if existing_uni_name is not None and existing_uni_name != spec.university_name:
            raise ValueError(
                f"Conflicting university names for {key.university_id!r}: "
                f"{existing_uni_name!r} vs {spec.university_name!r}."
            )
        university_names[key.university_id] = spec.university_name

        topic = build_topic_name(key.university_id, key.faculty_id, spec.fcm_topic_prefix)
        if topic in topics:
            raise ValueError(f"Conflicting FCM topic {topic!r} between {topics[topic]} and {key}.")
        topics[topic] = key

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

            if "\\" in period.path:
                raise ValueError(f"Invalid output path for {key}: {period.path}")
            path = PurePosixPath(period.path)
            if (
                path.is_absolute()
                or ".." in path.parts
                or "." in path.parts
                or period.path != path.as_posix()
                or path.suffix != ".json"
                or not all(re.fullmatch(r"[a-zA-Z0-9_.-]+", part) for part in path.parts)
            ):
                raise ValueError(f"Invalid output path for {key}: {period.path}")
            _ensure_no_symlinks_under(dist_resolved, path, key, period.path)
            target = dist_resolved / path
            resolved_target = target.resolve()
            target_ci = target.as_posix().lower()
            if (
                resolved_target != target
                or not resolved_target.is_relative_to(dist_resolved)
                or target_ci in paths_ci
                or any(target.is_relative_to(known) or known.is_relative_to(target) for known in paths)
            ):
                raise ValueError(f"Conflicting or unsafe output path for {key}: {period.path}")
            paths.add(target)
            paths_ci.add(target_ci)

        removed_seen: set[str] = set()
        for removed_id in spec.removed_periods:
            if removed_id in removed_seen or removed_id in ids:
                raise ValueError(f"Invalid or overlapping removed period {removed_id!r} for {key}.")
            removed_seen.add(removed_id)

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


@dataclass(frozen=True)
class _StagedFaculty:
    schedules: dict[str, bytes]
    commit_cache: Callable[[], None] | None = None


def stage_faculty(
    spec: FacultySpec,
    adapter: FacultyAdapter[Payload],
    *,
    fixture: Path | None = None,
    remote: bool = False,
    force: bool = False,
    strict: bool = False,
) -> _StagedFaculty | SourceFailure | ParseFailure:
    source = adapter.acquire(force=force) if remote else adapter.read_local(fixture)
    if isinstance(source, Unchanged):
        print(f"[Source] {spec.key}: Unchanged. Reprocessing the cached input with the current pipeline.")
        source = adapter.read_local(None)
    if isinstance(source, SourceFailure):
        return source

    schedule = adapter.normalize(source.payload, strict=strict)
    if isinstance(schedule, ParseFailure):
        return schedule
    try:
        serialized = serialize_schedule(spec, schedule)
    except ValueError as error:
        return ParseFailure(str(error))
    return _StagedFaculty(schedules=serialized, commit_cache=source.commit_cache)


def run_batch_pipeline(
    entries: Sequence[tuple[FacultySpec, FacultyAdapter[Any]]],
    dist: Path,
    today: date,
    *,
    all_specs: Iterable[FacultySpec] | None = None,
    fixture_by_faculty: Mapping[FacultyKey, Path] | None = None,
    remote_by_faculty: Mapping[FacultyKey, bool] | None = None,
    remote: bool = False,
    force: bool = False,
    strict: bool = False,
    remove_periods: Mapping[FacultyKey, Collection[str]] | None = None,
) -> BatchResult:
    """Stage every selected faculty independently, then publish all valid faculties in one coherent batch."""
    dist_resolved = dist.resolve()
    specs_to_validate = tuple(all_specs) if all_specs is not None else tuple(spec for spec, _ in entries)
    validate_specs(specs_to_validate, dist_resolved)

    staged_specs: list[FacultySpec] = []
    schedules_by_faculty: dict[FacultyKey, dict[str, bytes]] = {}
    cache_commits: dict[FacultyKey, Callable[[], None]] = {}
    pre_failures: dict[FacultyKey, FacultyOutcome] = {}

    for spec, adapter in entries:
        fixture = fixture_by_faculty.get(spec.key) if fixture_by_faculty else None
        use_remote = remote_by_faculty.get(spec.key, remote) if remote_by_faculty else remote
        staged = stage_faculty(spec, adapter, fixture=fixture, remote=use_remote, force=force, strict=strict)
        if isinstance(staged, SourceFailure):
            pre_failures[spec.key] = FacultyOutcome(
                key=spec.key,
                status="failed",
                stage="source",
                diagnostic=staged.diagnostic,
            )
            continue
        if isinstance(staged, ParseFailure):
            pre_failures[spec.key] = FacultyOutcome(
                key=spec.key,
                status="failed",
                stage="parse",
                diagnostic=staged.diagnostic,
            )
            continue
        staged_specs.append(spec)
        schedules_by_faculty[spec.key] = staged.schedules
        if staged.commit_cache is not None:
            cache_commits[spec.key] = staged.commit_cache

    if not staged_specs:
        return BatchResult(outcomes=tuple(pre_failures[spec.key] for spec, _ in entries))

    published = publish_batch(
        specs=staged_specs,
        dist=dist_resolved,
        today=today,
        schedules_by_faculty=schedules_by_faculty,
        remove_periods=remove_periods,
        isolate_failures=True,
    )
    outcomes_by_key = {**pre_failures, **{outcome.key: outcome for outcome in published.outcomes}}
    for outcome in published.outcomes:
        if outcome.status != "failed" and outcome.key in cache_commits:
            try:
                cache_commits[outcome.key]()
            except OSError as error:
                outcomes_by_key[outcome.key] = FacultyOutcome(
                    key=outcome.key,
                    status="failed",
                    changed_periods=outcome.changed_periods,
                    written_files=outcome.written_files,
                    removed_files=outcome.removed_files,
                    stage="cache",
                    diagnostic=f"Could not persist input cache: {error}",
                )
    ordered_outcomes = tuple(outcomes_by_key[spec.key] for spec, _ in entries if spec.key in outcomes_by_key)
    return BatchResult(
        outcomes=ordered_outcomes,
        written_files=published.written_files,
        removed_files=published.removed_files,
        changed_schedule_faculties=published.changed_schedule_faculties,
    )


def format_batch_summary(result: BatchResult, *, mode: str) -> str:
    """Render a concise GitHub-flavored Markdown summary table for CI step summaries."""
    lines = [
        f"### Schedule Pipeline Summary (`{mode}`)",
        "",
        f"Overall status: **{result.status}**",
        "",
        "| Faculty | Status | Changed periods | Details |",
        "| --- | --- | --- | --- |",
    ]
    for outcome in result.outcomes:
        changed = ", ".join(outcome.changed_periods) if outcome.changed_periods else "none"
        if outcome.status == "failed":
            detail = f"{outcome.stage or 'error'}: {outcome.diagnostic or 'unknown error'}"
        elif outcome.written_files or outcome.removed_files:
            parts = []
            if outcome.written_files:
                parts.append("wrote " + ", ".join(outcome.written_files))
            if outcome.removed_files:
                parts.append("removed " + ", ".join(outcome.removed_files))
            detail = "; ".join(parts)
        else:
            detail = "No published changes"
        safe_detail = detail.replace("|", "\\|").replace("\n", " ")
        lines.append(f"| `{outcome.key}` | **{outcome.status}** | {changed} | {safe_detail} |")
    if result.changed_schedule_faculties:
        targets = ", ".join(f"`{key}`" for key in result.changed_schedule_faculties)
        lines.extend(["", f"Faculties eligible for FCM after confirmed push: {targets}"])
    else:
        lines.extend(["", "Faculties eligible for FCM after confirmed push: none"])
    lines.append("")
    return "\n".join(lines)
