---
name: room-and-data
description: >-
  Detailed guide for Room database entities, migrations, DAO semantics, and DataStore synchronization in ClemenTime.
  Use when modifying Room entities (Subject, ClassSlot), writing database migrations, changing ScheduleDao, or dealing with DataStore hashes.
---

# Room Database & Data Layer Architecture

## Data Model

Two Room entities:
- `Subject` (1) -> `ClassSlot` (N, cascade delete), exposed via `SubjectWithSlots` relation and `ScheduleDao` `Flow`s.

Key fields:
- `Subject.selectedLabGroup`: Pins one lab variant. Pinned subjects are excluded from conflict solver search.
- `Subject.isDummy`: Placeholder subject, skipped by the conflict solver.
- `Subject.semester`: Academic period index `1..8` (configured via `PeriodConfig` in `data/AcademicPeriod.kt`; UI filters by `SettingsRepository.currentSemesterKey`).
- `ClassSlot.entryType`: `THEORY` or `LAB`. Theory is fixed; labs are the degrees of freedom.

## Room Migrations are Explicit

- Database version 2, `exportSchema = false`, no destructive fallback.
- `MIGRATION_1_2` is declared in `AppDatabase` **and** registered in `di/DatabaseModule`.
- A schema change requires updating **both places** or the app crashes on upgrade.

## DAO Upsert Semantics

- `ScheduleDao.upsertSubjectWithSlots` runs `@Update` over **every column** and deletes all existing slots before reinserting.
- Any `Subject` field left at default when rebuilding a row silently overwrites user data (`notes`, `attachedFiles`, `defaultDurationMinutes`).
- Passing an empty slot list empties the subject.
- Applying slot diffs must go through `ImportRepository.applySlotDiffs`, which passes the existing `Subject` row through untouched and updates only slots.

## DataStore Hashes

Three separate per-semester hash entries live in DataStore and have distinct meanings:
1. `last_known_schedule_hash_$semester`: The remote schedule file hash the user accepted or ignored (drives diff detection; advancing it early leaves the diff sheet empty).
2. `last_notified_schedule_hash_$semester`: Prevents duplicate notifications for the same remote version while the user has not yet accepted/ignored it.
3. `last_remote_subject_hashes_$semester`: JSON `Map<String, String>` of per-subject canonical upstream slot hashes (`ScheduleDiffChecker.computeRemoteSubjectHashes`) used to distinguish upstream schedule changes from deliberate manual user edits.
**Do not collapse or interchange them.**
