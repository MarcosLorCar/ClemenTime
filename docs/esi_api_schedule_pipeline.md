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
              schedules/dist/schedules_index.json
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

### 2.7. HTTP `HEAD` Change Detection & Metadata
- `check_esi_update.py` checks `ETag` and `Last-Modified` headers against `schedules/input/esi_meta.json`. It only downloads and triggers processing if the headers changed on the server (or if `--force` is supplied).

---

## 3. Pipeline Commands

```bash
# Run full sync check and process if updated
python3 process_schedules.py --check-esi

# Force a redownload and regenerate distributions
python3 process_schedules.py --check-esi --force

# Run strictly (fails non-zero if unknown mappings exist)
python3 process_schedules.py --strict

# Regenerate schedules_index.json only
python3 schedules/script/generate_index.py
```
