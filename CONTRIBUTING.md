# Contributing to App Cloner

## Update workflow (every time)

1. `git checkout main`, `git pull`
2. New branch: `feature/<short-name>` (for example
   `feature/dark-mode-fix`). Never commit directly to `main`.
3. One small change, then test by building the app
   (`./gradlew assembleDebug`) and running it.
4. Update `CHANGELOG.md` under `[Unreleased]` (`Added` / `Fixed`).
5. Push the branch, open a pull request, check it, merge to `main`.
6. New version: bump `versionCode` / `versionName` in
   `app/build.gradle.kts`, tag (`v1.0.1` bug fix, `v1.1.0` new
   feature, `v2.0.0` breaking change), GitHub Release with the
   changelog.
7. Urgent bug: `hotfix/<name>` from `main`, fix, test, merge, tag.

## Code rules

- All code is written from scratch in this repo. Do not copy code
  from other repositories. Ideas may be studied elsewhere and then
  written here in your own words.
- Clean architecture: `ui` talks to `domain`, `data` implements it.
  Keep new features behind the `CloneEngine` interface so V2
  (sandbox mode) plugs in without rewriting screens.
- Meaningful names, clear comments, small functions. A screen that
  fails shows a plain-words error, never a crash.
- No network calls, no analytics SDKs, no third-party SDKs beyond
  AndroidX and Material. Keep the permission list short.
- No AI branding, ever: never add any AI name, credit or watermark
  anywhere in this repo — no AI name, no "AI created" / "AI
  generated" / "made with AI", no AI badge. If a change reintroduces
  such wording, remove it before merging.

## Screenshots

Screenshots in the README come only from the real app running on a
real phone (Redmi 13C). No stock images, no generic mockups, no
screenshots until the feature shown actually works on the device.
