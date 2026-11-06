import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.ExperimentalStdlibApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.karakept.core.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        // The reader fonts are Compose resources, which a KMP library ships as Android assets.
        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    jvm("desktop")

    sourceSets {
        commonMain {
            dependencies {
                api(project(":core:data"))
                implementation(project(":api-client"))

                api(libs.compose.runtime)
                api(libs.compose.foundation)
                api(libs.compose.material3)
                api(libs.compose.ui)
                implementation(libs.compose.components.resources)

                // AppNavigator wraps a NavKey back stack.
                api(libs.navigation3.runtime)

                implementation(libs.coil.compose)
                implementation(libs.koin.compose)
                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.browser)
                implementation(libs.androidx.core)
            }
        }
        commonTest {
            dependencies {
                implementation(project(":core:testing"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockk)
            }
        }
        getByName("desktopTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.compose.desktop.ui.test.junit4)
                implementation(currentDesktopComposeDependency())
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.karakept.core.ui.resources"
}

// See :composeApp — without it `./gradlew test` would skip this module's suites.
tasks.register("test") {
    group = "verification"
    description = "Alias for allTests."
    dependsOn("allTests")
}

fun currentDesktopComposeDependency(): Provider<MinimalExternalModuleDependency> {
    val os = org.gradle.internal.os.OperatingSystem.current()
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    return when {
        os.isMacOsX -> if (isArm64) libs.compose.desktop.macos.arm64 else libs.compose.desktop.macos.x64
        os.isWindows -> if (isArm64) libs.compose.desktop.windows.arm64 else libs.compose.desktop.windows.x64
        else -> if (isArm64) libs.compose.desktop.linux.arm64 else libs.compose.desktop.linux.x64
    }
}
