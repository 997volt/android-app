# Workout

A local-only workout planner and logger for Android, written in Kotlin with Jetpack
Compose (Material 3).

## What it asks for

**No permissions at all.** The app declares none: no storage, no network, no notifications. The
background rest alert was the only one it ever had, and removing it (ROADMAP N26) left the manifest
empty — which says more about where your training data lives than any wording could.

## Project docs

- [ROADMAP.md](ROADMAP.md) — what is planned, in order
- [DECISIONS.md](DECISIONS.md) — settled choices, and the rules that apply to every change
- [CHANGELOG.md](CHANGELOG.md) — what shipped, per version
- [RELEASING.md](RELEASING.md) — how a release is cut, and its traps

## Toolchain

| Component | Version |
| --- | --- |
| Android Gradle Plugin | 9.4.1 |
| Gradle | 9.8.0 (via wrapper) |
| Kotlin | 2.4.20 |
| Compose BOM | 2026.09.00 |
| JDK | 21 (minimum supported by AGP 9.4 is 17) |
| compileSdk / targetSdk | 37 |
| minSdk | 26 |
| Build Tools | 36.0.0 |

Versions for libraries and plugins are declared in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Build

```bash
./gradlew assembleDebug      # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease    # R8-minified release -> app/build/outputs/apk/release/
./gradlew testDebugUnitTest  # JVM unit tests
./gradlew connectedDebugAndroidTest   # instrumented tests (needs a device/emulator)
./gradlew lint               # Android lint (warnings are errors)
./gradlew detekt             # static analysis + Compose rules
```

## Quality gates

Both gates **fail** the build rather than warn, so CI means something:

- **Android lint** — `warningsAsErrors = true` in
  [`app/build.gradle.kts`](app/build.gradle.kts). Version-freshness checks
  (`NewerVersionAvailable`, `GradleDependency`, `AndroidGradlePluginVersion`) are
  disabled because they consult the network and would fail for reasons unrelated
  to this code.

  There is deliberately **no** `baseline = file(...)`. Wiring one looks harmless
  but AGP *creates* the file when it is missing: the first run to find a warning
  writes it into the baseline, and every run after that silently accepts it —
  "fail on every warning" quietly becomes "fail once". If a warning genuinely has
  to be accepted, do it in two deliberate steps: run
  `./gradlew updateLintBaseline`, review and commit the diff it produces, **then**
  add the `baseline = file("lint-baseline.xml")` line.
- **detekt** — [`config/detekt/detekt.yml`](config/detekt/detekt.yml), layered on
  detekt's defaults (`buildUponDefaultConfig = true`) plus the Compose ruleset.
  That ruleset is pinned to the **0.4.x** line deliberately: `0.5.0+` targets
  detekt 2.0 and, against detekt 1.23.x, loads successfully while registering no
  rules at all — a gate that silently catches nothing.

## Release builds and signing

`release` runs R8 with resource shrinking. Signing material comes from a
**gitignored** `keystore.properties` at the repo root:

```properties
storeFile=workout.jks
storePassword=…
keyAlias=workout
keyPassword=…
```

Without that file the release build still succeeds and produces an unsigned APK —
which is what CI does, since compiling through R8 on every change is the part
worth enforcing. With it, `assembleRelease` writes a **signed** `app-release.apk`,
and [`tools/build-apk.sh`](tools/build-apk.sh) wraps that build, prints its hash,
and can install it. See [Install on your own phone](#install-on-your-own-phone).

R8 is left to the libraries' own consumer rules (Hilt, Compose and
`kotlinx-serialization` all ship them) instead of blanket `-keep` rules, which
would defeat shrinking. That is verified, not assumed: the signed minified build
navigates between screens, exercising the generated route serializers. If a
release-only crash ever appears, add the narrowest rule that fixes it and record
why in [`app/proguard-rules.pro`](app/proguard-rules.pro).

CI uploads `app/build/outputs/mapping/release/mapping.txt`; without it an
obfuscated release stack trace is unreadable.

## Install on your own phone

There is no Play Store step: the app ships as an APK you install yourself. There
are two paths, and the choice is worth making deliberately.

| | Debug APK | Signed release APK |
| --- | --- | --- |
| Build | `./gradlew assembleDebug` | [`tools/build-apk.sh`](tools/build-apk.sh) |
| Output | `app/build/outputs/apk/debug/app-debug.apk` | `app/build/outputs/apk/release/app-release.apk` |
| Signing | auto debug key | your keystore |
| `debuggable` | **true** | false |
| R8 / shrinking | off (~13 MB) | on (~1.5 MB) |

Prefer the signed release. A debug build is `debuggable`, so anyone with adb
access to the phone can `run-as` the app and read the workout database — the
opposite of what the backup opt-out is protecting.

### Creating the keystore (once)

```bash
keytool -genkeypair -v \
  -keystore workout.jks -alias workout \
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12 \
  -dname "CN=Workout Tracker, O=you, C=US"
```

Then create `keystore.properties` at the repo root with the shape shown in
[Release builds and signing](#release-builds-and-signing) and the password you
chose. Both files are gitignored.

> **The signing key is permanent.** Every later build must be signed with the same
> `workout.jks`, or Android refuses to update over the installed app
> (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) and the only fix is to uninstall — which
> deletes the workout history, because backup is off. Back up the keystore **and**
> its password somewhere outside this repo. Moving from a debug build to a release
> build has the same effect, since they share the `applicationId` but not the
> signature.

### Building and installing

```bash
tools/build-apk.sh              # build; print the path, version and sha256
tools/build-apk.sh --install    # ...and `adb install -r` it onto the device
```

To install by hand, copy the APK to the phone and open it (allowing "install
unknown apps" for the file manager), or go over USB:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Verify what you built:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Bump `versionCode` in [`version.properties`](version.properties) for each build you
keep, so installs are tellable apart.

## Continuous integration

[`.github/workflows/android.yml`](.github/workflows/android.yml) has two jobs:

| Job | Runs |
| --- | --- |
| `build` | `testDebugUnitTest`, `lint`, `detekt`, `assembleDebug`, `assembleRelease` |
| `instrumented` | `connectedDebugAndroidTest` on a KVM-accelerated runner emulator |

The workflow deliberately does **not** use `.toolchain/`: that is a per-machine,
gitignored install, and `tools/android-env.sh` is a no-op without it, so CI uses
the runner's own JDK and Android SDK.

### Known flakiness: intermittent task failures under memory pressure

Four different Gradle tasks have each failed once and then passed on an immediate
re-run with **no code change**:

| Task | Symptom seen |
| --- | --- |
| `hiltJavaCompileDebug` / `hiltJavaCompileRelease` | `cannot access com.example.androidapp` |
| `expandReleaseArtProfileWildcards` | `getInputStream(...) must not be null` |
| `lintAnalyzeDebugAndroidTest` | task failure, no findings in the report |

The common factor looks like memory rather than one particular plugin. This
sandbox has ~12 GB total with under 300 MB free while a booted emulator
(~2–3 GB) competes with the Gradle and Kotlin daemons, and every one of those
tasks is worker-based. `./gradlew clean` or a plain re-run has cleared every
occurrence, and the same sequences have since passed repeatedly — so this is
**not** deterministically reproducible, and nothing here is worked around
speculatively.

Practical consequence: if a build fails in one of these tasks with an error that
does not point at your own code, **re-run before investigating**. If it becomes
frequent, free memory (stop the emulator), cap concurrency with
`--max-workers=2`, or drop `org.gradle.parallel` from
[`gradle.properties`](gradle.properties). The CI `build` job runs on a dedicated
runner with no emulator and should not hit this; the `instrumented` job does run
an emulator, so it is the one to watch.

## Local toolchain in `.toolchain/`

This checkout carries a self-contained, **gitignored** toolchain in `.toolchain/`:

```
.toolchain/
├── jdk/            # Temurin JDK 21
├── android-sdk/    # platform-tools, platforms, build-tools, emulator, system image
├── syslibs/        # X11/Pulse libraries the emulator needs but the host lacks
├── gradle-home/    # GRADLE_USER_HOME (dependency + wrapper caches)
└── android-home/   # ANDROID_USER_HOME (state for the Android CLI)
```

Nothing needs to be exported by hand: [`gradlew`](gradlew) sources
[`tools/android-env.sh`](tools/android-env.sh), which puts the local JDK and SDK
on `PATH` and redirects the Gradle and Android CLI caches into `.toolchain/`.
If `.toolchain/` is absent, both scripts are no-ops and the build falls back to
whatever `java` and Android SDK your machine provides.

To use the toolchain from your own shell (for `adb`, `sdkmanager`, ...):

```bash
. ./tools/android-env.sh
```

### Why the caches are redirected

The sandbox this project was set up in only permits writes inside the project
directory, so the default `~/.gradle` and `~/.android` locations are unusable.
Pointing them at `.toolchain/` also keeps the whole toolchain in one deletable
folder.

## Running on an emulator

The toolchain includes the emulator and one AVD (`pixel6_api36`, a Pixel 6
running Android 16 / API 36):

```bash
. ./tools/android-env.sh
emulator -list-avds
emulator -avd pixel6_api36 -no-window -no-audio -no-boot-anim \
    -gpu swiftshader_indirect -accel on &
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
# `-n` needs the full activity name, not `io.github.volt997.workout/.MainActivity`:
# the applicationId and the source namespace differ (see app/build.gradle.kts).
adb shell am start -n io.github.volt997.workout/com.example.androidapp.MainActivity
adb exec-out screencap -p > shot.png
```

To create the AVD again from scratch:

```bash
sdkmanager "emulator" "system-images;android-36;default;x86_64"
echo no | avdmanager create avd -n pixel6_api36 \
    -k "system-images;android-36;default;x86_64" -d pixel_6
```

`tools/bin/emulator` wraps the real binary so it works where `$HOME` is not
writable. It redirects `HOME` to `.toolchain/home` — the emulator and `adb` must
agree on `~/.android/adbkey` or devices appear as *unauthorized* — and sets
`ANDROID_AVD_HOME`, because the emulator ignores `ANDROID_USER_HOME` (unlike
`avdmanager`).

**KVM is required.** x86_64 emulation needs hardware acceleration, and the
default `workspace-write` sandbox denies `open("/dev/kvm")` with `EPERM` even
though the device is world-writable. The emulator must therefore be started with
wider sandbox access; everything afterwards (`adb install`, screenshots,
`connectedDebugAndroidTest`) works under normal access. `./gradlew
connectedDebugAndroidTest` works because [`gradlew`](gradlew) scopes `HOME` to
`.toolchain/home` for the build process only, leaving an interactive shell's
`~/.ssh` — and therefore git over SSH — untouched.

### Emulator host libraries

The host image lacks the X11/Pulse libraries the emulator links against and
provides no installable package manager. `.toolchain/syslibs/` holds them,
extracted from distribution packages (`pacman -Sp` resolves download URLs
without root; nothing is installed system-wide), and
[`tools/android-env.sh`](tools/android-env.sh) exposes them via
`LD_LIBRARY_PATH`. None of them shadow a library the host already has.

## Notes

Signing keys and `local.properties` are intentionally gitignored — this repo
contains source only. `local.properties` currently points `sdk.dir` at
`.toolchain/android-sdk` and is regenerated per machine.

## Data and backup policy

Workout history, body measurements, and progress photos are health data, so the
app opts out of the platform's backup and transfer mechanisms entirely:
`android:allowBackup="false"` in the manifest, plus explicit excludes in
[`data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml)
(cloud backup, device-to-device transfer, and iOS cross-platform transfer) and
[`backup_rules.xml`](app/src/main/res/xml/backup_rules.xml) for API ≤ 30.

`allowBackup="false"` alone is **not** sufficient: it disables cloud backup but
leaves device-to-device transfer fully enabled when the rules file has no
`<device-transfer>` section. See [Back up user data with Auto Backup](https://developer.android.com/identity/data/autobackup).

`minSdk` is 26 deliberately: it is the Health Connect client's floor and makes
`java.time` available without core library desugaring.

## License

[MIT](LICENSE) © 2026 997volt.

The app bundles third-party libraries — Compose, Hilt, Room, kotlinx-serialization
and AndroidX — all under Apache-2.0. That is a non-issue for sideloading your own
build; if this were ever published, the tidy thing would be an in-app
third-party-licences screen.
