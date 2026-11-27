# Schedule Processing Scripts

The schedule pipeline acquires and normalizes data through registered faculty
adapters, stages results per faculty, and publishes flat Android schedule JSON,
a combined v2 catalog (`schedules_catalog.json`), and ESI's legacy index
(`schedules_index.json`). The production registry currently contains ESI in
Ciudad Real (`uclm/esi`).

Run these commands from the repository root with Python 3.11 or newer. Use
`uv sync` to install optional environment-file support.

```bash
# Sync all registered faculties in strict mode and write a JSON report + Markdown summary
python process_schedules.py --all --sync --strict --report report.json --summary-file summary.md

# Sync a single faculty and force a re-download
python process_schedules.py --faculty uclm/esi --sync --strict --force

# Reprocess a local fixture offline and write results to a separate directory
python process_schedules.py --faculty uclm/esi --strict --fixture schedules/input/schedules.json --dist-dir scratch-dist

# Rebuild combined catalog and ESI legacy index from existing published term files
python process_schedules.py --all --index-only --report index-report.json

# Preview selective FCM topic payloads without sending
python schedules/script/notify_fcm.py --report report.json --dry-run
python schedules/script/notify_fcm.py --faculty uclm/esi --dry-run

# Run offline characterization, multi-faculty, and FCM tests
python -m unittest discover -s schedules/script -p "test_*.py"
```

Fixtures use the selected adapter's mappings. The ESI adapter keeps its
production `mappings.json` in `adapters/esi/`. The legacy CLI still supports
running `process_schedules.py` for the cached ESI input, passing a positional
local JSON file, and using `--check-esi` for remote sync.

See the [ESI operating guide](../../docs/esi_api_schedule_pipeline.md) and the
[modular pipeline contract](../../docs/modular_schedule_pipeline.md).
