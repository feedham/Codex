# Changelog

## 1.0.0 - 2026-10-21

First source version recorded in Git. Earlier edits were not tracked as individual versions.

- Android, Windows and Chrome use local email/password accounts without phone or Gmail OTP.
- Add Forgot password using a saved recovery code. Reset preserves profile/member data and replaces the recovery code.
- Add right-side show/hide eye buttons to sign-in, signup and reset password fields.
- Existing accounts enroll in recovery after a successful password sign-in; recovery codes stay private to the local account/device.
- Preserve the member directory, single combined search, enrollment sorting and insights, advocate profiles, references, credits and backups.
- Include matching Call/WhatsApp buttons, local WhatsApp sharing and contact export up to 256 recipients.
- Display My Support Status throughout profiles and insight drill-downs.
- Keep SQLite directory backend support without Docker.
- Place Android navigation in the second row, below the app logo and title.
- Add offline name-based gender suggestions to all apps, with explicit review and editable saved corrections. Ambiguous names stay unassigned. The small classifier is not a verified demographic source.

Validation: Android debug/release builds and release checks, local signup/sign-in/reset checks, real PBKDF2 recovery tests, migration preservation checks and insight drill-down checks passed. Browser layout and show/hide buttons inspected. No real account password was changed during testing.

Private directory records, member portraits, credentials, signing keys, installed tools and app binaries are excluded from source control. Local working copies are retained.
