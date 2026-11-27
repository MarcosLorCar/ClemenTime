# Modular Schedule Pipeline

The contract is documented and offline tests characterize ESI
output. The production pipeline still runs the existing ESI scripts. The
adapter registry, publication core, and ESII integration are proposed.

The [ESI guide](esi_api_schedule_pipeline.md) describes current operations.
Tasks, dependencies, acceptance criteria, and progress are tracked in issues
[#47](https://github.com/MarcosLorCar/ClemenTime/issues/47),
[#48](https://github.com/MarcosLorCar/ClemenTime/issues/48),
[#49](https://github.com/MarcosLorCar/ClemenTime/issues/49), and
[#50](https://github.com/MarcosLorCar/ClemenTime/issues/50).

## Goal and Scope

Add faculties through independent adapters that implement a common contract.
ESI (Ciudad Real) keeps its API. ESII (Albacete), also part of UCLM, will have
an HTML adapter. Configuration and publication are scoped to a faculty, and
each adapter may consult multiple pages or sources.

A universal scraper, server, external plugins, public format changes, and an
Android migration are outside the proposal's scope. If the ESII source requires
additional client capabilities, track that decision separately.

## Current Consumers

| Consumer | Current input and behavior |
| --- | --- |
| `process_schedules.py` CLI | Accepts an optional local JSON file, `--strict`, and `--check-esi`. When the HTTP check is enabled, it passes through `--force`. It runs the parser and then generates the indexes. After a successful HTTP check, the CLI processes the local file even if the headers stayed the same. |
| `sync-esi-schedules.yml` | Consumes `has_update` from the HTTP checker. Runs the CLI in strict mode on an update, a push, or a manual run. Commits and pushes changes under `schedules/`. It uses `success()` to gate FCM after the push or in manual FCM test mode. |
| `update-schedules-index.yml` | Runs `generate_index.py` directly when a change under `dist/` reaches `master`, excluding the two indexes. Publishes the indexes if they differ and sends FCM after the push. Both workflows share the `schedules-master-write` concurrency group. |
| Catalog v2 | `RemoteScheduleFetcher` reads `schedules_catalog.json`. The hierarchy is `universities → faculties → terms`. Each term contains `id`, `name`, `description`, `path`, `hash`, and `updatedTime`. Android resolves `path` from the catalog base URL. ESI publishes the relative paths `1C.json` and `2C.json`. |
| Legacy index | `schedules_index.json` is a flat ESI array with the same term data, using `title` in place of `name`. Android can wrap it in a synthetic UCLM/ESI catalog as a fallback. The generator reads each term's previous update date from this index. |
| Android parser | `JsonScheduleParser` reads arrays of `JsonFlatSlot` and groups slots by year, group, and subject. It treats `evento` sessions as common events and also accepts nested JSON for imports. |
| FCM payload | `notify_fcm.py` reads the catalog and builds one message per faculty with string values for `university`, `faculty`, `updated_at`, and `hash_{termId}`. ESI uses topic `sched_uclm_esi` and keys `hash_1C` and `hash_2C`. Android uses `TermCodeParser` to resolve the key suffix to a period. |

## Current ESI Behavior Covered by Tests

The [fixtures](../schedules/script/fixtures/esi) and
[pipeline tests](../schedules/script/test_schedule_pipeline.py) pin the flat
JSON published for ESI. Coverage includes `codigo` (short identifier),
`asignatura` (display name), `GENERAL` events, shared sessions, deduplication,
lab suffixes, rounding end times at `:50`, and filtering Universidad de Mayores.
Both indexes record the SHA-256 of each file's bytes and retain the period's
`updatedTime` while the hash matches its previous legacy index entry. The
[HTTP tests](../schedules/script/test_check_esi_update.py) mock HEAD and GET.

- Strict mode aborts on unresolved subject or professor mappings. The parser
  publishes cleaned classroom names without consulting `mappings.classrooms`.
  Aborting preserves both schedules, both indexes, and the mappings. An earlier
  HTTP check may already have updated the source JSON and HTTP state.
- A response with new headers and identical bytes refreshes HTTP state,
  rewrites the source file, and emits `has_update=false`. The checker accepts
  a nonempty JSON object, while the parser requires an array. In the default
  tolerant mode, HTTP failures and unchanged sources both yield
  `has_update=false`.
- The parser writes periods with sessions and keeps the prior file if a period
  is missing from the source. It writes directly to the destination after
  checking unresolved mappings.
- ESI's ordering is stable for the same input. Slots tied on group, day, start,
  and code retain their source order. Text-mode output uses native line endings,
  so the same schedule can have different hashes on Windows (CRLF) and on the
  Linux publishing runner (LF).
- Any commit under `schedules/` can trigger the sync notification. That includes
  HTTP-state-only changes if the run reaches the publishing step. The sender
  iterates over every faculty in the supplied catalog.

## Proposed Responsibilities

### Registry and Adapters

An explicit registry maps each `(university_id, faculty_id)` pair to an adapter
and its metadata, including names, periods, output paths, and source configuration.
`uclm/esi` and `uclm/esii` have separate entries. The core has no default faculty.
ESI retains its identifiers and topic.

Each adapter handles acquisition, change detection, and interpretation for its
source. It owns its mappings, fixtures, and HTTP state. The ESI adapter owns
the `:50` rounding, lab suffixes, `GENERAL` events, filters, and names. Extract
shared functions where sources genuinely use the same behavior, with test
coverage.

Sources choose the change detection their endpoints support. The HTML adapter
can use conditional downloads or hashes. Changes to code, configuration, or
mappings must allow regeneration even when the remote source has stayed the
same. The pipeline must support offline reprocessing from fixtures.

### Contract and Core

The adapter acquires source data and normalizes sessions. The core receives
sessions keyed by period, checks their periods against configuration, and
serializes valid results. These signatures define the internal data for the
adapter extraction.

```text
FacultyKey(university_id: str, faculty_id: str)
FacultySpec(key: FacultyKey, university_name: str, faculty_name: str,
            periods: tuple[PeriodSpec, ...], fcm_topic_prefix: str | None,
            subgroups_label: str | None, period_type: str | None, period_count: int | None)
PeriodSpec(id: str, index: int, name: str, description: str | None, path: str)
Session(subject_code: str, subject_name: str, group: str, day: int,
        start: time, end: time, kind: str, room: str, professor: str,
        is_lab: bool, subgroup: str)
FacultySchedule(key: FacultyKey, sessions_by_period: Mapping[str, tuple[Session, ...]])

acquire() -> Unchanged | Candidate[source_payload] | SourceFailure
normalize(source_payload) -> FacultySchedule | ParseFailure
```

- Each university and faculty pair has a stable, nonempty registry key. Period
  IDs and indices are unique within a faculty. The core checks that
  `sessions_by_period` uses declared period IDs and that every published path
  is unique and stays within `dist/`.
- The index is an integer from 1 through 8. `TermCodeParser` must resolve the
  published ID to the same index from the catalog, `cuatrimestre`, and
  `hash_{termId}`. ESI retains `1C` and `2C`. Support is limited to IDs verified
  in Android. If the faculty declares `period_type` or `period_count`, the
  configuration from Android's `PeriodConfig.inferFromCatalog` must cover all
  published indices. The current ESI catalog omits both fields.
- `Session` stores the subject code and name separately and keeps one record per
  attending group. `day` runs from Monday (1) to Sunday (7). `start` and `end`
  are local times at minute precision. `kind`, `is_lab`, and `subgroup` retain
  the source semantics separately. A common ESI event uses `group = "GENERAL"`
  and `kind = "evento"`.
- The serializer converts each session to the current flat object. It uses the
  period key for `cuatrimestre` and writes `grupo`, `dia` (Spanish day name),
  `hora_inicio` and `hora_fin` (HH:mm), `codigo`, `asignatura`, `tipo`, `aula`,
  `profesor`, `es_laboratorio`, and `grupo_practicas`. The existing indexes
  carry university, faculty, and period metadata. The hash covers the final bytes.
- `Candidate` carries data to validate and can come from local reprocessing.
  `SourceFailure` and `ParseFailure` preserve the diagnostic and prevent
  publication of that result. `Unchanged` retains the last publication. An empty
  period is valid only when the source configuration and integrity checks
  explicitly allow it.

### Publication and Notifications

Each faculty prepares and validates its results in staging. A single publisher
assembles valid results and keeps the last successful publication for any failed
faculty. A new faculty first appears in the catalog after a successful run.
Period removals require explicit intent.

The catalog and schedules are published together in Git. Clients can still
read different revisions across HTTPS URLs. Issue #49 covers hash verification
and client recovery when versions temporarily differ. Determine any Android
changes required before enabling another faculty.

Send FCM after a confirmed push and only to faculties whose published schedules
changed. Metadata-only downloads do not trigger notifications. A failed FCM
send remains visible and can be retried after publication. Manual test
notifications remain an explicit mode.

Both workflows will share the catalog and publication rules, retain writer
mutual exclusion, and use `success()` where appropriate. A partial run's status
must identify each failed faculty alongside successful publications.

## Compatibility Requirements

- ESI keeps publishing `schedules/dist/1C.json` and `schedules/dist/2C.json`.
  New faculties use separate paths, such as `schedules/dist/uclm/esii/1C.json`
  if that period exists.
- The catalog remains v2 with `universities → faculties → terms` and its
  existing fields, including `path`, `hash`, and `updatedTime`.
- `schedules_index.json` remains the legacy view for ESI.
- Session files remain flat arrays and `codigo` and `asignatura` retain their
  meanings. Hashes and dates stay stable when content stays the same. The
  initial comparison must catch serialization differences that could cause
  unnecessary notifications.
- ESI retains `sched_uclm_esi` and the FCM keys `hash_{termId}`. New Android
  installations start without a selected university or faculty.
- Update CLI and script consumers when replacing their entry points. Remove
  superseded implementations in the same PR.

## Open Questions About the ESII Source

The source is reported to be HTML, and the maintainer has permission to extract
it. Issue #50 covers identifying the URL and inspecting samples for page count,
table structure, periods, groups, and possible degree-specific pages. It also
covers checking for JavaScript or authentication, selecting parsing tools, and
identifying schedule rules before ESII goes live.

## Maintaining This Document

Each implementation PR updates the affected sections and relevant operational
documentation or skill. During the migration, identify the parts that have
shipped in their respective sections.

When #50 closes, this document should describe the delivered architecture and
link to the actual modules and commands. Remove the original baseline, resolved
questions, and proposal language. Issues and Git retain the history and pending
work. If ESII is postponed or canceled, complete this documentation cleanup when
the core migration ends.
