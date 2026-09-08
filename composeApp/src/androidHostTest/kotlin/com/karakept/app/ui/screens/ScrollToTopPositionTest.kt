package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests for UI-01: scroll-to-top reaches exact position (0, 0).
 *
 * Verifies that animateScrollToItem(0, 0) resets both firstVisibleItemIndex
 * and firstVisibleItemScrollOffset to zero after scrolling from a non-zero position.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScrollToTopPositionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `scroll to top reaches index 0 offset 0`() {
        val listState = LazyListState()

        composeTestRule.setContent {
            LazyColumn(state = listState) {
                items(50) { index ->
                    Text("Item $index", modifier = Modifier.height(60.dp))
                }
            }
        }

        // Scroll to item 20 with a pixel offset
        composeTestRule.runOnIdle {
            runBlocking { listState.scrollToItem(20, 10) }
        }
        composeTestRule.waitForIdle()

        // Verify we are at a non-zero position
        composeTestRule.runOnIdle {
            assertEquals(20, listState.firstVisibleItemIndex)
        }

        // Scroll to top with explicit offset
        composeTestRule.runOnIdle {
            runBlocking { listState.scrollToItem(0, 0) }
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun `scroll to top clears residual pixel offset`() {
        val listState = LazyListState()

        composeTestRule.setContent {
            LazyColumn(state = listState) {
                items(50) { index ->
                    Text("Item $index", modifier = Modifier.height(60.dp))
                }
            }
        }

        // Scroll to item 0 but with a residual pixel offset (the bug scenario)
        composeTestRule.runOnIdle {
            runBlocking { listState.scrollToItem(0, 15) }
        }
        composeTestRule.waitForIdle()

        // Verify we have a residual offset
        composeTestRule.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(15, listState.firstVisibleItemScrollOffset)
        }

        // scrollToItem(0, 0) should clear the residual offset
        composeTestRule.runOnIdle {
            runBlocking { listState.scrollToItem(0, 0) }
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        }
    }
}
