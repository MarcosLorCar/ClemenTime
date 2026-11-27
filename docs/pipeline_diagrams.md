# ClemenTime pipeline diagrams

## Schedule updates

```mermaid
flowchart TD
    Start(["Every 6 hours · relevant push to master · manual run"]) --> Check["Check the ESI TV API<br/>Download updated source JSON"]
    Check --> Run{"Source changed, or push/manual run?"}
    Run -->|No| End([End run])
    Run -->|Yes| Build["Normalize and deduplicate in strict mode<br/>Generate 1C.json, 2C.json, catalog and legacy index"]
    Build -->|Success| Publish{"Files changed under schedules/?"}
    Build -->|Failure| Stop([Stop before publishing])
    ManualEdit["Schedule JSON changed directly on master"] --> Index["Regenerate catalog and legacy index"]
    Index -->|"If indexes changed"| Push
    Publish -->|Yes| Push["Commit and push to master"]
    Publish -->|No| Skip(["End run unless testing FCM"])
    Push -->|Successful push| FCM["Send faculty topic notification via Firebase"]
    Skip -.->|Manual FCM test| FCM
    FCM --> Sync["Android checks for a schedule update"]
    Periodic["Periodic fallback when enabled"] --> Sync
    Sync -->|If an update is needed| Download["Download published schedule JSON from GitHub"]
```

[Schedule sync workflow](../.github/workflows/sync-esi-schedules.yml) ·
[Index repair workflow](../.github/workflows/update-schedules-index.yml) ·
[Modular schedule pipeline proposal](modular_schedule_pipeline.md)

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
