# FCM Setup, Smoke Testing & Google Play Console Checklist

This guide covers all remaining manual setup steps to activate Firebase Cloud Messaging (FCM) in production, test it end-to-end **without creating a release tag**, and update your Google Play Console declarations before publishing the next release.

---

## 1. Firebase Console Setup

1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a project (e.g., `ClemenTime`).
   - Disable **Google Analytics** during project creation (ClemenTime does not use Analytics).
2. Register **two Android apps** in the same Firebase project so both release and debug builds work with FCM:
   - **Release app**: Package name `com.marcoslorcar.clementime`
   - **Debug app** *(optional, needed for local smoke testing)*: Package name `com.marcoslorcar.clementime.debug`
3. Download **`google-services.json`** from **Project Settings -> General -> Your apps**.
   - Make sure the downloaded file contains both `com.marcoslorcar.clementime` and `com.marcoslorcar.clementime.debug` under `client[]`.
4. Generate a Service Account key for GitHub Actions:
   - Go to **Project Settings -> Service accounts -> Firebase Admin SDK**.
   - Click **Generate new private key** and save the JSON file locally (do **not** commit it; `service-account*.json` is gitignored).
5. **GDPR / EU Terms Check**:
   - In **Project Settings -> Data Privacy / Terms**, verify that Google's **Firebase Data Processing and Security Terms** are accepted.

---

## 2. GitHub Repository Secrets

Go to your GitHub repository: **Settings -> Secrets and variables -> Actions -> New repository secret**, and create:

| Secret Name | Value | Used By |
| :--- | :--- | :--- |
| `GOOGLE_SERVICES_JSON` | Full raw JSON contents of `google-services.json` | `ci.yml`, `release.yml` (overwrites placeholder `app/google-services.json` during builds) |
| `FCM_SERVICE_ACCOUNT_KEY` | Full raw JSON contents of the Firebase Admin Service Account private key | `sync-esi-schedules.yml`, `update-schedules-index.yml` (used by `notify_fcm.py`) |

---

## 3. End-to-End Smoke Test (No Release Tag Needed!)

You do **not** need to tag a release (`v*`) to test real FCM pushes end-to-end:

### A. Build & Install a Debug APK with Your Real Firebase Config
Because `app/src/debug/google-services.json` is gitignored and takes precedence over `app/google-services.json` for `debug` builds:

1. Copy your real downloaded `google-services.json` (which must include `com.marcoslorcar.clementime.debug`) to:
   ```text
   app/src/debug/google-services.json
   ```
2. Install the debug build onto your physical test phone from Android Studio or CLI:
   ```bash
   ./gradlew installDebug
   ```
3. Launch ClemenTime on your phone:
   - Ensure **Online Synchronization** is enabled (**More -> Online Synchronization -> ON**) and notifications are permitted.
   - Import at least one active subject from `1C` (e.g., `FunProg1`).
   - *(Tip to simulate a real change)*: Go to **More -> Developer Settings -> Reset Schedule Hashes** (visible in debug builds) — OR edit one slot slightly — so your phone's `lastKnownScheduleHash` is blank/mismatched and will trigger a sync + notification when the FCM push arrives.
   - *(Optional)*: Watch logs in real time via:
     ```bash
     adb logcat | grep -iE "FirebaseMessaging|FcmTopicManager|ScheduleUpdateWorker"
     ```

### B. Fire a Real Push Without Waiting for ESI to Change
Once PR #26 is merged to `master` (or while running locally):

- **Option 1 — Trigger via GitHub Actions (Tests the `FCM_SERVICE_ACCOUNT_KEY` secret too)**:
  1. Go to GitHub **Actions -> Sync ESI Schedules -> Run workflow** (on `master`).
  2. Check **`test_fcm` = `true`** (`Send FCM topic push even if no schedule changes were committed`).
  3. Click **Run workflow**.
  4. `notify_fcm.py` will authenticate with `FCM_SERVICE_ACCOUNT_KEY` and send a real high-priority data push to `/topics/sched_uclm_esi`.
  5. Your phone receives the push in `ClemenTimeFirebaseMessagingService.onMessageReceived` and runs `ScheduleUpdateWorker`.

- **Option 2 — Trigger directly from your terminal (before merging)**:
  ```bash
  export FCM_SERVICE_ACCOUNT_KEY="$(cat path/to/your-service-account.json)"
  python3 schedules/script/notify_fcm.py
  ```

### C. Also Test Manually Edited Slot Protection
1. In your phone's timetable, manually edit one subject's class time/room (e.g., move a slot to a custom day/time).
2. Run **More -> Check for updates now** (or send another FCM push via `test_fcm = true`).
3. Confirm that because the official remote slot hash for that subject did not change, **no false "Schedule changes detected" alert** is shown for your manually moved subject.

---

## 4. Google Play Console Policies & Bureaucracy Checklist

Before uploading the next `.aab` (built from a release tag after adding `GOOGLE_SERVICES_JSON`):

### A. Data Safety Form (`Policy and programs -> App content -> Data safety`)
Because `firebase-messaging` generates a **Firebase Installation ID / FCM registration token** and sends it to Google servers when Online Services are enabled, update your Data Safety form:

1. **Does your app collect or share any of the required user data types?** $\to$ **Yes**
2. **Is all of the user data collected by your app encrypted in transit?** $\to$ **Yes**
3. **Do you provide a way for users to request that their data is deleted?** $\to$ **No** *(no user accounts exist; users can opt out in Settings or uninstall, and your contact email in the Privacy Policy handles inquiries)*
4. **Data type to declare**: **Device or other IDs** $\to$ **Device or other IDs**:
   - **Collected or Shared?** $\to$ **Collected**
   - **Processed ephemerally?** $\to$ **No** *(FCM token persists on Google FCM servers across sessions while Online Sync is active)*
   - **Required or optional?** $\to$ **Users can choose whether this data is collected** *(users can choose **Fully Offline** during onboarding or toggle off **Online Synchronization** in Settings)*
   - **Purpose**: **App functionality** *(delivering timetable update alerts)*

### B. Advertising ID Declaration (`App content -> Advertising ID`)
- Answer **No** (the app does not use an Advertising ID).
- ClemenTime removes `com.google.android.gms.permission.AD_ID` via `tools:node="remove"` and disables Firebase Analytics in `AndroidManifest.xml`.

### C. Privacy Policy (`App content -> Privacy policy`)
- Confirm the URL remains `https://marcoslorcar.github.io/ClemenTime/PRIVACY_POLICY`.
- Once PR #26 merges to `master`, GitHub Pages will serve the updated `PRIVACY_POLICY.md` documenting **Online Services** (Firebase Cloud Messaging + GitHub raw schedule files) vs. **Fully Offline** mode.
