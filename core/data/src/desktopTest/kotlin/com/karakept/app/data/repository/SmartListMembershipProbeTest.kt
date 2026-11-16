package com.karakept.app.data.repository

import com.karakept.api.model.Bookmark as BookmarkDto
import com.karakept.api.model.KarakeepList
import com.karakept.api.model.PaginatedBookmarks
import com.karakept.app.data.local.InMemoryDatabaseTest
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.testing.testBookmarkEntity
import com.karakept.app.utils.TestAppDispatchers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The per-bookmark smart-list probe against a real database: what it writes into a bookmark's
 * cached list ids, and what it leaves flagged for a sync when it cannot answer.
 */
internal class SmartListMembershipProbeTest : InMemoryDatabaseTest() {

    private val testDispatcher = StandardTestDispatcher()
    private val server = Server(id = "server-1", url = "https://example.com", apiKey = "key", label = "Test")
    private val remote: RemoteDataSource = mockk(relaxed = true)
    private val offline = MutableStateFlow(false)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true) {
        every { offlineMode } returns offline
    }
    private val serverRepository: ServerRepository = mockk(relaxed = true) {
        every { servers } returns flowOf(listOf(server))
    }
    private lateinit var repository: BookmarkActionsRepository

    // 2024-05-19T12:34:56.000Z
    private val createdAt = 1_716_122_096_000L
    private val createdAtIso = "2024-05-19T12:34:56.000Z"

    private val excludesManual = KarakeepList(id = "notWork", name = "Not work", type = KarakeepList.Type.SMART, query = "-list:Work")
    private val inAnyList = KarakeepList(id = "listed", name = "Listed", type = KarakeepList.Type.SMART, query = "is:inlist")
    private val favourites = KarakeepList(id = "favs", name = "Favourites", type = KarakeepList.Type.SMART, query = "is:fav")
    private val work = KarakeepList(id = "work", name = "Work", type = KarakeepList.Type.MANUAL)

    @Before
    fun setUp() {
        repository = BookmarkActionsRepository(
            bookmarkDao = db.bookmarkDao(),
            pendingActionDao = db.pendingActionDao(),
            remoteDataSource = remote,
            serverRepository = serverRepository,
            settingsRepository = settingsRepository,
            appDispatchers = TestAppDispatchers(testDispatcher)
        )
    }

    private suspend fun seed(remoteId: String = "bm1", listIds: String = "", createdAt: Long = this.createdAt) {
        db.bookmarkDao().insertBookmark(
            testBookmarkEntity(localId = 0, remoteId = remoteId, serverId = server.id, createdAt = createdAt, listIds = listIds)
        )
    }

    private suspend fun listIdsOf(remoteId: String = "bm1") =
        db.bookmarkDao().getBookmarkByRemoteId(remoteId, server.id)!!.listIds.split(",").filter { it.isNotBlank() }.toSet()

    private fun page(vararg rows: Pair<String, String>) =
        PaginatedBookmarks(bookmarks = rows.map { BookmarkDto(id = it.first, createdAt = it.second) })

    private fun answer(listId: String, result: PaginatedBookmarks) {
        coEvery { remote.fetchListPage(server, listId, any(), any()) } returns result
    }

    @Test
    fun `a bookmark the server puts in the smart list gains that id and nothing else changes`() = runTest(testDispatcher) {
        seed(listIds = "work,favs")
        answer("listed", page("bm1" to createdAtIso))

        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(inAnyList, favourites, work)).join()

        assertEquals(setOf("work", "favs", "listed"), listIdsOf())
        assertTrue(repository.smartListsNeedingRefresh.value.isEmpty())
    }

    @Test
    fun `an excluded bookmark loses only that smart list`() = runTest(testDispatcher) {
        seed(listIds = "work,notWork,favs")
        answer("notWork", page("older" to "2024-05-19T12:00:00.000Z"))

        repository.addToList("bm1", server.id, "work")
        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(excludesManual, favourites, work)).join()

        assertEquals(setOf("work", "favs"), listIdsOf())
        assertTrue(repository.smartListsNeedingRefresh.value.isEmpty())
    }

    @Test
    fun `an empty page takes the bookmark out`() = runTest(testDispatcher) {
        seed(listIds = "notWork")
        answer("notWork", PaginatedBookmarks(bookmarks = emptyList()))

        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(excludesManual)).join()

        assertEquals(emptySet(), listIdsOf())
    }

    @Test
    fun `the probe asks for one row, starting at the bookmark's own cursor`() = runTest(testDispatcher) {
        seed()
        answer("listed", page("bm1" to createdAtIso))

        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(inAnyList)).join()

        coVerify(exactly = 1) { remote.fetchListPage(server, "listed", "bm1_$createdAtIso", 1) }
    }

    @Test
    fun `a smart list whose query cannot see lists is neither probed nor flagged`() = runTest(testDispatcher) {
        seed(listIds = "favs")

        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(favourites, work)).join()

        coVerify(exactly = 0) { remote.fetchListPage(any(), any(), any(), any()) }
        assertEquals(setOf("favs"), listIdsOf())
        assertTrue(repository.smartListsNeedingRefresh.value.isEmpty())
    }

    @Test
    fun `a failed request keeps the row and flags the list`() = runTest(testDispatcher) {
        seed(listIds = "notWork")
        coEvery { remote.fetchListPage(server, "notWork", any(), any()) } throws RuntimeException("boom")
        answer("listed", page("bm1" to createdAtIso))

        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(excludesManual, inAnyList)).join()

        assertEquals(setOf("notWork", "listed"), listIdsOf())
        assertEquals(setOf("notWork"), repository.smartListsNeedingRefresh.value)
    }

    @Test
    fun `a page that does not read as expected keeps the row and flags the list`() = runTest(testDispatcher) {
        seed(listIds = "notWork")
        answer("notWork", page("newer" to "2024-05-19T13:00:00.000Z"))

        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(excludesManual)).join()

        assertEquals(setOf("notWork"), listIdsOf())
        assertEquals(setOf("notWork"), repository.smartListsNeedingRefresh.value)
    }

    @Test
    fun `offline mode probes nothing and flags every affected list`() = runTest(testDispatcher) {
        offline.value = true
        seed(listIds = "notWork")

        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(excludesManual, inAnyList, favourites)).join()

        coVerify(exactly = 0) { remote.fetchListPage(any(), any(), any(), any()) }
        assertEquals(setOf("notWork"), listIdsOf())
        assertEquals(setOf("notWork", "listed"), repository.smartListsNeedingRefresh.value)
    }

    @Test
    fun `an edit the server has not received yet is not probed`() = runTest(testDispatcher) {
        seed(listIds = "notWork")
        coEvery { remote.addBookmarkToList(server, "work", "bm1") } throws RuntimeException("network down")

        repository.addToList("bm1", server.id, "work")
        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(excludesManual)).join()

        assertEquals(1, db.pendingActionDao().countActionsForBookmarkByType("bm1", server.id, PendingActionType.MOVE_TO_LIST))
        coVerify(exactly = 0) { remote.fetchListPage(any(), any(), any(), any()) }
        assertEquals(setOf("work", "notWork"), listIdsOf())
        assertEquals(setOf("notWork"), repository.smartListsNeedingRefresh.value)
    }

    @Test
    fun `the edit is pushed to the server before the probe asks`() = runTest(testDispatcher) {
        seed()
        answer("notWork", PaginatedBookmarks(bookmarks = emptyList()))

        repository.addToList("bm1", server.id, "work")
        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(excludesManual)).join()

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            remote.addBookmarkToList(server, "work", "bm1")
            remote.fetchListPage(server, "notWork", any(), any())
        }
    }

    @Test
    fun `a createdAt the server did not send is not probed`() = runTest(testDispatcher) {
        seed(listIds = "notWork", createdAt = createdAt + 417)

        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(excludesManual)).join()

        coVerify(exactly = 0) { remote.fetchListPage(any(), any(), any(), any()) }
        assertEquals(setOf("notWork"), repository.smartListsNeedingRefresh.value)
    }

    @Test
    fun `a probe overtaken by a newer edit writes nothing and flags nothing`() = runTest(testDispatcher) {
        seed(listIds = "notWork")
        // While the first probe's request is out, the edit is undone.
        coEvery { remote.fetchListPage(server, "notWork", any(), any()) } coAnswers {
            repository.removeFromList("bm1", server.id, "work")
            PaginatedBookmarks(bookmarks = emptyList())
        }

        repository.addToList("bm1", server.id, "work")
        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(excludesManual)).join()

        assertEquals(setOf("notWork"), listIdsOf())
        assertTrue(repository.smartListsNeedingRefresh.value.isEmpty())
    }

    @Test
    fun `the probe after an undo is the one that writes`() = runTest(testDispatcher) {
        seed(listIds = "notWork")
        answer("notWork", PaginatedBookmarks(bookmarks = emptyList()))
        repository.addToList("bm1", server.id, "work")
        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(excludesManual)).join()
        assertEquals(setOf("work"), listIdsOf())

        answer("notWork", page("bm1" to createdAtIso))
        repository.removeFromList("bm1", server.id, "work")
        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(excludesManual)).join()

        assertEquals(setOf("notWork"), listIdsOf())
    }

    @Test
    fun `a batch past the request budget is flagged instead of probed`() = runTest(testDispatcher) {
        val count = SmartListMembershipProbe.MAX_PROBE_REQUESTS / 2 + 1
        val ids = (1..count).map { "bm$it" }
        ids.forEach { seed(remoteId = it, listIds = "notWork") }

        repository.reconcileSmartListsAfterEdit(server.id, ids, listOf(excludesManual, inAnyList)).join()

        coVerify(exactly = 0) { remote.fetchListPage(any(), any(), any(), any()) }
        ids.forEach { assertEquals(setOf("notWork"), listIdsOf(it)) }
        assertEquals(setOf("notWork", "listed"), repository.smartListsNeedingRefresh.value)
    }

    @Test
    fun `a batch within the budget is probed bookmark by bookmark`() = runTest(testDispatcher) {
        seed(remoteId = "bm1", listIds = "notWork")
        seed(remoteId = "bm2", listIds = "notWork")
        coEvery { remote.fetchListPage(server, "notWork", "bm1_$createdAtIso", any()) } returns page("bm1" to createdAtIso)
        coEvery { remote.fetchListPage(server, "notWork", "bm2_$createdAtIso", any()) } returns page("bm1" to createdAtIso)

        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1", "bm2"), listOf(excludesManual)).join()

        assertEquals(setOf("notWork"), listIdsOf("bm1"))
        // At the same second ids sort descending, so bm1 opening bm2's page is the next row
        // after bm2: bm2 is not in the list.
        assertEquals(emptySet(), listIdsOf("bm2"))
        assertTrue(repository.smartListsNeedingRefresh.value.isEmpty())
        coVerify(exactly = 2) { remote.fetchListPage(any(), any(), any(), any()) }
    }

    @Test
    fun `taking a flag clears that list only`() = runTest(testDispatcher) {
        offline.value = true
        seed()
        repository.reconcileSmartListsAfterEdit(server.id, listOf("bm1"), listOf(excludesManual, inAnyList)).join()

        assertTrue(repository.takeSmartListRefresh("notWork"))
        assertEquals(false, repository.takeSmartListRefresh("notWork"))
        assertEquals(setOf("listed"), repository.smartListsNeedingRefresh.value)
    }

    @Test
    fun `the list-id write is a compare-and-set`() = runTest(testDispatcher) {
        seed(listIds = "a")
        val row = db.bookmarkDao().getBookmarkByRemoteId("bm1", server.id)!!

        assertEquals(0, db.bookmarkDao().updateListIdsIfUnchanged(row.localId, "stale", "x"))
        assertEquals(1, db.bookmarkDao().updateListIdsIfUnchanged(row.localId, "a", "a,b"))
        assertEquals(setOf("a", "b"), listIdsOf())
    }
}
