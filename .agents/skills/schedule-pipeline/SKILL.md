---
name: schedule-pipeline
description: >-
  Guide for the ClemenTime multi-faculty schedule pipeline, including
  per-adapter declarative config.json, the ESI TV API adapter, mapping and
  normalization rules, multi-faculty publication, selective FCM, and the flat
  wire format. Use when changing schedules/, process_schedules.py, or import
  JSONs.
---

# Schedule Data Pipeline

```
process_schedules.py → registry.py → adapters/<faculty>/ (config.json, HTTP, cache, normalization)
                                   → pipeline.py (registry validation, staging, flat serialization)
                                   → publication.py (staged batch publish, v2 catalog, ESI legacy index)
                                   → notify_fcm.py (selective post-push FCM or manual test/retry)
```

The [modular pipeline contract](../../../docs/modular_schedule_pipeline.md) and
[pipeline diagrams](../../../docs/pipeline_diagrams.md) describe the adapter
boundary, declarative `config.json` loading (`schedules/script/config.py`),
multi-faculty batch publication, CI workflows, and client verification rules.
The production registry currently contains `uclm/esi` only (future faculties
such as ESII Albacete are tracked separately in GitHub issues). Keep faculty
identifiers, public source URLs (`adapters/<faculty>/config.json`), source
format, data quirks, HTTP state, and mapping rules strictly inside each faculty
adapter—do not add faculty URLs to global `.env` or GitHub Secrets.

## Commands

Run from the root with Python 3.11 or newer (stdlib-only; no third-party dependencies required).

```bash
python process_schedules.py --all --sync --strict --report report.json --summary-file summary.md
python process_schedules.py --faculty uclm/esi --sync --strict
python process_schedules.py --faculty uclm/esi --sync --strict --force
python process_schedules.py --faculty uclm/esi --strict --fixture path/to/local.json --dist-dir scratch-dist
python process_schedules.py --all --strict --dist-dir scratch-dist
python process_schedules.py --all --index-only --report index-report.json
python process_schedules.py --faculty uclm/esi --index-only --remove-period 2C
python schedules/script/notify_fcm.py --report report.json --dry-run
python schedules/script/notify_fcm.py --faculty uclm/esi --dry-run
python -m unittest discover -s schedules/script -p "test_*.py"
```

- A successful HTTP check with unchanged content reprocesses the cached input so
  code, configuration, and mapping edits take effect.
- Multi-faculty runs stage each faculty independently. Valid faculties publish
  even if another faculty fails; failed faculties keep their last valid files
  and committed `schedules/input/` caches untouched (raw caches persist only
  after normalization and publication succeed), and initial failures never add
  empty catalog entries.
- Exit codes: `0` (all succeeded), `2` (partial success: valid faculties
  published, at least one failed), `1` (fatal/config error or all failed).
- ESI keeps `schedules/input/schedules.json`, `schedules/input/esi_meta.json`,
  and root paths `1C.json`/`2C.json` (`legacy_index=True`).
- New faculties keep their own `schedules/script/adapters/<faculty>/config.json`,
  isolated cache `schedules/input/<faculty>/`, and nested dist paths via
  `contract.default_period_path` (`<uni>/<fac>/<period>.json`).
- Strict mode reports unresolved mappings or structural errors before changing
  public files or committed input caches.

## Wire Format & Publication Rules

- `schedules/dist/{1C,2C}.json` (ESI) and `schedules/dist/<uni>/<fac>/<period>.json`
  (new faculties) contain flat `List<JsonFlatSlot>` arrays with keys such as
  `hora_inicio`, `es_laboratorio`, and `grupo_practicas`.
- `codigo` is the short identifier (for example `FunProg1`) and `asignatura` is
  the display name. Keep both through normalization and serialization, and
  ensure codes do not collide across distinct subjects.
- `JsonScheduleParser` accepts flat arrays and legacy nested custom imports
  (`years[] → groups[] → matters[]`). The pipeline publishes flat arrays.
- A published period ID must resolve to its declared index (1–8) in Android's
  `TermCodeParser`. Missing source data retains the existing file. Removing a
  previously published period requires explicit `removed_periods` on
  `FacultySpec` or `--remove-period`; an empty period requires `allow_empty=True`.
- Hashes cover the exact published LF bytes (enforced by `json_bytes()` and
  `.gitattributes`); unchanged hashes preserve `updatedTime`. `--index-only`
  preserves unregistered/foreign catalog entries.
- FCM dispatches only after a confirmed `git push` and only for faculties in
  `changed_schedule_faculties` (raw/meta changes do not trigger FCM). Failed
  FCM delivery never reverts pushed data; retry via `retry_fcm=true` in workflow
  dispatch or `notify_fcm.py --faculty <uni/fac>`.

## ESI Rules

`schedules/script/adapters/esi/config.json` stores ESI's faculty metadata,
periods, and default TV hall URL, and `schedules/script/adapters/esi/mappings.json`
stores subject and professor mappings (`matters` and `professors`; cleaned
classroom names pass through). The adapter applies
subject and professor mappings, collapses common `GENERAL` events, preserves
sessions for each attending group, rounds end times at `:50`, and handles lab
suffixes and TV layout fields. See the
[ESI operating guide](../../../docs/esi_api_schedule_pipeline.md) for details.
