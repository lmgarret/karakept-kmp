package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for issue #279: tap-to-show/hide the FAB in the reader view.
 *
 * Tests the rememberFabVisibilityState composable from ViewerScrollBehavior.kt, which now
 * returns the scroll-driven visibility paired with a manual toggle so tapping the content
 * can show/hide the FAB independent of scrolling, while scroll movement still re-asserts
 * the scroll-driven value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FabVisibilityStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `fab starts visible at the top of the content`() {
        composeTestRule.setContent {
            val state = rememberLazyListState()
            val (fabVisible, _) = rememberFabVisibilityState(scrollState = state, fabExpanded = false)

            LazyColumn(state = state) {
                items(50) { index -> Text("Item $index") }
            }

            if (fabVisible) {
                Text("Fab", modifier = Modifier.testTag("fab"))
            }
        }

        composeTestRule.onNodeWithTag("fab").assertIsDisplayed()
    }

    @Test
    fun `tapping content toggles the fab visible while at rest`() {
        var toggle: (() -> Unit)? = null

        composeTestRule.setContent {
            val state = rememberLazyListState()
            val (fabVisible, toggleFabVisible) = rememberFabVisibilityState(scrollState = state, fabExpanded = false)
            toggle = toggleFabVisible

            LazyColumn(state = state) {
                items(50) { index -> Text("Item $index") }
            }

            if (fabVisible) {
                Text("Fab", modifier = Modifier.testTag("fab"))
            }
        }

        // Visible by default at the top.
        composeTestRule.onNodeWithTag("fab").assertIsDisplayed()

        // A content tap toggles it off.
        composeTestRule.runOnIdle { toggle!!.invoke() }
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodesWithTag("fab").fetchSemanticsNodes().isEmpty())

        // Tapping again toggles it back on.
        composeTestRule.runOnIdle { toggle!!.invoke() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fab").assertIsDisplayed()
    }

    @Test
    fun `scrolling re-asserts scroll-driven visibility after a manual toggle`() {
        var toggle: (() -> Unit)? = null
        var scrollState: LazyListState? = null

        composeTestRule.setContent {
            val state = rememberLazyListState()
            scrollState = state
            val (fabVisible, toggleFabVisible) = rememberFabVisibilityState(scrollState = state, fabExpanded = false)
            toggle = toggleFabVisible

            LazyColumn(state = state) {
                items(50) { index -> Text("Item $index") }
            }

            if (fabVisible) {
                Text("Fab", modifier = Modifier.testTag("fab"))
            }
        }

        // Scroll down past the 100px threshold so the FAB auto-hides.
        composeTestRule.runOnIdle {
            runBlocking { scrollState!!.scrollToItem(5) }
        }
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodesWithTag("fab").fetchSemanticsNodes().isEmpty())

        // Manual tap brings it back while scrolled down.
        composeTestRule.runOnIdle { toggle!!.invoke() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fab").assertIsDisplayed()

        // Scrolling further down re-asserts the scroll-driven (hidden) state, overriding the tap.
        composeTestRule.runOnIdle {
            runBlocking { scrollState!!.scrollToItem(10) }
        }
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodesWithTag("fab").fetchSemanticsNodes().isEmpty())
    }
}
