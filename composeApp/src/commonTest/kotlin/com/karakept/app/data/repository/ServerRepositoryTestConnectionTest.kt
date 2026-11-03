package com.karakept.app.data.repository

import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerRepositoryTestConnectionTest {

    private val remote: RemoteDataSource = mockk(relaxed = true)
    private val repo = ServerRepository(mockk(relaxed = true), mockk(relaxed = true), remote)

    @Test
    fun delegatesToRemote() = runTest {
        // Arrange
        coEvery { remote.testConnection("https://k.example", "key") } returns true
        // Act / Assert
        assertTrue(repo.testConnection("https://k.example", "key"))

        // Arrange
        coEvery { remote.testConnection("https://k.example", "key") } returns false
        // Act / Assert
        assertFalse(repo.testConnection("https://k.example", "key"))
    }
}
