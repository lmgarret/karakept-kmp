// Thin Android application shell.
//
// AGP 9 refuses to apply com.android.application alongside the Kotlin Multiplatform
// plugin, and its replacement (com.android.kotlin.multiplatform.library) is library-only.
// So the KMP module (:composeApp) stays a library and this module supplies the parts a
// library cannot have: the application id, build types, signing, and versioning. It holds
// no Kotlin — every Android source file, the manifest and the resources live in
// :composeApp's androidMain, and merge in from the AAR.
plugins {
    alias(libs.plugins.androidApplication)
}

android {
    // Not com.karakept.app: two modules cannot share a namespace, and :composeApp owns
    // that one (it is where MainActivity and the launcher aliases resolve from). The
    // applicationId below is what users and the Play Store see.
    namespace = "com.karakept.app.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.karakept.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Literals on purpose: F-Droid reads both straight from this file at each release tag.
        // The release workflow rewrites them (see .github/workflows/release.yml); the code is
        // MAJOR * 1_000_000 + MINOR * 1_000 + PATCH.
        versionCode = 2006000
        versionName = "2.6.0"
    }

    // An encrypted dependency report only Google Play can read; F-Droid's scanner rejects it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        val keystorePath = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
        if (keystorePath != null && file(keystorePath).exists()) {
            create("ciSigning") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
                // minSdk 24 verifies v2, and the v1 JAR signature adds entries to the APK that
                // make comparing it against an unsigned rebuild harder.
                enableV1Signing = false
            }
        }
    }

    buildTypes {
        val ciSigning = signingConfigs.findByName("ciSigning")
        getByName("release") {
            isMinifyEnabled = false
            // Unsigned without a keystore: F-Droid builds that way, then copies the signature
            // over from the published APK (reproducible builds).
            signingConfig = ciSigning
            // The VCS stamp would tie the APK to a .git checkout a source rebuild may lack.
            vcsInfo.include = false
        }
        create("devRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // PR builds from forks have no keystore and must still install.
            signingConfig = ciSigning ?: signingConfigs.getByName("debug")
            // The "Karakept Dev" label and the dev launcher icons come from src/devRelease/res.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(projects.composeApp)
    debugImplementation(libs.compose.ui.tooling)
}
