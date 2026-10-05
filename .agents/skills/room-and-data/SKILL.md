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
- `Subject.semester`: The entire UI is filtered by the current semester (`SettingsRepository.currentSemesterKey`).
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

Two separate per-semester hashes live in DataStore and have distinct meanings:
1. `last_known_hash`: The version the user accepted or ignored (drives diff detection; advancing it early leaves the diff sheet empty).
2. `last_notified_hash`: Exists only to prevent duplicate notifications from recurring background worker intervals.
**Do not collapse or interchange them.**
