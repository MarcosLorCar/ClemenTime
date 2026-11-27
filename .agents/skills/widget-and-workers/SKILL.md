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
- ViewModels that write to the DAO already do this; any new mutation paths must follow this pattern.
- `ScheduleWidgetUtils.updateWidget` runs immediately (no `delay(...)` settlement workarounds — Room `suspend` transactions are already committed when they return) and handles exceptions inside its coroutine while rethrowing `CancellationException`.

## Background Schedule Sync, FCM & WorkManager

Schedule update delivery is **event-driven via FCM push**, with periodic WorkManager as a fallback:
- `worker/FcmTopicManager` manages topic subscriptions (`sched_{university}_{faculty}`, or `sched_{fcm_topic_prefix}`) whenever `SyncMode.ONLINE` and a faculty is linked. On devices without Google Play Services (GMS), or when `subscribeToTopic` fails/cancels, it falls back to periodic `ScheduleUpdateWorker` (every 6h); periodic work is only canceled after `subscribeToTopic` succeeds. In `SyncMode.OFFLINE` or unlinked state, it unsubscribes and cancels periodic work.
- `worker/ClemenTimeFirebaseMessagingService` receives high-priority data-only pushes containing per-term hashes (`hash_{term}`), compares against `last_known_schedule_hash_$semester` for active semesters, and enqueues an expedited one-time `ScheduleUpdateWorker`.
- `worker/ScheduleUpdateWorker` (a `@HiltWorker`) runs `performSync`:
  - Resolves the linked `(universityId, facultyId)` in `schedules_catalog.json` (falling back to `schedules_index.json` only for legacy `uclm`/`esi`).
  - Short-circuits per semester on `last_known_schedule_hash_$semester` before downloading full JSON.
  - Uses `ScheduleDiffChecker` + `last_remote_subject_hashes_$semester` to detect upstream changes while ignoring deliberate manual user edits, then surfaces diffs in `ScheduleDiffBottomSheet`.

### Critical Hilt Worker Constraints

- **`@HiltWorker` requires `HiltWorkerFactory`**: WorkManager's default factory cannot construct assisted-injected workers.
- `ClemenTimeApplication` implements `Configuration.Provider` and `AndroidManifest.xml` removes `WorkManagerInitializer` from `androidx.startup.InitializationProvider`.
- **Do not undo either configuration**: removing either causes background runs to fail silently at worker instantiation.
- Periodic fallback uses `ensurePeriodicWorkScheduled` (`ExistingPeriodicWorkPolicy.KEEP`) so app launches do not reset the timer.
