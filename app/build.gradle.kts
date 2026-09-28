import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}

/**
 * Static analysis gate (ROADMAP F12). Lint covers Android-specific issues;
 * detekt adds complexity/maintainability checks plus the Compose ruleset, which
 * catches things lint cannot (a composable taking a `Modifier` it never uses,
 * an unstable collection parameter forcing recomposition, and similar).
 */
detekt {
    buildUponDefaultConfig = true
    parallel = true
    // Only the deltas live here; detekt's defaults stay authoritative.
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

/**
 * Release signing material is deliberately outside version control:
 * `keystore.properties` is gitignored (see .gitignore), and a checkout or CI run
 * without it still builds — the release APK is simply left unsigned, which is
 * enough to prove R8 succeeds. Create the file with:
 *
 *   storeFile=../release.jks
 *   storePassword=...
 *   keyAlias=...
 *   keyPassword=...
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseStoreFile: String? = keystoreProperties.getProperty("storeFile")
val hasReleaseSigning = releaseStoreFile != null

/**
 * Room exports its schema as JSON so migrations can be tested against a real
 * baseline (ROADMAP F5). The files under `app/schemas/` are committed on
 * purpose: they are the record of every shipped schema version.
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.example.androidapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.androidapp"
        // API 26 is the floor for the Health Connect client (ROADMAP P4.1) and
        // makes java.time available natively, so no core library desugaring is
        // needed. Raising this from 24 was a deliberate trade: see ROADMAP F9.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile.orEmpty())
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 on for the real build (ROADMAP F10). Shrinking is not just about
            // APK size: it is the step that can silently strip reflection-based
            // code, and this app leans on it in two places — Hilt's generated
            // components and kotlinx-serialization's generated route serializers.
            // Both ship consumer rules, so they are left to prove themselves in
            // the signed-release smoke test rather than papered over with blanket
            // -keep rules that would defeat shrinking.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        // Makes the exported schema JSON visible to instrumented migration tests.
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    lint {
        // The CI gate (ROADMAP F12) only means something if warnings fail the
        // build. Version-freshness checks are excluded because they hit the
        // network and would fail for reasons unrelated to this code — a
        // dependency bump is a deliberate act, not a build error.
        abortOnError = true
        warningsAsErrors = true
        disable += setOf(
            "NewerVersionAvailable",
            "GradleDependency",
            "AndroidGradlePluginVersion",
        )
        // If a warning ever has to be accepted, record it with
        // `./gradlew updateLintBaseline` and reference the baseline here, rather
        // than disabling the check for the whole project.
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Presentation layer: ViewModels + lifecycle-aware state collection (F3).
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation with type-safe routes (F2).
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)

    // Dependency injection (F4).
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)

    // Local persistence (F5).
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    detektPlugins(libs.detekt.rules.compose)
}
