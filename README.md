# Amaal Tracker

A native Android app built with Kotlin and Jetpack Compose. Includes Google-only Firebase authentication, private families, realtime tracking, weighted prayer statuses, recurring amals, Gregorian and Umm al-Qura Hijri dates, local reminders, and weekly/monthly family reviews.

## Open and run

Open this folder in Android Studio, use JDK 17 or 21, install Android SDK 35, and run the `app` configuration on Android 8.0 or newer. Or:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`. The app builds without Firebase credentials. Choose **Explore local preview** to try tracking, edit amals, inspect charts, and configure local reminders. Preview data persists on this device and is never uploaded. Use **Family → Create another local family** to try the selector with separate local records. It contains no fabricated scores or members. Exit preview in Settings to sign in.

## Connect your Firebase project

1. Create a Firebase project on Spark, register Android package `com.amaal.tracker`, and add your debug/release signing certificate SHA-1 and SHA-256 fingerprints. `./gradlew signingReport` prints the debug fingerprints.
2. Enable **Authentication → Sign-in method → Google**, configure your support email and OAuth consent screen as needed, and download the updated `google-services.json` into `app/`. The Google Services plugin is applied only when that file exists. Never use a service-account private key in the app.
3. Create Cloud Firestore in production mode, choose your region, and publish `firestore.rules` using the Firebase console, or with an authenticated Firebase CLI:

   ```sh
   npx firebase-tools deploy --only firestore:rules --project YOUR_PROJECT_ID
   ```

4. Rebuild the app. Sign in, open **Family**, and create a family. Share the code or `amaal://join/CODE` link. An invited user with the app installed can paste the code/link or open the custom link and sign in.
5. Validate with two Google accounts on two devices: create/join, record a deed, and verify that the other device's family progress updates. Google OAuth itself cannot be tested using the local preview or a demo Firebase project.

Firebase setup follows [Firebase's Android guide](https://firebase.google.com/docs/android/setup) and [Google sign-in with Credential Manager](https://firebase.google.com/docs/auth/android/google-signin). Your Firebase project and its credentials are required; none are provisioned by this repository.

## Behavior and data

- **Family selector:** a shared selector above Today, Family and Insights lists every family you belong to. Switch from any tab without re-entering an invite. Selection is remembered separately for each signed-in account on each device. Existing memberships are discovered automatically from the family documents.
- **Today:** select any of the most recent 35 days, record one weighted status for each prayer, check general amals, or clear a record. Repeated saves replace the same deterministic document, rather than adding duplicate points.
- **Family:** create/join a private family, see each member with their role and daily progress, and copy/share an invitation. Admins can rename the family and add/edit/archive/delete amals. The five built-in salah and the built-in Al-Kahf/Ashura amals are protected from deletion; custom and regular amals can be deleted after confirmation. Deleted definitions move to immutable historical metadata, and their tracking logs remain available in reports. Maximum family membership is 30. Creating/joining another family selects it when membership is confirmed. Earlier memberships remain listed in the selector, so no invite code is needed to return.
- **Insights:** switch between calendar weeks (Monday–Sunday) and calendar months, navigate to older periods, and select everyone or an individual member. **Race** shows a podium, a points leaderboard with gaps and rank movement, period awards, a running-points chart and momentum against the previous period; equal scores share a rank. **Daily** picks a date and lists every member's amals for it, with each prayer's status and points, what was not recorded, and the day's champion. Tap a leaderboard name or a calendar day to open it. **Salah** ranks members by salah points and shows how each prayer was recorded; **Habits** has the activity calendar and per-amal totals. The checklist uses each amal's current schedule, plus anything recorded that day. The current period excludes future days. Scores include archived/deleted amal records. Reviews query the exact selected period and are not limited to the most recent 35 days. Switching families preserves the weekly/monthly mode and selected period, and resets the individual-member filter to all members of the new family. Category edits to existing definitions can reinterpret older records; deletion preserves the last definition.
- **Calendar:** Umm al-Qura conversion runs entirely on-device. Hijri dates may differ from local moon sightings, and the civil date changes at midnight rather than sunset. Special days are family-configured dates, not a remote religious-events service.
- **Privacy:** family members can read each other's tracking and weekly/monthly reviews. Private user documents remain accessible only to their owner. Google is the only account sign-in provider; local preview is not an account.

Firestore layout:

```text
users/{uid}                                 name, legacy/last-joined circleId (private)
circles/{circleId}                           name, owner, members, inviteCode
circles/{circleId}/profiles/{uid}            circle-visible display name
circles/{circleId}/amals/{amalId}             category, recurrence, active state
circles/{circleId}/retiredAmals/{amalId}      immutable deleted-amal definitions
circles/{circleId}/logs/{uid_date_amalId}     owner, date, status, validated score
invites/{randomCode}                         circleId, owner
```

Rules deny nonmember reads, allow members to add/edit/archive amal definitions while reserving individual amal deletion for owners, validate weighted scores, and require a submitted invite code when adding the current user to a circle. Invite documents cannot be listed. Circle creation and joining use atomic batches validated with `getAfter()`. Invitations are reusable bearer secrets: share only with intended members. This version does not include invite revocation, member removal, or account deletion workflows.

The app queries `circles` with `members array-contains currentUid` to discover joined families. It listens to the selected family and a 35-day daily-tracking window, with a separate bounded query for the selected review period. Historical classification metadata is also synchronized. New memberships are activated after Firestore confirms the join; local pending writes do not trigger reads of protected child data. Old family listeners are detached on selection changes, and generation tokens reject delayed snapshots or queued edits from a previous selection. Tracking and family-management dialogs are scoped to the selected family; loading/error states block edits until that family is ready. Firestore's Android persistence supports cached data and pending writes; actual sync requires a network connection. Tracking, amal edits and family renames apply locally at once and sync in the background, so they never wait on the network; a change the server rejects is rolled back with a message. Creating, joining and deleting still wait for the server. Small circles reduce reads, but usage depends on member count and activity. No paid messaging broker, Cloud Functions, or billing-enabled service is required. Firebase's free quotas still apply and cannot guarantee unlimited operation; monitor your project's usage. See [Firestore quotas](https://firebase.google.com/docs/firestore/quotas).

## On-device reminders

In Settings, enable local reminders and set **your local prayer times** in Fajr/Zuhr/Asr/Maghrib/Isha order, plus an evening nudge. Initial times are editable placeholders, not location-derived prayer times; update them as local prayer times change. All scheduling is on-device, with no FCM or remote push dependency.

- `AlarmManager` schedules each prayer and the daily tracking nudge. Exact alarms are used when Android grants special access, otherwise alarms are inexact.
- Android 13+ asks for notification permission after you save enabled reminders. Settings also links to Android's precise-alarm access screen.
- `WorkManager` periodically restores schedules. A boot/time/timezone/package-replaced receiver and app resume also restore schedules.
- A nudge is suppressed when today's locally observed tracking is complete for the selected family. No background Firestore polling is used. Sign-out disables reminders and cancels scheduled work.
- Android battery restrictions, force-stop, or denied permissions can delay or prevent notifications. WorkManager is periodic maintenance, not an exact prayer timer. See [Android's scheduling guidance](https://developer.android.com/develop/background-work/services/alarms).

## Verification

Unit tests exercise weekly/Hijri recurrence, archived amals, score weights, and member/date-scoped completion. Android lint checks platform integration.

Security rules tests run against the local Firestore emulator (Node 20+ and Java 21 recommended):

```sh
npm ci
npm run test:rules
```

Tests cover private reads, invite enumeration, correct and forged joins, atomic creation, admin restrictions, score validation, ownership, and private user profiles. The emulator uses project `demo-amaal-tracker` and does not access production data.

Emulator UI smoke checks (with the debug APK installed and `adb` on your PATH):

```sh
python3 tests/android_smoke.py
```

The smoke script records Fajr in local preview, checks family/reports and process-restart persistence, verifies local alarm scheduling and restoration after a reboot with the app in the active standby bucket, and saves captures to `docs/screenshots/`. Run it only on a disposable test emulator. Lint may report newer dependency versions; versions here are pinned for the verified SDK 35 build.

## Upgrade to 1.4.1

Version **1.4.1** (code **6**) starts new families with only the five salah amals and daily Quran Tilawat. These six IDs are protected from individual deletion; Quran Tilawat also retains its daily frequency and Tilawat category. Existing optional amals remain intact but owners can delete them, including Kahf, Ashura and Azkar.

Insights category legends, calendar filters and amal breakdowns now show active definitions or actual records in the selected period. Deleted/archived amals without records in that period no longer create empty sections. Historical records still appear with their labels.

Publish the updated `firestore.rules` before using this version, then sign with the existing release key.

## Upgrade to 1.4

Version **1.4.0** (version code **5**) adds detailed Insights with Overview, Salah and Habits sections:
- Daily stacked category bars with tap-to-open record details, including daily detail in monthly reviews.
- Equal-length current/previous period comparisons for points, records and active member-days. Longer months are trimmed for comparison only; full-period totals remain visible.
- Status distributions for each salah, member comparison cards, category-filtered activity calendars, and per-amal counts/points with archived/deleted labels.

An active member-day means one member recorded at least one deed that day. Missing records are not treated as missed deeds. Reports use saved amal definitions and current membership; past schedules and membership dates are not stored. Reviews now fetch the selected and previous periods, increasing reads compared with a single-period review. No new service or security-rule change is needed beyond the existing 1.3 rules.

Build and sign with `scripts/sign-release.sh`. UI checks are in `tests/android_insights_smoke.py` (disposable emulator only).

## Upgrade to 1.3

Publish `firestore.rules` in Firebase Console before using the new permissions, then run `scripts/sign-release.sh` with the existing keystore to install version **1.3.0** (version code **4**).

Every member can add, edit, activate and archive family amals. Individual amal deletion and family renaming remain owner-only; protected salah and occasion amals cannot be individually deleted.

Owners can choose **Delete family** in the Family tab and confirm permanent deletion for all members. The app locks new writes, deletes all logs, active/retired amals and profiles in pages, then removes the invitation and family document. Keep the app online until completion. If interrupted, select that family and tap **Finish deleting family**. Other joined families remain available. Historical `users.circleId` pointers may remain but do not grant access or affect membership discovery. Local preview deletion removes that family's local records, including when it is the last family.

## Upgrade to 1.2

Publish the updated `firestore.rules` in **Firebase Console → Firestore Database → Rules → Publish**. Parent-family reads now support the membership-filtered discovery query; unrestricted family listing remains denied. Existing memberships are discovered directly from `circles/{id}.members`, including families joined in earlier versions. No data migration or rejoining is needed.

Version **1.2.0** introduced multiple families with version code **3**. Run `scripts/sign-release.sh` with your existing release keystore and install the resulting APK as an update. The selector is shared by Today, Family and Insights, remembers the selection on each device, and keeps each family's data and permissions separate.

Run the multiple-family smoke checks on a disposable emulator with the debug APK installed:

```sh
python3 tests/android_multiple_families_smoke.py
```

This creates a second local-preview family, verifies independent prayer records, switches from each tab, checks that review periods are preserved and member filters reset, and verifies selection/records after restarting the app. It uses local preview, never the production Firebase project.

## Version 1.1 family management

Publish the updated `firestore.rules` in **Firebase Console → Firestore Database → Rules → Publish** before using family renaming, deletion and reviews. Existing data stays under the same `circles` paths, so families created by version 1.0 continue to work without migration. The rules add admin-only name updates and atomic deletion with immutable `retiredAmals` metadata. Do not delete old tracking data manually.

Version **1.1.0** introduced family management with version code **2**. Run the signing script with your existing release keystore and install the resulting APK as an update. A newly built unsigned APK still needs the signing script before sharing.

New Family UI smoke checks, using a disposable emulator with the debug APK installed:

```sh
python3 tests/android_family_smoke.py
```

These cover name editing/persistence, custom-amal deletion and cancellation, protected salah, retained historical scores, monthly/weekly navigation, and member selection.

## Sign a release from VS Code

With a complete JDK selected through `JAVA_HOME`, your Firebase configuration in `app/google-services.json`, and your existing `amaal-release.jks` in the project root, run:

```sh
./scripts/sign-release.sh
```

The script builds the unsigned release, aligns it, prompts for the signing password in your terminal, signs it, and verifies the resulting `app/build/outputs/apk/release/Amaal-Tracker-release.apk`. It stops if an earlier step fails. Do not skip directly to verification: that command requires the signed file to already exist.

The default key alias is `amaal`. Override `AMAAL_KEYSTORE`, `AMAAL_KEY_ALIAS`, or `AMAAL_BUILD_TOOLS` if your paths or alias differ. Keep the keystore and password backed up for future updates. Enable Google authentication and download the updated Firebase configuration, including its web OAuth client, before building an APK intended for shared-circle use.

The debug APK is for local evaluation. Configure release signing and your real Firebase project before distributing a production release.
