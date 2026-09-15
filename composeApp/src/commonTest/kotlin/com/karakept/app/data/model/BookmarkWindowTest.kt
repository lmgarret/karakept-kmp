package com.karakept.app.data.model

import com.karakept.app.data.local.entity.BookmarkEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [BookmarkWindow] is what decouples "where a row sits in the view" from "whether it has been
 * read yet". The tests below are about that mapping: an index outside the loaded run must resolve
 * to a placeholder rather than to the wrong row or to nothing at all.
 */
class BookmarkWindowTest {

    private fun row(id: Long) = BookmarkEntity(
        localId = id,
        remoteId = "remote-$id",
        serverId = "server-1",
        url = "https://example.com/$id",
        title = "Bookmark $id",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = 1_700_000_000_000L,
        isArchived = false,
        isStarred = false
    )

    private val rows = (1L..5L).map(::row)

    @Test
    fun `a dense window loads every slot it counts`() {
        val window = BookmarkWindow.dense(rows)

        assertEquals(5, window.total)
        assertEquals(5, window.loadedCount)
        assertEquals(0..4, window.loadedRange.first..window.loadedRange.last)
        for (index in 0 until window.total) {
            assertEquals(
                BookmarkSlot.Loaded(rows[index]),
                window[index],
                "index $index of a dense window must be loaded"
            )
        }
    }

    @Test
    fun `an index past the loaded run is a placeholder, not a wrong row`() {
        // The state step 2 introduces: the list is sized by the view and the walk has only
        // reached part of it.
        val window = BookmarkWindow(total = 100, rows = rows, loadedFrom = 0)

        assertEquals(BookmarkSlot.Loaded(rows[4]), window[4])
        assertEquals(BookmarkSlot.Placeholder, window[5])
        assertEquals(BookmarkSlot.Placeholder, window[99])
        assertNull(window.bookmarkAt(5))
    }

    @Test
    fun `a run that does not start at zero is read at its absolute indices`() {
        val window = BookmarkWindow(total = 100, rows = rows, loadedFrom = 40)

        assertEquals(BookmarkSlot.Placeholder, window[39])
        assertEquals(BookmarkSlot.Loaded(rows[0]), window[40])
        assertEquals(BookmarkSlot.Loaded(rows[4]), window[44])
        assertEquals(BookmarkSlot.Placeholder, window[45])
        assertEquals(40, window.indexOfRemoteId("remote-1"))
        assertEquals(44, window.indexOfRemoteId("remote-5"))
    }

    @Test
    fun `an index outside the list resolves to a placeholder rather than throwing`() {
        val window = BookmarkWindow.dense(rows)

        assertNull(window.bookmarkAt(-1))
        assertNull(window.bookmarkAt(5))
        assertEquals(BookmarkSlot.Placeholder, window[-1])
        assertEquals(BookmarkSlot.Placeholder, window[5])
    }

    @Test
    fun `a loaded slot keys on its remoteId and a placeholder does not`() {
        // The effects that read the visible items back pick bookmarks out with `key as? String`,
        // so a placeholder key must not be a String or they would treat it as a remoteId.
        val window = BookmarkWindow(total = 100, rows = rows, loadedFrom = 0)

        assertEquals("remote-1", window.keyAt(0))
        assertTrue(window.keyAt(7) !is String, "a placeholder key must not read as a remoteId")
        assertTrue(window.keyAt(7) != window.keyAt(8), "and must still be unique per slot")
    }

    @Test
    fun `an unloaded remoteId is not found`() {
        val window = BookmarkWindow(total = 100, rows = rows, loadedFrom = 0)

        assertEquals(-1, window.indexOfRemoteId("remote-99"))
    }

    @Test
    fun `an empty window counts nothing and loads nothing`() {
        assertTrue(BookmarkWindow.EMPTY.isEmpty)
        assertEquals(0, BookmarkWindow.EMPTY.total)
        assertEquals(BookmarkSlot.Placeholder, BookmarkWindow.EMPTY[0])
        assertTrue(BookmarkWindow.dense(emptyList()).isEmpty)
    }

    @Test
    fun `a window with rows is not empty even when none has loaded yet`() {
        // `isEmpty` asks whether the view holds anything, which is what the empty state renders
        // on — not whether the rows have arrived.
        val window = BookmarkWindow(total = 100, rows = emptyList(), loadedFrom = 0)

        assertTrue(!window.isEmpty)
        assertEquals(0, window.loadedCount)
    }
}
