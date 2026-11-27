#!/usr/bin/env python3
"""Run a registered schedule adapter or rebuild its indexes."""

import argparse
import sys
from datetime import date
from pathlib import Path

from schedules.script.adapters.esi import ESI_KEY, EsiAdapter
from schedules.script.contract import ParseFailure, SourceFailure
from schedules.script.pipeline import run_pipeline, validate_specs
from schedules.script.publication import publish
from schedules.script.registry import registered_faculties


def main() -> int:
    root = Path(__file__).resolve().parent
    try:
        from dotenv import load_dotenv
    except ImportError:
        pass
    else:
        load_dotenv(root / ".env")

    today = date.today()
    faculties = {str(key): entry for key, entry in registered_faculties(root / "schedules", today).items()}
    parser = argparse.ArgumentParser(description="Process schedules for a registered faculty.")
    parser.add_argument("--faculty", choices=faculties, default=str(ESI_KEY), help="Faculty key (legacy default: %(default)s).")
    parser.add_argument("file", nargs="?", type=Path, help="Local source file (positional alias for --fixture).")
    parser.add_argument("--fixture", type=Path, help="Reprocess a local source file offline.")
    parser.add_argument("--dist-dir", type=Path, default=root / "schedules" / "dist", help="Directory for public output.")
    parser.add_argument("--strict", action="store_true", help="Fail on unresolved mappings without prompting.")
    parser.add_argument("--sync", action="store_true", help="Check the remote source before processing.")
    parser.add_argument("--check-esi", action="store_true", help="Legacy alias for --faculty uclm/esi --sync.")
    parser.add_argument("--force", action="store_true", help="Download and process the source even when headers match.")
    parser.add_argument("--index-only", action="store_true", help="Refresh indexes from existing distribution files.")
    args = parser.parse_args()

    if args.file and args.fixture:
        parser.error("Choose either a positional file or --fixture.")
    if args.check_esi and args.faculty != str(ESI_KEY):
        parser.error("--check-esi is only available for uclm/esi.")
    fixture = args.fixture or args.file
    remote = args.sync or args.check_esi or args.force
    if args.index_only and (fixture or remote):
        parser.error("--index-only uses the existing distribution files.")
    if fixture and remote:
        parser.error("Local fixtures are processed offline. Omit --sync, --check-esi, and --force.")

    spec, adapter = faculties[args.faculty]
    dist = args.dist_dir.resolve()
    try:
        validate_specs((entry[0] for entry in faculties.values()), dist)
        if args.index_only:
            result = publish(spec, dist, today, {})
        else:
            # Preserve the original command's first-run behavior when its cache is missing.
            if fixture is None and isinstance(adapter, EsiAdapter) and not adapter.cached_input.is_file():
                remote = True
            result = run_pipeline(
                spec, adapter, dist, today, fixture=fixture, remote=remote, force=args.force, strict=args.strict
            )
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"[Publication error] {spec.key}: {error}", file=sys.stderr)
        return 1

    if isinstance(result, (SourceFailure, ParseFailure)):
        stage = "Source" if isinstance(result, SourceFailure) else "Parse"
        print(f"[{stage} error] {spec.key}: {result.diagnostic}", file=sys.stderr)
        return 1
    if result:
        print(f"[Published] {spec.key}: {', '.join(sorted(result))}")
    else:
        print(f"[Unchanged] {spec.key}: public files are current.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
