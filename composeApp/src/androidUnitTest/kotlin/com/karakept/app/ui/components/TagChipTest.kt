package com.karakept.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Compose UI tests for the TagChip reusable component.
 *
 * TagChip is a core building block used throughout the app (TagEditorDialog,
 * BookmarkTagsDisplay, filter panels). These tests verify its rendering states
 * and callback wiring: text display, remove icon visibility, onClick/onRemove
 * callbacks, and selected state rendering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TagChipTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays tag text`() {
        composeTestRule.setContent {
            MaterialTheme {
                TagChip(tag = "kotlin")
            }
        }

        composeTestRule.onNodeWithText("kotlin").assertIsDisplayed()
    }

    @Test
    fun `shows remove icon when onRemove provided`() {
        composeTestRule.setContent {
            MaterialTheme {
                TagChip(tag = "test", onRemove = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Remove tag").assertExists()
    }

    @Test
    fun `hides remove icon when onRemove is null`() {
        composeTestRule.setContent {
            MaterialTheme {
                TagChip(tag = "test")
            }
        }

        composeTestRule.onNodeWithContentDescription("Remove tag").assertDoesNotExist()
    }

    @Test
    fun `remove button triggers onRemove callback`() {
        var removed = false
        composeTestRule.setContent {
            MaterialTheme {
                TagChip(tag = "test", onRemove = { removed = true })
            }
        }

        composeTestRule.onNodeWithContentDescription("Remove tag").performClick()
        assertTrue(removed, "onRemove callback should have been invoked")
    }

    @Test
    fun `click triggers onClick callback`() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                TagChip(tag = "test", onClick = { clicked = true })
            }
        }

        composeTestRule.onNodeWithText("test").performClick()
        assertTrue(clicked, "onClick callback should have been invoked")
    }

    @Test
    fun `renders with both onClick and onRemove`() {
        composeTestRule.setContent {
            MaterialTheme {
                TagChip(
                    tag = "combined",
                    onClick = {},
                    onRemove = {}
                )
            }
        }

        composeTestRule.onNodeWithText("combined").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Remove tag").assertExists()
    }

    @Test
    fun `renders in selected state`() {
        composeTestRule.setContent {
            MaterialTheme {
                TagChip(tag = "selected", selected = true)
            }
        }

        composeTestRule.onNodeWithText("selected").assertIsDisplayed()
    }
}
