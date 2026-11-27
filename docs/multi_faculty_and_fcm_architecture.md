# Multi-Faculty Schedule Architecture & FCM Push Synchronization

## 1. Executive Summary & Problem Statement

ClemenTime was originally architected specifically around the **Escuela Superior de Informática (ESI) at UCLM**, hardcoding assumptions around:
1. **Catalog & Semester Structure**: Exactly two annual files (`1C` and `2C`), stored in `dist/1C.json` and `dist/2C.json`.
2. **Nomenclature & Domain Quirks**: ESI TV Hall API, `Pruebas de Progreso`, general conferences, and laboratory groups (`grupo_practicas`, `-L`, `-Lab`).
3. **Synchronization**: Hardcoded index endpoints and fixed WorkManager polling keys (`auto_update_interval_hours`).

To scale ClemenTime beyond ESI to other faculties, universities, or even a community-driven "Schedule Hub", while simultaneously transitioning to **Firebase Cloud Messaging (FCM)** push notifications, the architecture must generalize across four pillars:

```mermaid
flowchart TD
    subgraph P1["Pillar 1: Domain & Subgroup Model"]
        A[Labs Generalized to Optional Subgroups]
        A1[Faculty with NO labs -> Pure Lecture Mode]
        A2[Faculty WITH subdivisions -> Subgroup Optimization]
    end

    subgraph P2["Pillar 2: Hierarchical Catalog Schema"]
        B[Catalog Hierarchy]
        B1["University -> Faculty / Center -> Degree -> Schedule/Term"]
        B2["Support for Admin CI Pipelines & Future Community Hub"]
    end

    subgraph P3["Pillar 3: Scoped Event-Driven Push (FCM)"]
        C[Dynamic Topic Subscriptions]
        C1["Scoped Topics: /topics/{source}_{faculty}_{scheduleId}"]
        C2["Dispatched by CI or Hub Backend upon Catalog Change"]
    end

    subgraph P4["Pillar 4: Unified Onboarding & Privacy"]
        D[Student User Experience]
        D1["Step 1: Pick University & Faculty or Custom/Offline"]
        D2["Step 2: Choose Online Services (Push) vs Fully Offline"]
    end
```

---

## 2. Generalizing the Lab Selection System (Addressing the Lab Concern)

### 2.1. The User's Concern: "Not every faculty has labs"
A central worry when expanding to Law, Humanities, Business, or other engineering schools is that timetable structures differ:
- **Humanities / Law / Social Sciences**: Often have **no laboratories whatsoever**. Students have fixed lecture slots per group with no subdivision variants.
- **Sciences / Engineering**: Have lectures + laboratory groups (`L1`, `L2`, `L3`) or seminar groups (`S1`, `S2`).
- **Medical / Nursing**: Have clinical rotations, seminars, and lectures.

### 2.2. Reality Check in ClemenTime's Current Codebase
A close inspection of `ConflictSolver.kt`, `Subject.kt`, and `ClassSlot.kt` reveals that **ClemenTime already handles lab-free subjects natively**:

```kotlin
// In ConflictSolver.kt:
val subjectsWithChoices = subjects.filter { s ->
    !s.subject.isDummy && s.subject.selectedLabGroup == null && run {
        val labGroupCount = s.slots
            .filter { it.entryType == EntryType.LAB }
            .mapNotNull { it.labGroupName }
            .distinct()
            .size
        labGroupCount > 1 // Only subjects with >1 lab group are treated as choices!
    }
}
```

1. If a subject has **0 lab slots** (e.g. standard lecture-only classes):
   - `labGroupCount == 0`.
   - The subject is **never added** to `subjectsWithChoices`.
   - All its slots are routed directly into `fixedSlots`.
   - The Cartesian product combination has length 1.
   - The conflict solver simply checks for lecture clashes (e.g. cross-course enrollment conflicts).
2. In the UI (`ConflictResolverComponents.kt` and `SubjectItemComponents.kt`):
   - Lab selection chips and badges are **only rendered** when `ConflictSolver.labVariantCount(subject) > 1`.
   - If a subject has no labs, **no lab selection UI is shown**. The subject behaves like a regular class.

### 2.3. The Architectural Generalization: From "Lab" to "Subgroup / Activity Group"
To prevent semantic awkwardness when showing seminars or workshops, we generalize the vocabulary:
- **Wire Schema & Model**:
  - `entryType`: Rename or treat `LAB` as `SUBGROUP` (or retain `LAB` as a subtype of `SUBGROUP`).
  - `labGroupName`: Renamed/aliased to `subgroupName` (e.g. `"Seminario A"`, `"Prácticas 2"`, `"Taller 1"`).
  - `selectedLabGroup`: Renamed/aliased to `selectedSubgroup`.
- **UI Presentation**:
  - If a faculty's schedule has zero subgroups, ClemenTime seamlessly functions in **"Standard Fixed Schedule Mode"**.
  - The "Conflict Resolver / Optimizer" tab gracefully adapts:
    - If the student has enrolled in subjects with multiple subgroups $\to$ Shows the subgroup optimizer.
    - If all enrolled subjects are fixed $\to$ Shows an overlap report or congratulates the student that no conflicts exist.

---

## 3. Ingestion Strategy: Admin Pipelines vs. Community "Schedule Hub"

Scaling beyond ESI raises the question of how schedule data is collected, validated, and published. We evaluate three models:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Ingestion Strategy Comparison                   │
├───────────────────────┬──────────────────────┬─────────────────────────┤
│ Model A:              │ Model B:             │ Model C: Recommended    │
│ Multi-Pipeline (CI)   │ Pure Schedule Hub    │ Hybrid Progressive Hub  │
├───────────────────────┼──────────────────────┼─────────────────────────┤
│ • Scrapers per school │ • Crowdsourced wiki  │ • Core CI scrapers for  │
│ • Admin maintains all │ • Users upload JSON  │   partner faculties     │
│ • High quality        │ • High scalability   │ • Community Hub for     │
│ • High admin overhead │ • Moderation & spam  │   user-submitted files  │
│                       │   risks              │ • Unified JSON format   │
└───────────────────────┴──────────────────────┴─────────────────────────┘
```

### Model C (Hybrid Progressive Architecture) — Recommended:
1. **Standard Normalized Wire Format**:
   - All faculties — whether scraped by CI or uploaded by students — conform to a single standardized schema:
     ```json
     {
       "schema_version": 2,
       "faculty_id": "uclm_esi",
       "faculty_name": "Escuela Superior de Informática",
       "university": "Universidad de Castilla-La Mancha",
       "term_id": "2026_1C",
       "term_title": "1º Cuatrimestre (2026-2027)",
       "subgroups_label": "Laboratorios", 
       "slots": [
         {
           "codigo": "FunProg1",
           "asignatura": "Fundamentos de Programación 1",
           "grupo": "1A",
           "dia": 1,
           "hora_inicio": "08:30",
           "hora_fin": "10:00",
           "aula": "Fermín Caballero - A1.1",
           "profesor": "GARCIA",
           "tipo": "teoria",
           "subgrupo": null
         },
         {
           "codigo": "FunProg1",
           "asignatura": "Fundamentos de Programación 1",
           "grupo": "1A",
           "dia": 3,
           "hora_inicio": "10:00",
           "hora_fin": "11:30",
           "aula": "LD1",
           "profesor": "LOPEZ",
           "tipo": "subgrupo",
           "subgrupo": "Lab 1"
         }
       ]
     }
     ```
2. **Hierarchical Master Index (`schedules_catalog.json`)**:
   Instead of the flat `[{"id": "1C"}, {"id": "2C"}]`, the remote catalog becomes hierarchical:
   ```json
   {
     "version": 2,
     "universities": [
       {
         "id": "uclm",
         "name": "Universidad de Castilla-La Mancha",
         "faculties": [
           {
             "id": "esi",
             "name": "Escuela Superior de Informática (Ciudad Real)",
             "fcm_topic_prefix": "uclm_esi",
             "terms": [
               { "id": "1C", "name": "1º Cuatrimestre", "url": "schedules/uclm/esi/1C.json", "hash": "..." },
               { "id": "2C", "name": "2º Cuatrimestre", "url": "schedules/uclm/esi/2C.json", "hash": "..." }
             ]
           },
           {
             "id": "farmacia",
             "name": "Facultad de Farmacia (Albacete)",
             "fcm_topic_prefix": "uclm_farmacia",
             "terms": [
               { "id": "1C", "name": "1º Cuatrimestre", "url": "schedules/uclm/farmacia/1C.json", "hash": "..." }
             ]
           }
         ]
       }
     ]
   }
   ```
3. **Community Hub Extension (Phase 4)**:
   - When a community user creates a schedule (via an in-app export or a web-based builder), it generates the exact same standardized JSON.
   - Users can share schedules via **direct URL**, **QR code**, or submit to the **Schedule Hub** for admin review.

---

## 4. Reconciling the FCM Push Plan with Multi-Faculty

In our initial FCM study, we proposed static topics: `/topics/schedule_updates_1c` and `/topics/schedule_updates_2c`.

### 4.1. Why Static Topics Break in a Multi-Faculty World
If ClemenTime supports 10 faculties, having hardcoded topics would cause:
- **Cross-talk**: Students in Medicine would receive push wakeups when Computer Science schedules update.
- **Unnecessary battery drain**: Devices would download diffs for schedules they don't follow.

### 4.2. Scoped Dynamic Topic Subscriptions (Implemented)
With the hierarchical catalog, FCM topics are scoped **per faculty** (not per term), while individual term hashes are carried inside the data payload (`hash_1C`, `hash_2C`, ...):

$$\text{Topic Format: } \texttt{sched\_\{university\}\_\{faculty\}} \quad (\text{or } \texttt{sched\_\{fcm\_topic\_prefix\}})$$

*Example Topics:*
- `sched_uclm_esi`
- `sched_uclm_farmacia`

### 4.3. Dispatch Architecture (CI & Hub)
1. **Official Scraped Faculties (GitHub Actions)**:
   - When `.github/workflows/sync-esi-schedules.yml` (or future multi-faculty workflows) commits schedule changes, `schedules/script/notify_fcm.py` dispatches a high-priority data-only message to `sched_uclm_esi` containing `hash_{termId}` for each term in `schedules_catalog.json`.
   - On the device, `ClemenTimeFirebaseMessagingService` checks incoming `hash_{term}` values against `last_known_schedule_hash_$semester` for active semesters and enqueues an expedited one-time `ScheduleUpdateWorker` only when a change is detected.
2. **Community / Custom Schedules**:
   - If a student imports a schedule from a custom local JSON file (not part of the catalog), **FCM topic push is omitted**.

```mermaid
sequenceDiagram
    autonumber
    participant Student as ClemenTime App
    participant Cat as Catalog API (GitHub Raw / Hub)
    participant FCM as Firebase Cloud Messaging
    participant CI as GitHub Actions Pipeline

    Note over Student: Imports UCLM -> ESI schedule (Online Mode)
    Student->>FCM: subscribeToTopic("sched_uclm_esi")
    
    Note over CI: CI detects update in ESI schedules
    CI->>Cat: Push updated JSON & schedules_catalog.json
    CI->>FCM: Dispatch data push to "sched_uclm_esi" (hash_1C, hash_2C)
    FCM->>Student: Deliver silent data push
    Student->>Cat: Download changed term JSON & compute diff
    Student->>Student: Notify only if student's courses are affected
```

---

## 5. Legacy Fallback Migration & Deprecation Roadmap

### 5.1. Purpose of Legacy Constants
During the transition from an ESI-only application to a multi-faculty platform, legacy users upgrading from earlier releases (v1.4.1 and below) do not have explicit `selected_university_id` or `selected_faculty_id` preferences stored in DataStore.

To ensure zero friction and preserve background schedule checks for existing users, `SettingsRepository` defines:
- `LEGACY_FALLBACK_UNIVERSITY_ID` = `"uclm"`
- `LEGACY_FALLBACK_FACULTY_ID` = `"esi"`

These constants are **gated strictly behind the presence of pre-v1.5 schedule hashes** (`lastKnownScheduleHash_1` or `lastKnownScheduleHash_2`). 

> [!IMPORTANT]
> Fresh installs receive an empty string `""` (unlinked state) and do NOT default to UCLM/ESI.

### 5.2. Future Deprecation & Removal Roadmap (For Future AI Agents & Developers)
Once pre-v1.5 adoption approaches zero (e.g., in a major release like v2.0), the legacy migration fallback logic can be safely removed to complete total decoupling:

1. **Step 1 — Remove Hash-Based Resolution in `SettingsRepository.kt`**:
   - Simplify `resolveSelectedUniversityId` and `resolveSelectedFacultyId` to return `storedUniversityId ?: ""` / `storedFacultyId ?: ""`.
   - Remove `LEGACY_FALLBACK_UNIVERSITY_ID` and `LEGACY_FALLBACK_FACULTY_ID` constants.

2. **Step 2 — Remove Synthetic Index Fallback in `RemoteScheduleFetcher.kt`**:
   - Remove `canFallbackToLegacyIndex` and `syntheticCatalog` generation that wraps legacy `schedules_index.json` as UCLM · ESI.
   - Require all remote schedule lookups to resolve strictly through `schedules_catalog.json`.

3. **Step 3 — Clean Up Legacy Tests**:
   - Remove unit tests asserting legacy hash fallback to `"uclm"` / `"esi"`.

---

## 6. Unified Onboarding & Settings Flow (Implemented)

### 6.1. Onboarding & On-Import Faculty Binding
- **Onboarding** asks the user to choose a synchronization mode (**Online Services** vs **Fully Offline**) without forcing a university/faculty picker upfront.
- **Faculty binding happens implicitly on import**: when the user imports a remote schedule from the searchable 3-level catalog in `ImportScreen`, ClemenTime persists `(universityId, facultyId, universityName, facultyName, fcmTopicPrefix)`, resets stale hashes when switching faculties, and invokes `FcmTopicManager.syncRegistration()`.

### 6.2. Settings Experience ("More" Screen)
- **Linked Faculty Status**: Displays the bound `University · Faculty` (or unlinked prompt).
- **Sync Mode Toggle**: Switches between `Online Services` (FCM push, with periodic WorkManager fallback on non-GMS devices) and `Fully Offline`.
- **Check for updates now**: Runs an inline diff check and opens `ScheduleDiffBottomSheet`.

---

## 7. Roadmap Status

- **Phase 1 — Data Model Generalization (Implemented)**: Lab-free compatibility verified in `ConflictSolver` + tests; n-semester (`1..8` academic periods) support added in `AcademicPeriod.kt`. *(Note: `subgroups_label` is parsed in `ScheduleCatalog.kt` for forward compatibility but intentionally not wired into UI yet while localized Lab strings suffice.)*
- **Phase 2 — Hierarchical Catalog Specification (Implemented)**: `schedules_catalog.json` v2 generated by the schedule publisher and browsable in `ImportScreen` (with legacy `schedules_index.json` fallback).
- **Phase 3 — Dynamic FCM Push Dispatch & Client Integration (Implemented)**: `ClemenTimeFirebaseMessagingService`, `FcmTopicManager`, `notify_fcm.py`, GMS fallback, and manual-edit protection via `last_remote_subject_hashes_$semester`.
- **Phase 4 — Online vs. Fully Offline UX (Implemented)**: Binary sync mode choice in `OnboardingScreen` and `MoreScreen`.
- **Phase 5 — Multi-Faculty Expansion & Schedule Hub (Future)**:
  - Add GitHub Actions scrapers for additional faculties/universities as demand grows.
  - Explore a Community Schedule Hub allowing student associations to submit and maintain their own faculty schedule JSON files.
  - Remove v1.5 legacy fallback constants (`LEGACY_FALLBACK_*`) in v2.0 (see Section 5.2).

---

## 8. Conclusion

By recognizing that **ClemenTime already supports lab-free subjects seamlessly**, and scoping **FCM topics per faculty** (`sched_{uni}_{faculty}`) with per-term hashes in the push payload, push notifications and multi-faculty expansion stay unified without requiring dedicated backend servers.
