# ESI Schedule Processing Scripts (`schedules/script/`)

Automated tools to fetch timetable data from the official ESI TV hall endpoint (`https://esi.uclm.es/TV/hall/horarios.json`), apply name normalization via `mappings.json`, handle deduplication and faculty event collapse, publish semester distribution files (`dist/1C.json`, `dist/2C.json`, `dist/schedules_catalog.json`, and legacy `dist/schedules_index.json`), and dispatch FCM push notifications (`notify_fcm.py`).

---

## Pipeline Overview

```
https://esi.uclm.es/TV/hall/horarios.json
             │
   check_esi_update.py  (HTTP HEAD ETag / Last-Modified)
             │
schedules/input/schedules.json
             │
   parse_schedule.py    (mappings.json + deduplication + normalization)
             │
   schedules/dist/1C.json & 2C.json
             │
   generate_index.py    (deterministic metadata + SHA256 hashes)
             │
   schedules/dist/schedules_catalog.json (+ schedules_index.json)
             │ [CI on committed changes]
   notify_fcm.py        (FCM HTTP v1 data push to /topics/sched_uclm_esi)
```

---

## Dependencies & Setup

Uses `uv` for dependency management:

```bash
uv sync
```

---

## Usage Commands

```bash
# Check live ESI TV endpoint and process if updated
python process_schedules.py --check-esi

# Force a redownload and regenerate distributions
python process_schedules.py --check-esi --force

# Strict mode for CI (fails if unknown mappings exist)
python process_schedules.py --strict

# Regenerate schedules_catalog.json and schedules_index.json only
python schedules/script/generate_index.py

# Preview FCM topic payloads
python schedules/script/notify_fcm.py --dry-run
```

See [`docs/esi_api_schedule_pipeline.md`](../../docs/esi_api_schedule_pipeline.md) for full architectural documentation.
