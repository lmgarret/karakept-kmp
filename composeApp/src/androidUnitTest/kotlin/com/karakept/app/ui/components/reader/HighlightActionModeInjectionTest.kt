package com.karakept.app.ui.components.reader

import android.app.Activity
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val MENU_ID_HIGHLIGHT = 1001

/**
 * Regression coverage for #295: selecting text in the reader stopped offering a
 * "Highlight" action on Android.
 *
 * The item is injected into the platform text-selection `ActionMode` by swapping
 * the callback the platform's `ActionModeCallback2Wrapper` delegates to. That
 * wrapper is not a stable API — it has shipped both as
 * `DecorView$ActionModeCallback2Wrapper`, holding the callback in `mWrapped`,
 * and as `ActionModeController$ActionModeCallback2Wrapper`, which renamed the
 * field and so silently dropped the item. These tests pin the whole chain down:
 * composition installs the window callback, the swap finds the delegate under
 * either shape, the item survives the menu rebuilds Compose performs, and it
 * asks to be shown on the bar rather than in the overflow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HighlightActionModeInjectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Stands in for `DecorView$ActionModeCallback2Wrapper`, which names its field `mWrapped`. */
    private class WrapperCallback(@JvmField var mWrapped: ActionMode.Callback) : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) =
            mWrapped.onCreateActionMode(mode, menu)

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) =
            mWrapped.onPrepareActionMode(mode, menu)

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) =
            mWrapped.onActionItemClicked(mode, item)

        override fun onDestroyActionMode(mode: ActionMode) = mWrapped.onDestroyActionMode(mode)
    }

    /**
     * Stands in for `ActionModeController$ActionModeCallback2Wrapper`, the newer
     * platform wrapper that holds the callback under a different name — the
     * shape that broke #295 in the first place.
     */
    private class RenamedFieldWrapperCallback(@JvmField var mCallback: ActionMode.Callback) :
        ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) =
            mCallback.onCreateActionMode(mode, menu)

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) =
            mCallback.onPrepareActionMode(mode, menu)

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) =
            mCallback.onActionItemClicked(mode, item)

        override fun onDestroyActionMode(mode: ActionMode) = mCallback.onDestroyActionMode(mode)
    }

    /**
     * Stand-in for Compose's `TextActionModeCallbackImpl`: it clears and
     * repopulates the menu whenever its data changes, and leaves items it does
     * not recognise unhandled.
     */
    private class FakeComposeCallback : ActionMode.Callback2() {
        var menuVersion = 0
        val clickedItemIds = mutableListOf<Int>()
        var destroyed = false

        private var renderedVersion = -1

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            updateMenuItems(menu)
            return menu.size() > 0
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = updateMenuItems(menu)

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            clickedItemIds += item.itemId
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            destroyed = true
        }

        private fun updateMenuItems(menu: Menu): Boolean {
            if (renderedVersion == menuVersion) return false
            renderedVersion = menuVersion
            menu.clear()
            menu.add(Menu.NONE, 1, 1, "Copy").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, 2, 2, "Select all").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            return true
        }
    }

    @Test
    fun `highlight item is added to the selection menu`() {
        val wrapper = startFloatingActionMode()
        val menu = realMenu()

        assertTrue(wrapper.onCreateActionMode(actionMode(), menu))

        val item = menu.findItem(MENU_ID_HIGHLIGHT)
        assertNotNull(item, "Highlight item missing from the selection menu")
        assertEquals("Highlight", item.title.toString())
    }

    @Test
    fun `highlight item survives the menu rebuilds Compose performs`() {
        val compose = FakeComposeCallback()
        val wrapper = startFloatingActionMode(compose)
        val menu = realMenu()
        val mode = actionMode()

        wrapper.onCreateActionMode(mode, menu)

        // Selection changed: Compose clears the menu and repopulates it.
        compose.menuVersion++
        assertTrue(wrapper.onPrepareActionMode(mode, menu))

        assertNotNull(menu.findItem(MENU_ID_HIGHLIGHT), "Highlight lost after a menu rebuild")
        assertNotNull(menu.findItem(1), "Compose's own items should still be present")
    }

    @Test
    fun `unchanged menu data leaves the highlight item in place`() {
        val wrapper = startFloatingActionMode()
        val menu = realMenu()
        val mode = actionMode()

        wrapper.onCreateActionMode(mode, menu)

        // No data change: Compose returns false without touching the menu.
        assertFalse(wrapper.onPrepareActionMode(mode, menu))
        assertNotNull(menu.findItem(MENU_ID_HIGHLIGHT))
    }

    @Test
    fun `highlight item asks to be shown on the bar, not in the overflow`() {
        val wrapper = startFloatingActionMode()
        val item = mockk<MenuItem>(relaxed = true)
        val menu = mockk<Menu>(relaxed = true)
        every { menu.findItem(MENU_ID_HIGHLIGHT) } returns null
        every { menu.add(any(), MENU_ID_HIGHLIGHT, any(), any<CharSequence>()) } returns item

        wrapper.onCreateActionMode(actionMode(), menu)

        verify { item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
    }

    @Test
    fun `highlight item is ordered ahead of the items Compose adds`() {
        val wrapper = startFloatingActionMode()
        val order = slot<Int>()
        val menu = mockk<Menu>(relaxed = true)
        every { menu.findItem(MENU_ID_HIGHLIGHT) } returns null
        every {
            menu.add(any(), MENU_ID_HIGHLIGHT, capture(order), any<CharSequence>())
        } returns mockk(relaxed = true)

        wrapper.onCreateActionMode(actionMode(), menu)

        assertEquals(0, order.captured)
    }

    @Test
    fun `tapping highlight consumes the click and finishes the mode`() {
        val compose = FakeComposeCallback()
        val wrapper = startFloatingActionMode(compose)
        val menu = realMenu()
        val mode = actionMode()
        wrapper.onCreateActionMode(mode, menu)

        val item = mockk<MenuItem>(relaxed = true)
        every { item.itemId } returns MENU_ID_HIGHLIGHT

        assertTrue(wrapper.onActionItemClicked(mode, item))
        assertTrue(compose.clickedItemIds.isEmpty(), "Highlight must not fall through to Compose")
        verify { mode.finish() }
    }

    @Test
    fun `other items are delegated to Compose`() {
        val compose = FakeComposeCallback()
        val wrapper = startFloatingActionMode(compose)
        val menu = realMenu()
        val mode = actionMode()
        wrapper.onCreateActionMode(mode, menu)

        val item = mockk<MenuItem>(relaxed = true)
        every { item.itemId } returns 1
        wrapper.onActionItemClicked(mode, item)

        assertEquals(listOf(1), compose.clickedItemIds)
    }

    @Test
    fun `destroying the mode is delegated to Compose`() {
        val compose = FakeComposeCallback()
        val wrapper = startFloatingActionMode(compose)

        wrapper.onDestroyActionMode(actionMode())

        assertTrue(compose.destroyed)
    }

    @Test
    fun `primary action modes are left untouched`() {
        val activity = hostActivity()
        val compose = FakeComposeCallback()
        val wrapper = WrapperCallback(compose)

        activity.window.callback!!.onWindowStartingActionMode(wrapper, ActionMode.TYPE_PRIMARY)

        assertSame(compose, wrapper.mWrapped, "Only the floating selection mode is decorated")
    }

    @Test
    fun `injection finds the delegate when the platform renames the field`() {
        val compose = FakeComposeCallback()
        val wrapper = RenamedFieldWrapperCallback(compose)

        assertTrue(injectHighlightCallback(wrapper) { _, _ -> })
        assertNotSame(compose, wrapper.mCallback, "Delegate should have been swapped")

        val menu = realMenu()
        wrapper.onCreateActionMode(actionMode(), menu)
        assertNotNull(menu.findItem(MENU_ID_HIGHLIGHT))
    }

    @Test
    fun `injection reports failure when the wrapper holds no delegate callback`() {
        val opaque = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu) = true
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
            override fun onDestroyActionMode(mode: ActionMode) = Unit
        }

        assertFalse(injectHighlightCallback(opaque) { _, _ -> })
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private fun actionMode() = mockk<ActionMode>(relaxed = true)

    /** A real framework [Menu], so ordering and `findItem` behave as on device. */
    private fun realMenu(): Menu = PopupMenu(hostActivity(), View(hostActivity())).menu

    private lateinit var activity: Activity

    private fun hostActivity(): Activity {
        if (!::activity.isInitialized) composeToolbar { }
        return activity
    }

    private fun startFloatingActionMode(
        compose: ActionMode.Callback = FakeComposeCallback()
    ): WrapperCallback {
        val host = hostActivity()
        val wrapper = WrapperCallback(compose)
        host.window.callback!!.onWindowStartingActionMode(wrapper, ActionMode.TYPE_FLOATING)
        return wrapper
    }

    /** Composes [rememberHighlightTextToolbar] so the real window callback is installed. */
    private fun composeToolbar(onHighlight: (String) -> Unit) {
        composeTestRule.setContent {
            activity = LocalContext.current.findActivity()!!
            rememberHighlightTextToolbar(onHighlightRequested = onHighlight)
        }
        composeTestRule.waitForIdle()
    }
}
