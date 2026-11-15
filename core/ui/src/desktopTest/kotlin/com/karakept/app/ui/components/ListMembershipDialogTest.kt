package com.karakept.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.karakept.api.model.KarakeepList
import com.karakept.app.domain.ListMembershipChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class ListMembershipDialogTest {

    private val readLater = KarakeepList(id = "a", name = "Read Later", type = KarakeepList.Type.MANUAL)
    private val work = KarakeepList(id = "b", name = "Work", type = KarakeepList.Type.MANUAL)
    private val smart = KarakeepList(id = "s", name = "All Feeds", type = KarakeepList.Type.SMART)

    private fun toggleState(state: ToggleableState) =
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

    @Test
    fun `a list only some of the selection is in is indeterminate and left alone`() = runComposeUiTest {
        var applied: ListMembershipChange? = null
        setContent {
            MaterialTheme {
                ListMembershipDialog(
                    lists = listOf(readLater, work, smart),
                    bookmarksListIds = listOf(setOf("a"), setOf("a", "b")),
                    onApply = { applied = it },
                    onDismiss = {}
                )
            }
        }

        onNodeWithText("Read Later").assert(toggleState(ToggleableState.On))
        onNodeWithText("Work").assert(toggleState(ToggleableState.Indeterminate))
        onNodeWithText("All Feeds").assertDoesNotExist()

        onNodeWithText("Read Later").performClick()
        onNodeWithText("Done").performClick()

        assertEquals(ListMembershipChange(removed = setOf("a")), applied)
    }

    @Test
    fun `tapping a mixed list puts the whole selection in it`() = runComposeUiTest {
        var applied: ListMembershipChange? = null
        setContent {
            MaterialTheme {
                ListMembershipDialog(
                    lists = listOf(readLater, work),
                    bookmarksListIds = listOf(setOf("a"), setOf("a", "b")),
                    onApply = { applied = it },
                    onDismiss = {}
                )
            }
        }

        onNodeWithText("Work").performClick()
        onNodeWithText("Work").assert(toggleState(ToggleableState.On))
        onNodeWithText("Done").performClick()

        assertEquals(ListMembershipChange(added = setOf("b")), applied)
    }

    @Test
    fun `taps survive lists that arrive while the sheet is open`() = runComposeUiTest {
        var lists by mutableStateOf(listOf(readLater))
        var applied: ListMembershipChange? = null
        setContent {
            MaterialTheme {
                ListMembershipDialog(
                    lists = lists,
                    bookmarksListIds = listOf(setOf("a")),
                    onApply = { applied = it },
                    onDismiss = {}
                )
            }
        }

        onNodeWithText("Read Later").performClick()
        lists = listOf(readLater, work)
        waitForIdle()

        onNodeWithText("Read Later").assert(toggleState(ToggleableState.Off))
        onNodeWithText("Done").performClick()
        assertEquals(ListMembershipChange(removed = setOf("a")), applied)
    }

    @Test
    fun `nothing can be applied while the memberships are loading`() = runComposeUiTest {
        var applied: ListMembershipChange? = null
        setContent {
            MaterialTheme {
                ListMembershipDialog(
                    lists = listOf(readLater),
                    bookmarksListIds = emptyList(),
                    isLoading = true,
                    onApply = { applied = it },
                    onDismiss = {}
                )
            }
        }

        onNodeWithText("Read Later").assertDoesNotExist()
        onNodeWithText("Done").assertIsNotEnabled().performClick()
        assertNull(applied)
    }

    @Test
    fun `only smart lists shows an empty state`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ListMembershipDialog(
                    lists = listOf(smart),
                    bookmarksListIds = listOf(setOf("s")),
                    onApply = {},
                    onDismiss = {}
                )
            }
        }

        onNodeWithText("No manual lists yet").assertExists()
        onNodeWithText("All Feeds").assertDoesNotExist()
    }
}
