# Schedule Pipeline Migration Index

Use this temporary index to find the [modular schedule pipeline
design](modular_schedule_pipeline.md) and the implementation issues. GitHub
tracks the status, detailed scope, and acceptance criteria for each delivery.

## PR Sequence

1. [#47 — Contract and ESI characterization](https://github.com/MarcosLorCar/ClemenTime/issues/47)
2. [#48 — Modular core and ESI adapter](https://github.com/MarcosLorCar/ClemenTime/issues/48)
3. [#49 — Catalog, publication, and CI by faculty](https://github.com/MarcosLorCar/ClemenTime/issues/49)
4. [#50 — ESII adapter and documentation cleanup](https://github.com/MarcosLorCar/ClemenTime/issues/50)

Each issue depends on the previous one and is scoped for one reviewable PR.
The original planning PR only documents the sequence. Complete each delivery
and verify its criteria before starting the next. If a delivery needs splitting,
update its issue with the new dependencies first.

## When to Remove This Index

The PR that closes #50 removes this index and any temporary links to it, then
updates the design document to describe the delivered behavior. If ESII is
postponed or canceled, remove this index when the core migration ends and track
any remaining ESII work in a separate issue.
