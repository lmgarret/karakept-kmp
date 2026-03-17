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
                // API Client (generated from OpenAPI spec)
                implementation(project(":api-client"))
                
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

                // HTML Parsing (KMP)
                implementation(libs.ksoup)

                // Notifications (KMP — Android + Desktop)
                implementation(libs.kmpnotifier)

                // Native WebView (WKWebView on macOS, WebView2 on Windows, WebKitGTK on Linux)
                implementation(libs.compose.webview)
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
                implementation(libs.androidx.core.splashscreen)
                implementation(libs.androidx.browser)
                implementation(libs.androidx.work.runtime)
                implementation(libs.koin.androidx.workmanager)
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
                // System Tray (native menus with icons, HiDPI support)
                implementation(libs.compose.native.tray)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.testcontainers.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.junit)
                implementation(libs.mockk)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockk)
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
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0"
        manifestPlaceholders["appName"] = "Karakept"
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
            manifestPlaceholders["appName"] = "Karakept Dev"
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
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // SOFTWARE_FAST is only supported on Linux; on macOS use the default (Metal).
        // The actual property is set conditionally in main.kt at runtime.
        // jvmArgs += "-Dskiko.renderApi=SOFTWARE_FAST"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Karakept"
            packageVersion = (project.findProperty("versionName") as String?) ?: "1.0.0"
            modules("jdk.unsupported")
            linux {
                iconFile.set(project.file("src/commonMain/composeResources/drawable/icon.png"))
            }
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
                bundleID = "com.karakept.app"
                appCategory = "public.app-category.productivity"
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleURLTypes</key>
                        <array>
                            <dict>
                                <key>CFBundleURLName</key>
                                <string>com.karakept.app</string>
                                <key>CFBundleURLSchemes</key>
                                <array>
                                    <string>karakept</string>
                                </array>
                            </dict>
                        </array>
                    """
                }
            }
        }
    }
}


configurations.all {
    resolutionStrategy {
        force(libs.kotlinx.datetime.get().toString())
    }
}

// Flatpak packaging — requires flatpak-builder and org.gnome.Platform//46 installed on the host.
// Usage: ./gradlew packageFlatpak
// Output: composeApp/build/flatpak/Karakept.flatpak
run {
    val flatpakDir = layout.buildDirectory.dir("flatpak")
    val manifestFile = rootProject.file("flatpak/com.karakept.app.yml")

    val flatpakBuild = tasks.register<Exec>("flatpakBuild") {
        group = "compose desktop"
        description = "Runs flatpak-builder to populate the local Flatpak repo (internal)"
        dependsOn("createDistributable")
        // Pass -Pflatpak.disableSandbox=true when building in CI environments that
        // don't support user namespaces (e.g. GitHub Actions).
        val disableSandbox = project.findProperty("flatpak.disableSandbox")?.toString()?.toBoolean() ?: false
        commandLine(buildList {
            add("flatpak-builder")
            if (disableSandbox) add("--disable-sandbox")
            add("--force-clean")
            add("--repo=${flatpakDir.get().dir("repo").asFile.absolutePath}")
            add(flatpakDir.get().dir("build-dir").asFile.absolutePath)
            add(manifestFile.absolutePath)
        })
    }

    tasks.register<Exec>("packageFlatpak") {
        group = "compose desktop"
        description = "Creates a distributable Flatpak bundle (.flatpak) — requires flatpak-builder and org.gnome.Platform//46"
        dependsOn(flatpakBuild)
        val bundleFile = flatpakDir.get().file("Karakept.flatpak").asFile
        commandLine(
            "flatpak",
            "build-bundle",
            flatpakDir.get().dir("repo").asFile.absolutePath,
            bundleFile.absolutePath,
            "com.karakept.app",
        )
        doLast {
            println("Flatpak bundle: ${bundleFile.absolutePath}")
        }
    }
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Post-process DMG to set volume icon (fixes OpenJDK icon in Finder title bar)
if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
    val setDmgVolumeIcon = tasks.register("setDmgVolumeIcon") {
        group = "compose desktop"
        description = "Sets the volume icon on the packaged DMG"

        doLast {
            val dmgDir = layout.buildDirectory.dir("compose/binaries/main/dmg").get().asFile
            val iconFile = project.file("src/desktopMain/resources/icon.icns")
            val dmg = dmgDir.listFiles()?.firstOrNull { it.extension == "dmg" }
                ?: error("No DMG found in $dmgDir")
            val rwDmg = File(dmg.parentFile, "rw-${dmg.name}")
            val mountPoint = "/Volumes/KarakeptVolumeIcon"

            project.exec { commandLine("hdiutil", "convert", dmg.absolutePath, "-format", "UDRW", "-o", rwDmg.absolutePath) }
            project.exec { commandLine("hdiutil", "attach", rwDmg.absolutePath, "-mountpoint", mountPoint, "-nobrowse") }
            try {
                iconFile.copyTo(File(mountPoint, ".VolumeIcon.icns"), overwrite = true)
                project.exec { commandLine("SetFile", "-a", "C", mountPoint) }
            } finally {
                project.exec { commandLine("hdiutil", "detach", mountPoint) }
            }
            dmg.delete()
            project.exec { commandLine("hdiutil", "convert", rwDmg.absolutePath, "-format", "UDZO", "-o", dmg.absolutePath) }
            rwDmg.delete()
            println("Volume icon set on ${dmg.name}")
        }
    }

    afterEvaluate {
        tasks.named("packageDmg") {
            finalizedBy(setDmgVolumeIcon)
        }
    }
}
