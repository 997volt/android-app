---
name: android-device-loop
description: Build, install, launch, and visually verify this Android app on the workspace-local emulator or a USB device, using the self-contained toolchain in .toolchain/ and the wrappers in tools/bin/.
whenToUse: Use when building or running the app, when verifying a Compose screen actually renders (screenshots, UI text), when reading logcat, or when running instrumented tests. Also use when an Android command fails with a permission error under $HOME.
---

# Android device loop

This repo carries a self-contained, gitignored toolchain in `.toolchain/`
(JDK 21, Android SDK, Gradle caches). `$HOME` is typically **not writable**, so
commands that fall back to `~/.android` or `~/.gradle` fail with `EACCES`.
Everything below therefore goes through `tools/android-env.sh` and `tools/bin/`.

## 0. Always source the env first

Every shell that runs an Android or Gradle command must start with:

```sh
. ./tools/android-env.sh
```

(from the repo root; the script is a no-op when `.toolchain/` is absent).

It puts `tools/bin`, the local JDK, `platform-tools`, `cmdline-tools`, and
`emulator` on `PATH`, and redirects the caches into the workspace:

| Variable | Points at | Why |
| --- | --- | --- |
| `JAVA_HOME` | `.toolchain/jdk` | JDK 21 |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | `.toolchain/android-sdk` | SDK |
| `ANDROID_USER_HOME` | `.toolchain/android-home` | `~/.android` is unwritable |
| `ANDROID_AVD_HOME` | `.toolchain/android-home/avd` | the emulator ignores `ANDROID_USER_HOME` |
| `GRADLE_USER_HOME` | `.toolchain/gradle-home` | `~/.gradle` is unwritable |

Symptom → cause: `Cannot mkdir '/home/dev/.android': Permission denied` means the
env was **not** sourced. Use `adb` from `tools/bin/adb`, not a system `adb`.

## 1. Build

```sh
./gradlew assembleDebug --console=plain      # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                  # JVM unit tests (no device needed)
./gradlew lint                               # Android lint
./gradlew connectedDebugAndroidTest          # instrumented tests; NEEDS a device (§2)
```

Prefer `testDebugUnitTest` for logic. Keep domain math (1RM, volume, unit
conversion, progression) in pure Kotlin files with JVM tests — the existing
`Greeting.kt` / `GreetingTest.kt` split is the pattern to copy.

## 2. A device or emulator

Check what is already attached before starting anything — an emulator is often
already running:

```sh
adb devices
```

`emulator -list-avds` lists the AVDs (currently `pixel6_api36`, API 36).

For the canonical launch command (including the headless `-no-window -no-audio
-no-boot-anim -gpu swiftshader_indirect` flags and how to recreate the AVD), see
the "Running on an emulator" section of [README.md](../../../README.md) — treat
that as the source of truth for emulator invocation and keep this skill to the
operator details it does not cover.

**Starting an emulator needs wider sandbox access.** The default
`workspace-write` sandbox denies `open("/dev/kvm")` with `EPERM`, and x86_64
emulation requires KVM. Launch through the wrapper with
`sandbox_permissions=danger-full-access` on **that one command only**:

```sh
tools/bin/emulator -avd pixel6_api36        # needs danger-full-access for /dev/kvm
```

`tools/bin/emulator` also gives the emulator the same workspace-local `HOME` as
`tools/bin/adb`, so the two agree on the ADB key. Without that they disagree and
the device shows as `unauthorized`. Everything after launch (`adb`, `install`,
`screencap`, `logcat`) runs under normal access.

## 3. Install and launch

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.example.androidapp/.MainActivity
```

Package is `com.example.androidapp`; the launcher activity is `.MainActivity`.

## 4. Verify it actually rendered — gate on the focused window

**This is the trap.** `adb exec-out screencap` right after `am start` captures
the **splash window**, not your UI: you get the launcher showing through a grey
overlay with the placeholder `ic_launcher` square in the middle. That PNG looks
like a real screenshot and will fool you into "verifying" a UI that never drew.

Gate on the focused window instead, then capture:

```sh
adb shell dumpsys window | grep -i mCurrentFocus
# expect: mCurrentFocus=Window{... com.example.androidapp/com.example.androidapp.MainActivity}

adb exec-out screencap -p > /tmp/shot.png
```

`am start -W` (capital W) already blocks until the launch reports `Complete`,
but that is the *start* completing, not the first frame — still confirm focus.
A short `sleep 2` after focus is a cheap extra guard on a cold start.

Write screenshots to `/tmp/`, **not** into the repo: this tree has no `*.png`
ignore rule and captures must not end up in a commit.

Read the PNG with the image-reading tool to inspect it visually.

## 5. Assert on UI without an image

`uiautomator` dumps the semantic tree — faster and more precise than eyeballing
a screenshot for text, and it proves the Compose semantics are exposed (which
is also what TalkBack and Compose UI tests see):

```sh
adb shell uiautomator dump /sdcard/ui.xml
adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"' | sort -u
```

For the current scaffold this prints `Hello, Android!`, `0`, `Increment`, and
`Tap the button to increment`. Accessibility labels matter here: `content-desc`
is what a screen reader announces, so a missing label shows up as an empty
`content-desc` in this dump.

## 6. Logcat and crashes

```sh
adb logcat -d -t 200                                    # last 200 lines
adb logcat -d -t 400 | grep -iE "AndroidRuntime|FATAL|com.example.androidapp"
adb shell pidof com.example.androidapp                  # filter to just this app
adb logcat -d --pid=$(adb shell pidof com.example.androidapp) -t 200
```

Note the emulator is noisy: `FrameTracker` / `Missed App frame` / `JANK_*`
lines are normal emulator telemetry, not app defects.

Useful resets while iterating:

```sh
adb shell am force-stop com.example.androidapp          # kill the app
adb shell pm clear com.example.androidapp               # wipe app data (destructive)
```

## 7. Privacy constraint — do not break this

Workout history, body measurements, and progress photos are **health data** and
this app opts out of every platform transfer path
(`android:allowBackup="false"` plus explicit excludes in
`app/src/main/res/xml/data_extraction_rules.xml` and `backup_rules.xml`).

- Never add code, tooling, or scripts that upload app data or device contents
  off the machine, including "helpful" telemetry, crash-symbol upload, or
  analytics defaults.
- Never flip `allowBackup` to `true`, and do not remove the `<device-transfer>`
  section from `data_extraction_rules.xml`. `allowBackup="false"` alone does
  **not** stop device-to-device transfer.
- When a task involves a new dependency or SDK, say what it collects before
  adding it. Health Connect data (ROADMAP P4.1) stays on-device by default.

## 8. The `@zseven-w/dsh-android` plugin tools

This profile has the community bundle `@zseven-w/dsh-android` installed (see
`~/.dsh/profiles/web/package.json`), which adds `android_*` tools for device
work — `android_devices`, `android_ui_tree`, `android_interact`,
`android_tap_element`, `android_screenshot`, `android_logs`, `android_build_run`,
and more. Prefer them over hand-rolled adb when they fit: they carry their own
safety rules and return structured results. Plain `adb` (§1–§6) remains correct
for anything they do not cover.

The plugin also ships its own skill, `android-ui-automation` — load it for UI
driving. This skill stays authoritative for the **workspace toolchain, build
commands, and the privacy rules**, which the plugin knows nothing about.

Three host-specific constraints:

**It needs `ADB` in the harness process.** The plugin resolves adb as
`$ADB` → `adb` on PATH → `<sdk>/platform-tools/adb` (`lib/adb.js`), and it does
**not** source `tools/android-env.sh`. Since the harness does not inherit this
project's PATH, `.env` in the repo root sets `ADB` (and `ANDROID_HOME` for AVD
discovery). DSH loads the invoking directory's `.env` at **boot**, so a change
there needs a harness restart. Symptom when it is missing: every `android_*` call
fails with *"adb is unavailable — adb was not found"*.

Point `ADB` at `tools/bin/adb`, never at `platform-tools/adb` directly: the
wrapper is what redirects `HOME` so adb can write its key material, otherwise
raw adb dies with `Cannot mkdir '/home/dev/.android'`.

**OCR tools do not work on this Linux host.** `lib/ocr-backend.js` gates on
`process.platform !== 'darwin'` and compiles a bundled Vision helper with
`swiftc`. So `android_find_text`, `android_tap_text`, and `android_wait_for`
fail here, as do the `expect_text` / `expect_gone` assertion options. Use the
view-hierarchy path instead: `android_ui_tree` to read, `android_tap_element`
(with `identifier` or `label`) to act. That is also the better choice generally —
identity beats pixels, and Compose semantics show up in `content-desc`.

**Let `android_boot` attach, do not boot.** A plugin-booted AVD would use the raw
emulator binary (no workspace `HOME`, so the adb key disagrees and the device
reads `unauthorized`) and the harness sandbox denies `/dev/kvm`. Boot with
`tools/bin/emulator` under wider sandbox access instead, then pass the already
online serial (`emulator-5554`).

## Pitfalls recap

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Cannot mkdir '/home/dev/.android'` | env not sourced | `. ./tools/android-env.sh` |
| Device shows `unauthorized` | emulator and adb disagree on the ADB key | launch via `tools/bin/emulator` |
| `open("/dev/kvm")` EPERM | default sandbox | relaunch with `danger-full-access` |
| Screenshot is a grey screen with a purple square | captured the splash | gate on `mCurrentFocus`, then capture |
| Gradle re-downloads everything | `GRADLE_USER_HOME` not set | source the env script |
| `connectedDebugAndroidTest` fails | no device/emulator attached | start one (§2) |
| `android_*` tool: "adb is unavailable" | harness started without `ADB` | set it in `.env`, restart the harness (§8) |
| `android_find_text`/`android_tap_text`/`android_wait_for` fail with a swiftc hint | OCR helper needs macOS | use `android_ui_tree` + `android_tap_element` (§8) |
