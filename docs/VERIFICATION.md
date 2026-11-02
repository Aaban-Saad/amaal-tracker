# Amaal Tracker tracking responsiveness verification — 2 November 2026

- Tracking, amal edits and family renames no longer wait for a server response or hold the shared `busy` state. They write to Firestore's local cache and sync in the background; a rejected write shows a message and is rolled back by Firestore.
- Debug build succeeded; 34 unit tests passed; lint completed with zero errors. `android_family_smoke.py` passed on an API 35 emulator, and six rapid checkbox taps in local preview each registered with the checkbox enabled.
- Local preview does not use Firestore, so the background-sync path was not exercised against live Firebase with a signed-in account.

# Amaal Tracker Insights redesign verification — 2 November 2026

- Built on version 1.4.2 (code 7), version not bumped. Debug build succeeded; lint completed with zero errors.
- 34 unit tests passed, including eight new tests for shared ranks, race status text, rank movement, streaks, tied day wins, awards and the daily member checklist.
- Android API 35 emulator/local preview: `android_insights_smoke.py`, `android_family_smoke.py` and `android_multiple_families_smoke.py` passed with no crash entries. `android_smoke.py` was not run; reminders were not changed. Screenshots saved as `insights-race.png` and `insights-daily.png` under `docs/screenshots/`.
- Local preview has one member, so podium, leaderboard, awards, the running-points chart and member filtering were checked by eye on a throwaway build seeded with four members. That seed is not part of the project.
- Firestore rules and queries were not changed. Live Firebase/Google authentication and release signing were not exercised.

# Amaal Tracker 1.4.1 verification — 31 October 2026

- Debug and unsigned release builds succeeded for version 1.4.1 (code 6); lint completed with zero errors.
- 26 unit tests passed, including six protected defaults and optional category visibility with active, archived and deleted definitions plus selected-period records.
- 18 Firestore emulator tests passed. Quran Tilawat is protected from deletion and category/frequency changes; former occasion defaults and Azkar support owner deletion while preserving history.
- Updated security rules must be published before installing this version. Existing optional definitions are retained for owners to manage; new families start with six definitions only.
- This patch was not device-tested; earlier 1.4.0 chart interaction checks remain below.

# Amaal Tracker 1.4.0 verification — 31 October 2026

- Debug and unsigned release APKs built successfully (version code 5).
- All 25 unit tests passed, including new equal-window comparison tests for partial weeks, shorter months, leap years/year boundaries, record deduplication/member isolation, member-days and zero baselines.
- Android lint completed with zero errors.
- Android API 35 emulator/local preview: checked overview metrics, period comparison, tappable daily record dialog, Salah status distribution, Habits category selection, per-amal breakdown, monthly calendar and monthly comparison. Screenshots saved as `insights-overview.png`, `insights-day-detail.png`, `insights-salah.png`, `insights-habits.png` and `insights-monthly-calendar.png` under `docs/screenshots/`. A first test swipe skipped a heading; smaller scroll steps fixed the locator. The longer UI run stopped after the first stages; remaining checks passed in a separate shorter run.
- Firestore rules were not changed. Reviews now read the current and previous periods through the existing member-scoped query.
- Live Firebase/Google authentication and release signing were not exercised. Use the existing signing script and key before sharing the release.

Earlier release verification follows.

# Amaal Tracker 1.3.0 verification — 31 October 2026

- Firestore emulator: 17 security tests pass. Added coverage for member editing/creation/archiving, owner-only deletion lock, denied writes and joins during deletion, owner cleanup of all four child collections, retrying the lock, and atomic invite/parent deletion. Existing protected-amal, isolation and history checks still pass.
- Final version 1.3.0 (code 4) debug and unsigned release APKs built successfully. All 19 unit tests pass; lint has zero errors. An incremental APK packaging attempt failed without a detailed cause; the diagnostic retry completed successfully. See reports under `app/build/reports/`.
- The new deletion confirmation and retry UI have not been exercised on a device in this update. Production Firebase operations and release signing were not performed.
- Publish the updated `firestore.rules` and sign the 1.3.0 APK with the existing key before distributing it. Deletion runs on the owner's device; interrupted cleanup requires selecting the family and choosing **Finish deleting family**.

Previous verification follows; its device screenshots describe version 1.2.0.

# Amaal Tracker 1.2.0 verification — 31 October 2026

- Debug and unsigned release APKs built successfully with Android SDK 35, Kotlin 2.4.21, and Android Studio's bundled JDK. Version code is 3 for upgrading existing installations.
- `testDebugUnitTest`: 19 tests passed with zero failures/errors. New coverage includes discovery of older memberships, saved selection, newly joined selection, revoked/invalid membership fallback, duplicate names, stale callbacks/queued edits across A→B→A switching, and waiting for new memberships to commit. Existing tracking, recurrence, review and deletion tests also pass.
- `lintDebug`: passed with zero errors. Advisory dependency and Gradle-version warnings remain.
- Firestore emulator: all 15 security tests passed. Membership-filtered queries return all joined families while unrestricted/foreign/anonymous listing stays denied. Changing a private user's legacy selection field does not grant membership or admin rights. Identical log IDs in two families keep independent scores. Existing admin/deletion/history rules tests also pass.
- Android 15/API 35 emulator, local preview: switched families from Today, Family and Insights; verified independent Fajr statuses/records; preserved monthly mode and the selected period; reset member selection on family changes; and verified selection and records across app process restart. The emulator's System UI stalled during one run; after recovery the remaining restart-persistence check passed on the final build.
- The earlier reminder scheduling/reboot checks remain in `tests/android_smoke.py`; reminder scheduling was not changed by this update. Nudge completion caching now follows the selected family.

Screenshots: `docs/screenshots/today-multiple.png`, `docs/screenshots/family-multiple.png`, and `docs/screenshots/insights-multiple.png`. The second family and its records were created through local-preview UI checks, not uploaded to Firebase.

Live Google OAuth and production Firestore operations have not been exercised against the owner's project. Publish the updated `firestore.rules` before using the new membership query. Existing family documents and memberships remain compatible; no migration or rejoining is needed. Sign the release with the existing keystore using `scripts/sign-release.sh`; the password is entered locally by the owner.
