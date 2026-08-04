import com.karakept.app.utils.AppLogger
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit

/**
 * Works around a crash in Compose Multiplatform 1.11's desktop accessibility layer.
 *
 * ComposeSceneAccessibility.defaultAccessibilityFocusTarget can compute a null focus target and
 * hand it to ArrayDeque.addFirst (which rejects null), throwing a NullPointerException while
 * SemanticsOwnerAccessibility.syncNodes walks removed nodes. This happens during rapid semantics
 * churn — e.g. bulk "mark as unread" mutating many rows at once. The a11y sync runs as an
 * InvocationEvent on the AWT event dispatch thread, so the uncaught exception kills the whole app.
 *
 * Until the upstream bug is fixed, we wrap event dispatch and swallow ONLY these
 * accessibility-originated crashes (logging them); every other exception is rethrown so genuine
 * bugs still surface normally.
 */
private class A11yCrashGuardEventQueue : EventQueue() {
    override fun dispatchEvent(event: AWTEvent) {
        try {
            super.dispatchEvent(event)
        } catch (t: Throwable) {
            if (isComposeAccessibilityCrash(t)) {
                AppLogger.e("A11yCrashGuard", "Suppressed Compose desktop accessibility crash", t)
            } else {
                throw t
            }
        }
    }
}

internal fun isComposeAccessibilityCrash(t: Throwable): Boolean {
    var current: Throwable? = t
    while (current != null) {
        val stack = current.stackTrace
        if (stack.any { frame ->
                val name = frame.className
                name.contains("androidx.compose.ui.platform.a11y") ||
                    name.contains("Accessibility")
            }
        ) {
            return true
        }
        // Safety net: HotSpot "fast throw" can replace a frequently-thrown implicit exception
        // (this NPE fires from the a11y sync loop) with a preallocated, STACKLESS copy. With no
        // frames we can't attribute it, but a stackless NPE on the AWT dispatch thread is
        // overwhelmingly this library bug — swallow it rather than let it kill the app. The
        // -XX:-OmitStackTraceInFastThrow jvmArg normally keeps stacks intact so this is rarely hit.
        if (current is NullPointerException && stack.isEmpty()) {
            return true
        }
        current = current.cause
    }
    return false
}

/**
 * Installs [A11yCrashGuardEventQueue] on the AWT event dispatch thread. Safe to call once at
 * startup, after AWT has initialized (e.g. from inside the Compose `application { }` block).
 */
fun installA11yCrashGuard() {
    EventQueue.invokeLater {
        try {
            Toolkit.getDefaultToolkit().systemEventQueue.push(A11yCrashGuardEventQueue())
            AppLogger.d("A11yCrashGuard", "Installed desktop accessibility crash guard")
        } catch (t: Throwable) {
            AppLogger.w("A11yCrashGuard", "Failed to install a11y crash guard: ${t.message}")
        }
    }
}
