#!/usr/bin/env bash
#
# Builds the signed release APK for sideloading — the "first release" of the
# roadmap, with no Play Store involved.
#
#   tools/build-apk.sh            # build, then print the APK path and hash
#   tools/build-apk.sh --install  # ...and `adb install -r` it onto the device
#
# Signing material comes from a gitignored `keystore.properties` at the repo
# root. Without it `assembleRelease` produces an *unsigned* APK that Android
# will refuse to install, so this fails early rather than at install time.
#
# See "Install on your own phone" in README.md for creating the keystore.

set -euo pipefail

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

# Put the workspace-local JDK, SDK and adb on PATH when the toolchain is present.
if [ -f "$ROOT/tools/android-env.sh" ]; then
    # shellcheck disable=SC1091
    . "$ROOT/tools/android-env.sh"
fi

if [ ! -f "$ROOT/keystore.properties" ]; then
    cat >&2 <<'MSG'
keystore.properties is missing, so assembleRelease would produce an UNSIGNED
APK that cannot be installed. Create it at the repo root (it is gitignored):

    storeFile=workout.jks
    storePassword=<your password>
    keyAlias=workout
    keyPassword=<your password>

See "Install on your own phone" in README.md for the keytool command that
creates workout.jks in the first place.
MSG
    exit 1
fi

./gradlew assembleRelease --console=plain

APK="$ROOT/app/build/outputs/apk/release/app-release.apk"

# The signed output is app-release.apk; the unsigned fallback is
# app-release-unsigned.apk. Never hand over the latter by accident.
case "$APK" in
    *-unsigned.apk)
        echo "refusing to hand over an unsigned APK" >&2
        exit 1
        ;;
esac

if [ ! -f "$APK" ]; then
    echo "expected $APK, but the build did not produce it" >&2
    exit 1
fi

version=$(sed -n 's/^versionName=//p' "$ROOT/version.properties")
code=$(sed -n 's/^versionCode=//p' "$ROOT/version.properties")

echo
echo "APK      $APK"
echo "version  $version ($code)"
echo "size     $(du -h "$APK" | cut -f1)"
echo "sha256   $(sha256sum "$APK" | cut -d' ' -f1)"
echo
echo "Install onto a connected device with:"
echo "  adb install -r $APK"

if [ "${1:-}" = "--install" ]; then
    echo
    adb install -r "$APK"
fi
