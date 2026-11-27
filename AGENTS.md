# AGENTS.md

ClemenTime is an offline-first Android timetable app for university students (Jetpack Compose, Material 3), supporting a multi-faculty searchable catalog, dynamic FCM push topic binding, and lab/subgroup conflict optimization.
The repo contains:
- `app/` — Android application (Kotlin, single Gradle module `:app`).
- `schedules/` + `process_schedules.py` — Python pipeline generating normalized flat JSON schedules (`dist/1C.json`, `dist/2C.json`, and dynamic catalog indexes).

Specialized subsystem documentation is available via Antigravity workspace skills in `.agents/skills/`:
- `schedule-pipeline`: Python pipeline, ESI TV hall API, mappings, flat wire format, strict mode.
- `room-and-data`: Room migrations (`MIGRATION_1_2`), `ScheduleDao.upsertSubjectWithSlots` caveats, DataStore hashes, and multi-faculty legacy migration fallbacks (`LEGACY_FALLBACK_UNIVERSITY_ID`, `LEGACY_FALLBACK_FACULTY_ID`).
- `widget-and-workers`: Glance widget push updates, FCM push/WorkManager fallback, `HiltWorkerFactory` constraints.

---

## Token & Context Efficiency Directives

1. **Never Dump Schedule Data**:
   - `schedules/input/schedules.json` (~9,300 lines) and `schedules/dist/{1C,2C}.json` (~4,000 lines) must NEVER be read in full.
   - Use targeted CLI queries (`jq`, `rg`/`grep`, `head -n 30`, or small Python one-liners) to inspect specific keys.
2. **Targeted Test Execution**:
   - Run tests targeting the specific class/method being modified:
     `./gradlew testDebugUnitTest --tests "com.marcoslorcar.clementime.utils.ConflictSolverTest"`
   - Pipe or filter noisy outputs where possible.

---

## Build & Test Commands

Gradle (`./gradlew` on Linux/Bash, `.\gradlew` on PowerShell):

```bash
./gradlew testDebugUnitTest --tests "com.marcoslorcar.clementime.utils.ConflictSolverTest"  # single test
./gradlew test                                                                            # all unit tests (CI)
./gradlew assembleDebug                                                                   # debug APK
./gradlew lint                                                                            # Android Lint
```

Python schedule pipeline (`uv`, requires Python >= 3.11):

```bash
uv sync
python process_schedules.py --check-esi              # check live ESI endpoint, process if updated
python process_schedules.py                          # regenerate dist/{1C,2C}.json and index
```

---

## Architecture & Core Rules

- **Stack**: Compose-only, Hilt (KSP), Room (v2, explicit migrations), DataStore Preferences, Retrofit + OkHttp + kotlinx.serialization, WorkManager (`HiltWorkerFactory`), Glance home-screen widget. Type-safe navigation in `ui/navigation/Routes.kt` (marked `@Keep`).
- **Conflict resolution**: `utils/ConflictSolver` is pure Kotlin with zero Android dependencies. Keep it that way so `ConflictSolverTest` remains a fast plain JVM test.
- **Versioning**: Derived from git (`versionName = git describe --tags --always --dirty` or `-PversionName`; `versionCode` uses SemVer math `major*1_000_000 + minor*10_000 + patch*100 + (99 for stable, 1..98 for pre-release)` on exact SemVer tags, falling back to `1000 + git rev-list --count HEAD` for dev builds). Never add a hardcoded version.
- **Release signing**: Reads `KEYSTORE_*` from environment; falls back silently to debug keystore locally.
- **Multi-Faculty Architecture & Legacy Fallbacks**:
  - `selectedUniversityId` and `selectedFacultyId` default to `""` (empty string / unlinked) for fresh installs.
  - `LEGACY_FALLBACK_UNIVERSITY_ID` (`"uclm"`) and `LEGACY_FALLBACK_FACULTY_ID` (`"esi"`) are used **exclusively** for migrating legacy users (detected via presence of pre-v1.5 `last_known_schedule_hash_1/2`) or when generating a fallback synthetic catalog for `schedules_index.json`.
  - **Do NOT re-introduce hardcoded `"esi"` or `"uclm"` defaults for new installs or general features.**
  - **Deprecation Roadmap**: Once legacy adoption of pre-v1.5 builds approaches zero in future major releases (e.g., v2.0), `resolveSelectedUniversityId`/`resolveSelectedFacultyId` fallback branches and `LEGACY_FALLBACK_*` constants can be safely removed. See `docs/multi_faculty_and_fcm_architecture.md`.

### Code Hygiene & Anti-Slop Guardrails

- **No silent bandaids**: Do not add `delay(...)` to wait for DB/UI settlement, empty `catch (_: Throwable)` / `catch (_: Exception)` blocks, or `!!` assertions. Inside `suspend` functions, always rethrow `CancellationException`.
- **Delete superseded code in the same PR**: When generalizing or replacing a feature (e.g., polling → FCM, 2 semesters → N periods), remove the old overloads, unused ViewModel/UI callbacks, dead `strings.xml` entries, and `@Suppress("unused")` annotations instead of leaving parallel wrappers behind.
- **Single source of truth for domain helpers**: Do not duplicate day/time parsing, group normalization, or lab detection across parsers, repositories, and diff engines, and never regex-parse formatted UI strings back into domain objects.
- **No faculty-specific rules in Android code**: School-specific ingestion quirks (e.g., ESI `:50` rounding or `Universidad de Mayores` filtering) belong in the Python pipeline, not in app parsers.
- **Release & localization hygiene**: Gate debug interceptors/tools (`HttpLoggingInterceptor`, debug menu actions) behind `BuildConfig.DEBUG`. All user-facing strings (including ViewModel errors and diff labels) must go through `strings.xml` (EN & ES).
- **Keep skills and docs in sync**: Any change to sync/FCM, Room/DataStore keys, or pipeline outputs must update the relevant `.agents/skills/*/SKILL.md` and `docs/` files in the same PR.

### Testability Conventions (Do Not "Clean These Up")

Unit tests use JUnit 4 + `kotlinx-coroutines-test` with **hand-written fake DAOs** (e.g. `FakeScheduleDaoForRepositoryTest`). There is no Robolectric and no mocking framework.
- `SettingsRepository` is `open`, takes `@ApplicationContext Context?` (nullable), and wraps DataStore reads in try/catch returning default flows.
- ViewModels take `@ApplicationContext private val context: Context? = null` and null-check before touching Android APIs.
- Making these non-nullable or final breaks the test suite. Adding a `ScheduleDao` method requires updating every fake in `app/src/test`.

### CI & Workflows

All workflows trigger on **`master`** (the default branch). Check branch names before assuming a workflow is live.
- In `sync-esi-schedules.yml`, steps meant to run only on success must include `success()`.

---

## Git & Contribution Notes

- Leave edits uncommitted for review unless explicitly asked to commit, push, or open a PR. When asked to commit, never commit directly to `master` or append `Co-Authored-By:` trailers.
