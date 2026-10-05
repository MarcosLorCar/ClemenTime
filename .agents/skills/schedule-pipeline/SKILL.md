---
name: schedule-pipeline
description: >-
  Detailed guide for the Python schedule processing pipeline in ClemenTime,
  including ESI TV API synchronization, parsing, deduplication, mappings, and wire format.
  Use when modifying or debugging scripts in schedules/, process_schedules.py, or schedule import JSONs.
---

# Schedule Data Pipeline

ClemenTime uses a Python schedule pipeline to pull and process official ESI timetables into flat JSON files consumed by the Android app.

```
ESI TV hall API -> check_esi_update.py (HTTP HEAD ETag/Last-Modified) -> schedules/input/schedules.json
                -> parse_schedule.py (mappings.json + deduplication)  -> schedules/dist/{1C,2C}.json
                -> generate_index.py                                  -> schedules/dist/schedules_index.json
                -> app downloads over HTTPS
```

## Commands (requires Python >= 3.11, uv)

```bash
uv sync
python schedules/script/check_esi_update.py          # check ESI TV endpoint via HTTP HEAD, download if changed
python process_schedules.py                          # process schedules/input/schedules.json -> dist/{1C,2C}.json + index
python process_schedules.py --check-esi              # check live ESI endpoint first, then process if updated
python process_schedules.py --check-esi --force      # force redownload from ESI API and regenerate dist
python schedules/script/generate_index.py            # regenerate schedules_index.json only
```

`process_schedules.py` orchestrates everything: it runs `check_esi_update.py` (when `--check-esi`), then `parse_schedule.py`, then `generate_index.py`.

`--strict` passes `--non-interactive` down to `parse_schedule.py`. Unknown subject/professor/classroom names are **not** prompted for and **not** passed through: the script collects them, prints them, and exits non-zero *before writing anything*, so raw codes can never reach `dist/`. Fix by adding the name to `mappings.json`, or drop `--strict` to be prompted for each.

## Wire Format

- `dist/1C.json` and `dist/2C.json` are `List<JsonFlatSlot>`.
- One JSON object per class session, snake_case keys (`hora_inicio`, `es_laboratorio`, `grupo_practicas`).
- `codigo` holds the short code (e.g., `FunProg1`) and `asignatura` the full display name (`Fundamentos de Programacion I`). **Never swap those two.**
- `JsonScheduleParser.parseJson` accepts **both** shapes and normalises to internal `ScheduleJsonSchema`:
  - Leading `[` is parsed as flat array.
  - Anything else as legacy nested schema (`years[]` -> `groups[]` -> `matters[]`).
  - Flat is canonical for pipeline; nested branch exists for user-supplied custom files.

## Domain Quirks & Metadata

- State files: `schedules/input/esi_meta.json` (`url`, `etag`, `last_modified`, `sha256`).
- Mapping file: `schedules/script/mappings.json` maps abbreviated codes and raw names to canonical display names in three categories: `matters`, `professors`, `classrooms`.
- See `docs/esi_api_schedule_pipeline.md` for full breakdown of domain quirks:
  - 87 faculty events collapse into `GENERAL`
  - 53 shared group slots retention
  - `:50` rounding rule
  - TV screen grid layout strip
  - `-L` stripping
