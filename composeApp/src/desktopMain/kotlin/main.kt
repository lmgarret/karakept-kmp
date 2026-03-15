import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.karakept.app.di.appModule
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import java.awt.GraphicsEnvironment

fun main() {
    // Use software rendering so the Skia surface always resizes correctly
    // when running via X11 forwarding from a devcontainer / containerized env.
    // GPU-backed backends (GL/Vulkan) can silently fail to resize over X11,
    // leaving content stuck at the initial size with black bars.
    // Also set as -Dskiko.renderApi=SOFTWARE in build.gradle.kts jvmArgs for reliability.
    System.setProperty("skiko.renderApi", "SOFTWARE_FAST")

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

    // Load icon before entering composition (non-composable)
    val iconImage = Thread.currentThread().contextClassLoader
        .getResourceAsStream("composeResources/karakept.composeapp.generated.resources/drawable/icon.png")
        ?.readBytes()
        ?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }

    startKoin {
        modules(appModule)
    }
    application {
        // Use the maximum usable window bounds (screen minus taskbar/docks) rather than
        // WindowPlacement.Maximized, which XWayland/some WMs may not honour. Setting an
        // explicit size means Compose gets the correct constraints on the very first frame.
        val maxBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val state = rememberWindowState(
            placement = WindowPlacement.Maximized,
            width = maxBounds.width.dp,
            height = maxBounds.height.dp
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "Karakept",
            state = state,
            icon = iconImage?.let { BitmapPainter(it) }
        ) {
            App()
        }
    }
}
