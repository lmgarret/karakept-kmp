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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.karakept.api.model.KarakeepList
import com.karakept.app.domain.ListHierarchyUtils
import com.karakept.app.domain.ListMembershipChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `a smart list the bookmark is in shows checked and cannot be toggled`() = runComposeUiTest {
        var applied: ListMembershipChange? = null
        setContent {
            MaterialTheme {
                ListMembershipDialog(
                    lists = listOf(readLater, smart),
                    bookmarksListIds = listOf(setOf("s")),
                    onApply = { applied = it },
                    onDismiss = {}
                )
            }
        }

        onNodeWithText("All Feeds")
            .assert(toggleState(ToggleableState.On))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsNotEnabled()
            .assert(hasText("Smart list"))
            .performClick()
        onNodeWithText("All Feeds").assert(toggleState(ToggleableState.On))

        onNodeWithText("Read Later").performClick()
        onNodeWithText("Done").performClick()
        assertEquals(ListMembershipChange(added = setOf("a")), applied)
    }

    @Test
    fun `a smart list only part of the selection is in is indeterminate and read-only`() = runComposeUiTest {
        var applied: ListMembershipChange? = null
        setContent {
            MaterialTheme {
                ListMembershipDialog(
                    lists = listOf(readLater, smart),
                    bookmarksListIds = listOf(setOf("s"), emptySet()),
                    onApply = { applied = it },
                    onDismiss = {}
                )
            }
        }

        onNodeWithText("All Feeds")
            .assert(toggleState(ToggleableState.Indeterminate))
            .assertIsNotEnabled()
            .performClick()
        onNodeWithText("Done").performClick()

        assertEquals(ListMembershipChange(), applied)
    }

    @Test
    fun `a smart list the bookmark is not in shows unchecked`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ListMembershipDialog(
                    lists = listOf(smart),
                    bookmarksListIds = listOf(emptySet()),
                    onApply = {},
                    onDismiss = {}
                )
            }
        }

        onNodeWithText("All Feeds").assert(toggleState(ToggleableState.Off)).assertIsNotEnabled()
    }

    @Test
    fun `only smart lists shows them, and says there is nothing to edit`() = runComposeUiTest {
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
        onNodeWithText("All Feeds").assert(toggleState(ToggleableState.On))
    }

    @Test
    fun `no lists at all shows the empty state`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ListMembershipDialog(lists = emptyList(), bookmarksListIds = listOf(emptySet()), onApply = {}, onDismiss = {})
            }
        }

        onNodeWithText("No manual lists yet").assertExists()
    }

    @Test
    fun `lists are in the drawer's order, with a manual list nested under a smart one`() = runComposeUiTest {
        // Parent and child given out of order, smart and manual interleaved: the drawer's order is
        // buildListHierarchy over every list.
        val lists = listOf(
            KarakeepList(id = "z", name = "Zeta", type = KarakeepList.Type.MANUAL),
            KarakeepList(id = "c", name = "Child of Feeds", type = KarakeepList.Type.MANUAL, parentId = "s"),
            smart,
            readLater,
        )
        val drawerOrder = ListHierarchyUtils.buildListHierarchy(lists).map { it.first.name }
        setContent {
            MaterialTheme {
                ListMembershipDialog(lists = lists, bookmarksListIds = listOf(emptySet()), onApply = {}, onDismiss = {})
            }
        }

        val shownOrder = lists.mapNotNull { it.name }
            .sortedBy { onNodeWithText(it).getUnclippedBoundsInRoot().top.value }
        assertEquals(drawerOrder, shownOrder)
        assertEquals(listOf("All Feeds", "Child of Feeds", "Read Later", "Zeta"), shownOrder)

        val parentLeft = onNodeWithText("All Feeds", useUnmergedTree = true).getUnclippedBoundsInRoot().left
        val childLeft = onNodeWithText("Child of Feeds", useUnmergedTree = true).getUnclippedBoundsInRoot().left
        assertTrue(childLeft > parentLeft, "the child is indented under its smart parent")
    }

    @Test
    fun `an indented row is one colour, indent included, and the indent is part of the tap target`() =
        runComposeUiTest {
            val child = KarakeepList(id = "c", name = "Child", type = KarakeepList.Type.MANUAL, parentId = "a")
            var applied: ListMembershipChange? = null
            setContent {
                MaterialTheme {
                    ListMembershipDialog(
                        lists = listOf(readLater, child),
                        bookmarksListIds = listOf(emptySet()),
                        onApply = { applied = it },
                        onDismiss = {}
                    )
                }
            }

            val row = onNodeWithText("Child")
            val image = row.captureToImage().toPixelMap()
            val y = image.height / 2
            val inIndent = image[2, y]
            // Past the 16dp indent and the item's own leading padding, left of the text.
            val insideItem = image[image.width / 2, 1]
            assertEquals(inIndent, insideItem, "the indent shows the same background as the row")

            row.performTouchInput { click(Offset(2f, centerY)) }
            row.assert(toggleState(ToggleableState.On))
            onNodeWithText("Done").performClick()
            assertEquals(ListMembershipChange(added = setOf("c")), applied)
        }
}
