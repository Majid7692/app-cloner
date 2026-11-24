# Changelog

All notable changes to App Cloner are listed here. The format follows
Keep a Changelog, and the project uses Semantic Versioning.

## [Unreleased]

## [1.0.3] — 2026-11-23

### Fixed
- Main screen showed the profile switch ON while the profile was
  "not set up": the profile handle was the first other user on the
  phone, which on Xiaomi phones is the Dual Apps user (user 999),
  not a work profile. The handle now only counts when this app
  owns a managed profile, and the switch stays OFF and disabled
  until a profile exists.
- The profile switch fired on its own every launch: the first
  value the screen showed was ON, and setting the switch
  programmatically runs its listener, which tried to switch a
  profile that did not exist — and could silently turn a quiet
  profile back ON. Programmatic changes are now ignored by the
  listener, and the switch starts OFF.
- The picker added the clone and said "Clone created" before the
  install had even finished. The clone now appears only when the
  profile answers that the install worked; until then the screen
  says it is installing.
- Setup said the profile was created on the system's OK answer
  alone, and crashed if the phone offered no provisioning screen
  at all. Success is now checked against the real profile, the
  launch is guarded, and the error names the two usual causes on
  Xiaomi phones (Mi account not signed in, a profile already
  exists).
- The battery step ticked itself done when its button was tapped,
  whether or not the setting was changed. It is now only shown
  when the phone really lets App Cloner run in the background.
- The Limits screen and the "clone is not ready" error promised a
  "Re-clone" button that did not exist. The detail screen now has
  one: it installs the same app into the profile again, keeping
  its name and place in the list.
- Freeze and remove said "started" with no profile behind them.
  Both now refuse at once when no profile exists.
- Picking a big photo as a clone icon decoded it at full size
  first, which could run the app out of memory. Photos are now
  downsampled while decoding.

### Added
- Search in the app picker: type a name and the list narrows to
  matches. Long app lists without search are the most common
  complaint about other cloners.
- One "next step" line on the main screen, from the real state:
  set up the profile, clone an app, test a notification, or the
  2-clone limit. Buttons that cannot do anything yet are
  switched off instead of failing after a tap.
- Clone names are numbered by default ("WhatsApp 2"), so the
  list and the home screen shortcut are easy to tell from the
  original — the convention other cloners use.
- The detail screen shows when a clone was created, and the
  Limits screen names the Xiaomi Dual Apps clash plainly.

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
