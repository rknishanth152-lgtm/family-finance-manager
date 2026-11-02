# Sync, offline use and conflicts

How the two phones share one family's records (PRD 4.3, 4.5, 9.2–9.4, 11).

## Storage

- Each family is one Firestore **workspace**. Both parents sign in with their own Google
  account and are **members** of it; the founder is the **admin** (can invite and remove).
- Firestore's **persistent cache** is the phone's copy. Every screen reads from it, so
  records open without internet, and changes made offline are kept across restarts and
  sent when the connection returns.
- Money is stored as whole **paise** (integers) and dates as plain `YYYY-MM-DD` text, so
  syncing can never introduce rounding errors or shift a date across time zones.
- Records are never erased: deleting sets `deleted = true`, so the deletion itself syncs
  to the other phone and the history stays.

## What the sync indicator means (Home, top right)

| Shown | Meaning |
|---|---|
| Synced | Online, and the cloud has confirmed every change made on this phone |
| Syncing… | Online, sending changes or checking for new ones |
| Waiting to sync | Offline, with changes saved on this phone that the cloud has not received yet |
| Offline | No internet; nothing waiting |
| Sync problem | The cloud refused a change (for example, the person was removed from the family) |

The app never shows "Synced" until the cloud has confirmed the change (PRD 4.5).

## Conflict policy (PRD 11.5)

1. **Different entries never conflict.** Each transaction is its own record with a stable
   ID, so the two phones can add and edit different entries freely, online or offline.
2. **A transfer is one record**, so its two sides (money out of one account, into the
   other) always change together and can never get out of step (PRD 9.2).
3. **Every save is logged.** Each save of a transaction also writes an entry to the
   family's edit log: who saved it, when, the version they started from, and the full
   saved content. The log is append-only (the security rules forbid changing or deleting
   entries).
4. **Same entry edited on both phones without seeing each other** (typically both
   offline): both saves start from the same version. The cloud keeps the one that arrived
   last as the current record, but **both versions stay in the edit log**, and the app
   shows *"Changed on both phones"* on Home and on that entry, listing each version with
   who saved it and when.
5. **Resolving it:** the person checking either confirms *"The version shown is correct"*
   or taps *"Use this version"* on the other one. Either way a new save is made after
   seeing both, which closes the conflict on both phones.
6. **Deleted on one phone, edited on the other:** handled the same way; the edited version
   can be brought back with *"Use this version"*.

Nothing is ever silently lost: the worst case is a notice asking which version is right.

## Testing

| What | Where | How to run |
|---|---|---|
| Who can read/write what | `firebase/test/rules.test.js` (22 tests) | `cd firebase && npm install && npm run test:rules` |
| Record ↔ cloud conversion, sync status, conflict rules | `app/src/test/…` (unit tests) | `gradlew testDebugUnitTest` |
| Two phones against a local Firebase | `app/src/androidTest/…/FirebaseSyncTest.kt` | see below |

Two-phone tests need a phone or emulator plus the local Firebase emulator (never the real
project). With the phone connected:

```
adb reverse tcp:8080 tcp:8080
adb reverse tcp:9099 tcp:9099
cd firebase
firebase emulators:exec --project demo-family-finance --only auth,firestore "..\gradlew.bat -p .. connectedDebugAndroidTest"
```

Without the emulator running, `FirebaseSyncTest` is skipped, so `connectedDebugAndroidTest`
still works on its own for the screen tests.

Known test-setup limit: when all six sync tests run in one process over USB, a late test
occasionally stalls on a network call (leftover connections from earlier tests share the
`adb reverse` tunnel). Each test passes reliably in its own process, so for a clean result
run them one per instrumentation run (a small local script loops over the test names). The
standard fix is AndroidX Test Orchestrator, which runs every test in a fresh process.
