# Project-specific R8 rules.
# https://developer.android.com/develop/ui/views/layout/custom-views/optimize-code

# Keep line numbers for readable crash reports. The obfuscated class names are
# de-obfuscated from the mapping file written to
# app/build/outputs/mapping/release/mapping.txt, which CI uploads as an artifact.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Nothing else is needed here on purpose.
#
# Everything this app does that R8 cannot see statically is covered by the
# libraries' own consumer rules:
#   - Hilt / Dagger  -> META-INF/proguard rules in hilt-android
#   - kotlinx.serialization -> generated `$serializer` classes are kept by the
#     rules shipped in kotlinx-serialization-core
#   - Jetpack Compose -> rules shipped in the Compose artifacts
#
# Blanket `-keep` rules for those would silence a real failure mode (a release
# build that only breaks after shrinking) while making the APK bigger. If a
# release-only crash ever appears, prefer the narrowest rule that fixes it and
# record here why it was needed.
