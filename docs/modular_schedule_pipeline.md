# Modular Schedule Pipeline

Schedule ingestion, validation, catalog assembly, and selective FCM dispatch run
through the [adapter registry](../schedules/script/registry.py), the
[declarative config loader](../schedules/script/config.py), the
[shared pipeline](../schedules/script/pipeline.py), and the
[batch publisher](../schedules/script/publication.py). See
[pipeline diagrams](pipeline_diagrams.md) for visual architecture, adapter
lifecycle, and CI delivery flowcharts.

The production registry currently contains one active faculty:

| Faculty key | Faculty | Config & source | Local cache | Distribution paths | FCM topic |
| --- | --- | --- | --- | --- | --- |
| `uclm/esi` | Escuela Superior de Informática (Ciudad Real) | `adapters/esi/config.json` (ESI TV hall JSON API) | `schedules/input/schedules.json` + `esi_meta.json` | `1C.json`, `2C.json` (+ legacy `schedules_index.json`) | `sched_uclm_esi` |

## Architecture & Components

| Component | Responsibility |
| --- | --- |
| [CLI](../process_schedules.py) | Select one or more registered faculties (`--faculty` repeatable, or `--all`), sync remote sources, reprocess local fixtures, or rebuild indexes (`--index-only`). Writes optional JSON reports (`--report`) and Markdown CI summaries (`--summary-file`). Preserves legacy no-argument, positional file, and `--check-esi` entry points for ESI. |
| [Declarative Config](../schedules/script/config.py) | Load each adapter's self-contained `config.json` (faculty identity, public `source_url`, period metadata, and default nested paths `<university_id>/<faculty_id>/<period_id>.json`) into `FacultyConfig`, keeping public source URLs out of global `.env` / GitHub Secrets. |
| [Registry](../schedules/script/registry.py) | Explicit faculty registration binding each `FacultyKey` to its `FacultySpec` and `FacultyAdapter`. ESI keeps `1C.json` and `2C.json` with `legacy_index=True`; new faculties default to nested paths via `default_period_path` / `config.json`. |
| [ESI adapter](../schedules/script/adapters/esi) | Owns `config.json`, HEAD/ETag cache, `mappings.json` resolution, `GENERAL` event collapsing, lab and group rules, `:50` time rounding, and deduplication. |
| [Contract](../schedules/script/contract.py) | Defines `FacultyKey`, `PeriodSpec`, `FacultySpec` (including explicit `removed_periods`), normalized `Session`, adapter outcomes (`Unchanged`, `Candidate`, `SourceFailure`, `ParseFailure`), and structured batch results (`FacultyOutcome`, `BatchResult`). |
| [Pipeline](../schedules/script/pipeline.py) | Validates the entire registry before publication (key/path/topic collisions, consistent university names, single legacy index owner, symlink and path-escape checks, Android `TermCodeParser` compatibility, and period config coverage). Stages every faculty independently, sorts sessions deterministically, and serializes the flat wire format. |
| [Publication](../schedules/script/publication.py) | Assembles a coherent multi-faculty batch (`publish_batch`): combines registered updates into `schedules_catalog.json` v2 while preserving failed faculties and unregistered/foreign catalog entries intact, emits the ESI-only legacy `schedules_index.json`, retains `updatedTime` for unchanged hashes, enforces explicit period removal, and replaces/removes files via a staging directory with rollback on `OSError`. |
| [Selective FCM](../schedules/script/notify_fcm.py) | Filters data-only FCM v1 payloads by `--report` (`changed_schedule_faculties`) or `--faculty`, preserving `hash_{termId}` keys and per-faculty topics (`sched_<university>_<faculty>`). Retries transient HTTP 429/5xx and network errors with bounded backoff, attempts all targeted faculties independently, and emits manual retry commands when delivery fails. |

## Acquisition, Partial Failures, and Explicit Period Removal

- A sync that receives `Unchanged` reruns normalization on the cached input so
  mapping, configuration, and code edits take effect against unchanged remote
  sources.
- Each faculty in a multi-faculty batch (`--all`) is acquired, normalized, and
  serialized independently before touching `schedules/dist/`.
  - If one faculty fails while another succeeds, valid faculties are published
    and updated in `schedules_catalog.json` while failed faculties keep their
    previous period files, catalog entries, and (for ESI) `schedules_index.json`
    untouched.
  - If a newly registered faculty fails on its initial run (no prior files on
    disk), it is never added empty to `schedules_catalog.json`.
  - `process_schedules.py` exits `0` when all selected faculties succeed, `2` on
    partial success (valid faculties published, at least one failed), and `1` on
    fatal errors or when every selected faculty fails (leaving `dist/` untouched).
- Removing a previously published period requires an explicit decision via
  `FacultySpec.removed_periods` or `--remove-period`. Missing source data keeps
  the prior file intact, an unexpectedly missing previously published file in
  `dist/` fails validation instead of silently dropping the period, and an empty
  period is rejected unless `PeriodSpec.allow_empty=True` is configured.

## CI Workflows and Post-Publication FCM Retry Policy

Both [schedule sync](../.github/workflows/sync-esi-schedules.yml) and the
[manual dist edit safety net](../.github/workflows/update-schedules-index.yml)
share the `schedules-master-write` concurrency group (`cancel-in-progress: false`)
and the same batch publication + report mechanism:

- **Scheduled / push / manual sync**: runs `process_schedules.py --all --sync --strict`
  (or `--faculty <key>` when supplied in `workflow_dispatch`), writes a JSON
  report and `$GITHUB_STEP_SUMMARY` table, commits and pushes valid changes under
  `schedules/`, dispatches FCM **only after `git push` succeeds** and **only for
  faculties in `changed_schedule_faculties`**, and finally fails the workflow if
  any faculty failed (`rc == 2`) so partial failures remain visible in both CI
  status and the job summary.
- **No false FCM triggers**: raw/metadata updates under `schedules/input/` or
  non-hash catalog metadata edits without changed published period hashes leave
  `changed_schedule_faculties` empty and do not trigger FCM.
- **Manual `dist` edit safety net**: runs `process_schedules.py --all --index-only`,
  refreshing hashes only for registered faculties that have files on disk,
  preserving unregistered/foreign catalog entries verbatim, keeping
  `schedules_index.json` ESI-only, and notifying only faculties whose hashes
  changed.
- **Explicit manual test and post-publication FCM retry**:
  - `workflow_dispatch` with `test_fcm=true` sends an explicit test notification
    (optionally scoped with `faculty=<uni/fac>`) even when no schedule changed.
  - If `git push` succeeds and FCM dispatch fails even after `notify_fcm.py`'s
    transient retries, **published schedule data on `master` remains live and is
    never reverted**. Operators retry notification delivery without re-syncing
    by dispatching the sync workflow with `retry_fcm=true` (and optional
    `faculty=<uni/fac>`) or running
    `python3 schedules/script/notify_fcm.py --faculty <uni/fac>`.

## Non-Atomic Cross-URL HTTPS Reads and Android Client Mitigation

A single coherent Git commit to `master` keeps `schedules_catalog.json` and the
per-term JSON files consistent in the repository, **but does not guarantee atomic
reads across separate HTTPS requests** served through GitHub Raw / CDN caches. A
client can briefly read a newly updated `schedules_catalog.json` alongside an
older cached copy of a term JSON file (or vice versa).

Android mitigates this without assuming cross-URL atomicity:

- `RemoteScheduleFetcher.resolveScheduleUrl` resolves both legacy flat paths
  (`1C.json`) and nested faculty paths (`<uni>/<fac>/<period>.json`)
  consistently across `ImportViewModel`, `ImportRepository`, and
  `ScheduleUpdateWorker`.
- `RemoteScheduleFetcher.downloadVerifiedRawSchedule` appends `?v=<hash>` when
  the catalog provides a SHA-256 hash and verifies the exact downloaded bytes'
  SHA-256 against the catalog hash.
- On a hash mismatch, Android retries once with a fresh cache-busting query
  parameter (`cb=<timestamp>`) and, if configured, the fallback host.
- If the mismatch persists, Android rejects the mismatched bytes with
  `ScheduleHashMismatchException`:
  - `ScheduleUpdateWorker` does **not** advance `lastKnownScheduleHash` or
    `lastScheduleSyncTimestamp`, and returns `ListenableWorker.Result.retry()` so
    WorkManager retries later once CDN caches converge.
  - `ImportRepository.fetchRemoteScheduleSchema` refuses to cache mismatched
    bytes or silently substitute a stale cached revision whose hash differs from
    the catalog entry.

## Wire Format and Existing Consumers

| Consumer | Compatibility |
| --- | --- |
| Flat schedule JSON | `Session` stores `subject_code` (`codigo`) and `subject_name` (`asignatura`) separately, weekday `1..7` (`dia` in Spanish), and minute-precision local times (`hora_inicio`, `hora_fin`). Other fields remain `grupo`, `cuatrimestre`, `tipo`, `aula`, `profesor`, `es_laboratorio`, and `grupo_practicas`. |
| Android catalog | Schema v2 remains `universities → faculties → terms`. ESI keeps `1C.json` and `2C.json`; new faculties use nested paths relative to `schedules/dist/`. |
| Legacy index | `schedules_index.json` stays a flat ESI-only array using `title` where the catalog uses `name`. |
| FCM | Topics remain `sched_<university>_<faculty>` (or `sched_<fcm_topic_prefix>`), preserving `sched_uclm_esi` and `hash_{termId}` payload keys. |

## Adding Another Faculty

1. Create `schedules/script/adapters/<faculty>/` with a declarative `config.json` (loaded via `load_faculty_config`, using nested paths by default and `legacy_index: false`) and a `FacultyAdapter` implementing `acquire`, `read_local`, and `normalize`.
2. Register the adapter in [`schedules/script/registry.py`](../schedules/script/registry.py) with its own `schedules/input/<faculty>/` cache directory.
3. Add offline fixtures under `schedules/script/fixtures/<faculty>/` and unit tests covering parsing, structural failure preservation, HTTP acquisition, and catalog/FCM isolation.

Next planned faculty onboarding (ESII Albacete theory + lab sources) is tracked
in [Issue #57](https://github.com/MarcosLorCar/ClemenTime/issues/57).

See the [ESI operating guide](esi_api_schedule_pipeline.md) for ESI-specific
normalization rules and commands, and [pipeline diagrams](pipeline_diagrams.md)
for the visual architecture walkthrough.
