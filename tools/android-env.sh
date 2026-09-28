#!/usr/bin/env sh
# Puts the workspace-local Android toolchain on PATH.
#
#   . ./tools/android-env.sh
#
# The toolchain lives in .toolchain/ (gitignored). Sourcing this script is only
# needed on a machine where Java and the Android SDK are not already installed
# system-wide; when .toolchain/ is absent the script changes nothing.
#
# Callers may pre-set ANDROID_ENV_ROOT to the project root. `gradlew` does this.

# Resolve the project root. A *sourced* script cannot rely on $0, so prefer
# BASH_SOURCE when available and fall back to the current directory.
if [ -z "${ANDROID_ENV_ROOT:-}" ]; then
    _android_env_src="${BASH_SOURCE:-$0}"
    _android_env_root=$(CDPATH= cd -- "$(dirname -- "$_android_env_src")/.." 2>/dev/null && pwd)
    if [ ! -f "$_android_env_root/gradlew" ] && [ -f "$PWD/gradlew" ]; then
        _android_env_root=$PWD
    fi
    ANDROID_ENV_ROOT=$_android_env_root
fi
export ANDROID_ENV_ROOT

if [ -x "$ANDROID_ENV_ROOT/.toolchain/jdk/bin/java" ]; then
    JAVA_HOME="$ANDROID_ENV_ROOT/.toolchain/jdk"
    export JAVA_HOME
fi

if [ -d "$ANDROID_ENV_ROOT/.toolchain/android-sdk" ]; then
    ANDROID_HOME="$ANDROID_ENV_ROOT/.toolchain/android-sdk"
    ANDROID_SDK_ROOT="$ANDROID_HOME"
    export ANDROID_HOME ANDROID_SDK_ROOT
    # The Android CLI keeps its own state under ~/.android by default; point it
    # into the workspace so it stays writable inside restricted sandboxes.
    ANDROID_USER_HOME="$ANDROID_ENV_ROOT/.toolchain/android-home"
    export ANDROID_USER_HOME
    # avdmanager honours ANDROID_USER_HOME but the emulator does not: it looks in
    # $ANDROID_AVD_HOME first, then $ANDROID_SDK_HOME/avd, then $HOME/.android/avd.
    ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
    export ANDROID_AVD_HOME
fi

GRADLE_USER_HOME="$ANDROID_ENV_ROOT/.toolchain/gradle-home"
export GRADLE_USER_HOME
mkdir -p "$GRADLE_USER_HOME" 2>/dev/null || true

# The emulator links against X11/Pulse libraries the host image does not ship,
# and there is no package manager we may install into. .toolchain/syslibs holds
# them extracted from distribution packages. Verified to shadow no host library.
_syslibs="$ANDROID_ENV_ROOT/.toolchain/syslibs/root/usr/lib"
if [ -d "$_syslibs" ]; then
    LD_LIBRARY_PATH="$_syslibs:$_syslibs/pulseaudio${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
    export LD_LIBRARY_PATH
fi

PATH="$ANDROID_ENV_ROOT/tools/bin:${JAVA_HOME:+$JAVA_HOME/bin:}${ANDROID_HOME:+$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:}$PATH"
export PATH

unset _android_env_src _android_env_root _syslibs
