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

    /** A window holding [pageIndices] of a [viewTotal]-row view, ten rows to a page. */
    private fun window(
        viewTotal: Int,
        vararg pageIndices: Int,
        prepended: List<BookmarkEntity> = emptyList()
    ) = BookmarkWindow(
        viewTotal = viewTotal,
        pageSize = 10,
        pages = pageIndices.associateWith { page ->
            (0 until 10)
                .map { offset -> page * 10 + offset }
                .filter { it < viewTotal }
                .map { row((it + 1).toLong()) }
        },
        prepended = prepended
    )

    @Test
    fun `a dense window loads every slot it counts`() {
        val dense = BookmarkWindow.dense(rows, pageSize = 2)

        assertEquals(5, dense.total)
        assertEquals(5, dense.loadedCount)
        for (index in 0 until dense.total) {
            assertEquals(
                BookmarkSlot.Loaded(rows[index]),
                dense[index],
                "index $index of a dense window must be loaded"
            )
        }
    }

    @Test
    fun `an index whose page has not been read is a placeholder, not a wrong row`() {
        // Pages 0 and 5 are loaded and nothing in between — a jump reads the page it lands on.
        val w = window(viewTotal = 100, 0, 5)

        assertEquals(1L, w.bookmarkAt(0)?.localId)
        assertEquals(10L, w.bookmarkAt(9)?.localId)
        assertEquals(BookmarkSlot.Placeholder, w[10], "page 1 was never asked for")
        assertEquals(BookmarkSlot.Placeholder, w[49])
        assertEquals(51L, w.bookmarkAt(50)?.localId, "page 5 starts at index 50")
        assertEquals(60L, w.bookmarkAt(59)?.localId)
        assertEquals(BookmarkSlot.Placeholder, w[60])
        assertEquals(100, w.total, "the list is sized by the view, not by what has been read")
        assertEquals(20, w.loadedCount)
    }

    @Test
    fun `a jump asks for the page holding the target and nothing before it`() {
        val w = window(viewTotal = 1000)

        assertEquals(0, w.pageOf(0))
        assertEquals(0, w.pageOf(9))
        assertEquals(1, w.pageOf(10))
        assertEquals(30, w.pageOf(300))
        assertEquals(listOf(30, 31), w.pagesCovering(300..315))
        assertEquals(listOf(30), w.pagesCovering(300..309))
    }

    @Test
    fun `pages covering a range never run past the view`() {
        val w = window(viewTotal = 25)

        // 25 rows is pages 0..2, the last one short.
        assertEquals(listOf(0, 1, 2), w.pagesCovering(0..24))
        assertEquals(listOf(2), w.pagesCovering(20..24))
        assertEquals(listOf(2), w.pagesCovering(20..999), "a range past the end is clamped")
        assertEquals(emptyList(), BookmarkWindow.EMPTY.pagesCovering(0..10))
    }

    @Test
    fun `a short final page does not report rows the view does not have`() {
        val w = window(viewTotal = 25, 2)

        assertEquals(25L, w.bookmarkAt(24)?.localId)
        assertNull(w.bookmarkAt(25), "the view ends at 25 rows")
        assertEquals(BookmarkSlot.Placeholder, w[25])
    }

    @Test
    fun `a pending bookmark sits above the view and shifts every index below it`() {
        // A bookmark the user has just added is on screen before it is in the database, so the
        // view's total does not count it and it takes the first slot.
        val pending = row(99)
        val w = window(viewTotal = 100, 0, prepended = listOf(pending))

        assertEquals(101, w.total)
        assertEquals(BookmarkSlot.Loaded(pending), w[0])
        assertEquals(1L, w.bookmarkAt(1)?.localId, "the view's first row moves down one")
        assertNull(w.pageOf(0), "a pending row belongs to no page")
        assertEquals(0, w.pageOf(1))
        assertEquals(1, w.pageOf(11))
        assertEquals(0, w.indexOfRemoteId("remote-99"))
        assertEquals(1, w.indexOfRemoteId("remote-1"))
    }

    @Test
    fun `an index outside the list resolves to a placeholder rather than throwing`() {
        val w = window(viewTotal = 100, 0)

        assertNull(w.bookmarkAt(-1))
        assertNull(w.bookmarkAt(100))
        assertEquals(BookmarkSlot.Placeholder, w[-1])
        assertEquals(BookmarkSlot.Placeholder, w[100])
        assertNull(w.pageOf(-1))
        assertNull(w.pageOf(100))
    }

    @Test
    fun `a loaded slot keys on its remoteId and a placeholder does not`() {
        // The effects that read the visible items back pick bookmarks out with `key as? String`,
        // so a placeholder key must not be a String or they would treat it as a remoteId.
        val w = window(viewTotal = 100, 0)

        assertEquals("remote-1", w.keyAt(0))
        assertTrue(w.keyAt(50) !is String, "a placeholder key must not read as a remoteId")
        assertTrue(w.keyAt(50) != w.keyAt(51), "and must still be unique per slot")
    }

    @Test
    fun `a remoteId in no loaded page is not found`() {
        val w = window(viewTotal = 100, 0)

        assertEquals(-1, w.indexOfRemoteId("remote-60"))
    }

    @Test
    fun `loaded rows come back in view order however the pages arrived`() {
        // A jump can load a later page before an earlier one; order is the view's, not arrival's.
        val w = window(viewTotal = 100, 3, 0, 1)

        assertEquals(
            (1L..20L).toList() + (31L..40L).toList(),
            w.loadedRows().map { it.localId }
        )
    }

    @Test
    fun `an empty window counts nothing and loads nothing`() {
        assertTrue(BookmarkWindow.EMPTY.isEmpty)
        assertEquals(0, BookmarkWindow.EMPTY.total)
        assertEquals(BookmarkSlot.Placeholder, BookmarkWindow.EMPTY[0])
        assertTrue(BookmarkWindow.dense(emptyList()).isEmpty)
    }

    @Test
    fun `a view with rows is not empty even when none has loaded yet`() {
        // `isEmpty` asks whether the view holds anything, which is what the empty state renders
        // on — not whether the rows have arrived.
        val w = window(viewTotal = 100)

        assertTrue(!w.isEmpty)
        assertEquals(0, w.loadedCount)
        assertEquals(100, w.total)
    }
}
