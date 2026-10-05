---
name: widget-and-workers
description: >-
  Detailed guide for Glance widget mutation push semantics, WorkManager configuration, and HiltWorker constraints in ClemenTime.
  Use when modifying home screen widgets, background workers, or WorkManager schedules.
---

# Widget & Background Workers

## Glance Widget Updates: Push, Not Reactive

- The Glance widget does **not** observe Room or Flow directly.
- Anything mutating schedule data MUST call `ScheduleWidgetUtils.updateWidget(context)`.
- It delays 500 ms (to allow the Room transaction to settle) before triggering `ScheduleWidget().updateAll(context)`.
- ViewModels that write to the DAO already do this; any new mutation paths must follow this pattern.

## Background Schedule Sync & WorkManager

`worker/ScheduleUpdateWorker` (a `@HiltWorker`) periodically checks the published schedule index:
- Uses `ScheduleDiffChecker` to compute slot diffs.
- `ScheduleDiffBottomSheet` presents diffs to the user.
- Short-circuits on `hash` in `schedules_index.json` before downloading full JSON.

### Critical Hilt Worker Constraints

- **`@HiltWorker` requires `HiltWorkerFactory`**: WorkManager's default factory cannot construct assisted-injected workers.
- `ClemenTimeApplication` implements `Configuration.Provider` and `AndroidManifest.xml` removes `WorkManagerInitializer` from `androidx.startup.InitializationProvider`.
- **Do not undo either configuration**: removing either causes background runs to fail silently at worker instantiation.
- `schedulePeriodicWork` (`CANCEL_AND_REENQUEUE`) is for interval changes. App start uses `ensurePeriodicWorkScheduled` (`KEEP`).
- Default auto-update interval is `0` (off by default); user opts in from onboarding or settings.
