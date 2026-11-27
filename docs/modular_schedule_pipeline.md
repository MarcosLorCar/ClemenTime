# Modular Schedule Pipeline

ESI runs through the [adapter registry](../schedules/script/registry.py) and the
[shared pipeline](../schedules/script/pipeline.py). The registry contains one
production faculty, `uclm/esi`. The ESII adapter and multi-faculty production
rollout remain proposed.

## Implemented Pipeline

| Component | Responsibility |
| --- | --- |
| [CLI](../process_schedules.py) | Select a registered faculty, sync its source, reprocess a local input, or rebuild indexes from the distribution directory. The no-argument command and `--check-esi` preserve the legacy ESI entry points. |
| [ESI adapter](../schedules/script/adapters/esi) | Own the TV hall URL, local cache and HTTP metadata, name mappings, period configuration, event collapsing, lab and group rules, time rounding, and deduplication. |
| [Contract](../schedules/script/contract.py) | Define faculty and period configuration, normalized sessions, and the outcomes `Unchanged`, `Candidate`, `SourceFailure`, and `ParseFailure`. The adapter chooses its own payload type. |
| [Pipeline](../schedules/script/pipeline.py) | Validate the faculty, period declarations, paths, and normalized sessions. Sort sessions by group, day, start, and subject code while preserving source order for ties. Serialize the current flat wire format. |
| [Publication](../schedules/script/publication.py) | Prepare both period and index bytes before replacing files. Preserve previous files for omitted periods, retain hash-based update dates, and replace files only when their bytes differ. |

A sync that receives `Unchanged` reruns normalization on the cached input.
Mapping, configuration, and code edits therefore take effect with the same
remote source and headers. A source or parse failure returns a diagnostic
before publication. A malformed or entirely filtered source cannot produce a
successful empty publication. Explicit empty periods require configuration
that allows them.

Period IDs and indices must be unique within a faculty. Published paths must
be unique across the registry and stay within the distribution directory.
Period IDs support the numeric, prefixed, suffixed, and annual forms recognized
by Android's `TermCodeParser`, including IDs ending in an underscore-separated
period token. Their parsed index must match the declared index from 1 through 8.
Explicit period types use Android's canonical enum names, and the resulting
configuration must cover the declared indices.

`Session` stores the subject code and display name separately. It uses weekday
numbers from Monday (1) through Sunday (7) and local times at minute precision.
The serializer converts them to `dia` (Spanish day name), `hora_inicio`, and
`hora_fin` (HH:mm). The period key becomes `cuatrimestre`. The other flat fields
are `grupo`, `codigo`, `asignatura`, `tipo`, `aula`, `profesor`,
`es_laboratorio`, and `grupo_practicas`. The ESI adapter explicitly requests
the legacy index, whose entries supply its previous `updatedTime` values.
The catalog and legacy index carry the SHA-256 of the published bytes.

The [ESI operating guide](esi_api_schedule_pipeline.md) covers the commands,
strict-mode behavior, and workflow. The offline tests characterize ESI slot
bytes, catalog and legacy index metadata, FCM payloads, and mocked HEAD and GET
requests. The [CSV adapter in tests](../schedules/script/test_modular_pipeline.py)
exercises the same core with a different source format and faculty key.

## Existing Consumers

| Consumer | Compatibility |
| --- | --- |
| Android catalog | Schema v2 remains `universities → faculties → terms`. ESI keeps `1C.json` and `2C.json` relative to the catalog. |
| Legacy index | The flat ESI array still uses `title` where the catalog uses `name`. Android can wrap it in a synthetic UCLM/ESI catalog. |
| Android parser | The public files remain flat slot arrays. Android groups slots by year, group, and subject and treats ESI `evento` sessions as common events. |
| FCM | ESI retains the topic `sched_uclm_esi` and the keys `hash_1C` and `hash_2C`. |
| Workflows | Sync runs the same CLI in strict mode. The manual-edit safety net runs `--index-only`. Both use the `schedules-master-write` concurrency group and require success before sending FCM. |

## Proposed Follow-up

- [Issue #49](https://github.com/MarcosLorCar/ClemenTime/issues/49) covers Android
  hash verification and recovery when separate HTTPS requests read different
  revisions of Git-published schedules and indexes.
- [Issue #50](https://github.com/MarcosLorCar/ClemenTime/issues/50) covers
  inspecting and integrating the ESII (Albacete) HTML source. A future
  `uclm/esii` adapter would have its own configuration, state, and paths such
  as `uclm/esii/1C.json`.
- A multi-faculty run would need to report success or failure per faculty,
  retain valid files for failed faculties, and send FCM only to faculties
  whose published schedules changed. Production currently selects and runs
  one faculty at a time.
