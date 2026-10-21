# SQLite directory backend

This backend uses SQLite through Node.js 24's built-in node:sqlite module. It needs no Docker, MySQL, npm packages, or separate database server. SQLite is free and public domain: https://sqlite.org/copyright.html. Node API: https://nodejs.org/api/sqlite.html.

## Start

Double-click **Start Backend.cmd** in this folder. Alternatively, from the workspace root run:

    node backend/start.cjs

Keep the window open while using the backend. Press Ctrl+C to stop it. On the first run the backend creates a random private access token in backend/.env, creates backend/data/bar-members.sqlite, and imports all 2,594 members from the PDF's existing extraction. Later starts skip importing unchanged data. No database passwords are needed.

The local API address is http://127.0.0.1:8080. The SQLite file is stored on this PC. This is not cloud hosting: the PC must be on for clients to connect. The API binds only to this PC by default.

## Android

Install the updated app-release.apk. Connect the phone by USB with USB debugging enabled and run from the workspace root:

    .\.tools\android-sdk\platform-tools\adb.exe reverse tcp:8080 tcp:8080

In Android open **Backup / Restore ? Download from SQLite backend**. Enter http://127.0.0.1:8080 and the API_TOKEN value from backend/.env, then Download. The token is not saved on the phone. It grants read access to the directory; share it only with authorized users.

For remote use, host this Node service on a computer/server with persistent storage and an HTTPS reverse proxy, and enter its HTTPS URL. Outside USB/emulator connections, Android requires HTTPS. No public hosting is configured. MySQL credentials and Docker are not needed.

## Data and local edits

The schema indexes names, enrollment dates, cell numbers, and member IDs. Enrollment dates are validated ISO YYYY-MM-DD strings; the original PDF date is retained separately. The 101 missing/invalid dates are flagged for review. Names, member numbers, phone numbers, parent/residence, chamber, biometric, portrait asset paths and PDF source pages are preserved. Unknown fields remain NULL. Supplied portraits stay bundled in Android.

The API serves at most 100 members per page and checks the dataset revision across pages. Android validates all pages before merging into its offline Room database. Local campaign status, called flags, references, group affiliation, photos, and profile edits are preserved. Existing local members absent from the server remain. Local campaign edits are not uploaded; this is a directory download service, not shared campaign synchronization.

## Import and backup

Run node backend/import.cjs to rebuild the source directory from the current PDF extraction. It changes only backend source records, in one transaction. The original PDF is not reparsed at every launch. PDF changes must first be extracted/validated using the project's PDF import workflow. Generated directory.json and members.sql also carry the source PDF SHA-256 fingerprint.

For a backend backup, stop the service and copy backend/data/bar-members.sqlite and backend/.env to a private backup location. Keep the token private. Use Android's normal backup feature for local campaign edits.

## Checks

Run node backend/test.cjs. Tests cover a real in-memory SQLite database, the SQL export, all 2,594 members downloaded over HTTP, indexed cursor queries, authentication, page limits, revision mismatches, date conversion, and rollback after a failed import. Android physical-device crash reproduction remains a separate check requiring the affected device/logs.
