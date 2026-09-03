package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for READER-03/04: scroll-to-top button visibility.
 *
 * Tests the rememberScrollToTopVisibility composable from ViewerScrollBehavior.kt
 * which controls when the floating scroll-to-top button appears in the reader.
 *
 * The button is visible when:
 * - Hero is NOT visible (firstVisibleItemIndex > 0) AND (FAB is visible OR at end of article)
 *
 * Uses a real LazyColumn with programmatic scrollToItem to populate layoutInfo
 * (per Pitfall 7: constructor-based LazyListState does NOT populate visibleItemsInfo).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScrollToTopVisibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `scroll-to-top button hidden when hero is visible at index 0`() {
        var scrollState: LazyListState? = null

        composeTestRule.setContent {
            val state = rememberLazyListState()
            scrollState = state
            val visible = rememberScrollToTopVisibility(
                scrollState = state,
                fabVisible = true
            )

            LazyColumn(state = state) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            if (visible) {
                Text("ScrollToTop", modifier = Modifier.testTag("scrollToTop"))
            }
        }

        // At index 0 (hero visible), button should NOT be shown
        assertTrue(composeTestRule.onAllNodesWithTag("scrollToTop").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `scroll-to-top button visible when scrolled past hero with fab visible`() {
        var scrollState: LazyListState? = null

        composeTestRule.setContent {
            val state = rememberLazyListState()
            scrollState = state
            val visible = rememberScrollToTopVisibility(
                scrollState = state,
                fabVisible = true
            )

            LazyColumn(state = state) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            if (visible) {
                Text("ScrollToTop", modifier = Modifier.testTag("scrollToTop"))
            }
        }

        // Scroll past hero
        composeTestRule.runOnIdle {
            runBlocking { scrollState!!.scrollToItem(5) }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("scrollToTop").assertIsDisplayed()
    }

    @Test
    fun `scroll-to-top button visible at end of article even without fab`() {
        var scrollState: LazyListState? = null

        composeTestRule.setContent {
            val state = rememberLazyListState()
            scrollState = state
            val visible = rememberScrollToTopVisibility(
                scrollState = state,
                fabVisible = false
            )

            // Use enough items that the last item is off-screen and scrolling
            // is required; 10 short Text items fit on Robolectric's virtual
            // screen so firstVisibleItemIndex never advances past 0.
            LazyColumn(state = state) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            if (visible) {
                Text("ScrollToTop", modifier = Modifier.testTag("scrollToTop"))
            }
        }

        // Scroll to the last item (end of article)
        composeTestRule.runOnIdle {
            runBlocking { scrollState!!.scrollToItem(49) }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("scrollToTop").assertIsDisplayed()
    }

    @Test
    fun `scroll-to-top button not composed when scrollToTopEnabled setting is false`() {
        var scrollState: LazyListState? = null
        var scrollToTopEnabled by mutableStateOf(false)

        composeTestRule.setContent {
            val state = rememberLazyListState()
            scrollState = state
            val scrollToTopVisible = rememberScrollToTopVisibility(
                scrollState = state,
                fabVisible = true
            )

            LazyColumn(state = state) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            // Mirrors BookmarkViewerContent: visible = scrollToTopVisible && scrollToTopEnabled
            if (scrollToTopVisible && scrollToTopEnabled) {
                Text("ScrollToTop", modifier = Modifier.testTag("scrollToTop"))
            }
        }

        // Scroll past hero so scrollToTopVisible would be true
        composeTestRule.runOnIdle {
            runBlocking { scrollState!!.scrollToItem(5) }
        }
        composeTestRule.waitForIdle()

        // But scrollToTopEnabled is false, so button should NOT be composed
        assertTrue(composeTestRule.onAllNodesWithTag("scrollToTop").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `einkTapOnly hides the button at end of article when fab is not visible`() {
        var scrollState: LazyListState? = null

        composeTestRule.setContent {
            val state = rememberLazyListState()
            scrollState = state
            val visible = rememberScrollToTopVisibility(
                scrollState = state,
                fabVisible = false,
                einkTapOnly = true
            )

            LazyColumn(state = state) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            if (visible) {
                Text("ScrollToTop", modifier = Modifier.testTag("scrollToTop"))
            }
        }

        // Scroll to the last item (end of article) — without einkTapOnly this alone shows the
        // button; with it, only fabVisible (i.e. a tap) may.
        composeTestRule.runOnIdle {
            runBlocking { scrollState!!.scrollToItem(49) }
        }
        composeTestRule.waitForIdle()

        assertTrue(composeTestRule.onAllNodesWithTag("scrollToTop").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `einkTapOnly still shows the button at end of article once fab is tapped visible`() {
        var scrollState: LazyListState? = null

        composeTestRule.setContent {
            val state = rememberLazyListState()
            scrollState = state
            val visible = rememberScrollToTopVisibility(
                scrollState = state,
                fabVisible = true,
                einkTapOnly = true
            )

            LazyColumn(state = state) {
                items(50) { index ->
                    Text("Item $index")
                }
            }

            if (visible) {
                Text("ScrollToTop", modifier = Modifier.testTag("scrollToTop"))
            }
        }

        composeTestRule.runOnIdle {
            runBlocking { scrollState!!.scrollToItem(49) }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("scrollToTop").assertIsDisplayed()
    }
}
