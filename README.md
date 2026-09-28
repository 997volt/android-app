# android-app

An Android application written in Kotlin, using Jetpack Compose (Material 3).

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
  to this code. If a warning ever has to be accepted, record it with
  `./gradlew updateLintBaseline` rather than switching the check off.
- **detekt** — [`config/detekt/detekt.yml`](config/detekt/detekt.yml), layered on
  detekt's defaults (`buildUponDefaultConfig = true`) plus the Compose ruleset.
  That ruleset is pinned to the **0.4.x** line deliberately: `0.5.0+` targets
  detekt 2.0 and, against detekt 1.23.x, loads successfully while registering no
  rules at all — a gate that silently catches nothing.

## Release builds and signing

`release` runs R8 with resource shrinking. Signing material comes from a
**gitignored** `keystore.properties` at the repo root:

```properties
storeFile=../release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without that file the release build still succeeds and produces an unsigned APK —
which is what CI does, since compiling through R8 on every change is the part
worth enforcing.

R8 is left to the libraries' own consumer rules (Hilt, Compose and
`kotlinx-serialization` all ship them) instead of blanket `-keep` rules, which
would defeat shrinking. That is verified, not assumed: the signed minified build
navigates between screens, exercising the generated route serializers. If a
release-only crash ever appears, add the narrowest rule that fixes it and record
why in [`app/proguard-rules.pro`](app/proguard-rules.pro).

CI uploads `app/build/outputs/mapping/release/mapping.txt`; without it an
obfuscated release stack trace is unreadable.

## Continuous integration

[`.github/workflows/android.yml`](.github/workflows/android.yml) has two jobs:

| Job | Runs |
| --- | --- |
| `build` | `testDebugUnitTest`, `lint`, `detekt`, `assembleDebug`, `assembleRelease` |
| `instrumented` | `connectedDebugAndroidTest` on a KVM-accelerated runner emulator |

The workflow deliberately does **not** use `.toolchain/`: that is a per-machine,
gitignored install, and `tools/android-env.sh` is a no-op without it, so CI uses
the runner's own JDK and Android SDK.

### Known flakiness

`hiltJavaCompileDebug` / `hiltJavaCompileRelease` have been observed to fail
intermittently — Hilt's aggregating task, most likely racing with parallel
execution. It was seen three times, each time cleared by simply re-running, and
five subsequent clean builds (including the exact sequences that had failed)
passed, so it is **not** deterministically reproducible and is not worked around
speculatively. If CI goes red on that task, re-run the job; if it recurs often,
try `org.gradle.parallel=false` in [`gradle.properties`](gradle.properties) for
that job and see whether it stops.

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
adb shell am start -n com.example.androidapp/.MainActivity
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
