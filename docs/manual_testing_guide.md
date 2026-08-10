# End-to-End Manual Testing Guide: ESI Schedule Auto-Sync & Flat Schema Migration

This guide outlines step-by-step instructions to manually verify the entire system end-to-end, from the ESI Python pipeline to the Android app's schedule update detection and diff review sheet.

---

## 🧪 Check 1: Python ESI Endpoint Checker & Mock Update Simulation

### 1. Test Fetching Live ESI Headers
Run the ESI checker script without flags:
```bash
python3 schedules/script/check_esi_update.py
```
* **Expected Output**: `[No Update] ESI schedule files unchanged.`
* **Check File**: Inspect `schedules/input/esi_meta.json`. It should contain entries for `1C` and `2C` with `url`, `last_modified`, `etag`, and `sha256` fields.

### 2. Test Simulating an ESI Schedule Update (Mock Header / Force Flag)
Run the script with a mock ETag flag:
```bash
python3 schedules/script/check_esi_update.py --mock-etag "test-etag-manual-check"
```
* **Expected Output**: `[Update Detected] ESI schedule PDFs updated or force flag enabled.`
* **Check Files**: Fresh semester PDFs are downloaded into `schedules/pdf/`, older semester PDF versions are purged (non-cumulative), and `esi_meta.json` reflects `"etag": "test-etag-manual-check"`.

### 3. Test Running the Processing Pipeline
Run `process_schedules.py`:
```bash
python3 process_schedules.py --strict
```
* **Expected Output**:
  ```
  [Run] Processing schedules.json...
  [Run] Regenerating index...
  Successfully generated schedules/dist/schedules_index.json with 2 schedules.
  [Done] Pipeline finished successfully!
  ```
* **Check Output Files**:
  - `schedules/dist/1C.json`: Verify it is a flat array `[ { "grupo": "1A", ... } ]` without `celda_dividida` or `posicion_en_celda`.
  - `schedules/dist/2C.json`: Verify flat array format.
  - `schedules/dist/schedules_index.json`: Verify entries have human-readable titles `"Primer Cuatrimestre"` and `"Segundo Cuatrimestre"`.

---

## 🚀 Check 2: GitHub Actions Workflow Verification

1. Push your branch to GitHub.
2. Go to your GitHub repository in your browser -> **Actions** tab.
3. Click on the **"Sync ESI Schedules"** workflow on the left sidebar.
4. Click **Run workflow** (using `workflow_dispatch`).
5. Verify the workflow completes successfully (green checkmark) and executes `check_esi_update.py` and `process_schedules.py`.

---

## 📱 Check 3: Android App - Auto-Update Settings & Manual Sync

1. Launch the app on your Android device or emulator:
   ```bash
   ./gradlew installDebug
   ```
2. Navigate to **More / Settings** screen (gear icon or "Más" tab).
3. Scroll to the **"Schedule Auto-Updates" / "Actualizaciones de Horario"** section.
4. **Verify the default**: on a fresh install the interval reads **Off** — background sync is opt-in.
5. **Test Dropdown Options**:
   - Change the dropdown between `15 min`, `6 hours`, `12 hours`, `24 hours`, and `Off`.
   - Select `6 hours`.
6. **Test Manual Sync Button**:
   - Tap **"Check for Schedule Updates Now"** ("Comprobar actualizaciones ahora").
   - If schedules match, you will see a message indicating your schedule is up to date.
   - Note this calls `performSync` directly, bypassing WorkManager — passing here says nothing
     about whether the background path works. Use the ADB job trigger in Check 4 for that.

### Legacy default migration
Background sync used to be on (6h) by default. `SettingsRepository.migrateLegacyAutoUpdateDefault`
restores 6h for installs that completed onboarding without ever choosing an interval, so
upgrading users don't silently lose sync. To verify:

1. Install a build from before the change, complete onboarding, never touch the interval setting.
2. Install this build over it and launch. Settings must still read **6 hours**.
3. On a fresh install (onboarding not yet completed) it must read **Off**.
4. Explicitly select **Off**, force-stop, relaunch — it must stay **Off**, not revert to 6h.

---

## 🔔 Check 4: End-to-End Schedule Change Detection & Diff Review

This test verifies that when a subject's classroom or time changes on the server, ClemenTime detects it and shows the **Schedule Diff Sheet**.

### Step A: Import a Schedule
1. Open ClemenTime -> **Import Schedule**.
2. Select **"Primer Cuatrimestre"** from the remote list. (There is no bundled schedule —
   `ImportSourceType.BUNDLED` still exists but no `assets/schedules/` ships, so it is unreachable.)
3. Select your course group (e.g. `1A`) and import subjects like `FunProg1` or `Cálculo`. Verify they appear on your home schedule grid.

### Step B: Simulate a Remote Schedule Slot Change
To simulate a remote schedule change locally without waiting for UCLM:
1. Open `schedules/dist/1C.json`.
2. Find an entry for `FunProg1` (or your imported subject).
3. Change its classroom from `"John von Neumann - A1.1"` to `"Laboratorio B1.2"` or shift `hora_inicio` from `"10:00"` to `"10:30"`.
4. Re-generate `schedules_index.json`:
   ```bash
   python3 schedules/script/generate_index.py
   ```

### Step C: Trigger Update Detection in App
1. In ClemenTime, open **Settings** -> Tap **"Check for Schedule Updates Now"**.
2. Alternatively, trigger the WorkManager background job via ADB:
   ```bash
   adb shell cmd jobscheduler run -f com.marcoslorcar.clementime 1
   ```

### Step D: Review the Schedule Delta / Diff Sheet
1. The **Schedule Changes Detected** ("Cambios detectados en el horario") bottom sheet will pop up!
2. Verify:
   - It lists your subject (e.g. `FunProg1`) with a **MODIFIED** badge.
   - It clearly compares the **Previous Slot** (`John von Neumann - A1.1`) vs **New Slot** (`Laboratorio B1.2`).

### Step E: Apply Updates to Database
1. Tap **"Apply Updates"** ("Aplicar cambios").
2. Return to the home screen schedule grid.
3. Verify `FunProg1` now shows the updated classroom (`Laboratorio B1.2`) seamlessly!

---

## 🧩 Check 5: Home-Screen Widget Timeline

Nothing in `.\gradlew test` covers the widget, so this is device-only.

1. Add the ClemenTime widget to your home screen and enable the **Now line** in its settings.
2. **Before the first class** of a day that has classes: the timeline must start flush at the
   first class — no blank gridded rows above it — and **no Now line** is drawn.
3. **During the day**, with the current time inside class hours: the Now line appears, including
   when it falls in a gap between two clusters.
4. **After the last class has ended**: no Now line, and the timeline ends flush at the last class.
5. **On a day with no classes at all**: the empty state (logo + "no classes") shows, with no grid
   and no Now line.
6. Toggle the widget to a different day: no Now line on any day but today.

---

## 🔬 Check 6: Lab Optimizer

1. Open the **Lab Optimizer** from the schedule screen (magic wand).
2. Confirm the title, tooltip, help sheet and buttons all read as *lab*-scoped
   ("Lab Optimizer", "Lab Combinations") — then switch the system language to Spanish and
   confirm the same for "Optimizador de Laboratorios" / "Combinaciones de Laboratorio".
3. Generate combinations. In each result card, the lab-choices list must:
   - show **one entry per line**, not a wrapping row of chips;
   - **omit pinned subjects** (those with a lab group locked via the radio buttons);
   - **omit subjects with only one distinct lab schedule** — including a subject whose lab
     groups all share the same day/time, which previously rendered as e.g. `MAT: G1/G2`;
   - disappear entirely (header included) when no subject had a real choice.
4. Tap **Apply** and confirm the confirmation sheet still lists **every** subject it will
   change, including the deterministic ones — it deliberately stays unfiltered, because
   applying really does pick the first of a set of same-schedule groups.
