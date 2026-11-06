package com.karakept.app.data.repository

import androidx.room3.RoomRawQuery
import androidx.sqlite.SQLiteStatement
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.model.ReadFilter
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SortOption
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class BookmarkViewQueriesTest : BaseRepositoryTest() {

    private val server = Server("server1", "https://example.com", "test-key", "Test Server")
    private val filter = FilterConfig(
        status = FilterStatus.FAVORITES,
        sort = SortOption.TITLE_AZ,
        readFilter = ReadFilter.UNREAD,
        tags = listOf("kotlin")
    )
    private val rows = (0 until 12).map { bookmark(it) }
    private val issuedSql = mutableListOf<String>()

    // Answers every paged read as a table holding [rows] would: the last two binds of a page
    // query are its LIMIT and OFFSET.
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true).also { dao ->
        coEvery { dao.getBookmarksPaged(any()) } answers {
            val query = firstArg<RoomRawQuery>()
            issuedSql += query.sql
            val longs = bindsOf(query).filterIsInstance<Long>()
            val (limit, offset) = longs.takeLast(2).map { it.toInt() }
            rows.drop(offset).take(limit)
        }
    }

    private val queries = BookmarkViewQueries(bookmarkDao)

    @Test
    fun getBookmarkAtMatchesPage() = runTest(testDispatcher) {
        // Arrange
        val page = queries.getBookmarkPage(server, filter, offset = 0, limit = rows.size)
        val pageSql = issuedSql.single()

        for (index in rows.indices) {
            // Act
            val row = queries.getBookmarkAt(server, filter, index)

            // Assert
            assertEquals(page[index], row, "row at $index")
            assertEquals(pageSql, issuedSql.last(), "query for row $index")
        }
    }

    @Test
    fun getBookmarkAtNegativeIndexIsNull() = runTest(testDispatcher) {
        // Act
        val row = queries.getBookmarkAt(server, filter, -1)

        // Assert
        assertNull(row)
        assertEquals(emptyList(), issuedSql)
    }

    private fun bindsOf(query: RoomRawQuery): List<Any> {
        val binds = mutableMapOf<Int, Any>()
        val statement = mockk<SQLiteStatement>(relaxed = true)
        every { statement.bindLong(any(), any()) } answers { binds[firstArg()] = secondArg<Long>() }
        every { statement.bindText(any(), any()) } answers { binds[firstArg()] = secondArg<String>() }
        query.getBindingFunction().invoke(statement)
        return binds.entries.sortedBy { it.key }.map { it.value }
    }

    private fun bookmark(i: Int) = BookmarkEntity(
        localId = i.toLong() + 1,
        remoteId = "remote-$i",
        serverId = server.id,
        url = "https://example.com/$i",
        title = "Bookmark $i",
        content = null,
        imageUrl = null,
        bannerImageAssetId = null,
        screenshotAssetId = null,
        description = null,
        createdAt = i.toLong(),
        isArchived = false,
        isStarred = true
    )
}
