package com.karakept.app.ui.components.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Regression coverage for #295: selecting text in the reader stopped offering a
 * "Highlight" action on Android.
 *
 * The entry itself now comes from Compose's public text context menu API, but
 * Compose exposes no way to read what is selected, so the `SelectionManager` is
 * recovered from the closures of the items `SelectionContainer` contributes.
 * These tests pin that recovery down — it is the part that silently returns
 * null if Compose's internals shift.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HighlightContextMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Stands in for Compose's `SelectionManager`: matched on class name, and
     * queried through a `getSelectedText`-prefixed method, since the real one is
     * name-mangled to `getSelectedText$foundation`.
     */
    private class FakeSelectionManager(private val text: String) {
        fun getSelectedText(): AnnotatedString = AnnotatedString(text)
    }

    /** An intermediate object the search has to walk through to reach the manager. */
    private class Indirection(@JvmField val manager: Any)

    @OptIn(ExperimentalFoundationApi::class)
    private fun copyItemClosingOver(manager: Any) = TextContextMenuItem(
        key = Any(),
        label = "Copy",
        onClick = { manager.toString() }
    )

    @Test
    fun `finds the selection manager captured in an item's closure`() {
        val manager = FakeSelectionManager("selected words")

        val found = findSelectionManager(copyItemClosingOver(manager).onClick)

        assertSame(manager, found)
    }

    @Test
    fun `finds the selection manager nested behind another object`() {
        val manager = FakeSelectionManager("selected words")
        val holder = Indirection(manager)

        val found = findSelectionManager(copyItemClosingOver(holder).onClick)

        assertSame(manager, found, "The search must walk into intermediate objects")
    }

    @Test
    fun `returns null when no selection manager is reachable`() {
        assertNull(findSelectionManager(copyItemClosingOver("just a string").onClick))
    }

    @Test
    fun `stops descending at the depth limit`() {
        val manager = FakeSelectionManager("selected words")
        val nested = Indirection(Indirection(manager))

        assertNull(findSelectionManager(nested, maxDepth = 1))
        assertSame(manager, findSelectionManager(nested, maxDepth = 6))
    }

    @Test
    fun `survives a reference cycle`() {
        val cyclic = CyclicNode()
        cyclic.self = cyclic

        assertNull(findSelectionManager(cyclic))
    }

    private class CyclicNode {
        @JvmField
        var self: Any? = null
    }

    @Test
    fun `reads the selected text off the manager`() {
        assertEquals("selected words", readSelectedText(FakeSelectionManager("selected words")))
    }

    @Test
    fun `treats an empty selection as no selection`() {
        assertNull(readSelectedText(FakeSelectionManager("")))
    }

    @Test
    fun `reports no text when the object exposes no accessor`() {
        assertNull(readSelectedText(Any()))
    }

    @Test
    fun `holder captures the manager from a menu item and reads the selection`() {
        val holder = SelectionManagerHolder()

        holder.observe(copyItemClosingOver(FakeSelectionManager("selected words")))

        assertEquals("selected words", holder.selectedText())
    }

    @Test
    fun `holder keeps the first manager it captured`() {
        val first = FakeSelectionManager("first")
        val holder = SelectionManagerHolder()

        holder.observe(copyItemClosingOver(first))
        holder.observe(copyItemClosingOver(FakeSelectionManager("second")))

        assertSame(first, holder.current)
    }

    @Test
    fun `holder ignores items that reach no manager`() {
        val holder = SelectionManagerHolder()

        holder.observe(copyItemClosingOver("just a string"))

        assertNull(holder.current)
        assertNull(holder.selectedText())
    }

    @Test
    fun `provider renders its content`() {
        composeTestRule.setContent {
            HighlightContextMenuProvider(onHighlightRequested = {}) {
                Text("reader body")
            }
        }

        composeTestRule.onNodeWithText("reader body").assertIsDisplayed()
    }
}
