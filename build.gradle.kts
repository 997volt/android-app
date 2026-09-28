// Top-level build file. Plugins are declared here and applied in the modules.
// AGP 9 has built-in Kotlin support, so no separate kotlin-android plugin is needed.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
