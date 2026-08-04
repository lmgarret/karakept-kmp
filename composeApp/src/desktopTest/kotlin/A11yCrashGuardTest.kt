import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the guard only classifies Compose desktop accessibility crashes for suppression —
 * every other throwable must be reported as non-a11y so the guard rethrows it (real bugs still
 * surface).
 */
class A11yCrashGuardTest {

    private fun throwableWithStack(vararg classNames: String, cause: Throwable? = null) =
        RuntimeException("boom", cause).apply {
            stackTrace = classNames
                .map { StackTraceElement(it, "m", "f.kt", 1) }
                .toTypedArray()
        }

    @Test
    fun accessibilityStackFrame_isClassifiedAsA11yCrash() {
        val crash = throwableWithStack(
            "androidx.compose.ui.platform.a11y.ComposeSceneAccessibility",
            "java.util.ArrayDeque"
        )
        assertTrue(isComposeAccessibilityCrash(crash))
    }

    @Test
    fun unrelatedStack_isNotClassifiedAsA11yCrash() {
        val crash = throwableWithStack(
            "com.karakept.app.ui.screens.MainScreenModel",
            "kotlinx.coroutines.DispatchedTask"
        )
        assertFalse(isComposeAccessibilityCrash(crash))
    }

    @Test
    fun a11yFrameInCauseChain_isClassifiedAsA11yCrash() {
        val root = throwableWithStack("androidx.compose.ui.platform.a11y.SemanticsOwnerAccessibility")
        val wrapped = throwableWithStack("com.karakept.app.Foo", cause = root)
        assertTrue(isComposeAccessibilityCrash(wrapped))
    }

    @Test
    fun statelessNpe_isTreatedAsA11yCrash() {
        // HotSpot fast-throw yields a NullPointerException with no stack frames.
        val stackless = NullPointerException().apply { stackTrace = emptyArray() }
        assertTrue(isComposeAccessibilityCrash(stackless))
    }

    @Test
    fun statelessNonNpe_isNotTreatedAsA11yCrash() {
        val stackless = IllegalStateException().apply { stackTrace = emptyArray() }
        assertFalse(isComposeAccessibilityCrash(stackless))
    }
}
