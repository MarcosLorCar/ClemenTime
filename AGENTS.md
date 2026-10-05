# AGENTS.md

ClemenTime is an offline-first Android timetable app for ESI students (Jetpack Compose, Material 3).
The repo contains:
- `app/` — Android application (Kotlin, single Gradle module `:app`).
- `schedules/` + `process_schedules.py` — Python pipeline generating normalized flat JSON schedules (`dist/1C.json`, `dist/2C.json`).

Specialized subsystem documentation is available via Antigravity workspace skills in `.agents/skills/`:
- `schedule-pipeline`: Python pipeline, ESI TV hall API, mappings, flat wire format, strict mode.
- `room-and-data`: Room migrations (`MIGRATION_1_2`), `ScheduleDao.upsertSubjectWithSlots` caveats, DataStore hashes.
- `widget-and-workers`: Glance widget push updates (500 ms settlement delay), `HiltWorkerFactory` constraints.

---

## Token & Context Efficiency Directives

To keep conversation context fast and prevent token inflation (especially with large-context models like Gemini 3.8):
1. **Targeted File Reading**:
   - For files >100 lines, ALWAYS specify `StartLine` and `EndLine` with `view_file`.
   - Never view full files when inspecting a single function, interface, or layout block.
   - Do NOT re-view files immediately after editing if `replace_file_content` confirmed the replacement.
2. **Never Dump Schedule Data**:
   - `schedules/input/schedules.json` (~9,300 lines) and `schedules/dist/{1C,2C}.json` (~4,000 lines) must NEVER be opened with full `view_file`.
   - Use targeted CLI queries (`jq`, `grep`, `head -n 30`, or small Python one-liners) to inspect specific keys.
3. **Targeted Test Execution**:
   - Run tests targeting the specific class/method being modified:
     `./gradlew testDebugUnitTest --tests "com.marcoslorcar.clementime.utils.ConflictSolverTest"`
   - Pipe or filter noisy outputs where possible.
4. **Delegate Deep Research to Subagents**:
   - For broad codebase exploration or multi-file research, invoke the `research` subagent via `invoke_subagent`.
   - Subagents discard intermediate file dumps and return only concise summaries, keeping the main context lean.

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
- **Versioning**: Derived from git (`versionCode = 1000 + git rev-list --count HEAD`; `versionName = git describe --tags --always --dirty`). Never add a hardcoded version.
- **Release signing**: Reads `KEYSTORE_*` from environment; falls back silently to debug keystore locally.

### Testability Conventions (Do Not "Clean These Up")

Unit tests use JUnit 4 + `kotlinx-coroutines-test` with **hand-written fake DAOs** (e.g. `FakeScheduleDaoForRepositoryTest`). There is no Robolectric and no mocking framework.
- `SettingsRepository` is `open`, takes `@ApplicationContext Context?` (nullable), and wraps DataStore reads in try/catch returning default flows.
- ViewModels take `@ApplicationContext private val context: Context? = null` and null-check before touching Android APIs.
- Making these non-nullable or final breaks the test suite. Adding a `ScheduleDao` method requires updating every fake in `app/src/test`.

### CI & Workflows

All workflows trigger on **`master`** (the default branch). Check branch names before assuming a workflow is live.
- Pushing workflow changes under `.github/workflows/` requires a token with `workflow` scope.
- In `sync-esi-schedules.yml`, steps meant to run only on success must include `success()`.

---

## Git Workflow & Contribution Directives

To maintain clean repository history and streamline PR reviews:

1. **Proactive Branching for New Tasks**:
   - When starting work on a new feature, bug fix, or refactor (i.e. not an ongoing task on a WIP branch), check the active branch with `git branch`.
   - If on `master`, **create and switch to a dedicated topic branch before writing code**:
     - `fix/<kebab-case-desc>` for bug fixes (e.g. `fix/widget-tomorrow-date-labeling`)
     - `feat/<kebab-case-desc>` for features (e.g. `feat/restore-esi-api-pipeline`)
     - `refactor/<kebab-case-desc>` for refactoring (e.g. `refactor/cleanup-obsolete-code`)
     - `chore/<kebab-case-desc>` for maintenance and documentation
   - Never commit feature or fix code directly to `master`.

2. **Commit Conventions & Strict Authorship**:
   - Use Conventional Commits (`fix: ...`, `feat: ...`, `refactor: ...`, `chore: ...`).
   - Include a bulleted breakdown of changes in the commit body.
   - **No Co-Authors**: All commits must be authored exclusively by Marcos Loro (`marcoslorcar03@gmail.com`). Do **not** append `Co-authored-by:` metadata trailers.

3. **Pull Request Protocol**:
   - Push to `origin <branch-name>` and open a PR against `master` using `gh pr create`.
   - Format the PR description with clear `## Summary` and `## Changes` bullet points matching the commit.
