package com.karakept.app.ui.screens.main

import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.CustomSwipeActionConfig
import com.karakept.app.data.model.CustomSwipeActionType
import com.karakept.app.data.model.SwipeAction
import com.karakept.app.ui.icons.AppIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookmarkListRowLogicTest {

    private fun bookmark(tags: String = "", listIds: String = "", isRead: Boolean = false) = Bookmark(
        localId = 1L,
        remoteId = "r1",
        serverId = "server-1",
        url = "https://example.com",
        title = "t",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 0L,
        isArchived = false,
        isStarred = false,
        listIds = listIds,
        tags = tags,
        isRead = isRead,
    )

    private val tagConfig = CustomSwipeActionConfig(id = "c1", type = CustomSwipeActionType.ADD_TAG, tagName = "later")
    private val listConfig = CustomSwipeActionConfig(
        id = "c2", type = CustomSwipeActionType.ADD_TO_LIST, listId = "L1", listName = "Reading"
    )

    @Test
    fun addTag_isAppliedOnlyWhenTheTagIsPresent() {
        assertTrue(rowSideAction(SwipeAction.ADD_TAG, tagConfig, bookmark(tags = "a, later")).isApplied)
        assertFalse(rowSideAction(SwipeAction.ADD_TAG, tagConfig, bookmark(tags = "laterish")).isApplied)
        assertEquals("later", rowSideAction(SwipeAction.ADD_TAG, tagConfig, bookmark()).label)
    }

    @Test
    fun addToList_isAppliedOnlyWhenTheBookmarkIsInTheList() {
        assertTrue(rowSideAction(SwipeAction.ADD_TO_LIST, listConfig, bookmark(listIds = "L0,L1")).isApplied)
        assertFalse(rowSideAction(SwipeAction.ADD_TO_LIST, listConfig, bookmark(listIds = "L10")).isApplied)
        assertEquals("Reading", rowSideAction(SwipeAction.ADD_TO_LIST, listConfig, bookmark()).label)
    }

    @Test
    fun customActionWithoutConfig_isNeverApplied() {
        val side = rowSideAction(SwipeAction.ADD_TAG, null, bookmark(tags = "later"))
        assertFalse(side.isApplied)
        assertNull(side.label)
    }

    @Test
    fun markRead_iconFollowsTheReadState() {
        assertEquals(AppIcons.Filled.Visibility, rowSideAction(SwipeAction.MARK_READ, null, bookmark()).icon)
        assertEquals(
            AppIcons.Filled.VisibilityOff,
            rowSideAction(SwipeAction.MARK_READ, null, bookmark(isRead = true)).icon
        )
        assertNull(rowSideAction(SwipeAction.ARCHIVE, null, bookmark()).icon)
    }

    @Test
    fun dragSelection_extendingTheRangeTogglesOnlyTheNewRows() {
        assertEquals(listOf(6, 7), dragSelectionToggles(anchor = 3, last = 5, current = 7))
    }

    @Test
    fun dragSelection_pullingBackTogglesTheOvershoot() {
        assertEquals(listOf(6, 7), dragSelectionToggles(anchor = 3, last = 7, current = 5))
    }

    @Test
    fun dragSelection_crossingTheAnchorTogglesBothSides() {
        assertEquals(listOf(1, 2, 4, 5), dragSelectionToggles(anchor = 3, last = 5, current = 1))
    }

    @Test
    fun edgeScroll_isZeroInTheMiddleAndFullAtTheEdges() {
        assertEquals(0f, edgeScrollSpeed(y = 500f, viewportHeight = 1000f))
        assertEquals(-25f, edgeScrollSpeed(y = 0f, viewportHeight = 1000f))
        assertEquals(25f, edgeScrollSpeed(y = 1000f, viewportHeight = 1000f))
        assertTrue(edgeScrollSpeed(y = 140f, viewportHeight = 1000f) in -1f..0f)
    }
}
