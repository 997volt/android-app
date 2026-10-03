---
name: android-device-loop
description: Build, install, launch, and visually verify this Android app on the workspace-local emulator or a USB device, using the self-contained toolchain in .toolchain/ and the wrappers in tools/bin/.
whenToUse: Use when building or running the app, when verifying a Compose screen actually renders (screenshots, UI text), when reading logcat, or when running instrumented tests. Also use when an Android command fails with a permission error under $HOME.
---

# Android device loop

Operator rules for this repo's device loop. Scope: the workspace toolchain, the
build commands, and the privacy rules — the emulator invocation itself lives in
[README.md](../../../README.md#running-on-an-emulator), and UI driving in the
`android-ui-automation` skill (§8).

## 0. Source the env first

Every shell that runs an Android or Gradle command starts with this, from the
repo root:

```sh
. ./tools/android-env.sh
```

`$HOME` is **not writable**, so `~/.android` and `~/.gradle` fail with `EACCES`;
the script repoints them into `.toolchain/` and puts `tools/bin`, the local JDK
21, `platform-tools`, `cmdline-tools` and `emulator` on `PATH`. It is a no-op
when `.toolchain/` is absent (CI relies on that). Use `tools/bin/adb`, never a
system `adb`.

## 1. Build

```sh
./gradlew assembleDebug --console=plain      # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                  # JVM unit tests, no device
./gradlew lint                               # Android lint
./gradlew connectedDebugAndroidTest          # instrumented; needs a device (§2)
```

Keep domain math (1RM, volume, unit conversion, progression) in pure Kotlin
files with JVM tests — the `domain/ExerciseSearch.kt` + `ExerciseSearchTest.kt`
pair is the pattern to copy.

- **`connectedDebugAndroidTest` uninstalls the app when it finishes** (AGP
  removes the app and its test APKs). Nothing is broken; reinstall (§3) before
  looking for it on screen or in a live panel.
- **Install by hand (§3); do not use the plugin's `android_build_run` here.**
  It drives its own build *and* install, and `assembleDebug --console=plain`
  keeps the fix verifiable in one place — a broken build is then a Gradle
  error, not a plugin error.

## 2. A device or emulator

Check what is attached before starting anything — an emulator is often already
running:

```sh
adb devices
emulator -list-avds          # currently pixel6_api36 (API 36)
```

The canonical launch command (headless flags) and how to recreate the AVD are
in README §"Running on an emulator" — **that is the source of truth for
invocation; do not restate it here.**

Starting an emulator needs wider sandbox access: the default `workspace-write`
sandbox denies `open("/dev/kvm")` with `EPERM`, and the AVD is x86_64, so KVM
is required. Launch the wrapper with `sandbox_permissions=danger-full-access`
on **that one command only**:

```sh
tools/bin/emulator -avd pixel6_api36        # needs danger-full-access for /dev/kvm
```

The wrapper gives the emulator the same workspace-local `HOME` as `tools/bin/adb`
so the two agree on the ADB key; without it the device reads `unauthorized`.
Everything after launch (`adb`, `install`, `screencap`, `logcat`) runs under
normal access — but a read-only session blocks even those, because adb must
write `/tmp/adb.<uid>.log` (see the recap).

## 3. Install and launch

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n io.github.volt997.workout/com.example.androidapp.MainActivity
```

Package is `io.github.volt997.workout`, the launcher activity
`com.example.androidapp.MainActivity`. They differ on purpose (ROADMAP F14):
the applicationId is the shipped identity while the source namespace was left
alone, so `am start -n io.github.volt997.workout/.MainActivity` does **not**
resolve — always give the full activity name.

## 4. Gate on focus before you screenshot

**This is the trap.** `adb exec-out screencap` right after `am start` captures
the **splash window**, not your UI: the launcher showing through a grey overlay
with the placeholder `ic_launcher` square in the middle. That PNG looks like a
real screenshot and will fool you into "verifying" a UI that never drew.

Gate on the focused window instead, then capture:

```sh
adb shell dumpsys window | grep -i mCurrentFocus
# expect: mCurrentFocus=Window{... io.github.volt997.workout/com.example.androidapp.MainActivity}

adb exec-out screencap -p > .toolchain/screenshots/shot.png
```

`am start -W` (capital W) blocks until the launch reports `Complete`, but that
is the *start* completing, not the first frame. A short `sleep 2` after focus
is a cheap extra guard on a cold start.

Put captures in `.toolchain/screenshots/`, **not** in the repo tree: there is no
`*.png` ignore rule and a capture must never end up in a commit. `.toolchain/`
is wholly gitignored, so it is the one safe home. Read the PNG with the
image-reading tool to inspect it visually.

## 5. Assert on UI without an image

For raw text, `uiautomator` dumps the semantic tree — faster than eyeballing a
screenshot, and it proves the Compose semantics are exposed (what TalkBack and
the Compose UI tests see). For interactive work prefer `android_ui_tree` (§8).

```sh
adb shell uiautomator dump /sdcard/ui.xml
adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"' | sort -u
```

Read the labels rather than assuming them: they track whatever screen is up and
change as the app grows. A screen returning **no** labels at all is the real
signal — Compose semantics are missing, which breaks this check, TalkBack and
the Compose UI tests alike. `content-desc` is what a screen reader announces, so
an icon-only control with a missing label shows up here as an empty
`content-desc`; check for that when a screen looks right but reads badly.

## 6. Logcat, crashes, resets

```sh
adb logcat -d -t 200                                    # last 200 lines
adb logcat -d -t 400 | grep -iE "AndroidRuntime|FATAL|io.github.volt997.workout"
adb shell pidof io.github.volt997.workout               # pid, to filter to this app
adb logcat -d --pid=$(adb shell pidof io.github.volt997.workout) -t 200

adb shell am force-stop io.github.volt997.workout       # kill the app
adb shell pm clear io.github.volt997.workout            # wipe app data (destructive)
```

The emulator is noisy: `FrameTracker` / `Missed App frame` / `JANK_*` lines are
normal emulator telemetry, not app defects. `android_logs` (§8) gives the same
read bounded and per-process.

## 7. Privacy constraint — do not break this

Workout history, body measurements and progress photos are **health data**, and
the app opts out of every platform transfer path (`android:allowBackup="false"`
plus explicit excludes in `data_extraction_rules.xml` and `backup_rules.xml`).

- Never add code, tooling or scripts that move app data or device contents off
  the machine — no telemetry, crash-symbol upload or analytics defaults.
- Never flip `allowBackup` to `true`, and do not remove the `<device-transfer>`
  section from `data_extraction_rules.xml`: `allowBackup="false"` alone does
  **not** stop device-to-device transfer.
- Say what a new dependency or SDK collects before adding it. Health Connect
  data (ROADMAP P4.1) stays on-device by default.

## 8. The `@zseven-w/dsh-android` plugin

The profile bundles `@zseven-w/dsh-android` (rc.8), which adds the `android_*`
tools; its own `android-ui-automation` skill is the playbook for driving them.
Use them for device work — they carry their own safety rules and return
structured results — and plain `adb` (§1–§6) for anything they do not cover.
This skill stays authoritative for the toolchain, the build commands and the
privacy rules, which the plugin knows nothing about.

- **`ADB` must be in the harness process.** The plugin resolves adb as `$ADB` →
  `adb` on PATH → `<sdk>/platform-tools/adb` and never sources
  `tools/android-env.sh`, so it cannot see this project's PATH. The value has to
  arrive through an `.env` layer, and the effective one is the **harness home**
  `$DSH_HOME/.env` (`~/.dsh/.env`) — *not* the repo's `.env`, because `dsh web`
  does not start in this repo. Both are gitignored, both are read at **boot**,
  so an edit needs a harness restart. It must point at `tools/bin/adb`, never at
  `platform-tools/adb`, or adb dies with `Cannot mkdir '/home/dev/.android'`.
  `echo $ADB` in a tool shell shows which layer won.
- **OCR is macOS-only.** `android_find_text` / `android_tap_text` /
  `android_wait_for` compile a bundled Swift Vision helper, so on this Linux
  host they fail with a swiftc hint, as do `expect_text` / `expect_gone`. Use
  the hierarchy instead: `android_ui_tree` to read, `android_tap_element` (by
  `identifier` or `label`) to act. Identity also beats pixels generally —
  Compose semantics show up in `content-desc`.
- **Let `android_boot` attach; do not boot through it.** It would launch the
  raw emulator binary (no workspace `HOME`, so the ADB key disagrees and the
  device reads `unauthorized`) under a sandbox that denies `/dev/kvm`. Boot with
  `tools/bin/emulator` (§2), then pass the already-online serial
  (`emulator-5554`).

## Pitfalls recap

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Cannot mkdir '/home/dev/.android'` | env not sourced | `. ./tools/android-env.sh` |
| `cannot open /tmp/adb.<uid>.log: Permission denied`, `ADB server didn't ACK` | the session cannot write adb's log dir | rerun with `danger-full-access` |
| Device shows `unauthorized` | emulator and adb disagree on the ADB key | launch via `tools/bin/emulator` |
| `open("/dev/kvm")` EPERM | default sandbox | relaunch with `danger-full-access` |
| Screenshot is a grey screen with a purple square | captured the splash | gate on `mCurrentFocus`, then capture |
| Gradle re-downloads everything | `GRADLE_USER_HOME` not set | source the env script |
| `connectedDebugAndroidTest` fails | no device/emulator attached | start one (§2) |
| App gone from the launcher after a test run | `connectedDebugAndroidTest` uninstalls it | reinstall (§3) |
| `android_*` tool: "adb is unavailable" | harness booted without `ADB` | add it to `$DSH_HOME/.env`, restart (§8) |
| `android_find_text`/`android_tap_text`/`android_wait_for` fail with a swiftc hint | OCR helper needs macOS | `android_ui_tree` + `android_tap_element` (§8) |
