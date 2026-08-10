# AGENTS.md

This file provides guidance to AI coding agents (such as Antigravity / agy CLI, Claude Code, and other agentic assistants) when working with code in this repository.

## Project

ClemenTime is an offline-first Android timetable app for ESI students (Jetpack Compose, Material 3). The repo contains two distinct halves:

- `app/` — the Android application (Kotlin, single Gradle module `:app`).
- `schedules/` + `process_schedules.py` — a Python pipeline that scrapes the ESI web page, turns university schedule PDFs into JSON via Gemini, and publishes the files the app downloads at runtime.

## Commands

Gradle (PowerShell is the primary shell here; use `.\gradlew` — `./gradlew` works from the Bash tool):

```powershell
.\gradlew test              # all unit tests (what CI runs)
.\gradlew assembleDebug     # debug APK
.\gradlew lint              # Android Lint
```

Single test class / method:

```powershell
.\gradlew testDebugUnitTest --tests "com.marcoslorcar.clementime.utils.ConflictSolverTest"
.\gradlew testDebugUnitTest --tests "*.ConflictSolverTest.someTestMethod"
```

Python schedule pipeline (`uv`, requires Python ≥3.14 and system `poppler` for `pdf2image`):

```powershell
uv sync
python schedules/script/check_esi_update.py          # scrape ESI page, download changed PDFs
python process_schedules.py [file.pdf] [--strict]    # PDF -> schedules/dist/{1C,2C}.json + index
python process_schedules.py --check-esi              # run the ESI check first, then process
python schedules/script/generate_index.py            # regenerate schedules_index.json only
```

`process_schedules.py` orchestrates everything: it runs `parse_schedule.py` per PDF, then `generate_index.py`, then `check_pdf_update.py`. `parse_schedule.py` needs `GEMINI_API_KEY` (`.env` or env var); `GEMINI_API_KEY_ALT` is used as failover when the primary key hits `RESOURCE_EXHAUSTED`/429. `GEMINI_MODEL` overrides the default model.

`--strict` passes `--non-interactive` down to `parse_schedule.py`. Unknown subject/professor/classroom names are then **not** prompted for and **not** passed through: the script collects them, prints them, and exits non-zero *before writing anything*, so raw codes can never reach `dist/`. Fix by adding the name to `mappings.json`, or drop `--strict` to be prompted for each.

## Build configuration facts

- **Versioning is derived from git.** `versionCode = 1000 + git rev-list --count HEAD`; `versionName = git describe --tags --always --dirty` (minus a leading `v`). Override with `-PversionName=... -PversionCode=...`. Don't add a hardcoded version.
- Debug builds use `applicationIdSuffix = ".debug"` so debug and release can coexist on a device.
- Release signing reads `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` from the environment and **silently falls back to the debug keystore** when they're absent — a locally built "release" APK is debug-signed.
- Gradle configuration cache is enabled (`org.gradle.configuration-cache=true`), which is why versioning uses `providers.exec` rather than direct execution.
- Lint has `WebpUnsupported`, `UnusedResources`, and `VectorRaster` disabled.

## Architecture

Stack: Compose-only UI, Hilt (KSP) for DI, Room for persistence, DataStore Preferences for settings, Retrofit + OkHttp + kotlinx.serialization for the remote schedule index, WorkManager for background sync, Glance for the home-screen widget. Navigation is type-safe: `@Serializable` route classes in `ui/navigation/Routes.kt` (all `@Keep`, since minification is on in release), driven from `MainActivity` inside a `NavigationSuiteScaffold` for adaptive phone/tablet/foldable layouts.

### Data model

Two Room entities: `Subject` (1) → `ClassSlot` (N, cascade delete), exposed via the `SubjectWithSlots` relation and `ScheduleDao` `Flow`s. Fields that carry real behaviour:

- `Subject.selectedLabGroup` — pins one lab variant; a pinned subject is excluded from optimizer search.
- `Subject.isDummy` — placeholder subject, skipped by the conflict solver.
- `Subject.semester` — the whole UI is filtered by the current semester (`SettingsRepository.currentSemesterKey`, with auto-switch by date plus a manual override flag).
- `ClassSlot.entryType` — `THEORY` or `LAB`; theory is always fixed, labs are the degrees of freedom.

**Room migrations are explicit.** DB version 2, `exportSchema = false`, no destructive fallback. `MIGRATION_1_2` is declared in `AppDatabase` *and* registered in `di/DatabaseModule`. A schema change requires touching both places or the app crashes on upgrade.

`ScheduleDao.upsertSubjectWithSlots` runs `@Update` over **every** column and deletes all existing slots before reinserting. Any `Subject` field you leave at its default when rebuilding a row silently overwrites what the user had (notes, attached files, duration), and passing an empty slot list empties the subject.

### Schedule data pipeline (crosses the repo boundary)

```
ESI web page -> check_esi_update.py (scrape + HEAD/ETag check) -> schedules/pdf/*.pdf
             -> parse_schedule.py (Gemini vision + mappings.json) -> schedules/dist/{1C,2C}.json
             -> generate_index.py -> schedules/dist/schedules_index.json
             -> app downloads over HTTPS
```

State lives in committed files, not workflow state: `schedules/input/esi_meta.json` (per-semester URL/ETag/Last-Modified/sha256) and `schedules/input/pdf_meta.json`. `schedules/script/mappings.json` maps the PDF's abbreviated codes to display names in three categories — `matters`, `professors`, `classrooms`. Gemini responses are cached in `schedules/script/.cache/`, keyed by **page-image content hash**, so a changed PDF can never hit a stale entry.

The app fetches `schedules_index.json` from `SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL` (a `buildConfigField`), user-overridable in settings. `ImportRepository` rewrites `github.com` → `raw.githubusercontent.com` and appends `schedules_index.json` when the configured URL is a directory. Retrofit uses `@Url` for full URLs, so the `baseUrl` in `NetworkModule` is only a placeholder.

**Wire format is a flat array.** `dist/1C.json` and `dist/2C.json` are `List<JsonFlatSlot>` — one object per class session, snake_case keys (`hora_inicio`, `es_laboratorio`, `grupo_practicas`), with `codigo` holding the short code (`FunProg1`) and `asignatura` the full display name (`Fundamentos de Programacion I`). Don't swap those two.

`JsonScheduleParser.parseJson` accepts **both** shapes and normalises to the nested `ScheduleJsonSchema` used internally: a leading `[` is parsed as the flat array above, anything else as the legacy nested schema (root / `years[]` / `years[].groups[]`, each level using `@SerialName("matters")`, lab variants as `Map<groupName, List<JsonTimeSlot>>`). The pipeline only ever emits flat; the nested branch exists for user-supplied custom files.

`ImportSourceType` still declares `BUNDLED` alongside `REMOTE` and `CUSTOM`, but no `assets/schedules/` asset ships, so that branch is unreachable dead code. Don't build on it.

### Background schedule sync

`worker/ScheduleUpdateWorker` (a `@HiltWorker`) periodically re-checks the published schedule; `utils/ScheduleDiffChecker` computes per-subject slot diffs; `ScheduleDiffBottomSheet` (reached through the `MoreRoute(showDiff = true)` route) presents them for apply/dismiss.

- Detection short-circuits on the `hash` field in `schedules_index.json` before downloading the full JSON. **Two separate per-semester hashes live in DataStore and mean different things:** the *last known* hash is the version the user accepted or ignored (it drives diff detection, and the diff sheet re-runs the sync to populate itself, so advancing it early leaves the sheet empty); the *last notified* hash exists only to stop the same notification re-firing every interval. Don't collapse them.
- `performSync` is a plain suspend function on the companion. The "Check for updates now" button calls it **directly**, bypassing WorkManager — so manual testing passing tells you nothing about whether the background path works.
- **`@HiltWorker` requires `HiltWorkerFactory`.** WorkManager's default factory cannot construct assisted-injected workers. `ClemenTimeApplication` implements `Configuration.Provider` and the manifest removes `WorkManagerInitializer` from `androidx.startup.InitializationProvider` — remove either and every scheduled run fails at instantiation with no visible symptom. This shipped broken once; don't undo it.
- `schedulePeriodicWork` (`CANCEL_AND_REENQUEUE`) is for a user-chosen interval change. App start uses `ensurePeriodicWorkScheduled` (`KEEP`) — restarting the period on every launch would let a frequently-opened app never reach the interval. `SettingsRepository.DEFAULT_AUTO_UPDATE_INTERVAL_HOURS` is `0`, i.e. background sync is **off by default** — the user opts in from onboarding or Settings. Since the default applies whenever the DataStore key is absent, users who never touched the setting fall through to it.
- **`migrateLegacyAutoUpdateDefault` is temporary — delete it eventually.** The default used to be 6h, so upgrading users who never picked an interval would have silently lost background sync. The migration writes 6h back for installs that completed onboarding with no interval stored (a fresh install hasn't finished onboarding, so it keeps the new off-by-default). It runs from `MainActivity.onCreate` before the interval is read, and is self-limiting: it writes the key it tests for, so it can only fire once per install and can't override a deliberate "Off". Once enough releases have passed that essentially every active install has launched at least once on ≥ the version that introduced it, remove the function, its `LEGACY_AUTO_UPDATE_INTERVAL_HOURS` constant and the `MainActivity` call. Anyone who somehow upgrades later just gets sync off, which is only the current default.
- Applying a diff goes through `ImportRepository.applySlotDiffs`, which passes the existing `Subject` row through untouched, replaces only slots, and skips any subject whose slots didn't match (an empty match would otherwise wipe it). `importSubjects` (manual import) rebuilds the row instead and must keep carrying `notes` / `attachedFiles` / `defaultDurationMinutes` across — see the `upsertSubjectWithSlots` warning above.

### Conflict resolution

`utils/ConflictSolver` is the app's one piece of real algorithmic logic: given `List<SubjectWithSlots>`, it enumerates lab-variant combinations (skipping dummy and pinned subjects), and ranks the resulting `ScheduleSolution`s by overlap count, free days, and compactness. It's pure Kotlin with no Android dependencies — keep it that way so `ConflictSolverTest` stays a plain JVM test.

### Widget updates are pushed, not reactive

The Glance widget does **not** observe the database. Anything that mutates schedule data must call `ScheduleWidgetUtils.updateWidget(context)`, which delays 500 ms (to let the Room transaction settle) before `ScheduleWidget().updateAll(context)`. ViewModels that write to the DAO already do this; new mutation paths must too.

### Testability conventions (don't "clean these up")

Unit tests are plain JUnit 4 + `kotlinx-coroutines-test` with **hand-written fake DAOs** (e.g. `FakeScheduleDaoForRepositoryTest` implementing `ScheduleDao`) — there is no Robolectric and no mocking library. To make that possible:

- `SettingsRepository` is `open`, takes a **nullable** `@ApplicationContext Context?`, and wraps every DataStore read in try/catch returning a default flow.
- ViewModels take `@ApplicationContext private val context: Context? = null` and null-check before touching Android APIs.

Making these non-nullable or final will break the test suite. Adding a `ScheduleDao` method means updating every fake implementation in `app/src/test`.

Nothing in the suite exercises WorkManager, so worker wiring regressions are invisible to `.\gradlew test`. Verify those on a device.

## CI

All workflows trigger on **`master`**, the default branch. `ci.yml` and `update-schedules-index.yml` were once pointed at `main` and silently never ran — check the branch name before trusting that a workflow is live.

- `ci.yml` — `test` + `assembleDebug` on pushes/PRs to `master`.
- `sync-esi-schedules.yml` — every 6h, on `workflow_dispatch`, and on pushes to `master` touching `schedules/pdf/**`, `schedules/script/**`, `process_schedules.py`, or the workflow itself. Runs `check_esi_update.py`, then `process_schedules.py --strict` when an update is detected, and commits `schedules/` back with `[skip ci]`. Needs the `SCHEDULE_SOURCE_URL`, `GEMINI_API_KEY`, `GEMINI_API_KEY_ALT`, and `GEMINI_MODEL` secrets. Push trigger is deliberately `master`-only: the job runs a paid Gemini parse and auto-commits, so a wildcard branch filter burns quota and pushes commits onto feature branches. Use `workflow_dispatch` to test from a branch.
- Any step in that workflow carrying a custom `if:` must include `success()` — supplying an `if` replaces the implicit `success()` check, so a step can otherwise run after an earlier one failed.
- `update-schedules-index.yml` — regenerates `schedules_index.json` when `schedules/dist/**` changes. Redundant for pipeline commits (`process_schedules.py` already regenerates it, and `[skip ci]` suppresses this workflow); it only really fires for hand-committed dist files.
- `release.yml` — builds and publishes on `v*` tags or manual dispatch with a version input.

Pushing changes under `.github/workflows/` requires a token with `workflow` scope (`gh auth refresh -h github.com -s workflow`).
