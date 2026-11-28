# Schedule Processing Scripts

The schedule pipeline acquires and normalizes data through registered faculty
adapters, stages results per faculty, and publishes flat Android schedule JSON,
a combined v2 catalog (`schedules_catalog.json`), and ESI's legacy index
(`schedules_index.json`). Each adapter defines its metadata, periods, and public
source URL declaratively in `adapters/<faculty>/config.json`. The production
registry currently contains ESI in Ciudad Real (`uclm/esi`).

Run these commands from the repository root with Python 3.11 or newer. The
pipeline uses only the Python standard library (no third-party dependencies or
`.env` loader required); `uv run python ...` also works if you prefer `uv`.

```bash
# Sync all registered faculties in strict mode and write a JSON report + Markdown summary
python process_schedules.py --all --sync --strict --report report.json --summary-file summary.md

# Sync a single faculty and force a re-download
python process_schedules.py --faculty uclm/esi --sync --strict --force

# Reprocess a local fixture offline and write results to a separate directory
python process_schedules.py --faculty uclm/esi --strict --fixture schedules/input/schedules.json --dist-dir scratch-dist

# Validate all registered faculties offline using committed caches (same check as CI)
python process_schedules.py --all --strict --dist-dir scratch-dist

# Rebuild combined catalog and ESI legacy index from existing published term files
python process_schedules.py --all --index-only --report index-report.json

# Preview selective FCM topic payloads without sending
python schedules/script/notify_fcm.py --report report.json --dry-run
python schedules/script/notify_fcm.py --faculty uclm/esi --dry-run

# Run offline characterization, multi-faculty, and FCM tests
python -m unittest discover -s schedules/script -p "test_*.py"
```

Fixtures use the selected adapter's configuration and mappings. The ESI adapter
keeps its declarative `config.json` and `mappings.json` in `adapters/esi/` and
its cache in `schedules/input/`. The legacy CLI still supports running
`process_schedules.py` for the cached ESI input, passing a positional local JSON
file, and using `--check-esi` for remote sync.

See the [modular pipeline contract](../../docs/modular_schedule_pipeline.md),
[pipeline diagrams](../../docs/pipeline_diagrams.md), and the
[ESI operating guide](../../docs/esi_api_schedule_pipeline.md).
