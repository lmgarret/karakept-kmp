plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    // The settings and sync state classes here are rendered directly; see :core:model.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.ExperimentalStdlibApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.karakept.core.data"
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
                api(project(":core:model"))
                implementation(project(":api-client"))

                // Settings expose Compose colours, and ActionSnackbarManager a SnackbarDuration.
                implementation(libs.compose.ui)
                implementation(libs.compose.material3)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.logging)

                api(libs.androidx.room.runtime)
                implementation(libs.sqlite.bundled)
                api(libs.androidx.datastore.preferences)

                api(libs.kotlinx.coroutines.core)
                // dataModule is a Koin module; the app includes it.
                api(libs.koin.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ksoup)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.datastore.preferences.android)
                // FileProvider (FileUtils.android.kt). :composeApp gets it through activity-compose;
                // this module has to ask for it. 1.16.0 is what activity-compose already pulls in.
                implementation(libs.androidx.core)
            }
        }
        commonTest {
            dependencies {
                implementation(project(":core:testing"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockk)
                implementation(libs.ktor.client.mock)
            }
        }
        getByName("desktopTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspCommonMainMetadata", libs.androidx.room.compiler)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

configurations.all {
    resolutionStrategy {
        force(libs.kotlinx.datetime.get().toString())
    }
}

// The Docker-backed integration tests need a running backend; they run locally or in a dedicated
// job. Pass -PwithIntegrationTests to include them.
tasks.named<Test>("desktopTest") {
    if (!project.hasProperty("withIntegrationTests")) {
        exclude("**/data/integration/**")
    }
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// See :composeApp — without it `./gradlew test` would skip this module's suites.
tasks.register("test") {
    group = "verification"
    description = "Alias for allTests."
    dependsOn("allTests")
}
