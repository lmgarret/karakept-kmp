import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.VariantBuilder
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.awt.Color as AwtColor
import java.awt.Graphics2D as AwtGraphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage as AwtBufferedImage
import javax.imageio.ImageIO
import javax.inject.Inject
import java.util.concurrent.ConcurrentLinkedQueue

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.compose.hot-reload")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
}

// The deprecated `compose.desktop.currentOs` shorthand picks the desktop-jvm-<os>-<arch> artifact
// matching the machine running the build, using OS/arch detection that lives in an internal
// (non-public) part of the Compose Gradle plugin. Reimplemented here — matching
// org.jetbrains.compose.internal.utils.OsUtilsKt — against the explicit catalog entries.
fun currentDesktopComposeDependency(): Provider<MinimalExternalModuleDependency> {
    val os = OperatingSystem.current()
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    return when {
        os.isMacOsX -> if (isArm64) libs.compose.desktop.macos.arm64 else libs.compose.desktop.macos.x64
        os.isWindows -> if (isArm64) libs.compose.desktop.windows.arm64 else libs.compose.desktop.windows.x64
        else -> if (isArm64) libs.compose.desktop.linux.arm64 else libs.compose.desktop.linux.x64
    }
}

kotlin {
    // Opt in to ExperimentalStdlibApi globally (enum.entries, etc.)
    compilerOptions {
        optIn.add("kotlin.ExperimentalStdlibApi")
        // `expect`/`actual` classes are still flagged Beta; this is the flag the warning itself
        // points at. The app relies on them for every platform boundary (AppIconManager,
        // FileUtils, SecureCredentialStore, ...), so the warning is pure noise.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    jvm("desktop")
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                // API Client (generated from OpenAPI spec)
                implementation(project(":api-client"))
                
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.material)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.components.ui.tooling.preview)
                
                // Navigation 3
                implementation(libs.navigation3.runtime)
                implementation(libs.navigation3.ui)
                implementation(libs.androidx.lifecycle.viewmodel.navigation3)

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
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.koin.compose.navigation3)

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

                // Native WebView (WKWebView on macOS, WebView2 on Windows, WebKitGTK on Linux)
                implementation(libs.compose.webview)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.koin.android)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.datastore.preferences.android)
                implementation(libs.androidx.core.splashscreen)
                implementation(libs.androidx.browser)
                implementation(libs.androidx.webkit)
                implementation(libs.androidx.work.runtime)
                implementation(libs.koin.androidx.workmanager)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(currentDesktopComposeDependency())
                // ktor-client-darwin is for native macOS/iOS, not JVM desktop.
                // We use OkHttp for desktop (JVM).
                implementation(libs.ktor.client.okhttp)
                // Coroutines Swing dispatcher for desktop Main dispatcher
                implementation(libs.kotlinx.coroutines.swing)
                // System Tray (native menus with icons, HiDPI support)
                implementation(libs.compose.native.tray)
                // Notifications (native D-Bus on Linux, Notification Centre on macOS)
                implementation(libs.knotify)
                // Native file picker (GTK on Linux, NSOpenPanel on macOS)
                implementation(libs.nativefiledialog)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.junit)
                implementation(libs.mockk)
                // Headless Compose layout assertions — a reader block's geometry is not
                // observable from a pure unit test, and desktopTest is the PR CI entry point.
                implementation(libs.compose.desktop.ui.test.junit4)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.robolectric)
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.test.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockk)
                implementation(libs.ktor.client.mock)
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

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.karakept.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0"
        manifestPlaceholders["appName"] = "Karakept"
        buildConfigField("boolean", "IS_DEV", "false")
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
            buildConfigField("boolean", "IS_DEV", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    dependencies {
        debugImplementation(libs.compose.ui.tooling)
    }
}

// The `release` and `devRelease` build types are non-minified (isMinifyEnabled = false),
// so their unit tests exercise byte-for-byte the same code as `debug`. Running the
// (Robolectric-backed) Android unit suite once per build type triples CI test time for
// zero extra coverage, so only the `debug` variant's unit tests are kept. `./gradlew test`
// then runs testDebugUnitTest + desktopTest instead of three Android variants + desktop.
androidComponents {
    fun VariantBuilder.disableUnitTests() {
        (this as HasHostTestsBuilder).hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
    }
    beforeVariants(selector().withBuildType("release")) { it.disableUnitTests() }
    beforeVariants(selector().withBuildType("devRelease")) { it.disableUnitTests() }
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
        // Dev mode: ./gradlew run -Pdev=true → window title shows "(DEV)", onboarding badge visible
        if (project.hasProperty("dev")) {
            jvmArgs += "-Dkarakept.dev=true"
        }
        // ZGC reduces GC pause times to <1ms, eliminating the stutters/jank
        // that G1GC (the default) causes in interactive Compose Desktop apps.
        // -XX:+ZGenerational enables the generational mode added in JDK 21,
        // which improves throughput without sacrificing low-latency guarantees.
        jvmArgs += "-XX:+UseZGC"
        jvmArgs += "-XX:+ZGenerational"
        // Keep full stack traces on repeated implicit exceptions (NPE/AIOOBE/etc.). HotSpot's
        // "fast throw" optimization otherwise swaps in a preallocated, stackless copy after a
        // site throws often — which defeats the a11y-crash guard, whose classification relies on
        // seeing the androidx.compose.ui.platform.a11y frames. See A11yCrashGuard.
        jvmArgs += "-XX:-OmitStackTraceInFastThrow"
        // SOFTWARE_FAST is only supported on Linux; on macOS use the default (Metal).
        // The actual property is set conditionally in main.kt at runtime.
        // jvmArgs += "-Dskiko.renderApi=SOFTWARE_FAST"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
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
            windows {
                iconFile.set(project.file("src/desktopMain/resources/win-icon.ico"))
                menu = true
                menuGroup = "Karakept"
                shortcut = true
            }
        }
    }
}


configurations.all {
    resolutionStrategy {
        force(libs.kotlinx.datetime.get().toString())
    }
}

// The nativefiledialog JAR bundles kotlin-stdlib classes that shadow the project's
// kotlin-stdlib on a flat classpath. This causes NoSuchMethodError in Skiko at startup.
// Distributions (DMG/Flatpak) use jlink which handles this correctly, so only the
// `run` task (plain java -cp) is affected.  Exclude it from `run`; FilePicker.jvm.kt
// falls back to Swing JFileChooser when nativefiledialog classes aren't available.
afterEvaluate {
    listOf("run", "hotRunDesktop").forEach { taskName ->
        tasks.findByName(taskName)?.let { task ->
            (task as? JavaExec)?.classpath = (task as JavaExec).classpath.filter { "nativefiledialog" !in it.name }
        }
    }
}

// Flatpak packaging — requires flatpak-builder and org.gnome.Platform//48 installed on the host.
// Usage: ./gradlew packageFlatpak
// Output: composeApp/build/flatpak/Karakept.flatpak
run {
    val flatpakDir = layout.buildDirectory.dir("flatpak")
    val manifestFile = rootProject.file("flatpak/com.karakept.app.yml")

    // Resize the macOS app icon to 512x512 for the Flatpak hicolor icon theme.
    // macos-icon.png already has rounded corners, so no masking is needed.
    val flatpakResizeIcon = tasks.register("flatpakResizeIcon") {
        // Use the macOS icon which already has properly rounded corners.
        val srcIcon = file("src/desktopMain/resources/macos-icon.png")
        val destIcon = flatpakDir.get().file("icon-512.png").asFile
        inputs.file(srcIcon)
        inputs.property("iconVersion", 3) // bump to bust Gradle up-to-date cache
        outputs.file(destIcon)
        doLast {
            destIcon.parentFile.mkdirs()
            val size = 512
            val src = javax.imageio.ImageIO.read(srcIcon)
            val out = AwtBufferedImage(size, size, AwtBufferedImage.TYPE_INT_ARGB)
            val g2 = out.createGraphics()
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.drawImage(src, 0, 0, size, size, null)
            g2.dispose()
            javax.imageio.ImageIO.write(out, "PNG", destIcon)
        }
    }

    val flatpakBuild = tasks.register<Exec>("flatpakBuild") {
        group = "compose desktop"
        description = "Runs flatpak-builder to populate the local Flatpak repo (internal)"
        dependsOn("createDistributable", flatpakResizeIcon)
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
        description = "Creates a distributable Flatpak bundle (.flatpak) — requires flatpak-builder and org.gnome.Platform//48"
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

// The desktop test task is the PR CI entry point (./gradlew desktopTest). Two adjustments:
//  1. Drop the nativefiledialog jar from the test runtime classpath. It bundles an outdated
//     kotlin-stdlib that shadows the project's stdlib on the flat test classpath (same problem
//     the `run` task works around below), causing NoSuchMethodError for kotlin.time.Clock.
//     Tests never use the native picker (FilePicker falls back to Swing), so removing it is safe.
//  2. Exclude the Docker-backed integration tests by default — they require a running backend
//     and are meant to run locally / in a dedicated job. Pass -PwithIntegrationTests to include.
tasks.named<Test>("desktopTest") {
    classpath = classpath.filter { "nativefiledialog" !in it.name }
    if (!project.hasProperty("withIntegrationTests")) {
        exclude("**/data/integration/**")
    }
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    // Log per-class durations and flag individual tests that take > 500ms.
    // This gives the data needed to identify slow test classes and outliers.
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) = Unit
        override fun beforeTest(testDescriptor: TestDescriptor) = Unit

        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            val ms = result.endTime - result.startTime
            if (ms >= 500) println("  [SLOW ${ms}ms] ${testDescriptor.className} > ${testDescriptor.name}")
        }

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) return
            val ms = result.endTime - result.startTime
            println("  [suite ${ms}ms] ${suite.displayName}: ${result.testCount} tests")
        }
    })
}

// Gradle 9 removed Project.exec/javaexec; external processes must run through the
// injected ExecOperations service. Obtaining it via an injected interface keeps the
// task action configuration-cache compatible.
interface ExecServiceInjection {
    @get:Inject val execOps: ExecOperations
}

// Post-process DMG to set volume icon (fixes OpenJDK icon in Finder title bar)
if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
    val execInjection = objects.newInstance<ExecServiceInjection>()
    val dmgDir = layout.buildDirectory.dir("compose/binaries/main/dmg")
    val iconFile = project.file("src/desktopMain/resources/icon.icns")

    val setDmgVolumeIcon = tasks.register("setDmgVolumeIcon") {
        group = "compose desktop"
        description = "Sets the volume icon on the packaged DMG"
        // The icon is the only external input; the DMG dir is both consumed (from
        // packageDmg) and rewritten in place, so declaring it as the output lets Gradle
        // skip the hdiutil round-trip when neither the icon nor the packaged DMG changed.
        inputs.file(iconFile)
        outputs.dir(dmgDir)

        doLast {
            val execOps = execInjection.execOps
            val dmg = dmgDir.get().asFile.listFiles()?.firstOrNull { it.extension == "dmg" }
                ?: error("No DMG found in ${dmgDir.get().asFile}")
            val rwDmg = File(dmg.parentFile, "rw-${dmg.name}")
            val mountPoint = "/Volumes/KarakeptVolumeIcon"

            execOps.exec { commandLine("hdiutil", "convert", dmg.absolutePath, "-format", "UDRW", "-o", rwDmg.absolutePath) }
            execOps.exec { commandLine("hdiutil", "attach", rwDmg.absolutePath, "-mountpoint", mountPoint, "-nobrowse") }
            try {
                iconFile.copyTo(File(mountPoint, ".VolumeIcon.icns"), overwrite = true)
                execOps.exec { commandLine("SetFile", "-a", "C", mountPoint) }
            } finally {
                execOps.exec { commandLine("hdiutil", "detach", mountPoint) }
            }
            dmg.delete()
            execOps.exec { commandLine("hdiutil", "convert", rwDmg.absolutePath, "-format", "UDZO", "-o", dmg.absolutePath) }
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

// ============================================================================
// Windows: MSI + EXE installers with a custom, branded WiX wizard (banner + welcome image)
// ----------------------------------------------------------------------------
// Compose's packageMsi/packageExe can neither enable the WiX dialog UI nor pass a custom
// --resource-dir, and the default jpackage MSI ships NO wizard dialogs at all, so
// msiexec falls back to its bare basic UI. These tasks run jpackage directly on the
// createDistributable app image with:
//   --win-dir-chooser   -> emits the full WixUI_InstallDir dialog set (which has a banner)
//   --resource-dir       -> our main.wxs override that points WixUIBannerBmp / WixUIDialogBmp
//                           at the generated bitmaps (paths passed via the environment)
// The stock packageMsi/packageExe tasks are disabled and delegated to these (see the
// afterEvaluate block below), so `gradlew packageMsi`/`packageExe` and CI get branding
// for free. Requires JDK 21; WiX is downloaded by :unzipWix.
// ============================================================================
if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
    val winResDir = layout.buildDirectory.dir("jpackage/resources")
    val winLogo = project.file("src/desktopMain/resources/win-icon.png")
    val winShot = rootProject.file("docs/screenshots/screenshot_linux_bookmark_list_and_reader.png")
    val winVersion = (project.findProperty("versionName") as String?) ?: "1.0.0"

    val generateWindowsInstallerBitmaps = tasks.register("generateWindowsInstallerBitmaps") {
        group = "compose desktop"
        description = "Generates the WiX installer banner/dialog bitmaps from the app icon and a screenshot"
        inputs.file(winLogo)
        inputs.file(winShot)
        inputs.property("rev", 2) // bump to bust the up-to-date cache when the layout changes
        outputs.dir(winResDir)
        doLast {
            val dir = winResDir.get().asFile
            dir.mkdirs()
            fun hints(g: AwtGraphics2D) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            }
            // Progressive (halving) downscale keeps a large source crisp at small sizes —
            // a single-step bicubic reduction of the 824px logo to 48px looks soft.
            fun downscale(src: AwtBufferedImage, target: Int): AwtBufferedImage {
                var cur = src
                while (cur.width / 2 >= target) {
                    val w = maxOf(target, cur.width / 2)
                    val h = maxOf(target, cur.height / 2)
                    val next = AwtBufferedImage(w, h, AwtBufferedImage.TYPE_INT_ARGB)
                    next.createGraphics().run { hints(this); drawImage(cur, 0, 0, w, h, null); dispose() }
                    cur = next
                }
                if (cur.width == target && cur.height == target) return cur
                val out = AwtBufferedImage(target, target, AwtBufferedImage.TYPE_INT_ARGB)
                out.createGraphics().run { hints(this); drawImage(cur, 0, 0, target, target, null); dispose() }
                return out
            }
            // Banner: 493x58, white, app logo right-aligned (shows top-right on interior pages).
            val banner = AwtBufferedImage(493, 58, AwtBufferedImage.TYPE_INT_RGB)
            banner.createGraphics().run {
                hints(this)
                color = AwtColor.WHITE; fillRect(0, 0, 493, 58)
                val s = 48
                drawImage(downscale(ImageIO.read(winLogo), s), 493 - s - 8, (58 - s) / 2, null)
                dispose()
            }
            ImageIO.write(banner, "bmp", dir.resolve("banner.bmp"))
            // Welcome/Exit dialog: 493x312, screenshot cover-cropped into a left band (drawer + list);
            // the right side stays white so WiX's dark title/body text remains legible.
            val dw = 493; val dh = 312; val band = 170
            val dialog = AwtBufferedImage(dw, dh, AwtBufferedImage.TYPE_INT_RGB)
            dialog.createGraphics().run {
                hints(this)
                color = AwtColor.WHITE; fillRect(0, 0, dw, dh)
                val shot = ImageIO.read(winShot)
                val srcW = (band.toDouble() * shot.height / dh).toInt() // cover-crop, left-anchored
                drawImage(shot, 0, 0, band, dh, 0, 0, srcW, shot.height, null)
                color = AwtColor(220, 220, 220); drawLine(band, 0, band, dh)
                dispose()
            }
            ImageIO.write(dialog, "bmp", dir.resolve("dialog.bmp"))
            println("Windows installer bitmaps written to $dir")
        }
    }

    // Builds the branded Windows installers. The MSI and EXE are fully independent jpackage
    // runs — separate --temp and --dest dirs; they only *read* the shared app-image, WiX
    // binaries and bitmaps — so we launch them concurrently from a single task instead of as
    // two sequential Exec tasks. Same-project tasks never run in parallel under --parallel,
    // and the two jpackage invocations are ~85s each (the build's critical-path tail), so
    // running them on separate threads roughly halves that tail on multi-core machines.
    // Both types share the same WiX pipeline, so the custom main.wxs + bitmaps apply
    // identically (the exe simply wraps the branded msi).
    val execInjection = objects.newInstance<ExecServiceInjection>()
    val installerTypes = listOf("msi", "exe")
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/Karakept")
    val winIcon = project.file("src/desktopMain/resources/win-icon.ico")
    val resDir = project.file("jpackage")
    // Capture the root build dir as a Provider (config time) so the task action below closes
    // over a serializable value rather than `rootProject`, which the configuration cache
    // cannot store.
    val rootBuildDir = rootProject.layout.buildDirectory
    val wixDir = rootBuildDir.dir("wix311")
    val jpackageExe = File(System.getProperty("java.home"), "bin/jpackage.exe")
    val destDirs = installerTypes.associateWith { layout.buildDirectory.dir("compose/binaries/main/$it") }
    val tmpDirs = installerTypes.associateWith { layout.buildDirectory.dir("jpackage/temp-$it") }

    val packageWindowsInstallers = tasks.register("packageWindowsInstallersBranded") {
        group = "compose desktop"
        description = "Builds the branded Windows MSI + EXE installers in parallel with the custom WiX wizard"
        dependsOn("createDistributable", generateWindowsInstallerBitmaps, ":unzipWix")

        inputs.dir(appImage)
        inputs.file(winIcon)
        inputs.dir(resDir)
        inputs.dir(winResDir)
        inputs.property("version", winVersion)
        destDirs.values.forEach { outputs.dir(it) }

        doLast {
            check(jpackageExe.exists()) { "jpackage not found at $jpackageExe — package with JDK 21." }
            // WiX (candle/light) is downloaded by :unzipWix; jpackage finds it via PATH.
            val wixBin = wixDir.get().asFile.takeIf { File(it, "candle.exe").exists() }
                ?: rootBuildDir.asFile.get().walkTopDown()
                    .firstOrNull { it.name.equals("candle.exe", ignoreCase = true) }?.parentFile
                ?: error("WiX candle.exe not found under ${rootBuildDir.get()} (did :unzipWix run?)")
            val pathWithWix = wixBin.absolutePath + File.pathSeparator + (System.getenv("PATH") ?: "")
            // Bitmap paths are injected via the environment so the committed main.wxs stays portable.
            val bannerBmp = winResDir.get().file("banner.bmp").asFile.absolutePath
            val dialogBmp = winResDir.get().file("dialog.bmp").asFile.absolutePath
            val execOps = execInjection.execOps
            val appImagePath = appImage.get().asFile.absolutePath
            val iconPath = winIcon.absolutePath
            val resDirPath = resDir.absolutePath

            val failures = ConcurrentLinkedQueue<Throwable>()
            val threads = installerTypes.map { type ->
                val destDir = destDirs.getValue(type).get().asFile
                val tmpDir = tmpDirs.getValue(type).get().asFile
                Thread({
                    try {
                        // jpackage requires --temp absent/empty and refuses to overwrite an existing installer.
                        tmpDir.deleteRecursively()
                        destDir.mkdirs()
                        destDir.resolve("Karakept-$winVersion.$type").delete()
                        execOps.exec {
                            commandLine(
                                jpackageExe.absolutePath,
                                "--type", type,
                                "--name", "Karakept",
                                "--app-version", winVersion,
                                "--app-image", appImagePath,
                                // Multi-resolution app icon for the installer .exe, shortcuts and ARP entry.
                                "--icon", iconPath,
                                "--win-dir-chooser",
                                "--win-menu", "--win-menu-group", "Karakept",
                                "--win-shortcut", "--win-shortcut-prompt",
                                "--resource-dir", resDirPath,
                                "--dest", destDir.absolutePath,
                                "--temp", tmpDir.absolutePath,
                            )
                            environment("PATH", pathWithWix)
                            environment("KARAKEPT_BANNER_BMP", bannerBmp)
                            environment("KARAKEPT_DIALOG_BMP", dialogBmp)
                        }
                        println("Branded $type: ${destDir.resolve("Karakept-$winVersion.$type")}")
                    } catch (t: Throwable) {
                        failures.add(t)
                    }
                }, "jpackage-$type")
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            failures.firstOrNull()?.let { throw it }
        }
    }

    // Make the stock Compose tasks produce the BRANDED installers: disable their own
    // (unbranded) jpackage action and route both to the combined task, which writes to the
    // same build/compose/binaries/main/{msi,exe} locations. `gradlew packageMsi`/`packageExe`
    // (and the release CI that calls them) thus get branding — and parallel packaging — free.
    afterEvaluate {
        tasks.named("packageMsi") { enabled = false; dependsOn(packageWindowsInstallers) }
        tasks.named("packageExe") { enabled = false; dependsOn(packageWindowsInstallers) }
    }
}
