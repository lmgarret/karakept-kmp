package com.karakept.app.data.repository

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ListRepositoryFetchTest : BaseRepositoryTest() {

    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)
    private val repository = ListRepository(
        remoteDataSource,
        mockk<ListDao>(relaxed = true),
        mockk<SettingsRepository>(relaxed = true),
        testAppDispatchers
    )
    private val server = Server("s1", "http://localhost", "key", "Label")

    @Test
    fun fetchLists_returnsTheRemoteLists() = runTest(testDispatcher) {
        val lists = listOf(mockk<KarakeepList>(relaxed = true))
        coEvery { remoteDataSource.fetchLists(server) } returns lists

        assertEquals(lists, repository.fetchLists(server))
    }
}
