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
import com.kdroid.composetray.utils.isMenuBarInDarkMode
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.di.appModule
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.IconRenderProperties
import com.mmk.kmpnotifier.notification.Notifier
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
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
        System.setProperty("apple.awt.application.name", "Karakept")

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
            val awtImage = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(iconBytes))
            if (awtImage != null && java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar.getTaskbar().iconImage = awtImage
            }
        } catch (_: UnsupportedOperationException) { }
    }
    val iconImage = iconBytes?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }

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
        modules(appModule)
    }

    // Initialize KMPNotifier for desktop notifications
    NotifierManager.initialize(
        NotificationPlatformConfiguration.Desktop(
            showPushNotification = true,
            notificationIconPath = null
        )
    )

    // Read persisted window state before entering composition
    val settingsRepo = getKoin().get<SettingsRepository>()
    val savedMaximized = runBlocking { settingsRepo.windowMaximized.first() }
    val savedWidth = runBlocking { settingsRepo.windowWidth.first() }
    val savedHeight = runBlocking { settingsRepo.windowHeight.first() }
    val savedX = runBlocking { settingsRepo.windowX.first() }
    val savedY = runBlocking { settingsRepo.windowY.first() }

    application {
        val maxBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        var isWindowVisible by remember { mutableStateOf(true) }
        val coroutineScope = rememberCoroutineScope()
        val bookmarkRepo = remember { getKoin().get<BookmarkRepository>() }
        val serverRepo = remember { getKoin().get<ServerRepository>() }
        val notifier: Notifier = remember { NotifierManager.getLocalNotifier() }

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
        // The iconContent overload renders the composable via ImageComposeScene and
        // re-renders when isMenuBarInDarkMode() changes, so the icon adapts to
        // light/dark theme. We tint the bitmap to a monochrome silhouette (black
        // for light menu bar, white for dark) like a native macOS template image.
        // Padding keeps the icon slightly smaller than the full menu-bar height
        // (~18 pt within the 22 pt slot) to match native macOS status-bar icons.
        val isDarkMenuBar = isMenuBarInDarkMode()
        val trayIconTint = if (isDarkMenuBar) Color.White else Color.Black
        // Render menu-item icons at 32×32 px so Retina displays get a crisp
        // @2x representation (the Swift side sets NSSize 16×16 pt).
        val retinaMenuIcon = IconRenderProperties(
            sceneWidth = 64, sceneHeight = 64,
            targetWidth = 32, targetHeight = 32
        )
        if (trayIconImage != null) {
            Tray(
                iconContent = {
                    Image(
                        bitmap = trayIconImage,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        colorFilter = ColorFilter.tint(trayIconTint)
                    )
                },
                tooltip = "Karakept",
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
                                    notifier.notify(title = "Karakept", body = "No URL found in clipboard")
                                    return@launch
                                }
                                notifier.notify(title = "Karakept", body = "Saving bookmark...")
                                try {
                                    val result = bookmarkRepo.createBookmark(url)
                                    if (result.isSuccess) {
                                        val bookmark = result.getOrThrow()
                                        notifier.notify(title = "Bookmark Saved", body = bookmark.title)
                                    } else {
                                        notifier.notify(
                                            title = "Save Failed",
                                            body = result.exceptionOrNull()?.message ?: "Unknown error"
                                        )
                                    }
                                } catch (e: Exception) {
                                    notifier.notify(title = "Save Failed", body = e.message ?: "Unknown error")
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
            title = "Karakept",
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
