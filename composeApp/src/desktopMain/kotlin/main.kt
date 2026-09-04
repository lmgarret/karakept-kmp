import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.setWindowState
import com.karakept.app.di.appModule
import com.karakept.app.services.BackgroundSyncOrchestrator
import com.karakept.app.services.BackgroundSyncScheduler
import com.karakept.app.services.DesktopNotificationProvider
import com.karakept.app.services.NotificationProvider
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.nucleusframework.composenativetray.utils.IconRenderProperties
import dev.nucleusframework.composenativetray.utils.isMenuBarInDarkMode
import io.github.kdroidfilter.knotify.builder.AppConfig
import io.github.kdroidfilter.knotify.builder.ExperimentalNotificationsApi
import io.github.kdroidfilter.knotify.builder.Notification
import io.github.kdroidfilter.knotify.builder.NotificationInitializer
import io.github.kdroidfilter.knotify.builder.notification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.getKoin
import java.awt.GraphicsEnvironment

/**
 * URL received via the karakept:// URL scheme.
 * Each new URL bumps [shareUrlVersion] so the composition can detect changes
 * even when the same URL is shared twice.
 */
private var pendingShareUrl by mutableStateOf<String?>(null)
private var shareUrlVersion by mutableStateOf(0)

@OptIn(FlowPreview::class)
fun main(args: Array<String> = emptyArray()) {
    // macOS-specific AWT properties — must be set before AWT initializes.
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        // Set app name so notifications show "Karakept" instead of "java"
        System.setProperty("apple.awt.application.name", if (isDevBuild) "Karakept (DEV)" else "Karakept")

        // Let AWT/JBR follow the OS appearance automatically so the Compose
        // window chrome (title bar, toolbar) matches the current system theme.
        // Note: this does NOT affect the native NSMenu from ComposeNativeTray —
        // the library's Swift code controls menu appearance independently.
        System.setProperty("apple.awt.application.appearance", "system")
    }

    // On Linux, default to the OpenGL backend explicitly so Skiko doesn't
    // silently fall back to software rendering if GL context creation stumbles
    // on the first attempt. OPENGL is already the Skiko default, but being
    // explicit ensures env-var overrides (SKIKO_RENDER_API) still take effect
    // while guarding against any future default change.
    if (System.getProperty("os.name").lowercase().contains("linux") &&
        System.getenv("SKIKO_RENDER_API") == null &&
        System.getProperty("skiko.renderApi") == null
    ) {
        System.setProperty("skiko.renderApi", "OPENGL")
    }

    // On Linux, tell AWT to use "Karakept" as the WM_CLASS so KDE/GNOME can
    // match the window to the .desktop file's StartupWMClass=Karakept entry.
    // Without this the JVM reports the main class name (e.g. "MainKt") and the
    // desktop environment shows two taskbar entries on launch.
    if (System.getProperty("os.name").lowercase().contains("linux")) {
        System.setProperty("sun.awt.wmclass", "Karakept")
    }

    // On Linux Wayland, use the native Wayland AWT toolkit (JDK 21+)
    // instead of X11/XWayland — eliminates black bars on resize and improves performance.
    // Falls back to X11 automatically if Wayland socket is not available.
    // NOTE: disabled — WLToolkit causes "layout state is not idle before measure starts"
    // errors on JBR 21 when --socket=wayland is granted. The app runs correctly via
    // XWayland without it.
    // if (System.getenv("WAYLAND_DISPLAY") != null || java.io.File("/tmp/wayland-0").exists()) {
    //     try {
    //         Class.forName("sun.awt.wl.WLToolkit")
    //         System.setProperty("awt.toolkit.name", "WLToolkit")
    //     } catch (_: ClassNotFoundException) { }
    // }

    val isMac = System.getProperty("os.name").lowercase().contains("mac")
    val isLinux = System.getProperty("os.name").lowercase().contains("linux")
    val isWindows = System.getProperty("os.name").lowercase().contains("win")

    // Load the window icon before entering composition (non-composable).
    // macOS: use the macOS-styled icon and also push it to the Dock early so the
    //   default Java coffee-cup doesn't flash while the window is opening.
    // Linux: KDE/GNOME resolve the app icon from the hicolor theme via the .desktop
    //   file's Icon= entry, so Window(icon = null) is correct — no _NET_WM_ICON needed.
    //   Critically, we must NOT call Taskbar.getTaskbar() here on Linux: that call
    //   initialises AWT before application {} runs, which can surface a hidden AWT
    //   frame with the wrong WM_CLASS and briefly appear as a second taskbar entry.
    // macOS: also push the icon to the Dock early so the default Java coffee-cup
    // doesn't flash. Must happen before application {} (before AWT init).
    // Linux: load the icon for Window(icon = ...) but do NOT call Taskbar.getTaskbar()
    // here — that call initialises AWT before application {} and can surface a hidden
    // frame with the wrong WM_CLASS, briefly appearing as a second taskbar entry.
    val iconBytes = Thread.currentThread().contextClassLoader
        .getResourceAsStream("macos-icon.png")
        ?.readBytes()
    if (isMac && iconBytes != null) {
        try {
            var awtImage = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(iconBytes))
            if (awtImage != null) {
                if (isDevBuild) awtImage = overlayDevBanner(awtImage)
                if (java.awt.Taskbar.isTaskbarSupported()) {
                    java.awt.Taskbar.getTaskbar().iconImage = awtImage
                }
            }
        } catch (_: UnsupportedOperationException) { }
    }
    val iconImage = iconBytes?.let { bytes ->
        var awtImg = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
        if (isDevBuild && awtImg != null) awtImg = overlayDevBanner(awtImg)
        val finalBytes = awtImg?.toPngBytes() ?: bytes
        Image.makeFromEncoded(finalBytes).toComposeImageBitmap()
    }

    // Windows: full-colour, square app icon (win-icon.png) for the system tray so it
    // matches the taskbar/shortcut icon rather than the rounded macOS variant.
    val winIconImage = Thread.currentThread().contextClassLoader
        .getResourceAsStream("win-icon.png")
        ?.readBytes()
        ?.let { bytes ->
            var awtImg = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
            if (isDevBuild && awtImg != null) awtImg = overlayDevBanner(awtImg)
            val finalBytes = awtImg?.toPngBytes() ?: bytes
            Image.makeFromEncoded(finalBytes).toComposeImageBitmap()
        }

    // Load monochrome tray icon: trim adaptive-icon padding so the silhouette
    // fills the menu-bar slot. ComposeNativeTray's Painter overload handles
    // resizing (via IconRenderProperties) and light/dark tinting automatically.
    val trayIconImage = Thread.currentThread().contextClassLoader
        .getResourceAsStream("tray-icon.png")
        ?.readBytes()
        ?.let { bytes ->
            val src = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
                ?: return@let null
            // Find bounding box of non-transparent pixels
            var minX = src.width; var minY = src.height; var maxX = 0; var maxY = 0
            for (y in 0 until src.height) {
                for (x in 0 until src.width) {
                    if ((src.getRGB(x, y) ushr 24) > 0) {
                        if (x < minX) minX = x; if (y < minY) minY = y
                        if (x > maxX) maxX = x; if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX < minX) return@let null
            val cropped = src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1)
            Image.makeFromEncoded(cropped.toPngBytes()).toComposeImageBitmap()
        }

    // Register macOS URL scheme handler (karakept://save?url=...)
    // When the OS opens a karakept:// link, this callback fires even if the app is already running.
    if (java.awt.Desktop.isDesktopSupported()) {
        try {
            java.awt.Desktop.getDesktop().setOpenURIHandler { event ->
                val uri = event.uri
                if (uri.scheme == "karakept" && uri.host == "save") {
                    val url = uri.getQueryParameter("url")
                    if (!url.isNullOrBlank()) {
                        pendingShareUrl = url
                        shareUrlVersion++
                    }
                }
            }
        } catch (_: UnsupportedOperationException) {
            // Not supported on this platform (e.g. Linux without XDG)
        }
    }

    startKoin {
        modules(appModule, module {
            single<NotificationProvider> { DesktopNotificationProvider() }
        })
    }

    // Initialize background sync — mirrors KarakeptApp.initializeBackgroundSync() on Android.
    // Uses a long-lived scope that outlives individual Compose compositions.
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val bgSyncSettingsRepo = getKoin().get<SettingsRepository>()
    val bgSyncOrchestrator = getKoin().get<BackgroundSyncOrchestrator>()
    appScope.launch {
        combine(
            bgSyncSettingsRepo.backgroundSyncEnabled,
            bgSyncSettingsRepo.backgroundSyncFrequencyMinutes
        ) { enabled, frequency -> enabled to frequency }
            .distinctUntilChanged()
            .collect { (enabled, frequency) ->
                if (enabled) {
                    BackgroundSyncScheduler.schedule(
                        scope = appScope,
                        orchestrator = bgSyncOrchestrator,
                        frequencyMinutes = frequency
                    )
                } else {
                    BackgroundSyncScheduler.cancel()
                }
            }
    }

    // Initialize KNotify for native desktop notifications (D-Bus on Linux, NSUserNotification on macOS).
    // The app-level smallIcon is extracted from the JAR to a temp file automatically by KNotify.
    try {
        val iconUri = Thread.currentThread().contextClassLoader
            .getResource("macos-icon.png")?.toString()
        NotificationInitializer.configure(AppConfig(appName = "Karakept", smallIcon = iconUri))
    } catch (_: UnsatisfiedLinkError) {
        // libnotify.so not available — notifications will be silently skipped
    } catch (_: Exception) {
        // Resource extraction fails when running from a directory classpath (e.g. hotRunDesktop)
        // rather than a JAR. Notifications are silently skipped in that case.
    }

    // Read persisted window state before entering composition
    val settingsRepo = getKoin().get<SettingsRepository>()
    val savedMaximized = runBlocking { settingsRepo.windowMaximized.first() }
    val savedWidth = runBlocking { settingsRepo.windowWidth.first() }
    val savedHeight = runBlocking { settingsRepo.windowHeight.first() }
    val savedX = runBlocking { settingsRepo.windowX.first() }
    val savedY = runBlocking { settingsRepo.windowY.first() }

    application {
        // Guard against a Compose 1.11 desktop accessibility NPE that otherwise crashes the app
        // during rapid list changes (e.g. bulk mark-as-unread). See installA11yCrashGuard.
        LaunchedEffect(Unit) { installA11yCrashGuard() }

        val maxBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        var isWindowVisible by remember { mutableStateOf(true) }
        val coroutineScope = rememberCoroutineScope()
        val bookmarkRepo = remember { getKoin().get<BookmarkRepository>() }
        val serverRepo = remember { getKoin().get<ServerRepository>() }
        // Observe server connection status for tray menu
        var hasServer by remember { mutableStateOf(false) }
        var serverLabel by remember { mutableStateOf("") }
        var serverUrl by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            serverRepo.servers.collect { servers ->
                hasServer = servers.isNotEmpty()
                val server = servers.firstOrNull()
                serverLabel = server?.label?.takeIf { it.isNotBlank() } ?: server?.url ?: ""
                serverUrl = server?.url ?: ""
            }
        }

        // Validate saved position — ensure it's at least partially visible on some screen
        val savedPosition = if (savedX != null && savedY != null) {
            val inBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { device ->
                val bounds = device.defaultConfiguration.bounds
                savedX >= bounds.x - 100 && savedX < bounds.x + bounds.width &&
                    savedY >= bounds.y - 100 && savedY < bounds.y + bounds.height
            }
            if (inBounds) WindowPosition(savedX.dp, savedY.dp) else WindowPosition.PlatformDefault
        } else {
            WindowPosition.PlatformDefault
        }

        val state = rememberWindowState(
            placement = if (savedMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            width = (savedWidth ?: maxBounds.width.toFloat()).dp,
            height = (savedHeight ?: maxBounds.height.toFloat()).dp,
            position = savedPosition
        )

        // Debounced persistence of window state changes
        LaunchedEffect(Unit) {
            snapshotFlow {
                Triple(state.size, state.position, state.placement)
            }
                .debounce(1000)
                .collect { (size, pos, placement) ->
                    val absPos = pos as? WindowPosition.Absolute
                    settingsRepo.setWindowState(
                        width = size.width.value,
                        height = size.height.value,
                        x = absPos?.x?.value ?: 0f,
                        y = absPos?.y?.value ?: 0f,
                        maximized = placement == WindowPlacement.Maximized
                    )
                }
        }

        // System tray icon with native menu (ComposeNativeTray).
        //
        // macOS: render the monochrome tray-icon.png as a template-image style silhouette,
        //   tinted white in dark menu bar and black in light menu bar. padding(16.dp) keeps
        //   the icon slightly smaller than the 22 pt slot (~18 pt), matching native icons.
        //
        // Linux: render the full-colour app icon. Linux DEs (GNOME, KDE…) display coloured
        //   tray icons and do not apply automatic template-image inversion.
        //
        // Dark mode: isMenuBarInDarkMode() handles detection on all platforms
        //   (macOS wallpaper-based, KDE theme-based, GNOME/XFCE/CINNAMON always dark).
        val isDarkMenuBar = isMenuBarInDarkMode()
        val trayIconTint = if (isDarkMenuBar) Color.White else Color.Black
        // Render menu-item icons at 32×32 px so Retina displays get a crisp
        // @2x representation (the Swift side sets NSSize 16×16 pt).
        val retinaMenuIcon = IconRenderProperties(
            sceneWidth = 64, sceneHeight = 64,
            targetWidth = 32, targetHeight = 32
        )
        // Linux tray icon: render at 128×128 so the icon stays crisp on HiDPI panels.
        // The DE downscales to its preferred slot size. Without this, the library default
        // is 192 scene → 24 target on Linux which looks blurry on modern displays.
        val linuxTrayIconProps = IconRenderProperties.withoutScalingAndAliasing(
            sceneWidth = 128, sceneHeight = 128
        )
        // On Linux, skip the system tray if there is no D-Bus session — the
        // energye/systray native bridge panics with a nil-pointer dereference
        // when DBus is unavailable (e.g. devcontainer, CI, headless servers).
        val hasDBus = !isLinux ||
            System.getenv("DBUS_SESSION_BUS_ADDRESS") != null
        if (trayIconImage != null && hasDBus) {
            Tray(
                iconRenderProperties = if (!isMac) linuxTrayIconProps else IconRenderProperties.forCurrentOperatingSystem(),
                iconContent = {
                    if (isMac) {
                        // macOS: monochrome silhouette, adaptive tint
                        Image(
                            bitmap = trayIconImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            colorFilter = ColorFilter.tint(trayIconTint)
                        )
                    } else if (isWindows && winIconImage != null) {
                        // Windows: full-colour, square app icon matching the taskbar/shortcut.
                        Image(
                            bitmap = winIconImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (iconImage != null) {
                        // Linux: full-colour app icon, no padding — the 128×128
                        // scene gives plenty of resolution for crisp rendering.
                        Image(
                            bitmap = iconImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
                tooltip = if (isDevBuild) "Karakept (DEV)" else "Karakept",
                primaryAction = { isWindowVisible = !isWindowVisible },
                menuContent = {
                    // Server status (informational, disabled)
                    Item(
                        label = if (hasServer) serverLabel else "No server configured",
                        isEnabled = false
                    )
                    Divider()
                    Item(
                        label = "Save Bookmark from Clipboard",
                        icon = Icons.Default.ContentPaste,
                        iconRenderProperties = retinaMenuIcon,
                        isEnabled = hasServer,
                        onClick = {
                            coroutineScope.launch {
                                val url = readUrlFromClipboard()
                                if (url == null) {
                                    notify(title = "Karakept", message = "No URL found in clipboard")
                                    return@launch
                                }
                                // KNotify has no progress/silent API, so this is a plain toast
                                // that gets hidden once the final notification is ready to show.
                                val progressNotification = notify(title = "Karakept", message = "Saving bookmark…")
                                try {
                                    val result = bookmarkRepo.createBookmark(url)
                                    progressNotification?.hideSafely()
                                    if (result.isSuccess) {
                                        val bookmark = result.getOrThrow()
                                        notify(title = "Bookmark Saved", message = bookmark.title)
                                    } else {
                                        notify(
                                            title = "Save Failed",
                                            message = result.exceptionOrNull()?.message ?: "Unknown error"
                                        )
                                    }
                                } catch (e: Exception) {
                                    progressNotification?.hideSafely()
                                    notify(title = "Save Failed", message = e.message ?: "Unknown error")
                                }
                            }
                        }
                    )
                    Item(
                        label = "Open in Browser",
                        icon = Icons.Default.OpenInBrowser,
                        iconRenderProperties = retinaMenuIcon,
                        isEnabled = hasServer,
                        onClick = {
                            if (serverUrl.isNotEmpty()) {
                                try {
                                    java.awt.Desktop.getDesktop().browse(java.net.URI(serverUrl))
                                } catch (_: Exception) {
                                    // Best effort
                                }
                            }
                        }
                    )
                    Divider()
                    Item(
                        label = if (isWindowVisible) "Hide Window" else "Show Window",
                        onClick = { isWindowVisible = !isWindowVisible }
                    )
                    Divider()
                    Item(
                        label = "Quit Karakept",
                        onClick = {
                            // Save window state before exit
                            runBlocking {
                                val absPos = state.position as? WindowPosition.Absolute
                                settingsRepo.setWindowState(
                                    width = state.size.width.value,
                                    height = state.size.height.value,
                                    x = absPos?.x?.value ?: 0f,
                                    y = absPos?.y?.value ?: 0f,
                                    maximized = state.placement == WindowPlacement.Maximized
                                )
                            }
                            appScope.cancel()
                            exitApplication()
                        }
                    )
                }
            )
        }

        // Main window — hide to tray on close instead of quitting
        Window(
            visible = isWindowVisible,
            onCloseRequest = { isWindowVisible = false },
            title = if (isDevBuild) "Karakept (DEV)" else "Karakept",
            state = state,
            icon = iconImage?.let { BitmapPainter(it) }
        ) {
            // key() on version forces a fresh App composition when a new URL arrives
            // via the karakept:// scheme, even if the app is already running.
            key(shareUrlVersion) {
                App(sharedUrl = pendingShareUrl)
            }
        }
    }
}

/**
 * Send a native desktop notification, silently ignoring if libnotify is unavailable.
 * Returns the sent [Notification] so the caller can [Notification.hide] it later
 * (e.g. once replaced by a follow-up notification), or null if sending failed.
 */
@OptIn(ExperimentalNotificationsApi::class)
private fun notify(title: String, message: String): Notification? {
    return try {
        val sent = notification(title = title, message = message)
        sent.send()
        sent
    } catch (_: UnsatisfiedLinkError) {
        // libnotify.so not available (e.g. devcontainer without libnotify-dev)
        null
    }
}

/** Hide a previously sent notification, ignoring any error so a stuck progress toast never crashes the caller. */
private fun Notification.hideSafely() {
    try {
        hide()
    } catch (_: Exception) {
        // Best effort - the notification may already be gone.
    }
}

/** Extract a query parameter from a URI. */
private fun java.net.URI.getQueryParameter(name: String): String? {
    val query = rawQuery ?: return null
    return query.split("&")
        .map { it.split("=", limit = 2) }
        .firstOrNull { it[0] == name }
        ?.getOrNull(1)
        ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
}

/** Encode a BufferedImage as PNG bytes. */
private fun java.awt.image.BufferedImage.toPngBytes(): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(this, "png", out)
    return out.toByteArray()
}

/** Overlay a red "DEV" banner at the bottom of an icon image, clipped to the icon's alpha. */
private fun overlayDevBanner(src: java.awt.image.BufferedImage): java.awt.image.BufferedImage {
    val size = src.width
    val result = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = result.createGraphics()
    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.drawImage(src, 0, 0, null)

    // Clip to the icon's existing alpha so the banner doesn't overflow rounded corners.
    // SrcAtop composites the banner only where the destination (icon) already has alpha.
    g.composite = java.awt.AlphaComposite.SrcAtop

    // Red banner positioned inside the icon's rounded bottom edge (~15% height, offset up 5%)
    val bannerHeight = (size * 0.15).toInt()
    val bannerY = size - bannerHeight - (size * 0.10).toInt()
    g.color = java.awt.Color(0xD3, 0x2F, 0x2F, 230)
    g.fillRect(0, bannerY, size, bannerHeight)

    // "DEV" text centered in the banner
    val fontSize = (bannerHeight * 0.65f)
    g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, fontSize.toInt())
    g.color = java.awt.Color.WHITE
    val fm = g.fontMetrics
    val textWidth = fm.stringWidth("DEV")
    val textX = (size - textWidth) / 2
    val textY = bannerY + (bannerHeight + fm.ascent - fm.descent) / 2
    g.drawString("DEV", textX, textY)

    g.dispose()
    return result
}

/** Read a URL from the system clipboard, or null if clipboard doesn't contain a URL. */
private fun readUrlFromClipboard(): String? {
    return try {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val text = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
            ?: return null
        val trimmed = text.trim()
        // Accept full URLs
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        // Accept bare domains (e.g. github.com/foo)
        if (trimmed.matches(Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*\\.)+[a-zA-Z]{2,}(/\\S*)?\$")))
            return "https://$trimmed"
        null
    } catch (_: Exception) {
        null
    }
}
