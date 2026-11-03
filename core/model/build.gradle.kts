plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    // Not for any composable: so the data classes here keep the stability the Compose compiler
    // infers for them. A class from a module built without it reads as unstable to every
    // composable that takes one, and stops them skipping recomposition.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.ExperimentalStdlibApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.karakept.core.model"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    jvm("desktop")

    sourceSets {
        commonMain {
            dependencies {
                // ListSyncConfig carries the generated KarakeepList.
                api(project(":api-client"))
                api(libs.compose.runtime)
                api(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// See :composeApp — without it `./gradlew test` would skip this module's suite.
tasks.register("test") {
    group = "verification"
    description = "Alias for allTests."
    dependsOn("allTests")
}
