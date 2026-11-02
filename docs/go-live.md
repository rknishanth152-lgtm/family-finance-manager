# Go-live, backups and recovery

How the app is set up for the family, how records are kept safe, and what to do when
something goes wrong (PRD 10.4, 12.5–12.7, 13, 16).

## Where the records live

| Copy | Where | Who can read it |
|---|---|---|
| Live records | Firebase project (production), Mumbai region | Only signed-in family members (`firebase/firestore.rules`) |
| Each phone's copy | The app's private storage, kept in sync | Only the app; not copied by Android's device backup |
| Backup files | Wherever you save them (e.g. Google Drive) | Anyone who has the file, so keep it private |
| Old app's file | `incomeexpense …db`, kept as it is | Never committed or uploaded by this project |

## Who can start a family

Only Google accounts on the **founders** list can create a family; everyone else can only
join by invitation. A stranger who signs in to the project cannot start one of their own.

To allow an account: Firebase console → Firestore Database → Data → start collection
`founders` → document ID = the Google email in lower case (e.g. `name@gmail.com`) → no
fields → Save. The app can never read or change this list.

## Backups (PRD 12.5–12.7)

- **Make one:** More → Backup and restore → Create backup, then pick where to save it.
  The app writes the file, reads it back and checks it before saying "saved".
- **When:** right after the first import, and then about once a month, and always before
  installing a new version of the app (PRD 16.4).
- **Restore:** More → Backup and restore → Choose backup file. The app checks the file
  (version, damage, missing links, totals) and shows what it would add. It only **adds
  missing records**; nothing already in the app is changed or deleted. Restored records
  are created in the name of the person restoring; dates, amounts and text are kept.

## Signing key (PRD 16.2, 16.3)

The release key proves updates come from you. **If it is lost, a new version cannot be
installed over the old one** (the family would have to uninstall, sign in again and the
cloud data would come back, but it is avoidable).

- Keep `family-finance-release.jks` and its password **outside** this folder.
- Keep two copies: e.g. your Google Drive and a USB stick, with the password in your
  password manager. Never commit it (git ignores `*.jks` and `keystore.properties`).

## Account recovery (PRD 10.4)

The app has no passwords of its own; each person signs in with their Google account.

| Situation | What to do |
|---|---|
| Phone lost or replaced | Install the app on the new phone, sign in with the same Google account. Everything comes back from the cloud. Changes made on the old phone while it was offline and never synced are lost, so sync before switching when possible. |
| Forgot the Google password | Recover it at accounts.google.com/signin/recovery (needs the phone number or recovery email on that account). Then sign in to the app as usual. |
| Google account lost for good | The admin removes that member (More → Family members) and invites the person's new Google account. Their old entries stay. |
| The admin's account is lost | Another member cannot become admin from the app. The project owner adds an admin in the Firebase console (workspaces → family → members → set `role` to `admin`). |
| Cloud project unusable | Create a new project, install the app pointed at it, then restore the latest backup file. |

## Updating the app (PRD 16.3, 16.4)

1. Make a backup on one phone.
2. Keep the previous signed APK.
3. Build the new version with the **same** key and a higher `versionCode`.
4. Install it over the old one (data stays). If something is wrong, reinstall the
   previous APK; the cloud records are unaffected.

## Go-live steps (Phase 6)

1. **Rehearsal (owner's phone):** release app on the production project, the owner's own
   Google account on the founders list, a trial family, import the old app's file, check
   totals, try export, backup and restore.
2. **Remove the trial family** before the real go-live:
   `firebase firestore:delete --recursive workspaces/<trial family id> --project <prod>`
   (also delete that person's `users/<uid>` pointer).
3. **Real go-live (father's phone):** his Google account on the founders list, sign in,
   create the family, import, check totals against the old app, make the first backup,
   invite the mother. Keep the old app until both phones show the same totals (PRD 13.3).

The import's expected totals and the old file's layout are in the project notes; the app
shows its own report before anything is written.
