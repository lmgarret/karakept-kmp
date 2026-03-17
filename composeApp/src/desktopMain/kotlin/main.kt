import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.di.appModule
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin
import java.awt.GraphicsEnvironment

@OptIn(FlowPreview::class)
fun main() {
    // macOS: detect system dark mode and set initial appearance for native title bar.
    // This must be set before AWT initializes for the first window.
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        try {
            // Check if macOS is in dark mode via defaults command
            val process = ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                .redirectErrorStream(true).start()
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            val isDark = result.equals("Dark", ignoreCase = true)
            // JetBrains Runtime property for all new windows
            System.setProperty("apple.awt.application.appearance", if (isDark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua")
        } catch (_: Exception) {
            // Best effort — defaults to system appearance
        }
    }

    // Use software rendering on Linux so the Skia surface always resizes correctly
    // when running via X11 forwarding from a devcontainer / containerized env.
    // GPU-backed backends (GL/Vulkan) can silently fail to resize over X11,
    // leaving content stuck at the initial size with black bars.
    // SOFTWARE_FAST is not supported on macOS, so only set it on Linux.
    if (System.getProperty("os.name").lowercase().contains("linux")) {
        System.setProperty("skiko.renderApi", "SOFTWARE_FAST")
    }

    // On Linux Wayland, use the native Wayland AWT toolkit (JDK 21+)
    // instead of X11/XWayland — eliminates black bars on resize and improves performance.
    // Falls back to X11 automatically if Wayland socket is not available.
    if (System.getenv("WAYLAND_DISPLAY") != null || java.io.File("/tmp/wayland-0").exists()) {
        try {
            Class.forName("sun.awt.wl.WLToolkit")
            System.setProperty("awt.toolkit.name", "WLToolkit")
        } catch (_: ClassNotFoundException) {
            // JDK < 24: WLToolkit not available, fall back to X11
        }
    }

    // Set macOS dock icon before AWT initializes to prevent OpenJDK icon flash
    val iconBytes = Thread.currentThread().contextClassLoader
        .getResourceAsStream("macos-icon.png")
        ?.readBytes()
    if (iconBytes != null) {
        try {
            val awtImage = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(iconBytes))
            if (awtImage != null && java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar.getTaskbar().iconImage = awtImage
            }
        } catch (_: UnsupportedOperationException) {
            // Taskbar icon not supported on this platform
        }
    }

    // Load icon before entering composition (non-composable)
    val iconImage = iconBytes
        ?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }

    startKoin {
        modules(appModule)
    }

    // Read persisted window state before entering composition
    val settingsRepo = getKoin().get<SettingsRepository>()
    val savedMaximized = runBlocking { settingsRepo.windowMaximized.first() }
    val savedWidth = runBlocking { settingsRepo.windowWidth.first() }
    val savedHeight = runBlocking { settingsRepo.windowHeight.first() }
    val savedX = runBlocking { settingsRepo.windowX.first() }
    val savedY = runBlocking { settingsRepo.windowY.first() }

    application {
        val maxBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds

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

        Window(
            onCloseRequest = {
                // Save final state synchronously on close
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
            },
            title = "Karakept",
            state = state,
            icon = iconImage?.let { BitmapPainter(it) }
        ) {
            App()
        }
    }
}
