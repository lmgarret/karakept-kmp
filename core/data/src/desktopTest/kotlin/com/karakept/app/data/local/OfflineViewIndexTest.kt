package com.karakept.app.data.local

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.local.migrations.MIGRATION_13_14
import com.karakept.app.data.local.migrations.withAppSchema
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.data.repository.BookmarkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The offline view must be answered from `hasContent`, never by reading article bodies.
 *
 * `content` holds the whole article, and the view used to select on `length(content) > 0`. SQLite
 * has to load a value to compare it, and `length()` on TEXT decodes the UTF-8 on top of that, so
 * counting the view read every body in the table: 523ms for 861 rows on a real library, against
 * 15ms for the 4280-row archive.
 *
 * The flag is only as good as whatever keeps it true, and nothing in a query fails loudly when it
 * drifts — a stale flag silently drops a bookmark out of the offline view, or holds one in it. So
 * what is pinned here is both halves: that the flag agrees with the column it stands for across
 * every way a row is written, and that the view's own SQL reaches it through the index.
 */
class OfflineViewIndexTest {

    private val dbPath = "/tmp/karakept-offline-index-${System.nanoTime()}.db"
    private val serverId = "s1"
    private val offline = FilterConfig(status = FilterStatus.OFFLINE)

    @AfterTest
    fun cleanup() {
        java.io.File(dbPath).delete()
    }

    private fun open() = Room.databaseBuilder<AppDatabase>(name = dbPath)
        .withAppSchema()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private suspend fun <T> withRawConnection(block: suspend (SQLiteConnection) -> T): T {
        val c = BundledSQLiteDriver().open(dbPath)
        return try { block(c) } finally { c.close() }
    }

    private fun SQLiteConnection.planOf(sql: String): String {
        val stmt = prepare("EXPLAIN QUERY PLAN $sql")
        return try {
            buildList { while (stmt.step()) add(stmt.getText(3)) }.joinToString(" | ")
        } finally { stmt.close() }
    }

    /** The app's own view SQL, with its binds inlined so EXPLAIN can be run on it verbatim. */
    private fun viewSql(select: String, orderBy: String = "") =
        "SELECT $select FROM bookmarks WHERE " +
            BookmarkRepository.buildViewPredicate(serverId, offline).sql
                .replace("?", "'$serverId'") + orderBy

    private fun bookmark(id: Long, content: String?) = BookmarkEntity(
        remoteId = "r$id", serverId = serverId, url = "u$id", title = "t$id",
        content = content, imageUrl = null, bannerImageAssetId = null,
        screenshotAssetId = null, description = null, createdAt = id,
        isArchived = false, isStarred = false
    )

    @Test
    fun `counting the view uses the index and never the table`() = runBlocking {
        open().let { db ->
            db.bookmarkDao().insertBookmark(bookmark(1, "body"))
            db.bookmarkDao().insertBookmark(bookmark(2, null))
            db.bookmarkDao().insertBookmark(bookmark(3, ""))
            assertEquals(
                1,
                db.bookmarkDao().countBookmarks(
                    BookmarkRepository.buildCountQuery(serverId, offline)
                ),
                "neither a null body nor an empty one is an offline bookmark"
            )
            db.close()
        }

        val plan = withRawConnection { it.planOf(viewSql("COUNT(*)")) }
        assertTrue(
            plan.contains("index_bookmarks_serverId_hasContent_createdAt_localId"),
            "the count must be an index scan — reaching the table means reading article " +
                "bodies. Plan was: $plan"
        )
    }

    @Test
    fun `reading a page of the view neither scans nor sorts`() = runBlocking {
        open().let { it.bookmarkDao().insertBookmark(bookmark(1, "body")); it.close() }

        val plan = withRawConnection {
            it.planOf(viewSql("localId", " ORDER BY createdAt DESC, localId DESC LIMIT 50"))
        }
        assertTrue(plan.contains("index_bookmarks_serverId_hasContent_createdAt_localId"), "Plan: $plan")
        assertTrue(
            !plan.contains("TEMP B-TREE"),
            "the index carries the default sort, so a page must come off it in order. Plan: $plan"
        )
    }

    @Test
    fun `the flag follows the content through every write`() = runBlocking {
        open().let { db ->
            val dao = db.bookmarkDao()
            // Constructed without a body, then given one by the content update — the one write
            // that sets content without rebuilding the row.
            dao.insertBookmark(bookmark(1, null))
            val stored = dao.getBookmarkByRemoteId("r1", serverId)!!
            assertEquals(0, dao.getOfflineCount(serverId))

            dao.updateContent(stored.localId, "body", 3)
            assertEquals(1, dao.getOfflineCount(serverId), "content arriving joins the view")

            dao.updateContent(stored.localId, "", 0)
            assertEquals(0, dao.getOfflineCount(serverId), "an emptied body leaves it")
            db.close()
        }
    }

    @Test
    fun `the drawer's count and the view's count are the same number`() = runBlocking {
        open().let { db ->
            db.bookmarkDao().insertBookmark(bookmark(1, "body"))
            db.bookmarkDao().insertBookmark(bookmark(2, null))
            db.bookmarkDao().insertBookmark(bookmark(3, ""))
            assertEquals(
                db.bookmarkDao().countBookmarks(
                    BookmarkRepository.buildCountQuery(serverId, offline)
                ),
                db.bookmarkDao().getOfflineCountFlow(serverId).first(),
                "the drawer and the list must not disagree about what is available offline"
            )
            db.close()
        }
    }

    @Test
    fun `the migration backfills the flag from the bodies already stored`() = runBlocking {
        // A v13 database: the column does not exist yet, so the rows are written raw.
        withRawConnection { c ->
            c.execSQL(
                "CREATE TABLE bookmarks (localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "remoteId TEXT NOT NULL, serverId TEXT NOT NULL, url TEXT NOT NULL, " +
                    "title TEXT NOT NULL, content TEXT, createdAt INTEGER NOT NULL)"
            )
            c.execSQL("INSERT INTO bookmarks (remoteId, serverId, url, title, content, createdAt) VALUES ('a','s1','u','t','body',1)")
            c.execSQL("INSERT INTO bookmarks (remoteId, serverId, url, title, content, createdAt) VALUES ('b','s1','u','t',NULL,2)")
            c.execSQL("INSERT INTO bookmarks (remoteId, serverId, url, title, content, createdAt) VALUES ('c','s1','u','t','',3)")

            MIGRATION_13_14.migrate(c)

            val stmt = c.prepare("SELECT remoteId, hasContent FROM bookmarks ORDER BY remoteId")
            val flags = buildList { while (stmt.step()) add(stmt.getText(0) to stmt.getInt(1)) }
            stmt.close()
            assertEquals(listOf("a" to 1, "b" to 0, "c" to 0), flags)
        }
    }
}
