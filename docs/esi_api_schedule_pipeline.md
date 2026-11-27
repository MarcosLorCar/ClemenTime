# ESI TV Hall Schedule Pipeline

This document explains the architecture, endpoint behavior, deduplication rules, and subtle domain quirks of the ESI timetable fetching and parsing pipeline.

---

## 1. Overview & Architecture

ClemenTime synchronizes timetable data directly from the official ESI hallway TV displays endpoint:

```
https://esi.uclm.es/TV/hall/horarios.json
```

```
ESI TV hall endpoint
    → ESI adapter checks HEAD and downloads or reads the local cache
    → ESI adapter normalizes and deduplicates sessions using faculty mappings
    → shared pipeline validates and serializes 1C.json and 2C.json
    → publisher prepares the catalog and legacy index with SHA-256 hashes
    → CI sends FCM to sched_uclm_esi after a public change or manual test
```

---

## 2. Subtle Domain Quirks & Deduplication Rules

### 2.1. Collapsing 87 Replicated Faculty-Wide Events (`Pruebas de Progreso` & `Conferencias`)
- **Origin**: The raw JSON API renders schedules on a per-group screen basis (`1A`, `1B`, `1C`, `1D`, `2A`, ..., `4TI`). Because the entire university reserves Monday/Friday mornings for Progress Tests and Wednesday midday for Conferences, the raw API contains **87 separate entries** repeating these events across all groups.
- **Handling**:
  1. The parser identifies `Pruebas de Progreso` and `Conferencias`.
  2. Overrides `grupo` to `"GENERAL"`, sets `tipo = "evento"`, clears `profesor`, and sets `es_laboratorio = False`.
  3. Retains full raw venue designations:
     - `Pruebas de Progreso` $\to$ `"Charles Babbage - 0.02+3"`
     - `Conferencias` $\to$ `"Alan Turing - Salón de Actos"`
  4. The deduplication key includes `grupo`: once all 87 occurrences share `grupo: "GENERAL"`, exact-slot set deduplication collapses them down to the **single canonical set of weekly event slots per semester**.

### 2.2. Preserving Multi-Group & Shared Academic Classes (53 Shared Sessions)
- **Origin**: Certain lectures and labs are shared across groups (e.g. `FunProg1-L` in room A1.2 on Monday 13:00–14:30 is attended by both `1B` and `1C`; optional subjects in `4OPT`; joint classes between `3IC` and `3TI`).
- **Handling**:
  - Because `grupo` is kept distinct in the deduplication key for academic classes, group-specific slots are **retained independently** for each group (`1B` and `1C`).
  - When a student in ClemenTime imports group `1B` or `1C`, `JsonScheduleParser` routes slots into group buckets (`years[yearName].groups[groupName]`). Retaining the group identifier ensures students of both groups receive their complete timetable.

### 2.3. Stripping TV Hall Screen Layout Artifacts (`celda_dividida` & `posicion_en_celda`)
- **Origin**: In the ESI TV display, if two sub-labs happen simultaneously, the TV splits that time cell into halves (`celda_dividida: true`, `posicion_en_celda: 0` or `1`).
- **Handling**: These are physical TV layout artifacts irrelevant to mobile timetables and are stripped during transformation.

### 2.4. Subject Suffixes & Lab Detection (`-L`, `(L)`, `-Lab`)
- **Origin**: In the API, practical classes are often labeled `Cálculo-L`, `TeCo-L`, `DyGRedes-L`, `API-L`.
- **Handling**:
  - `clean_subject_code` strips suffixes using `r'(-L|\(L\)|-Lab|\.L|\.Lab)$'`.
  - When a suffix or `LD` classroom is detected, `es_laboratorio` is set to `True` and `tipo` to `"laboratorio"`.
  - Stripping the suffix ensures the laboratory groups nest under the parent subject (`Cálculo`) in Room and the Lab Optimizer.

### 2.5. Time Padding and the ":50" Rounding Rule
- **Origin**: Single-digit hours like `"8:30"` lack padding. End times frequently state `"09:50"`, `"14:20"`, or `"17:50"` (representing the 10-minute passing buffer before the next block).
- **Handling**:
  - `parse_time(..., is_end_time=True)` checks if minutes are `50`. If so, it rounds up to the next full hour (`09:50` $\to$ `10:00`) so slots align cleanly with the app's timeline grid. Single digits are padded (`8:30` $\to$ `08:30`).

### 2.6. Senior University Program (`Universidad de Mayores`)
- Rows matching `univmayores` or `universidad de mayores` are ignored.
- The Python pipeline owns this filtering and the end-time rounding above. Android preserves subject names and end times from published schedules and JSON backups.

### 2.7. HTTP Change Detection and Cache

The ESI adapter compares HEAD's `ETag` and `Last-Modified` with
`schedules/input/esi_meta.json`. A header or URL change, missing
`schedules/input/schedules.json`, or `--force` triggers a download.
A metadata-only download refreshes the cache and HTTP state. The shared
pipeline also reprocesses cached input after an unchanged check, so changes to
mappings, configuration, or code can change the outputs on any sync run.

The sync workflow runs all registered faculties in strict mode, commits valid
changes under `schedules/`, and pushes to `master`. It sends selective FCM only
after a confirmed push for faculties whose published schedule hashes changed
(raw/meta updates under `schedules/input/` alone do not trigger FCM), or during
an explicit manual `workflow_dispatch` with `test_fcm=true` or `retry_fcm=true`.
If FCM fails after push, published files on `master` stay live and notifications
can be retried via `retry_fcm=true` without re-syncing.

### 2.8. Strict Parsing and Publication

Strict mode reports unresolved subject and professor mappings before changing
public files. The adapter reads mappings from
`schedules/script/adapters/esi/mappings.json` and passes cleaned classroom names
through. Without `--strict` it prompts for unresolved names.

A source or parse failure preserves the prior periods and indexes (exiting `1`
when all selected faculties fail, or `2` on partial failure in multi-faculty
runs so other valid faculties can still publish). A successful download can
update the raw input and HTTP metadata before normalization. ESI publishes
periods with sessions and retains the previous file for an omitted period;
removing a previously published period requires explicit confirmation via
`removed_periods` or `--remove-period`. Hashes cover the final file bytes, and
an unchanged hash retains its previous `updatedTime`.

## 3. Pipeline Commands

```bash
# Sync all registered faculties in strict mode and write a report
python3 process_schedules.py --all --sync --strict --report sync-report.json

# Sync ESI specifically, reprocessing its cache if unchanged
python3 process_schedules.py --faculty uclm/esi --sync --strict

# Force a redownload
python3 process_schedules.py --faculty uclm/esi --sync --strict --force

# Reprocess a local ESI JSON file offline
python3 process_schedules.py --faculty uclm/esi --strict --fixture path/to/local.json --dist-dir scratch-dist

# Refresh the combined catalog and ESI legacy index from existing term files
python3 process_schedules.py --all --index-only

# Preview selective FCM push payloads without sending
python3 schedules/script/notify_fcm.py --report sync-report.json --dry-run
python3 schedules/script/notify_fcm.py --faculty uclm/esi --dry-run

# Run offline characterization and multi-faculty tests from the repository root
python3 -m unittest discover -s schedules/script -p "test_*.py"
```

`--check-esi` remains available for remote sync. The no-argument command
processes ESI's cached input, and a positional JSON file selects a local input.
See the [modular pipeline contract](modular_schedule_pipeline.md) and
[pipeline diagrams](pipeline_diagrams.md) for the adapter boundary, declarative
per-adapter `config.json`, multi-faculty architecture, and public format.
