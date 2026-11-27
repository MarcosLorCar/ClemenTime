# Schedule Processing Scripts

The schedule pipeline acquires and normalizes data through a registered faculty
adapter, then validates and publishes flat Android schedule JSON and indexes.
The production registry contains ESI in Ciudad Real (`uclm/esi`).

Run these commands from the repository root with Python 3.11 or newer. Use
`uv sync` to install optional environment-file support.

```bash
# Check the ESI endpoint and process fresh or cached input in strict mode
python process_schedules.py --faculty uclm/esi --sync --strict

# Force a re-download
python process_schedules.py --faculty uclm/esi --sync --strict --force

# Reprocess a local fixture offline and write results to a separate directory
python process_schedules.py --faculty uclm/esi --strict --fixture schedules/input/schedules.json --dist-dir scratch-dist

# Rebuild catalog and legacy index from existing published term files
python process_schedules.py --faculty uclm/esi --index-only

# Preview FCM topic payloads without sending
python schedules/script/notify_fcm.py --dry-run

# Run offline characterization and adapter tests
python -m unittest discover -s schedules/script -p "test_*.py"
```

Fixtures use the selected adapter's mappings. The ESI adapter keeps its
production `mappings.json` in `adapters/esi/`. The legacy CLI still supports
running `process_schedules.py` for the cached ESI input, passing a positional
local JSON file, and using `--check-esi` for remote sync.

See the [ESI operating guide](../../docs/esi_api_schedule_pipeline.md) and the
[modular pipeline contract](../../docs/modular_schedule_pipeline.md).
