---
name: schedule-pipeline
description: >-
  Guide for the ClemenTime faculty schedule pipeline, including the ESI TV API
  adapter, mapping and normalization rules, publication, and the flat wire
  format. Use when changing schedules/, process_schedules.py, or import JSONs.
---

# Schedule Data Pipeline

```
process_schedules.py → registry.py → adapters/esi/ (HTTP, cache, mappings, normalization)
                                   → pipeline.py (validation, flat serialization)
                                   → publication.py (terms, catalog, legacy index)
                                   → notify_fcm.py (CI after a publication or manual test)
```

The [modular pipeline contract](../../../docs/modular_schedule_pipeline.md)
describes the adapter boundary and consumers. The production registry contains
`uclm/esi` only. Keep faculty identifiers, source format, data quirks, HTTP
state, and mapping rules in adapter code or registration.

## Commands

Run from the root with Python 3.11 or newer. Use `uv sync` for optional
environment-file support.

```bash
python process_schedules.py --faculty uclm/esi --sync --strict
python process_schedules.py --faculty uclm/esi --sync --strict --force
python process_schedules.py --faculty uclm/esi --strict --fixture path/to/local.json --dist-dir scratch-dist
python process_schedules.py --faculty uclm/esi --index-only
python schedules/script/notify_fcm.py --dry-run
python -m unittest discover -s schedules/script -p "test_*.py"
```

A successful HTTP check with unchanged content reprocesses the cached input so
code, configuration, and mapping edits take effect. ESI keeps its input
`schedules/input/schedules.json` and metadata `schedules/input/esi_meta.json`
(`url`, `etag`, `last_modified`, `sha256`). Strict mode reports unresolved
subject and professor mappings before changing public files. Source and parse
errors exit nonzero. A successful download can update the cache before parsing.
Classroom names pass through after ESI-specific cleanup.

## Wire Format

- `schedules/dist/{1C,2C}.json` contain flat `List<JsonFlatSlot>` arrays with
  keys such as `hora_inicio`, `es_laboratorio`, and `grupo_practicas`.
- `codigo` is the short identifier (for example `FunProg1`) and `asignatura`
  is the display name. Keep both through normalization and serialization.
- `JsonScheduleParser` accepts flat arrays and legacy nested custom imports
  (`years[] → groups[] → matters[]`). The pipeline publishes flat arrays.
- A published period ID must resolve to its declared index (1–8) in Android's
  `TermCodeParser`. A missing period retains its existing file. An empty
  period requires explicit configuration.
- Hash the final bytes. ESI's catalog and legacy index preserve
  `updatedTime` for unchanged hashes. Offline fixtures under
  `schedules/script/fixtures/esi/` pin output, including stable tie order.

## ESI Rules

`schedules/script/adapters/esi/mappings.json` stores mappings in
`matters`, `professors`, and `classrooms`. The adapter applies subject and
professor mappings, collapses common `GENERAL` events, preserves sessions for
each attending group, rounds end times at `:50`, and handles lab suffixes
and TV layout fields. See the
[ESI operating guide](../../../docs/esi_api_schedule_pipeline.md) for details.
