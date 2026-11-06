// Test fixtures shared by the suites of several modules. KMP has no testFixtures source set, so
// they are a module of their own, depended on only from test source sets.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    android {
        namespace = "com.karakept.core.testing"
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
                api(project(":core:data"))
                api(libs.kotlin.test)
                api(libs.kotlinx.coroutines.test)
            }
        }
        // kotlin-test resolves its JVM half on its own only in test source sets; here, where it
        // is a main dependency, BeforeTest/AfterTest need the JUnit binding named outright.
        androidMain {
            dependencies {
                api(kotlin("test-junit"))
            }
        }
        getByName("desktopMain") {
            dependencies {
                api(kotlin("test-junit"))
            }
        }
    }
}
