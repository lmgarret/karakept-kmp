package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.ServerDao
import com.karakept.app.data.local.entity.ServerEntity
import com.karakept.app.data.secure.SecureCredentialStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [ServerRepository] credential migration logic.
 *
 * Covers migration happy path, idempotency, fallback on secure store failure,
 * and servers Flow reading from secure store with DB fallback.
 */
class ServerRepositoryMigrationTest {

    private val serverDao: ServerDao = mockk(relaxed = true)
    private val secureStore: SecureCredentialStore = mockk(relaxed = true)

    private fun createRepo() = ServerRepository(serverDao, secureStore)

    private val serverWithKey = ServerEntity(
        id = "s1",
        url = "https://example.com",
        apiKey = "secret-key-123",
        label = "Test"
    )

    private val serverWithEmptyKey = ServerEntity(
        id = "s1",
        url = "https://example.com",
        apiKey = "",
        label = "Test"
    )

    // ---- Test 1: Migration moves credentials from DB to secure store ----

    @Test
    fun testMigrationMovesCredentialsFromDbToSecureStore() = runTest {
        coEvery { serverDao.getAllServersSync() } returns listOf(serverWithKey)
        every { secureStore.hasKey("s1") } returns false

        val repo = createRepo()
        repo.triggerMigration()

        verify { secureStore.storeApiKey("s1", "secret-key-123") }
        coVerify { serverDao.insertServer(match { it.id == "s1" && it.apiKey == "" }) }
    }

    // ---- Test 2: Migration is idempotent ----

    @Test
    fun testMigrationIsIdempotent() = runTest {
        coEvery { serverDao.getAllServersSync() } returns listOf(serverWithKey)
        every { secureStore.hasKey("s1") } returns false

        val repo = createRepo()
        repo.triggerMigration()
        repo.triggerMigration() // second call should be a no-op

        // storeApiKey called only once because migrated flag is set after first call
        verify(exactly = 1) { secureStore.storeApiKey("s1", "secret-key-123") }
    }

    // ---- Test 3: Migration fallback when secure store throws ----

    @Test
    fun testMigrationFallbackWhenSecureStoreThrows() = runTest {
        coEvery { serverDao.getAllServersSync() } returns listOf(serverWithKey)
        every { secureStore.hasKey("s1") } returns false
        every { secureStore.storeApiKey(any(), any()) } throws RuntimeException("Keystore unavailable")

        val repo = createRepo()
        repo.triggerMigration() // should NOT throw

        // DB apiKey should NOT be cleared since secure store failed
        coVerify(exactly = 0) { serverDao.insertServer(match { it.apiKey == "" }) }
    }

    // ---- Test 4: Servers Flow reads apiKey from secure store ----

    @Test
    fun testServersFlowReturnsApiKeyFromSecureStore() = runTest {
        coEvery { serverDao.getAllServers() } returns flowOf(listOf(serverWithEmptyKey))
        every { secureStore.getApiKey("s1") } returns "secret-key-123"

        val repo = createRepo()
        val result = repo.servers.first()

        assertEquals(1, result.size)
        assertEquals("secret-key-123", result[0].apiKey)
    }

    // ---- Test 5: Servers Flow falls back to DB key when secure store returns null ----

    @Test
    fun testServersFlowFallsBackToDbKeyWhenSecureStoreReturnsNull() = runTest {
        val serverWithDbKey = ServerEntity("s1", "https://example.com", "db-fallback-key", "Test")
        coEvery { serverDao.getAllServers() } returns flowOf(listOf(serverWithDbKey))
        every { secureStore.getApiKey("s1") } returns null

        val repo = createRepo()
        val result = repo.servers.first()

        assertEquals("db-fallback-key", result[0].apiKey)
    }

    // ---- Test 6: Servers Flow falls back to DB key when secure store throws ----

    @Test
    fun testServersFlowFallsBackToDbKeyWhenSecureStoreThrows() = runTest {
        val serverWithDbKey = ServerEntity("s1", "https://example.com", "db-fallback-key", "Test")
        coEvery { serverDao.getAllServers() } returns flowOf(listOf(serverWithDbKey))
        every { secureStore.getApiKey("s1") } throws RuntimeException("Keystore locked")

        val repo = createRepo()
        val result = repo.servers.first()

        assertEquals("db-fallback-key", result[0].apiKey)
    }
}
