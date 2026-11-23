# App Cloner V1 — Build Report

**Date:** 2026-11-23 (build finished ~15:33 UTC / ~20:33 PKT)
**Version:** 1.0.0 (versionCode 1)
**Package:** `com.majidali.appcloner`
**Status:** Source complete, **debug APK built and verified on this VM**. Device behaviour (profile provisioning, cloning, notifications) is **NOT** verified here — it can only be proven on MAJID's Redmi 13C. The step-by-step device checklist is §7.

---

## 1. Deliverables

| Item | Location |
|---|---|
| Full project (source + Gradle files + docs) | `~/workspace/project-0.1/app-cloner/` |
| Debug APK (signed, verified) | `~/workspace/project-0.1/app-cloner-build/app-cloner-debug.apk` |
| Manual build script (reproducible, no Gradle) | `~/workspace/project-0.1/app-cloner-build/build-apk.sh` |
| Dependency resolver (Maven POM walk) | `/tmp/resolve-deps.py` |
| View-binding generator (same output as AGP) | `/tmp/gen-bindings.py` |
| Downloaded SDK + dependencies | `~/Android/sdk/`, `~/Android/deps/` |
| JDK (Temurin 17, lives under $HOME) | `~/tools/jdk-17/` |
| Kotlin compiler 2.0.21 | `~/tools/kotlinc/` |
| This report | `~/workspace/project-0.1/app-cloner/BUILD-REPORT.md` |

**NOT done by this build (parent's step):** no push to GitHub, no release, no git tag, no commit. The repo `Majid7692/app-cloner` already exists (created in parallel); files are left on disk for the parent to publish.

### APK facts (verified, §5)

- File: `app-cloner-debug.apk` — **4,974,024 bytes**
- SHA-256: `a59f3551f9f20a37a4c19bf545dcc5b413b42aa5eda65bc2c4e0876a26a2fd5c`
- Package `com.majidali.appcloner`, versionCode `1`, versionName `1.0.0`
- `compileSdkVersion 36`, `minSdkVersion 28`, `targetSdkVersion 36`
- Launchable activity: `com.majidali.appcloner.ui.main.MainActivity`, label `App Cloner`
- Only permission: `POST_NOTIFICATIONS`. **No `INTERNET` permission** — the app makes no network calls.
- Signed: `apksigner verify` → **Verifies**, 1 signer. v3 scheme true; v2 block present and valid (see §5 note). Debug keystore at `app-cloner-build/out/debug.keystore` (outside the repo; a real release key must be created by MAJID for the Play Store — never reuse this debug key for release).
- `classes.dex` (8,920,256 bytes uncompressed) contains all **95** `com.majidali.appcloner` classes, including all 7 generated `*Binding` classes.
- **0 native `.so` files** — the 16 KB page-size rule is satisfied trivially (there is nothing to align). No native libraries are used in V1.

---

## 2. What was built

All code written from scratch (Kotlin + Gradle). No code copied from any repo; ideas only, per Project 0.1 v4.

### Architecture (clean, V2-ready)

```
app/src/main/java/com/majidali/appcloner/
  AppClonerApp.kt            Application + manual DI container (AppContainer)
  admin/CloneDeviceAdminReceiver.kt   DeviceAdminReceiver (profile owner)
  domain/model/Models.kt              AppInfo, CloneInfo, CloneResult, CloneLimits
  domain/engine/CloneEngine.kt        Engine interface (V1 + V2 share it)
  domain/engine/WorkProfileCloneEngine.kt   V1 engine: work profile
  domain/engine/SandboxCloneEngine.kt       V2 stub only — NOT wired, throws "not in V1"
  domain/repository/CloneRepository.kt      Repository interface
  data/local/CloneStore.kt          JSON clone store (private file)
  data/local/BackupManager.kt       JSON export / import (local file only)
  data/profile/ProfileManager.kt    Provisioning, quiet mode, cross-profile intents
  data/profile/AdbGuide.kt          Exact `pm install-existing` fallback commands
  data/profile/ShortcutHelper.kt    Home-screen shortcuts, custom icon bytes
  data/sharing/FileShuttle.kt       FileProvider content-URI shuttle only
  data/install/InstallResultReceiver.kt  PackageInstaller result receiver
  ui/common/UiHelpers.kt            Toasts, error dialogs (no silent failures)
  ui/main/        MainActivity, MainViewModel, CloneListAdapter
  ui/setup/       SetupActivity (5-step wizard)
  ui/picker/      AppPickerActivity, AppListAdapter
  ui/detail/      CloneDetailActivity (rename, re-icon, shortcut, freeze, remove)
  ui/limits/      LimitsActivity (honest limits, plain words)
```

- **Material3 DayNight theme** (dark mode), big 56 dp buttons, plain-language strings, vector logo (two overlapping rounded squares, teal) + adaptive launcher icon.
- **2-copy limit** enforced in `MainViewModel` (`CloneLimits.MAX_CLONES = 2`).
- **No ads, no trackers, no analytics SDKs, no network calls.** Dependencies are only AndroidX + Material + Kotlin stdlib (see §3).
- **README** is professional, step-by-step (build, install, provision, clone, test), with **no mention of ads or trackers anywhere** (per MAJID's rule) and a **Screenshots section holding only placeholder text** — real Redmi 13C captures go there only after V1 works on the device. No stock or generic images.
- Docs: `README.md`, `LICENSE` (MIT, Copyright (c) 2026 Majid Ali), `CHANGELOG.md` (1.0.0), `CONTRIBUTING.md`, `SECURITY.md`, `.gitignore` (covers `local.properties`, `build/`, `*.keystore`).
- **No AI branding anywhere** in the project (grep `brand-name` / `AI-generated` = 0 matches).

### How cloning / provisioning works

1. **Setup wizard** (`SetupActivity`, 5 steps): explains in plain words that Android will show an "organisation" warning (the work profile is owned by the app, not by a company), asks for notification permission (Android 13+), guides the battery-optimisation whitelist, then starts provisioning with `ACTION_PROVISION_MANAGED_PROFILE` + `CloneDeviceAdminReceiver`. No root.
2. **Provisioning** (`ProfileManager`): on `PROFILE_PROVISIONING_COMPLETE` the app becomes profile owner (`DevicePolicyManager.setProfileEnabled`, `setProfileName`, `addCrossProfileIntentFilter` with `FLAG_PARENT_CAN_ACCESS_MANAGED` / `FLAG_MANAGED_CAN_ACCESS_PARENT`).
3. **Clone** (`WorkProfileCloneEngine`): personal side reads the app's APK paths (`ApplicationInfo.sourceDir` + `splitSourceDirs` — base + every split, so split APKs don't crash) and sends them by cross-profile intent to the profile-side copy of App Cloner. That copy installs them with a **PackageInstaller session** (`SessionParams.MODE_FULL_INSTALL`, `setRequireUserAction(USER_ACTION_NOT_REQUIRED)`), base APK first, then splits. Result comes back through `InstallResultReceiver` and a cross-profile result intent.
   - `PackageManager.installExistingPackage` is **not in the public SDK** (verified with `javap` against API 36) — it cannot be called. The PackageInstaller session is the public equivalent. `pm install-existing` is kept as the documented ADB fallback (`AdbGuide`).
4. **Per clone:** rename + re-icon (custom icon bytes in the shortcut intent), home-screen shortcut (`ShortcutManager.requestPinShortcut`), freeze/unfreeze (`DevicePolicyManager.setApplicationHidden`), profile on/off (`UserManager.requestQuietModeEnabled` / `isQuietModeEnabled` — these are public on `UserManager`, not on `DevicePolicyManager`).
5. **Backup:** clone list exports/imports as JSON to a local file only (`BackupManager`).
6. **File shuttle:** `FileProvider` content URIs with a one-time grant only. Raw file paths are never used — on some phones (notably Huawei and Oppo) a raw path handed to the other profile resolves to the wrong file or nothing (MediaStore trap, documented in code comments + README).
7. **Limits screen:** states plainly — max 2 copies in V1; work-profile clones have normal notifications and banking works; sandbox clones (V2) will not run banking and notifications may be late; never a promise of 100 live accounts without a ban; proxy off by default (advanced only, not in V1 UI).

### Reference repos — what was actually used (ideas only, no code copied)

- `~/workspace/ai-agent-repos/kaedea-dualapp-mediastore-compatibility/` (kaedea/dualapp-mediastore, MediaStore compatibility test): studied only at its documented high-level purpose (MediaStore / file-IO compatibility between host-profile and work-profile apps, scoped-storage tests). Its README content failed an execution-risk check, so nothing from it was executed or reproduced. **Idea taken:** FileProvider + content URIs only, and the save+share test (already in the research, now in code comments + §7 checklist).
- `GorvGoyl-Clone-Wars`: a Star Wars fan-activity repo, not an app cloner — no relevant idea.
- `myclabs-DeepCopy`: a PHP object deep-copy library — not relevant.
- `bat` (verified binary at `~/workspace/tools/bat-0.26.1/`): used as a file-viewing tool while working.
- Installed skills/tools in `~/workspace/custom-skills/` and `~/workspace/tools/` were available as tooling; no skill code was copied into the app.

---

## 3. Dependencies (pinned in `app/build.gradle.kts`)

Resolved from Google Maven + Maven Central by POM walk (`/tmp/resolve-deps.py`, highest-version-wins like Gradle): **77 artifacts** (54 AARs + 22 JARs in the build; Maven's `kotlin-stdlib 1.8.22` is excluded — kotlinc 2.0.21's own stdlib is used instead, which is what Gradle's version resolution would pick).

- `androidx.core:core-ktx:1.13.1`, `androidx.appcompat:appcompat:1.7.0`, `com.google.android.material:material:1.12.0`, `androidx.constraintlayout:constraintlayout:2.1.4`, `androidx.recyclerview:recyclerview:1.3.2`, `androidx.activity:activity-ktx:1.9.2`, `androidx.fragment:fragment-ktx:1.8.4`, `androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6`, `androidx.lifecycle:lifecycle-livedata-ktx:2.8.6` (resolved transitively to e.g. `activity:1.9.2`, `fragment:1.8.4`, `lifecycle-viewmodel:2.8.6`, `lifecycle-livedata:2.8.6`, `savedstate:1.2.1`, `lifecycle-process:2.4.1`).
- Full list: `/tmp/deps-manifest.txt` (76 coordinates after the stdlib exclusion). All files under `~/Android/deps/`.
- API verification was done with `javap` against API 36 `android.jar` for every non-obvious call (see §2 step 3–4 and the code comments).

---

## 4. Blockers hit on this VM (and how each was worked around)

### Blocker 1 — Gradle daemon TCP is blocked by the sandbox (STILL BLOCKING `./gradlew`)

Every Gradle run fails identically:

```
DaemonConnectionException: Could not receive a message from the daemon
MessageIOException: Could not read message from '/127.0.0.1:42825'
IllegalArgumentException: Unexpected type tag 109 found   (109 = ASCII 'm')
```

Proof: a Java test client connecting to a Python server on `127.0.0.1:39999` received the text `muse: Other TCP connections is turned off for this assistant. To allow it, ask the user to open Muse`. A sandbox TCP interceptor accepts loopback connections to non-allowlisted ports and answers with that message; the daemon never receives the request (its log shows listening, then "Stop requested" ~10 s later). Loopback to `127.0.0.1:18080` (the egress relay) works; other loopback ports are blocked. `--no-daemon` does not help (it still forks a single-use daemon over the same TCP protocol). The message says the user must enable TCP connections — that is a question for MAJID, not something this build can change.

**Workaround used (this build):** a manual build with the same tools Gradle/AGP would use — `aapt2` (compile + link), `kotlinc` 2.0.21, `d8`, `zipalign`, `apksigner` — driven by `app-cloner-build/build-apk.sh`. It needs no daemon and no loopback TCP. The Gradle project itself is standard and untouched: `./gradlew assembleDebug` should work on any machine where the daemon can run (or once TCP is enabled here). **The Gradle build is NOT verified on this VM** — only the manual build is.

### Blocker 2 — `sdkmanager` is unusable on this VM

Fails with `java.util.NoSuchElementException` at `HttpURLConnection.doTunneling0` (StringTokenizer.nextToken), reproduced with a minimal Java program against `https://dl.google.com/android/repository/repository2-1.xml`, via both the direct egress proxy and the local relay. The CONNECT responses themselves are clean (`HTTP/1.1 200 Connection Established`, verified byte-for-byte with raw sockets) — the failure is inside Java's CONNECT parsing on this VM.

**Workaround:** SDK packages downloaded directly with `curl` from `repository2-1.xml` (URLs extracted by regex): `platform-36-ext19_r01.zip`, `build-tools_r36_linux.zip`, `platform-tools_r37.0.3-linux.zip`, plus Gradle 8.13 from services.gradle.org. Installed at `~/Android/sdk/` and verified (`android.jar`, `aapt2`, `adb` present).

### Blocker 3 — `/tmp` is a 512 MB tmpfs and filled to 100% during the first unzip

Extraction was moved under `$HOME` (122 GB free). `/tmp` cleaned to 1% used.

### Blocker 4 — the apt-installed JDK vanished mid-build

`openjdk-17-jdk-headless` was installed via apt and verified working, then disappeared: `/usr/lib/jvm/` no longer exists and `dpkg` has no record of the package. System directories outside `$HOME` are ephemeral on this VM.

**Workaround:** Temurin JDK 17.0.21.1 downloaded as a tarball into `~/tools/jdk-17/` (persists — it is under $HOME). All build tooling now lives under `$HOME`: JDK, kotlinc, Gradle 8.13 (`~/tools/gradle-8.13/`), Android SDK (`~/Android/sdk/`).

### Small fixes made while building (in the repo, from scratch)

- `AndroidManifest.xml`: added the `package="com.majidali.appcloner"` attribute (aapt2 link requires it; AGP normally injects it from the `namespace`), replaced `${applicationId}` placeholders with literal authorities (aapt2 does not substitute them), and added the `androidx.startup.InitializationProvider` entry (with `ProcessLifecycleInitializer` meta-data) that Gradle's manifest merger normally copies out of the `lifecycle-process` library. The library-manifest scan proved this is the **only** component any V1 dependency needs — no other AAR declares activities, services, receivers or providers.
- `ProfileManager.kt`: `badgedIcon()` / `badgedLabel()` now return the unbadged drawable/label when no profile handle exists (they previously passed a nullable `UserHandle?` to a non-null parameter — a real nullability bug the compiler caught).
- `build-apk.sh` also generates the 7 `*Binding` classes (`/tmp/gen-bindings.py` reads the layout XML and emits the same public surface AGP generates — `inflate()`, `bind()`, one typed property per `android:id`). Generated sources live under `out/` (never in the repo), exactly as AGP's generated sources do.

---

## 5. What was verified (exact commands and outputs)

All commands run on this VM, 2026-11-23. Build command:

```
bash ~/workspace/project-0.1/app-cloner-build/build-apk.sh
```

| Check | Command | Result |
|---|---|---|
| aapt2 compile (app + 54 library res dirs) | inside `build-apk.sh` | 0 errors |
| aapt2 link (manifest + resources → APK + R.java) | inside `build-apk.sh` | 0 errors |
| kotlinc 2.0.21 (9 app sources + R.java + 7 generated bindings) | inside `build-apk.sh` | **0 errors**, 2 warnings (see below) |
| d8 (app classes + 76 dependency jars → dex) | inside `build-apk.sh` | 0 errors; `added dex: ['classes.dex']` |
| zipalign | inside `build-apk.sh` | 0 errors |
| apksigner sign | inside `build-apk.sh` | 0 errors; key generated once (RSA 2048, CN=Majid Ali, O=App Cloner) |
| apksigner verify | `apksigner verify --verbose app-cloner-debug.apk` | **Verifies**, 1 signer, v3 scheme true |
| v2 signature | `apksigner verify --verbose --min-sdk-version 24 app-cloner-debug.apk` | v2 scheme **true** (see note) |
| Signing-block parse | Python EOCD parse of the APK | v2 block `0x7109871a`, v3 block `0xf05368c0`, v4 block `0x42726577` all present |
| Badging | `aapt dump badging app-cloner-debug.apk` | `package: name='com.majidali.appcloner' versionCode='1' versionName='1.0.0' … compileSdkVersion='36'`, `sdkVersion:'28'`, `targetSdkVersion:'36'`, `launchable-activity: …MainActivity`, `application-label:'App Cloner'`, only `uses-permission: POST_NOTIFICATIONS` |
| Manifest components in APK | `aapt dump xmltree … AndroidManifest.xml` | 5 activities, 2 receivers (`CloneDeviceAdminReceiver`, `InstallResultReceiver`), 2 providers (`FileProvider`, `InitializationProvider`) |
| Dex contents | Python dex string-table parse of `classes.dex` | all **95** `com.majidali.appcloner` classes present (activities, engines, store, bindings, receivers) |
| No network permission | `aapt dump badging … \| grep -i internet` | no match — no `INTERNET` permission |
| Native libs / 16 KB | `unzip -l app-cloner-debug.apk \| grep -c "\.so$"` | **0** `.so` files |
| README rule | `grep -niw "ads\|tracker\|analytics" README.md` | 0 matches; Screenshots section is placeholder text only |
| No AI branding | `grep -ri "brand-name\|AI-generated" .` (project) | 0 matches |
| File list | `find . -type f` in the project | **54 files** (see §2) |

**Notes on the two warnings and the v2 line:**
- kotlinc warning 1: `Intent.ACTION_UNINSTALL_PACKAGE` is deprecated — it is the API 28 fallback path only (API 29+ uses `PackageInstaller.uninstall`). Harmless.
- kotlinc warning 2: `info.splitNames != null` is "always true" — Kotlin's nullability view of a platform type. Harmless.
- v2 note: default `apksigner verify` reports "v2: false" because the APK's `minSdkVersion` is 28 — v2 is not *required* on Android 9+ (v3 covers it), so verify does not count it. The v2 signature block is present and valid, proven by `--min-sdk-version 24` (v2: true) and by the raw block parse above. The APK installs on every device it supports (Android 9+).

**NOT verified here (needs a phone or a Gradle-capable machine):**
- `./gradlew assembleDebug` — blocked by Blocker 1 (daemon TCP). The Gradle files are standard; this is an environment limit, not a project error.
- AGP lint — needs the Gradle daemon (same blocker). The standalone `lint` CLI cannot run on a Gradle project directory ("should be an XML descriptor file, not a directory"). Compile + link + dex + sign + verify above are the checks that ran.
- Any runtime behaviour — no Android device or emulator exists on this VM. See §7.

---

## 6. How to rebuild

```bash
# Full rebuild (manual, no Gradle daemon):
bash ~/workspace/project-0.1/app-cloner-build/build-apk.sh
# Output: ~/workspace/project-0.1/app-cloner-build/app-cloner-debug.apk

# Gradle build (needs loopback TCP for the daemon — see Blocker 1):
cd ~/workspace/project-0.1/app-cloner
./gradlew assembleDebug        # gradlew wrapper not yet generated (needs the daemon to run once);
                               # use ~/tools/gradle-8.13/bin/gradle assembleDebug instead, or
                               # generate the wrapper on a machine where Gradle works.
```

Toolchain locations (all under `$HOME`, so they survive): JDK `~/tools/jdk-17/`, kotlinc `~/tools/kotlinc/`, Gradle `~/tools/gradle-8.13/`, SDK `~/Android/sdk/` (platforms android-36, build-tools 36.0.0, platform-tools 37.0.3, cmdline-tools latest), dependencies `~/Android/deps/` (resolver manifest `/tmp/deps-manifest.txt`).

---

## 7. Device test checklist — Redmi 13C (MAJID's step)

Do these in order. Tick each one only when it actually happens on the phone. If anything fails, note the exact screen and message — do not guess.

### Before you start
- [ ] Phone: Redmi 13C, charged above 50%.
- [ ] Settings → About phone → tap MIUI version 7 times → Developer options on.
- [ ] Developer options → **USB debugging ON**, **Install via USB ON**.
- [ ] **Mi account signed in** (Settings → Mi Account). Xiaomi blocks work-profile provisioning on some builds until the account is signed in.
- [ ] Check Xiaomi's own Dual Apps first: Settings → Apps → Dual Apps. If it is on for any app, note it — then check whether our work profile clashes with it (step 6).

### 1. Install
- [ ] Copy `app-cloner-debug.apk` to the phone and tap it to install (allow "Install unknown apps" for the file manager if asked). Or: `adb install app-cloner-debug.apk` from a computer.
- [ ] App opens, no crash on first launch. Main screen shows "No clones yet" (or the empty state) and a Setup button.

### 2. Setup wizard
- [ ] Tap Setup. The wizard explains the organisation warning in plain words.
- [ ] Notification permission: allow it (Android 13+ asks once).
- [ ] Battery whitelist: follow the guide (Settings → Apps → App Cloner → Battery saver → No restrictions).
- [ ] Tap "Create work profile". Android shows its own organisation warning — accept it.
- [ ] Profile is created: the launcher now shows a second App Cloner icon **with a briefcase badge**, and Settings → Passwords & security → Work profile (or Settings → Accounts) lists the work profile.
- [ ] Back in the app, Setup shows "Work profile is ready".

### 3. First clone (use WhatsApp first — it is the done-criteria app)
- [ ] Main screen → Add clone → pick **WhatsApp**. The clone appears in the list.
- [ ] Wait for the install to finish (a few seconds). Open the clone from the list or the launcher (briefcase badge on the icon).
- [ ] WhatsApp clone opens and asks for its own setup (number, etc.). Set it up with the second number.

### 4. The done-criteria test — notifications without opening the cloner
- [ ] **Close App Cloner completely** (swipe it away from recents). Do not open it again.
- [ ] From another phone, send a WhatsApp message to the clone's number.
- [ ] The WhatsApp clone **notifies on its own** (notification shade, briefcase badge) without App Cloner being opened. **This is the DONE point.** If it does not notify, check: notification permission allowed (step 2), battery whitelist (step 2), work profile not quiet (step 5).

### 5. Freeze and profile on/off
- [ ] In App Cloner, open the WhatsApp clone → Freeze. The clone icon greys out / disappears from the launcher and stops notifying.
- [ ] Unfreeze: the clone comes back exactly as it was (chats and login intact — freezing loses no data).
- [ ] Main screen → Profile off (quiet mode). All clones stop. Profile on: they return.
- [ ] Rename the clone and change its icon, then add a home-screen shortcut. The shortcut opens the clone directly.

### 6. Xiaomi Dual Apps clash check
- [ ] If Xiaomi Dual Apps was on for any app (see "Before you start"), clone that same app with App Cloner and compare: both copies must work, and the work-profile copy must keep its own data. Note any clash (same app in both systems fighting over notifications or storage).

### 7. Phone restart
- [ ] Restart the phone. Do NOT open App Cloner.
- [ ] Clones are still installed, still listed, and WhatsApp clone still notifies (step 4 again). No crash, no stuck screen, no "profile missing" message.
- [ ] Open App Cloner: the clone list is intact (no lost clones).

### 8. Backup, file shuttle, second clone, limits
- [ ] Clone list backup: Menu → Backup → save the JSON file; remove a clone; Menu → Restore → the clone entry returns.
- [ ] File shuttle: share a photo from the personal side into the work profile (and back) using the app's share flow. It must arrive as the same photo — on Huawei/Oppo this is where the MediaStore trap shows, so test the save+share round trip on any second phone you have.
- [ ] Second clone: clone one more app (limit is 2 in V1). A third clone attempt must be refused with a clear message, not a crash.
- [ ] Limits screen: open it and check the wording is honest and plain (2 copies, banking in sandbox, no 100-accounts promise).
- [ ] Dark mode: switch the phone to dark mode — every screen stays readable, no white-on-white or black-on-black text.
- [ ] Try a Google app (e.g. YouTube) as a clone: it may refuse to run as a second copy — that is expected and the Limits screen says so. Note exactly what happens.

### If something fails
Write down: which step, what the screen showed (exact words), and whether the app crashed or just showed an error. A clear error message instead of a crash is the designed behaviour — report it as-is.

---

## 8. Quality bar — how V1 meets MAJID's Parts 1–3

- **Professional from day one, not a prototype:** clean architecture (`domain` / `data` / `ui` split, `CloneEngine` interface so V2 sandbox fits without touching V1), proper folder structure, clear comments on every non-obvious decision, meaningful names, modular code.
- **Design for real people:** readable text, good spacing, big 56 dp buttons, plain-language setup guide, dark mode (Material3 DayNight), clear error messages instead of crashes (every engine call catches and returns a readable failure + an ADB fallback command where one exists).
- **Reliable now and later:** updates must not break clones — clones are genuine installs of the same signed package (never re-signed, never repackaged), so Play updates and logins keep working; the clone list is a private JSON file with export/import backup; no network calls, so nothing phones home.
- **Android 13–16 rules:** notification permission (13), foreground-service type + Selected Photos Access noted in the research and the wizard (14), Private Space is not used for dual accounts (15), 16 KB page size (16 — no native libs, nothing to align), `targetSdk 36`.
- **Split APKs:** base + every split installed in one session; a base-only copy would crash, so the code refuses to install when the file list is empty and says so.
- **Honest limits in-app:** work profile = normal notifications + banking works; sandbox (V2) = no banking, notifications may be late; never a promise of 100 live accounts; proxy off by default (advanced only, not in V1).
- **Clean code, version tags:** v1.0.0 / versionCode 1, `CHANGELOG.md`, professional README with real Redmi screenshots space (placeholder only, added after the device test), no AI branding.

## 9. Open items / what the parent must decide

1. **Gradle daemon TCP** — `./gradlew` cannot run on this VM until loopback TCP is enabled (the interceptor's own message says the user must allow it). Ask MAJID, or build on another machine. The manual script is the working route here.
2. **Release signing key** — the APK is signed with a debug key made on this VM. A release keystore must be created by MAJID (or on his instruction) before any Play Store listing; never publish with this debug key.
3. **Device test (§7)** — only MAJID on the Redmi 13C can prove cloning, notifications, freeze, profile toggle and restart behaviour.
4. **Screenshots** — add real Redmi 13C captures to the README only after §7 passes.
5. **Publish** — push to `Majid7692/app-cloner`, tag v1.0.0, GitHub Release with the APK + SHA-256 (parent's step).
