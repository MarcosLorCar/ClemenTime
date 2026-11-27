# ClemenTime pipeline diagrams

## Schedule updates

```mermaid
flowchart TD
    Start(["Every 6 hours · relevant push to master · manual run"]) --> Stage["Stage each registered faculty independently<br/>Acquire, normalize in strict mode, and validate paths/periods"]
    Stage --> Valid{"At least one faculty valid?"}
    Valid -->|No| Stop([Exit nonzero without touching dist])
    Valid -->|Yes| Publish["Publish valid batch to dist, v2 catalog, and ESI legacy index<br/>Keep failed faculties intact · write report and CI summary"]
    ManualEdit["Schedule JSON edited directly on master"] --> Index["Rebuild indexes for all registered faculties (--all --index-only)<br/>Preserve foreign catalog entries"]
    Index --> Commit
    Publish --> Commit{"Changes to commit under schedules/?"}
    Commit -->|Yes| Push["Commit and push to master<br/>(schedules-master-write lock)"]
    Commit -->|No| Skip(["Skip push unless test_fcm / retry_fcm"])
    Push -->|Confirmed push + changed schedule hashes| FCM["Selective FCM push only for changed faculties"]
    Skip -.->|Manual test_fcm or retry_fcm| FCM
    FCM --> Partial{"Any faculty failed?"}
    Partial -->|Yes| FailRun([Fail workflow to surface partial error])
    Partial -->|No| Done([End run])
    FCM --> Sync["Android checks for schedule update"]
    Periodic["Periodic fallback when enabled"] --> Sync
    Sync --> Download["Download term JSON and verify SHA-256 against catalog<br/>Retry with cache-bust on split-revision mismatch"]
```

[Schedule sync workflow](../.github/workflows/sync-esi-schedules.yml) ·
[Index repair workflow](../.github/workflows/update-schedules-index.yml) ·
[Modular schedule pipeline](modular_schedule_pipeline.md)

## Continuous deployment

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
[CI workflow](../.github/workflows/ci.yml) runs checks on eligible pushes and
pull requests targeting `master`. The
[release workflow](../.github/workflows/release.yml) runs its own checks before publishing.
