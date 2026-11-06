package com.karakept.app.data.repository

import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.domain.ServerCompatibility
import com.karakept.app.domain.ServerVersionCheck
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerVersionRepositoryTest : BaseRepositoryTest() {

    private val remoteDataSource: RemoteDataSource = mockk()
    private val server = Server(id = "s1", url = "https://kk.example.com", apiKey = "KEY", label = "t")

    private fun repository() = ServerVersionRepository(remoteDataSource, testAppDispatchers)

    @Test
    fun refresh_remembersTheAnswerPerServer() = runTest(testDispatcher) {
        // Arrange
        coEvery { remoteDataSource.fetchServerVersion(server.url) } returns "0.1.0"
        val repository = repository()

        // Act
        val check = repository.refresh(server)

        // Assert
        val expected = ServerVersionCheck("0.1.0", ServerCompatibility.OUTDATED)
        assertEquals(expected, check)
        assertEquals(mapOf(server.id to expected), repository.versions.value)
    }

    @Test
    fun refresh_failureKeepsTheLastKnownAnswer() = runTest(testDispatcher) {
        // Arrange
        coEvery { remoteDataSource.fetchServerVersion(server.url) } returns "99.0.0"
        val repository = repository()
        repository.refresh(server)
        coEvery { remoteDataSource.fetchServerVersion(server.url) } throws ApiException("offline")

        // Act
        val check = repository.refresh(server)

        // Assert
        assertEquals(ServerCompatibility.SUPPORTED, check?.compatibility)
        assertEquals(ServerCompatibility.SUPPORTED, repository.versions.value[server.id]?.compatibility)
    }

    @Test
    fun check_unreachableServerIsUnknownNotOutdated() = runTest(testDispatcher) {
        coEvery { remoteDataSource.fetchServerVersion(server.url) } throws ApiException("offline")

        assertNull(repository().check(server.url))
    }

    @Test
    fun claimOutdatedWarning_onlyOncePerServer() {
        val repository = repository()

        assertTrue(repository.claimOutdatedWarning("s1"))
        assertFalse(repository.claimOutdatedWarning("s1"))
        assertTrue(repository.claimOutdatedWarning("s2"))
    }
}
