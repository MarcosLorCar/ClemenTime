#!/usr/bin/env python3
"""Run registered schedule adapters or rebuild multi-faculty indexes."""

import argparse
import json
import sys
from datetime import date
from pathlib import Path

from schedules.script.adapters.esi import ESI_KEY, EsiAdapter
from schedules.script.contract import BatchResult, FacultyKey
from schedules.script.pipeline import format_batch_summary, run_batch_pipeline, validate_specs
from schedules.script.publication import publish_batch
from schedules.script.registry import registered_faculties

EXIT_OK = 0
EXIT_FATAL = 1
EXIT_PARTIAL = 2


def _parse_remove_periods(raw_values: list[str], selected_keys: list[FacultyKey]) -> dict[FacultyKey, set[str]]:
    removals: dict[FacultyKey, set[str]] = {}
    selected_set = set(selected_keys)
    for raw in raw_values:
        if ":" in raw:
            fac_raw, period_id = raw.split(":", 1)
            key = FacultyKey.parse(fac_raw)
        elif len(selected_keys) == 1:
            key = selected_keys[0]
            period_id = raw
        else:
            raise ValueError(
                f"Ambiguous --remove-period {raw!r} with multiple faculties. Use '<university>/<faculty>:<period_id>'."
            )
        period_id = period_id.strip()
        if not period_id:
            raise ValueError(f"Empty period ID in --remove-period {raw!r}.")
        if key not in selected_set:
            raise ValueError(f"--remove-period target {key} is not among the selected faculties.")
        removals.setdefault(key, set()).add(period_id)
    return removals


def _write_report(report_path: Path | None, result: BatchResult, *, mode: str) -> None:
    if report_path is None:
        return
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(result.to_report_dict(mode=mode), indent=2) + "\n", encoding="utf-8")


def _append_summary(summary_path: Path | None, result: BatchResult, *, mode: str) -> None:
    if summary_path is None:
        return
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    with summary_path.open("a", encoding="utf-8") as handle:
        handle.write(format_batch_summary(result, mode=mode))


def main(argv: list[str] | None = None) -> int:
    root = Path(__file__).resolve().parent
    today = date.today()
    registered = registered_faculties(root / "schedules", today)
    faculties_by_name = {str(key): (key, spec, adapter) for key, (spec, adapter) in registered.items()}

    parser = argparse.ArgumentParser(description="Process schedules for one or more registered faculties.")
    parser.add_argument(
        "--faculty",
        action="append",
        dest="faculties",
        choices=sorted(faculties_by_name),
        help=f"Faculty key to process. Repeatable (legacy default: {ESI_KEY}).",
    )
    parser.add_argument("--all", action="store_true", help="Process every registered faculty in one publication batch.")
    parser.add_argument("file", nargs="?", type=Path, help="Local source file (positional alias for --fixture).")
    parser.add_argument("--fixture", type=Path, help="Reprocess a local source file offline.")
    parser.add_argument("--dist-dir", type=Path, default=root / "schedules" / "dist", help="Directory for public output.")
    parser.add_argument("--strict", action="store_true", help="Fail on unresolved mappings without prompting.")
    parser.add_argument("--sync", action="store_true", help="Check remote sources before processing.")
    parser.add_argument("--check-esi", action="store_true", help="Legacy alias for --faculty uclm/esi --sync.")
    parser.add_argument("--force", action="store_true", help="Download and process sources even when headers match.")
    parser.add_argument("--index-only", action="store_true", help="Refresh indexes from existing distribution files.")
    parser.add_argument(
        "--remove-period",
        action="append",
        default=[],
        help="Explicitly authorize removing a previously published period (<period_id> or <university>/<faculty>:<period_id>).",
    )
    parser.add_argument("--report", type=Path, help="Write a JSON publication report to this path.")
    parser.add_argument("--summary-file", type=Path, help="Append a Markdown summary table to this path.")
    args = parser.parse_args(argv)

    if args.file and args.fixture:
        parser.error("Choose either a positional file or --fixture.")
    if args.all and args.faculties:
        parser.error("Choose either --all or --faculty, not both.")

    if args.all:
        selected_names = list(faculties_by_name)
    elif args.faculties:
        selected_names = list(dict.fromkeys(args.faculties))
    else:
        selected_names = [str(ESI_KEY)]

    if args.check_esi and selected_names != [str(ESI_KEY)]:
        parser.error("--check-esi is only available for uclm/esi.")

    fixture = args.fixture or args.file
    remote = args.sync or args.check_esi or args.force
    if args.index_only and (fixture or remote):
        parser.error("--index-only uses the existing distribution files.")
    if fixture and remote:
        parser.error("Local fixtures are processed offline. Omit --sync, --check-esi, and --force.")
    if fixture and len(selected_names) != 1:
        parser.error("Local fixtures require selecting a single faculty.")

    selected = [faculties_by_name[name] for name in selected_names]
    selected_keys = [key for key, _, _ in selected]
    all_specs = tuple(spec for spec, _ in registered.values())
    dist = args.dist_dir.resolve()
    mode = "index-only" if args.index_only else ("sync" if remote else "local")

    try:
        remove_periods = _parse_remove_periods(args.remove_period, selected_keys)
        validate_specs(all_specs, dist)
        if args.index_only:
            result = publish_batch(
                specs=tuple(spec for _, spec, _ in selected),
                dist=dist,
                today=today,
                schedules_by_faculty={},
                remove_periods=remove_periods,
                index_only=True,
                isolate_failures=True,
            )
        else:
            fixture_map = {selected_keys[0]: fixture} if fixture is not None else None
            remote_map = {}
            for key, _, adapter in selected:
                use_remote = remote
                # Preserve the original command's first-run behavior when ESI's cache is missing.
                if fixture is None and isinstance(adapter, EsiAdapter) and not adapter.cached_input.is_file():
                    use_remote = True
                remote_map[key] = use_remote
            result = run_batch_pipeline(
                entries=tuple((spec, adapter) for _, spec, adapter in selected),
                dist=dist,
                today=today,
                all_specs=all_specs,
                fixture_by_faculty=fixture_map,
                remote_by_faculty=remote_map,
                remote=remote,
                force=args.force,
                strict=args.strict,
                remove_periods=remove_periods,
            )
    except (OSError, ValueError, KeyError, TypeError) as error:
        target_label = ", ".join(selected_names)
        print(f"[Publication error] {target_label}: {error}", file=sys.stderr)
        return EXIT_FATAL

    _write_report(args.report, result, mode=mode)
    _append_summary(args.summary_file, result, mode=mode)

    for outcome in result.outcomes:
        if outcome.status == "failed":
            stage = (outcome.stage or "Error").capitalize()
            print(f"[{stage} error] {outcome.key}: {outcome.diagnostic}", file=sys.stderr)
        elif outcome.status == "published":
            changed_items = sorted(set(outcome.written_files) | set(outcome.removed_files))
            if not changed_items and outcome.changed_periods:
                changed_items = list(outcome.changed_periods)
            print(f"[Published] {outcome.key}: {', '.join(changed_items)}")
        else:
            print(f"[Unchanged] {outcome.key}: public files are current.")

    if result.written_files:
        shared_indexes = sorted(path for path in result.written_files if path in {"schedules_catalog.json", "schedules_index.json"})
        if shared_indexes:
            print(f"[Indexes] Updated: {', '.join(shared_indexes)}")

    if result.all_failed:
        return EXIT_FATAL
    if result.has_failures:
        return EXIT_PARTIAL
    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
