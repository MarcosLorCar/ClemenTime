# ESI TV Hall Schedule Pipeline

This document explains the architecture, endpoint behavior, deduplication rules, and subtle domain quirks of the ESI timetable fetching and parsing pipeline.

---

## 1. Overview & Architecture

ClemenTime synchronizes timetable data directly from the official ESI hallway TV displays endpoint:

```
https://esi.uclm.es/TV/hall/horarios.json
```

```
           https://esi.uclm.es/TV/hall/horarios.json
                             │
                  check_esi_update.py
             (HTTP HEAD ETag / Last-Modified)
                             │ [Update Detected or --force]
                             ▼
               schedules/input/schedules.json
                             │
                   parse_schedule.py
    ┌────────────────────────┴────────────────────────┐
    ▼                                                 ▼
1. Strip TV Layout Coordinates         5. Suffix & Lab Detection
   (celda_dividida, posicion_en_celda)    (-L, -Lab, (L) stripped, es_lab=true)
2. Filter Universidad de Mayores       6. Time Padding & Rounding
3. Collapse 87 Replicated Events          (8:30 -> 08:30, 09:50 -> 10:00)
   (Pruebas de Progreso -> GENERAL)    7. Deduplication Key Check
4. Preserve 53 Shared Group Slots         (grupo, sem, dia, hora_ini, etc.)
   (FunProg1-L kept for 1B & 1C)
    │                                                 │
    └────────────────────────┬────────────────────────┘
                             ▼
                 schedules/dist/1C.json & 2C.json
                             │
                     generate_index.py
            (Deterministic Content Metadata)
            (Per-Semester SHA256 Hash Calculation)
                             │
                             ▼
      schedules/dist/schedules_catalog.json (+ legacy schedules_index.json)
                             │ [CI on committed change]
                             ▼
                       notify_fcm.py
             (FCM data push to /topics/sched_uclm_esi)
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
  - `format_time(..., is_end_time=True)` checks if minutes are `50`. If so, it rounds up to the next full hour (`09:50` $\to$ `10:00`) so slots align cleanly with the app's timeline grid. Single digits are padded (`8:30` $\to$ `08:30`).

### 2.6. Senior University Program (`Universidad de Mayores`)
- Rows matching `univmayores` or `universidad de mayores` are ignored.
- The Python pipeline owns this filtering and the end-time rounding above. Android preserves subject names and end times from published schedules and JSON backups.

### 2.7. HTTP `HEAD` Change Detection & Metadata
- `check_esi_update.py` compares `ETag` and `Last-Modified` with `schedules/input/esi_meta.json`. It downloads on a header or URL change, if the local input is missing, or in force or mock mode. The SHA-256 of the downloaded bytes distinguishes new content from a metadata-only update. A download with unchanged bytes refreshes local state and emits `has_update=false` unless force or mock mode is active or the input was missing.
- The scheduled workflow uses `has_update` to decide whether to process. The standalone `process_schedules.py --check-esi` checks the endpoint and then processes the local file even when the checker reports unchanged content.

### 2.8. Strict Parsing & Publication
- In strict mode the parser reports unresolved subject and professor mappings before writing either period. The wrapper stops before regenerating the indexes. Classroom names pass through after cleanup. A prior HTTP check may already have written the source input and HTTP metadata.
- `generate_index.py` hashes each output file's bytes. The prior legacy index provides `updatedTime` when the hash still matches. The catalog carries the same date and hash.
- The [modular pipeline contract](modular_schedule_pipeline.md) inventories the consumers and records current compatibility limits.

---

## 3. Pipeline Commands

```bash
# Check the source and process the local input
python3 process_schedules.py --check-esi

# Force a redownload and regenerate distributions
python3 process_schedules.py --check-esi --force

# Run strictly (fails non-zero if unknown mappings exist)
python3 process_schedules.py --strict

# Regenerate schedules_catalog.json + schedules_index.json only
python3 schedules/script/generate_index.py

# Preview FCM push payloads without sending
python3 schedules/script/notify_fcm.py --dry-run

# Run offline characterization tests from the repository root
python3 -m unittest discover -s schedules/script -p "test_*.py"
```
