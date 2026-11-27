# ClemenTime Pipeline Diagrams

Visual guide to the modular multi-faculty schedule pipeline and continuous deployment workflows.

## 1. Multi-Faculty Modular Architecture

Each faculty lives in its own isolated **adapter folder** (`schedules/script/adapters/<faculty>/`) with a declarative `config.json` (identity, periods, paths, public `source_url`) and source-specific parsing. Everything downstream—registry validation, flat JSON serialization, multi-faculty catalog publishing, selective FCM, and Android import—is completely shared and faculty-agnostic.

```mermaid
flowchart LR
    subgraph Adapters["1. Faculty Adapters (schedules/script/adapters/)"]
        direction TB
        ESI["uclm/esi · ESI Ciudad Real (Active)<br/>config.json (URL + periods)<br/>TV Hall JSON API · HEAD/ETag cache<br/>mappings.json · :50 rounding · events"]
        NEXT["Future Faculty · e.g. uclm/esii (Planned #57)<br/>config.json (URL + periods)<br/>Any source: HTML, API, PDF, ICS<br/>Isolated cache & domain rules"]
    end

    subgraph Contract["2. Common Contract (contract.py + config.py + registry.py)"]
        direction TB
        SPEC["FacultySpec (from config.json)<br/>key · periods · paths · FCM topic"]
        IFACE["FacultyAdapter Protocol<br/>acquire() · read_local() · normalize()"]
        OUTCOME["Typed Outcomes<br/>FacultySchedule (Session[])<br/>Unchanged · SourceFailure · ParseFailure"]
    end

    subgraph Core["3. Shared Pipeline Core"]
        direction TB
        CLI["process_schedules.py<br/>--all | --faculty | --sync | --index-only"]
        PIPE["pipeline.py<br/>Validate full registry<br/>Stage each faculty independently<br/>Serialize flat JSON"]
        PUB["publication.py<br/>Atomic staged batch publish<br/>Rollback on OSError<br/>Explicit period removal guard"]
    end

    subgraph Dist["4. Published Output (schedules/dist/)"]
        direction TB
        ESIDIST["1C.json · 2C.json<br/>schedules_index.json (ESI legacy)"]
        NEWDIST["{uni}/{fac}/{period}.json<br/>(nested per-faculty paths)"]
        CAT["schedules_catalog.json (v2)<br/>universities → faculties → terms + SHA-256"]
    end

    subgraph Delivery["5. Selective Push & Android Client"]
        direction TB
        FCM["notify_fcm.py<br/>sched_{uni}_{fac} topics<br/>Only faculties with changed hashes"]
        APP["Android App<br/>RemoteScheduleFetcher + SHA-256 verify<br/>JsonScheduleParser → Room DB"]
    end

    ESI --> IFACE
    NEXT -.-> IFACE
    SPEC --> CLI
    IFACE --> OUTCOME
    OUTCOME --> PIPE
    CLI --> PIPE
    PIPE --> PUB
    PUB --> ESIDIST
    PUB -.-> NEWDIST
    PUB --> CAT
    CAT --> FCM
    FCM --> APP
    CAT --> APP
    ESIDIST --> APP
    NEWDIST -.-> APP
```

## 2. Per-Faculty Lifecycle & How to Add a Faculty

Every registered adapter runs through the exact same three-method lifecycle. Onboarding a new faculty requires **only three touchpoints** (`adapters/<fac>/` with `config.json`, one registration line in `registry.py`, and offline fixtures/tests)—no edits to `pipeline.py`, `publication.py`, `notify_fcm.py`, CI workflows, `.env`, or Android.

```mermaid
sequenceDiagram
    autonumber
    participant CLI as process_schedules.py
    participant REG as registry.py
    participant PIPE as pipeline.py
    participant ADP as FacultyAdapter (esi / future)
    participant PUB as publication.py
    participant FCM as notify_fcm.py

    CLI->>REG: registered_faculties(schedules/, today)
    REG-->>CLI: {FacultyKey: (FacultySpec, FacultyAdapter)}
    CLI->>PIPE: validate_specs(all_specs, dist/)
    Note over PIPE: Checks key/path/topic collisions,<br/>symlinks, and TermCodeParser rules

    loop Independently for each selected faculty
        alt --sync (remote)
            PIPE->>ADP: acquire(force)
            alt Remote content unchanged
                ADP-->>PIPE: Unchanged
                PIPE->>ADP: read_local(None) (reprocess cache)
            else Remote content updated
                ADP-->>PIPE: Candidate(payload)
            else Network or malformed remote source
                ADP-->>PIPE: SourceFailure(diagnostic)
            end
        else Offline / --fixture
            PIPE->>ADP: read_local(fixture)
        end

        opt Candidate payload available
            PIPE->>ADP: normalize(payload, strict)
            alt Valid structure
                ADP-->>PIPE: FacultySchedule(sessions_by_period)
                PIPE->>PIPE: serialize_schedule() → flat JSON bytes
            else Structural or mapping error
                ADP-->>PIPE: ParseFailure(diagnostic)
            end
        end
    end

    PIPE->>PUB: publish_batch(valid_faculties, dist/)
    Note over PUB: Writes valid periods + v2 catalog<br/>Preserves failed faculties & foreign entries<br/>Rolls back staging dir on OSError
    PUB-->>CLI: BatchResult (changed_schedule_faculties, report, summary)

    opt After confirmed git push & changed hashes
        CLI->>FCM: notify_fcm.py --report report.json
        FCM->>FCM: Dispatch only to changed faculty topics
    end
```

## 3. Automated Sync, Partial Failure Isolation & Client Verification

```mermaid
flowchart TD
    Start(["Every 6 hours · relevant push to master · manual dispatch"]) --> Stage["Stage every registered faculty independently<br/>Acquire, normalize in strict mode, and validate paths/periods"]
    Stage --> Valid{"At least one faculty valid?"}
    Valid -->|"No (exit 1)"| Stop(["Fail workflow without touching dist/"])
    Valid -->|"Yes (exit 0 or 2)"| Publish["Publish valid batch to schedules/dist/<br/>Update schedules_catalog.json v2 & ESI schedules_index.json<br/>Keep failed faculties intact · write report + job summary"]

    ManualEdit(["Hand-edited JSON under schedules/dist/"]) --> Index["Rebuild indexes (--all --index-only)<br/>Preserve unregistered/foreign catalog entries"]
    Index --> Commit

    Publish --> Commit{"Changes under schedules/?"}
    Commit -->|Yes| Push["Commit and push to master<br/>(schedules-master-write lock)"]
    Commit -->|No| Skip(["Skip push unless test_fcm / retry_fcm"])

    Push -->|"Confirmed push + changed_schedule_faculties"| FCM["Selective FCM push only for changed faculties"]
    Skip -.->|"Manual test_fcm or retry_fcm"| FCM

    FCM --> Partial{"Did any faculty fail? (exit 2)"}
    Partial -->|Yes| FailRun(["Fail workflow to surface partial failure<br/>Valid pushed data stays live"])
    Partial -->|No| Done(["Run complete"])

    FCM --> Sync["Android triggers schedule update check"]
    Periodic(["Periodic WorkManager fallback"]) --> Sync
    Sync --> Download["Download term JSON (?v=hash) & verify SHA-256 against catalog<br/>Retry with cache-bust (?cb=...) on CDN split-revision mismatch"]
```

[Modular schedule pipeline](modular_schedule_pipeline.md) ·
[ESI operating guide](esi_api_schedule_pipeline.md) ·
[Schedule sync workflow](../.github/workflows/sync-esi-schedules.yml) ·
[Index repair workflow](../.github/workflows/update-schedules-index.yml)

## 4. Continuous Deployment

```mermaid
flowchart TD
    Start(["Push a version tag · manually run the release workflow"]) --> Validate["Resolve version and Play track<br/>Check required build secrets and run schedule validation and unit tests"]
    Validate -->|Checks pass| Tag["Create missing tag if a manual run supplied a version"]
    Tag --> Build["Build signed APK and AAB<br/>Generate release notes"]
    Build --> Artifacts["Upload GitHub Actions artifacts"]
    Artifacts --> GitHub["Publish GitHub Release with APK"]
    GitHub --> Play{"Play upload enabled and credentials available?"}
    Play -->|No| Done(["Release complete on GitHub"])
    Play -->|Yes| Upload["Upload AAB to Google Play"]
    Upload --> Internal(["Internal by default for prereleases"])
    Upload --> Production(["Production by default for stable releases"])
```

Manual runs can choose a Play track or skip Play. The
[CI workflow](../.github/workflows/ci.yml) runs Python lint, offline `--all --strict` schedule validation, unit tests, Android Lint, Detekt, and debug APK assembly on pushes and pull requests targeting `master`. The
[release workflow](../.github/workflows/release.yml) runs its own checks before publishing.
