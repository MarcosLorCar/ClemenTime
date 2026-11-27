"""Stage and publish per-faculty schedules, combined v2 catalog, and ESI legacy index."""

import copy
import hashlib
import json
import os
from collections.abc import Collection, Mapping, Sequence
from datetime import date
from pathlib import Path, PurePosixPath
from tempfile import TemporaryDirectory

from .contract import BatchResult, FacultyKey, FacultyOutcome, FacultySpec

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


def _safe_dist_target(dist: Path, relative_path: str) -> Path | None:
    try:
        pure = PurePosixPath(relative_path)
    except ValueError:
        return None
    if pure.is_absolute() or ".." in pure.parts or "." in pure.parts or relative_path != pure.as_posix():
        return None
    dist_resolved = dist.resolve()
    cursor = dist_resolved
    for part in pure.parts:
        cursor = cursor / part
        if cursor.is_symlink():
            return None
    target = dist_resolved / pure
    if target.resolve() != target or not target.resolve().is_relative_to(dist_resolved):
        return None
    return target


def _term_hashes(faculty_entry: dict | None) -> dict[str, str]:
    if not isinstance(faculty_entry, dict):
        return {}
    terms = faculty_entry.get("terms")
    if not isinstance(terms, list):
        return {}
    result = {}
    for item in terms:
        if isinstance(item, dict) and isinstance(item.get("id"), str) and isinstance(item.get("hash"), str):
            result[item["id"]] = item["hash"]
    return result


def _load_existing_catalog(dist: Path) -> dict:
    catalog = read_json(dist / CATALOG, {"version": 2, "universities": []})
    if not isinstance(catalog, dict) or not isinstance(catalog.get("universities"), list):
        raise ValueError(f"Invalid existing catalog structure in {dist / CATALOG}.")
    return catalog


def _prepare_faculty_update(
    spec: FacultySpec,
    dist: Path,
    today: date,
    faculty_schedules: Mapping[str, bytes],
    previous_faculty: dict | None,
    legacy_previous_terms: dict[str, dict],
    explicit_removals: set[str],
    *,
    index_only: bool,
) -> tuple[dict | None, list[dict] | None, dict[str, bytes], set[str], tuple[str, ...]]:
    """Build the catalog entry, legacy index entries, files to write/remove, and changed periods for one faculty."""
    previous_terms: dict[str, dict] = {}
    if isinstance(previous_faculty, dict) and isinstance(previous_faculty.get("terms"), list):
        for item in previous_faculty["terms"]:
            if isinstance(item, dict) and isinstance(item.get("id"), str):
                previous_terms[item["id"]] = item
    if spec.legacy_index:
        # Preserve legacy updatedTime values when migrating or regenerating from schedules_index.json.
        for term_id, item in legacy_previous_terms.items():
            previous_terms.setdefault(term_id, item)

    declared_ids = {period.id for period in spec.periods}
    files_to_remove: set[str] = set()
    for prev_id, prev_item in previous_terms.items():
        if prev_id not in declared_ids:
            if prev_id in explicit_removals:
                prev_path = prev_item.get("path")
                if isinstance(prev_path, str) and prev_path not in INDEX_FILES:
                    files_to_remove.add(prev_path)
            else:
                raise ValueError(
                    f"Refusing to remove previously published period {prev_id!r} for {spec.key} "
                    "without explicit removal (configure removed_periods or pass --remove-period)."
                )

    terms: list[dict] = []
    legacy: list[dict] = []
    staged_schedules: dict[str, bytes] = {}
    new_hashes: dict[str, str] = {}

    for period in sorted(spec.periods, key=lambda item: item.id):
        if period.id in explicit_removals:
            if period.path not in INDEX_FILES:
                files_to_remove.add(period.path)
            continue

        content = faculty_schedules.get(period.path)
        if content is not None:
            staged_schedules[period.path] = content
        else:
            try:
                content = (dist / period.path).read_bytes()
            except FileNotFoundError:
                if period.id in previous_terms:
                    raise ValueError(
                        f"Previously published period {period.id!r} for {spec.key} is missing at "
                        f"{period.path!r}. Missing data does not remove a period without explicit confirmation."
                    ) from None
                continue

        digest = hashlib.sha256(content).hexdigest()
        new_hashes[period.id] = digest
        old = previous_terms.get(period.id, {})
        updated_time = old.get("updatedTime") if old.get("hash") == digest else None
        metadata = {
            "description": period.description,
            "path": period.path,
            "hash": digest,
            "updatedTime": updated_time or today.isoformat(),
        }
        terms.append({"id": period.id, "name": period.name, **metadata})
        legacy.append({"id": period.id, "title": period.name, **metadata})

    prev_hashes = _term_hashes(previous_faculty)
    if spec.legacy_index and not prev_hashes:
        prev_hashes = {
            term_id: item["hash"]
            for term_id, item in legacy_previous_terms.items()
            if isinstance(item.get("hash"), str)
        }

    if not terms:
        if index_only and previous_faculty is None and not previous_terms:
            return None, None, {}, set(), ()
        if previous_terms and set(previous_terms).issubset(explicit_removals):
            changed_periods = tuple(sorted(prev_hashes))
            return None, ([] if spec.legacy_index else None), {}, files_to_remove, changed_periods
        raise ValueError(f"No schedule files available for {spec.key}.")

    faculty: dict[str, object] = {"id": spec.key.faculty_id, "name": spec.faculty_name}
    for field in ("fcm_topic_prefix", "subgroups_label", "period_type", "period_count"):
        value = getattr(spec, field)
        if value is not None:
            faculty[field] = value
    faculty["terms"] = terms

    all_period_ids = sorted(set(prev_hashes) | set(new_hashes))
    changed_periods = tuple(
        period_id for period_id in all_period_ids if prev_hashes.get(period_id) != new_hashes.get(period_id)
    )
    return faculty, (legacy if spec.legacy_index else None), staged_schedules, files_to_remove, changed_periods


def _apply_staged_changes(
    dist: Path,
    files_to_write: Mapping[str, bytes],
    files_to_remove: Collection[str],
) -> tuple[frozenset[str], frozenset[str]]:
    if not files_to_write and not files_to_remove:
        return frozenset(), frozenset()
    dist_resolved = dist.resolve()
    dist_resolved.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix=".schedule-", dir=dist_resolved) as tmp:
        staging = Path(tmp)
        replacements = []
        for index, (name, content) in enumerate(files_to_write.items()):
            target = dist_resolved / name
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

        removals = []
        for index, name in enumerate(sorted(files_to_remove)):
            if name in files_to_write:
                continue
            target = _safe_dist_target(dist_resolved, name)
            if target is None or not target.is_file():
                continue
            backup = staging / f"rm-{index}"
            backup.write_bytes(target.read_bytes())
            removals.append((name, target, backup))

        replaced = []
        removed = []
        try:
            for name, target, staged, backup in replacements:
                staged.replace(target)
                replaced.append((name, target, backup))
            for name, target, backup in removals:
                target.unlink()
                removed.append((name, target, backup))
        except OSError:
            for _, target, backup in reversed(removed):
                backup.replace(target)
            for _, target, backup in reversed(replaced):
                if backup is None:
                    target.unlink(missing_ok=True)
                else:
                    backup.replace(target)
            raise

    return frozenset(name for name, _, _ in replaced), frozenset(name for name, _, _ in removed)


def publish_batch(
    specs: Sequence[FacultySpec],
    dist: Path,
    today: date,
    schedules_by_faculty: Mapping[FacultyKey, Mapping[str, bytes]],
    *,
    remove_periods: Mapping[FacultyKey, Collection[str]] | None = None,
    index_only: bool = False,
    isolate_failures: bool = False,
) -> BatchResult:
    """Validate, stage, and publish a multi-faculty batch into a coherent catalog and dist directory."""
    dist_resolved = dist.resolve()
    existing_catalog = _load_existing_catalog(dist_resolved)
    catalog = copy.deepcopy(existing_catalog)

    legacy_previous_terms: dict[str, dict] = {}
    try:
        raw_legacy = read_json(dist_resolved / LEGACY_INDEX, [])
        if isinstance(raw_legacy, list):
            for item in raw_legacy:
                if isinstance(item, dict) and isinstance(item.get("id"), str):
                    legacy_previous_terms[item["id"]] = item
    except ValueError:
        legacy_previous_terms = {}

    staged_files: dict[str, bytes] = {}
    staged_removals: set[str] = set()
    faculty_plan: list[tuple[FacultySpec, tuple[str, ...], tuple[str, ...], tuple[str, ...], bool]] = []
    failed_outcomes: list[FacultyOutcome] = []
    catalog_touched = False

    for spec in specs:
        explicit_removals = set(spec.removed_periods)
        if remove_periods and spec.key in remove_periods:
            explicit_removals.update(remove_periods[spec.key])

        university = next(
            (item for item in catalog["universities"] if isinstance(item, dict) and item.get("id") == spec.key.university_id),
            None,
        )
        previous_faculty = None
        if university is not None and isinstance(university.get("faculties"), list):
            previous_faculty = next(
                (item for item in university["faculties"] if isinstance(item, dict) and item.get("id") == spec.key.faculty_id),
                None,
            )

        try:
            updated_faculty, legacy_entries, fac_writes, fac_removals, changed_periods = _prepare_faculty_update(
                spec=spec,
                dist=dist_resolved,
                today=today,
                faculty_schedules=schedules_by_faculty.get(spec.key, {}),
                previous_faculty=previous_faculty,
                legacy_previous_terms=legacy_previous_terms,
                explicit_removals=explicit_removals,
                index_only=index_only,
            )
        except (OSError, ValueError, KeyError, TypeError) as error:
            if not isolate_failures:
                raise
            failed_outcomes.append(
                FacultyOutcome(
                    key=spec.key,
                    status="failed",
                    stage="publication",
                    diagnostic=str(error),
                )
            )
            continue

        if updated_faculty is None:
            # Either skipped unpublished faculty in index_only mode, or all periods explicitly removed.
            if previous_faculty is not None and university is not None:
                university["faculties"].remove(previous_faculty)
                if not university["faculties"]:
                    catalog["universities"].remove(university)
                catalog_touched = True
            if legacy_entries is not None:
                staged_files[LEGACY_INDEX] = json_bytes(legacy_entries, trailing_newline=True)
            staged_removals.update(fac_removals)
            faculty_plan.append((spec, changed_periods, (), tuple(sorted(fac_removals)), False))
            continue

        if university is None:
            university = {"id": spec.key.university_id, "name": spec.university_name, "faculties": []}
            catalog["universities"].append(university)
        else:
            university["name"] = spec.university_name

        faculties_list = university["faculties"]
        if previous_faculty is None:
            faculties_list.append(updated_faculty)
        else:
            faculties_list[faculties_list.index(previous_faculty)] = updated_faculty

        catalog_touched = True
        staged_files.update(fac_writes)
        staged_removals.update(fac_removals)
        if legacy_entries is not None:
            staged_files[LEGACY_INDEX] = json_bytes(legacy_entries, trailing_newline=True)

        faculty_plan.append((spec, changed_periods, tuple(sorted(fac_writes)), tuple(sorted(fac_removals)), True))

    if not faculty_plan:
        return BatchResult(outcomes=tuple(failed_outcomes))

    if catalog_touched:
        staged_files[CATALOG] = json_bytes(catalog, trailing_newline=True)

    # Never remove a path that is also being written in this batch.
    effective_removals = staged_removals - set(staged_files)
    written_files, removed_files = _apply_staged_changes(dist_resolved, staged_files, effective_removals)

    outcomes_by_key: dict[FacultyKey, FacultyOutcome] = {outcome.key: outcome for outcome in failed_outcomes}
    changed_schedule_faculties: list[FacultyKey] = []

    for spec, changed_periods, planned_writes, planned_removals, is_active_in_catalog in faculty_plan:
        fac_written = tuple(path for path in planned_writes if path in written_files)
        fac_removed = tuple(path for path in planned_removals if path in removed_files)
        if changed_periods and is_active_in_catalog:
            changed_schedule_faculties.append(spec.key)
        status = "published" if (changed_periods or fac_written or fac_removed) else "unchanged"
        outcomes_by_key[spec.key] = FacultyOutcome(
            key=spec.key,
            status=status,
            changed_periods=changed_periods,
            written_files=fac_written,
            removed_files=fac_removed,
        )

    ordered_outcomes = tuple(outcomes_by_key[spec.key] for spec in specs if spec.key in outcomes_by_key)
    return BatchResult(
        outcomes=ordered_outcomes,
        written_files=written_files,
        removed_files=removed_files,
        changed_schedule_faculties=tuple(changed_schedule_faculties),
    )


def publish(
    spec: FacultySpec,
    dist: Path,
    today: date,
    schedules: dict[str, bytes],
    *,
    remove_periods: Collection[str] = (),
) -> set[str]:
    """Single-faculty compatibility wrapper over publish_batch."""
    removals = {spec.key: remove_periods} if remove_periods else None
    result = publish_batch(
        specs=(spec,),
        dist=dist,
        today=today,
        schedules_by_faculty={spec.key: schedules},
        remove_periods=removals,
    )
    return set(result.written_files)
