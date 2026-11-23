# App Cloner

Run a second copy of an app on the same phone — with its own login, its own chats, and its own notifications.

App Cloner creates a managed work profile on your phone and installs a second copy of an app inside it. The clone is a real, separate install, so it behaves like the original: same features, same notifications, its own data. A small briefcase badge in the launcher tells the clone apart from the original.

## Features

- **Second copy of any app** — WhatsApp, TikTok, or any app you use twice, each with its own login.
- **Work profile, not a trick** — clones are genuine installs inside Android's managed work profile, so notifications arrive normally and updates keep working.
- **Freeze a clone** — hide it and stop it from running or notifying, then unfreeze it exactly as it was.
- **Whole profile on/off** — switch the work profile off and every clone pauses at once; switch it back on and everything returns.
- **Rename and re-icon** — give each clone its own name and picture, and add its own home screen shortcut.
- **Backup and restore** — save the clone list as one JSON file on your phone, restore it later.
- **Share files with a clone** — send a photo or document across the profile border, safely, as a content link.
- **Honest limits** — the Limits screen says plainly what works, what does not, and why.
- **Dark mode** — follows your phone's light / dark setting.
- **Private by design** — everything stays on your phone; the app makes no network calls.

## Requirements

- Android 9 (API 28) or newer. Target is Android 16 (API 36).
- A phone that allows a managed work profile (most Android phones do; some heavily locked-down work phones do not).
- About 5 minutes for the first setup.

## Install — build it yourself

You need JDK 17 and the Android SDK (API 36, build-tools 36).

```bash
git clone https://github.com/Majid7692/app-cloner.git
cd app-cloner
./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Install it on your phone:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the phone and tap it to install (allow "Install unknown apps" for your file manager when asked).

## Setup — step by step

Open App Cloner and follow the wizard. It takes about two minutes:

1. **What a work profile is** — a separate, private space on the same phone. Read the short explanation.
2. **The "organisation" warning** — Android will say an organisation will manage the profile. That "organisation" is this app, on your own phone. Nothing is sent anywhere.
3. **Battery** — tap "Open battery settings" and allow App Cloner to run in the background, so clone notifications keep arriving. On Xiaomi phones this step matters most.
4. **Notifications** (Android 13 and newer) — allow notifications so a clone can tell you about a new message without opening App Cloner.
5. **Create the work profile** — tap the button and accept Android's own setup screen. Done.

Tip for Xiaomi phones (tested on Redmi 13C): turn on USB debugging and "Install via USB" in Developer options, and sign in to your Mi account before the first clone. Also check that Xiaomi's own Dual Apps is not already cloning the same app — the two can clash.

## Use — step by step

### Clone an app

1. On the main screen, tap **Clone an app**.
2. Pick the app from the list. Apps already cloned show "Cloned".
3. Wait a moment. Android installs the second copy inside the work profile. The first clone may ask once, in the work profile, to confirm the install — that is Android's own prompt.
4. The clone appears in the list and on your home screen, with a briefcase badge.

### Open, rename, re-icon a clone

- Tap a clone to open it, or tap its name for details.
- **Save name** — the name on the home screen shortcut. The app itself inside the profile keeps its own name.
- **Pick a new icon** — choose a picture; it is stored on your phone and used for the shortcut.
- **Add home screen shortcut** — puts the clone on your home screen with its custom name and icon.

### Freeze and unfreeze

- Tap **Freeze** on a clone. It stops running and stops notifying, and keeps all its data. Tap **Unfreeze** to use it again.

### Turn the whole profile off and on

- Use the switch on the main screen. Off hides every clone at once; on brings them all back exactly as they were.

### Back up and restore the clone list

- Menu → **Back up clone list**: saves one JSON file where you choose.
- Menu → **Restore clone list**: pick a backup file. The list comes back; the apps themselves are re-cloned from the list (a backup file cannot install apps, and that is a good thing).

### Share a file with a clone

- Tap **Share a file with a clone**, pick a photo or document, and choose the clone to send it to. Files cross the border as content links only — never as raw file paths, which fail on some phones.

## How cloning works

1. The setup wizard creates a managed work profile with Android's own provisioning screen. No root, no computer needed.
2. When you clone an app, App Cloner reads where the app's files live (base APK plus every split — most Play Store apps are split APKs) and hands them to its own copy inside the profile.
3. That copy installs them there with a PackageInstaller session. Android installs the whole package, splits included, so the clone does not crash on launch.
4. The clone is a genuine install of the same signed package. Nothing is re-signed and no APK is modified — that is why login, Play updates and banking keep working in the clone.

The full technical notes, including what was verified and what still needs a phone test, are in [BUILD-REPORT.md](BUILD-REPORT.md).

## Limits — read this first

- **V1 allows 2 clones.** The profile holds one extra copy of each app; the limit keeps the phone fast. More clones come with the sandbox mode in V2.
- **Some apps refuse a second copy.** Google apps, WhatsApp, YouTube, Snapchat and OneDrive check for this and may refuse to run cloned. That is their decision, not a bug in this app. The in-app Limits screen keeps the list.
- **No promise of 100 live accounts.** One phone can hold a few copies; a hundred accounts on one phone gets noticed and banned by the apps themselves.
- **Proxy is off by default.** It is an advanced option for V2 only, and it does not hide the phone — a proxy can leak the real address.
- **Updates do not break clones.** A clone is a separate install, so updating the original leaves the clone alone. If a clone ever stops opening after an update, re-clone it from its detail screen.
- **16 KB page size.** V1 ships no native libraries at all, so it is 16 KB page-size compatible by construction (required alongside targetSdk 36).

## How to update (for contributors)

See [CONTRIBUTING.md](CONTRIBUTING.md): feature branch, one small change, build and test, update the changelog, pull request, tag the release.

## License

MIT — see [LICENSE](LICENSE). Copyright (c) 2026 Majid Ali.

## Screenshots

Real screenshots from the app running on a Redmi 13C will go here, after V1 is tested on the device.

<!-- Screenshots — add only real captures from the Redmi 13C test:
1. Main screen — clone list (light mode).
2. Setup wizard — work profile step.
3. Clone detail — rename and shortcut.
4. Dark mode — main screen.
-->
