// Thin Android application shell.
//
// AGP 9 refuses to apply com.android.application alongside the Kotlin Multiplatform
// plugin, and its replacement (com.android.kotlin.multiplatform.library) is library-only.
// So the KMP module (:composeApp) stays a library and this module supplies the parts a
// library cannot have: the application id, build types, signing, and versioning. It holds
// no Kotlin — every Android source file, the manifest and the resources live in
// :composeApp's androidMain, and merge in from the AAR.
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
}

// Committed so a build from a release tag (F-Droid's) carries that release's version.
val releaseVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
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
        versionCode = ((project.findProperty("versionCode") as String?)
            ?: releaseVersion.getProperty("versionCode")).toInt()
        versionName = (project.findProperty("versionName") as String?)
            ?: releaseVersion.getProperty("versionName")
    }

    // AGP otherwise embeds a Google-encrypted dependency report in the APK, which F-Droid rejects.
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
            }
        }
    }

    buildTypes {
        val ciSigning = signingConfigs.findByName("ciSigning")
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = ciSigning ?: signingConfigs.getByName("debug")
        }
        create("devRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
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
