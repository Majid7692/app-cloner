# Changelog

All notable changes to App Cloner are listed here. The format follows
Keep a Changelog, and the project uses Semantic Versioning.

## [Unreleased]

## [1.0.2] — 2026-11-23

### Fixed
- Launch crash on Redmi 13C (root cause, from the phone's own "View
  summary"): `NoClassDefFoundError: androidx.startup.R$string`,
  thrown in `InitializationProvider.onCreate` — before
  `Application.onCreate`, so the v1.0.1 crash reporter never ran
  and its screen could never appear. The manual aapt2 build only
  generated R.java for the app package, so no library R class was
  in the dex at all. Fix: the startup provider is removed (V1
  never uses ProcessLifecycleOwner), and the build now generates
  every library's R class from the linked R.java + each AAR's
  R.txt (`gen-library-r.py`), with a build-time check that every
  R class the dex references exists in it (`verify-dex-r.py`).
  Removing the provider alone would only have moved the crash to
  AppCompatActivity's first R access — the generated R classes
  close the whole failure class.
- Crash screen hardened: it now extends plain `android.app.Activity`
  under a plain framework theme instead of AppCompatActivity, so it
  opens even if an AppCompat/Material library is the thing that
  broke. `Application.onCreate` can no longer throw while building
  the container — a failure there is saved as a crash report and
  the main screen shows it on the same launch.

## [1.0.1] — 2026-11-23

### Added
- Crash reporter: when the app closes because of an error, the exact
  error is saved on the phone and shown on the next launch in plain
  text, with Share and Dismiss. No permission, no network, nothing
  leaves the phone unless the user shares it. Added because v1.0.0
  closed back to the home screen on first open on a Redmi 13C
  (Android 14) while every check of the build passed — the saved
  error names the real cause, so the next fix targets it instead of
  guessing.

## [1.0.0] — 2026-11-23

### Added
- Work-profile cloning (V1): create a managed work profile from the
  setup wizard, clone apps into it, and run a second copy of an app
  with its own login, data and notifications.
- Clone list with briefcase-badged clones, freeze / unfreeze, and a
  whole-profile on/off switch.
- Per-clone rename, custom icon, and home screen shortcut.
- Clone list backup and restore as a local JSON file.
- File shuttle between profiles using FileProvider content URIs only.
- Limits screen with honest limits in plain words.
- Setup wizard: work profile, organisation warning in plain words,
  battery whitelist guide, notification permission (Android 13+).
- Dark mode (DayNight theme), big buttons, plain-language errors.
- targetSdk 36, minSdk 28, no native libraries (16 KB page size
  compatible by construction).

### Fixed
- Nothing yet — first release.
