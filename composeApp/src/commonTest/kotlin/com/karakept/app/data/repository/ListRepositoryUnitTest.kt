package com.karakept.app.data.repository

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ListRepositoryUnitTest : BaseRepositoryTest() {

    private val listDao = mockk<ListDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    private val repository = ListRepository(remoteDataSource, listDao, settingsRepository)

    private val testServer = Server("server1", "http://localhost", "apikey", "Test Server")

    @Test
    fun testRenameList_Success() = runTest(testDispatcher) {
        val listId = "list-123"
        val newName = "My Renamed List"
        val newIcon = "📚"

        val mockList = mockk<KarakeepList>(relaxed = true) {
            every { id } returns listId
            every { name } returns newName
            every { icon } returns newIcon
            every { parentId } returns null
            every { public } returns false
            every { description } returns null
            every { type } returns KarakeepList.Type.MANUAL
            every { query } returns null
        }

        coEvery { remoteDataSource.updateList(testServer, listId, newName, newIcon) } returns mockList
        coEvery { listDao.getListsForServerOnce(testServer.id) } returns emptyList()

        val result = repository.renameList(testServer, listId, newName, newIcon)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { remoteDataSource.updateList(testServer, listId, newName, newIcon) }
        coVerify(exactly = 1) { listDao.insertLists(any()) }
    }

    @Test
    fun testRenameList_NetworkError_UpdatesLocally() = runTest(testDispatcher) {
        val listId = "list-456"
        val newName = "Offline Renamed List"
        val newIcon = null

        coEvery {
            remoteDataSource.updateList(testServer, listId, newName, newIcon)
        } answers { throw ApiException("Network error") }
        coEvery { listDao.getListsForServerOnce(testServer.id) } returns emptyList()

        val result = repository.renameList(testServer, listId, newName, newIcon)

        // Should fail because remote failed, but local update should still happen
        assertTrue(result.isFailure)
        // Verify local DB was updated as fallback
        coVerify(exactly = 1) {
            listDao.updateListNameAndIcon(listId, testServer.id, newName, newIcon, any())
        }
    }

    @Test
    fun testRenameList_RemovesIcon_WhenIconIsNull() = runTest(testDispatcher) {
        val listId = "list-789"
        val newName = "List Without Icon"

        val mockList = mockk<KarakeepList>(relaxed = true) {
            every { id } returns listId
            every { name } returns newName
            every { icon } returns null
            every { parentId } returns null
            every { public } returns false
            every { description } returns null
            every { type } returns KarakeepList.Type.MANUAL
            every { query } returns null
        }

        coEvery { remoteDataSource.updateList(testServer, listId, newName, null) } returns mockList
        coEvery { listDao.getListsForServerOnce(testServer.id) } returns emptyList()

        val result = repository.renameList(testServer, listId, newName, null)

        assertTrue(result.isSuccess)
        coVerify { remoteDataSource.updateList(testServer, listId, newName, null) }
    }
}
