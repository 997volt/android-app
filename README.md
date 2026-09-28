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
| minSdk | 24 |
| Build Tools | 36.0.0 |

Versions for libraries and plugins are declared in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Build

```bash
./gradlew assembleDebug      # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest  # JVM unit tests
./gradlew connectedDebugAndroidTest   # instrumented tests (needs a device/emulator)
./gradlew lint               # Android lint
```

## Local toolchain in `.toolchain/`

This checkout carries a self-contained, **gitignored** toolchain in `.toolchain/`:

```
.toolchain/
├── jdk/            # Temurin JDK 21
├── android-sdk/    # platform-tools, platforms;android-37.0, build-tools;36.0.0
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

## Notes

Signing keys and `local.properties` are intentionally gitignored — this repo
contains source only. `local.properties` currently points `sdk.dir` at
`.toolchain/android-sdk` and is regenerated per machine.
