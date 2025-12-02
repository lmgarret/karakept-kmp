import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
    
    jvm("desktop")
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.material)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(compose.materialIconsExtended)
                
                // Voyager
                implementation(libs.voyager.navigator)
                implementation(libs.voyager.screenModel)
                implementation(libs.voyager.transitions)
                implementation(libs.voyager.koin)

                // Ktor
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.logging)

                // Coil
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor)

                // Koin
                implementation(libs.koin.core)
                implementation(libs.koin.compose)

                // Room
                implementation(libs.androidx.room.runtime)
                implementation(libs.sqlite.bundled)
                
                // Coroutines
                implementation(libs.kotlinx.coroutines.core)
                
                // Serialization
                implementation(libs.kotlinx.serialization.json)
                
                // DateTime
                implementation(libs.kotlinx.datetime)

                // DataStore
                implementation(libs.androidx.datastore.preferences)

                // HTML Parsing
                implementation(libs.jsoup)

                // Drag and Drop Reordering
                implementation(libs.reorderable)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(compose.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.koin.android)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.datastore.preferences.android)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // ktor-client-darwin is for native macOS/iOS, not JVM desktop.
                // We use OkHttp for desktop (JVM).
                implementation(libs.ktor.client.okhttp)
                // Coroutines Swing dispatcher for desktop Main dispatcher
                implementation(libs.kotlinx.coroutines.swing)
                
                // JavaFX
                val osName = System.getProperty("os.name")
                val targetOs = when {
                    osName == "Mac OS X" -> "mac"
                    osName.startsWith("Win") -> "win"
                    osName.startsWith("Linux") -> "linux"
                    else -> error("Unsupported OS: $osName")
                }

                implementation("org.openjfx:javafx-base:21.0.1:$targetOs")
                implementation("org.openjfx:javafx-graphics:21.0.1:$targetOs")
                implementation("org.openjfx:javafx-controls:21.0.1:$targetOs")
                implementation("org.openjfx:javafx-swing:21.0.1:$targetOs")
                implementation("org.openjfx:javafx-web:21.0.1:$targetOs")
                implementation("org.openjfx:javafx-media:21.0.1:$targetOs")
            }
        }
    }
}

android {
    namespace = "com.karakept.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.karakept.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Use debug signing for testing release builds
            // For production, replace with proper release signing configuration
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    dependencies {
        debugImplementation(compose.uiTooling)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspCommonMainMetadata", libs.androidx.room.compiler)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.karakept.app"
            packageVersion = "1.0.0"
        }
    }
}

// Force Skiko version to resolve version mismatch
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.9.4.2")
        force("org.jetbrains.skiko:skiko:0.9.4.2")
        force("org.jetbrains.skiko:skiko-awt:0.9.4.2")
        
        // Force all Compose dependencies to use the same version to prevent API mismatches
        force("org.jetbrains.compose.foundation:foundation:1.7.1")
        force("org.jetbrains.compose.foundation:foundation-layout:1.7.1")
        force("org.jetbrains.compose.ui:ui:1.7.1")
        force("org.jetbrains.compose.ui:ui-graphics:1.7.1")
        force("org.jetbrains.compose.ui:ui-text:1.7.1")
        force("org.jetbrains.compose.ui:ui-unit:1.7.1")
        force("org.jetbrains.compose.ui:ui-util:1.7.1")
        force("org.jetbrains.compose.ui:ui-geometry:1.7.1")
        force("org.jetbrains.compose.runtime:runtime:1.7.1")
        force("org.jetbrains.compose.animation:animation:1.7.1")
        force("org.jetbrains.compose.animation:animation-core:1.7.1")
    }
}
