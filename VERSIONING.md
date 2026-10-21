# Versions and GitHub

This project uses Git commits for source history, annotated version tags for release snapshots, and CHANGELOG.md for human-readable descriptions.

The first recorded version is 1.0.0. Earlier references to version 4 referred to the database schema or an untracked earlier state; they are not recoverable Git release tags. The current Android database schema is 7 so updates preserve existing data.

## Private local files

.gitignore excludes signing keys, .env credentials, local databases, the supplied PDF, all portraits (including credit photos), extracted member lists, generated exports, build tools and app packages. Keep these files locally. Do not force-add them to a public repository.

A fresh checkout also needs the two credit portraits restored locally at their expected Android resource and desktop asset paths; these are intentionally excluded from the public source snapshot.

A fresh checkout needs local.properties pointing to an Android SDK, local signing settings for signed Android releases, and the private directory files restored separately for the built-in list/backend tests. App source itself is tracked. Existing installs keep their own local database.

Some source tests are in scripts/. Run them from the project root with Node 24 or newer. Tests requiring private directory data need those local files present.

## Record a change

1. Make the change and run relevant checks.
2. Add a clear entry to CHANGELOG.md describing behavior, validation and limitations.
3. Review git diff and stage the intended source files.
4. Commit with a descriptive title and optional body.
5. For a release, update app version values, build and verify artifacts, then create an annotated tag matching the version (for example v1.1.0).
6. Push commits and the selected tag to the configured GitHub repository. Create a GitHub Release using that changelog entry for its description.

APK files and Windows packages contain the bundled member directory. Keep releases containing this data private; source history can be shared separately after review. Do not publish the signing keys or sender credentials in any release.

The updated Windows build is desktop/dist/win-unpacked/Bar Member Manager.exe; its adjacent files and resources/app.asar are required. Old Setup/Portable installers are not automatically rebuilt by source packaging.

## GitHub connection

Repository: https://github.com/feedham/Codex (public). The source snapshot and version descriptions are managed here; private application data and binaries remain local.

The connected GitHub app can upload source. This workspace currently blocks writes to local Git metadata, so configuring origin/fetch in this working folder must be completed in your own PowerShell terminal:

```powershell
.\scripts\Connect-GitHub.ps1
```

The helper defaults to this repository, links the local folder to its remote history without changing working files, then commits/pushes reviewed source changes. It uses normal Git authentication and never force-pushes. Review git status before running it if you have unrelated local edits. Published release tags are snapshots; do not reuse a tag for a different version.
