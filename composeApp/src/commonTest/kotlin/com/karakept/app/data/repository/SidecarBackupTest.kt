package com.karakept.app.data.repository

import com.karakept.app.data.model.AppBackup
import com.karakept.app.data.model.BackupSettings
import com.karakept.app.data.model.EncryptedBackupEnvelope
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SidecarConfig
import com.karakept.app.data.model.sidecarKey
import com.karakept.app.data.remote.SidecarDataSource
import com.karakept.app.data.secure.SecureCredentialStore
import com.karakept.app.domain.sidecar.SidecarKind
import com.karakept.app.utils.BackupCrypto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sidecar configuration has to survive export and import.
 *
 * Without this a restore brings the servers back and silently drops the services attached to them,
 * which looks like a working restore right up to the point the actions are missing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SidecarBackupTest : BaseRepositoryTest() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private lateinit var dataStore: FakeDataStore
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var secureStore: SecureCredentialStore
    private lateinit var sidecarRepository: SidecarRepository
    private lateinit var serverRepository: ServerRepository
    private lateinit var repository: BackupRepository

    private val serverId = "server-1"
    private val kind = SidecarKind.HTML_SCRUB
    private val config = SidecarConfig(
        serverId = serverId,
        kind = kind,
        baseUrl = "http://scrub:8080",
        enabled = true
    )

    @BeforeTest
    override fun setup() {
        super.setup()
        dataStore = FakeDataStore()
        settingsRepository = SettingsRepository(dataStore)
        secureStore = mockk(relaxed = true)
        sidecarRepository = SidecarRepository(
            settingsRepository,
            secureStore,
            mockk<SidecarDataSource>(relaxed = true),
            testAppDispatchers
        )
        serverRepository = mockk(relaxed = true)
        every { serverRepository.servers } returns flowOf(
            listOf(Server(serverId, "https://karakeep.example.com", "key", "Test"))
        )
        repository = BackupRepository(settingsRepository, serverRepository, sidecarRepository)
        mockkStatic("com.karakept.app.data.repository.SettingsRepositoryMutationsKt")
    }

    @AfterTest
    override fun tearDown() {
        unmockkStatic("com.karakept.app.data.repository.SettingsRepositoryMutationsKt")
        super.tearDown()
    }

    @Test
    fun `a configured sidecar is exported with its token`() = runTest(testDispatcher) {
        every { secureStore.getApiKey(any()) } returns "s3cret"
        sidecarRepository.save(config, token = "s3cret")

        val backup = repository.buildBackup()

        assertEquals(1, backup.sidecars.size)
        val exported = backup.sidecars.first()
        assertEquals(serverId, exported.serverId)
        assertEquals(kind, exported.kind)
        assertEquals("http://scrub:8080", exported.baseUrl)
        assertTrue(exported.enabled)
        // Like a server's API key: only ever inside the encrypted file.
        assertEquals("s3cret", exported.token)
    }

    @Test
    fun `no sidecars means an empty list, not a failure`() = runTest(testDispatcher) {
        val backup = repository.buildBackup()

        assertTrue(backup.sidecars.isEmpty())
    }

    @Test
    fun `importing restores the configuration and the token`() = runTest(testDispatcher) {
        every { secureStore.getApiKey(any()) } returns "s3cret"
        sidecarRepository.save(config, token = "s3cret")
        val envelope = encrypted(repository.buildBackup(), pin = "1234")

        // A fresh device: nothing stored yet.
        val freshDataStore = FakeDataStore()
        val freshSettings = SettingsRepository(freshDataStore)
        val freshSecure = mockk<SecureCredentialStore>(relaxed = true)
        val freshSidecars = SidecarRepository(
            freshSettings,
            freshSecure,
            mockk<SidecarDataSource>(relaxed = true),
            testAppDispatchers
        )
        val freshBackup = BackupRepository(freshSettings, serverRepository, freshSidecars)

        val result = freshBackup.importFromJson(envelope, pin = "1234")

        val restored = freshSidecars.sidecars.first()[sidecarKey(serverId, kind)]
        assertEquals("http://scrub:8080", restored?.baseUrl)
        assertTrue(restored?.enabled == true)
        verify { freshSecure.storeApiKey(any(), "s3cret") }
        assertTrue(result.contains("1 sidecar"))
    }

    @Test
    fun `a backup from a build without sidecars still imports`() = runTest(testDispatcher) {
        // The field defaults to an empty list, so an older backup file must not fail to parse.
        val older = """{"version":1,"exportedAt":"2024-01-01 00:00:00 UTC","settings":{},"servers":[]}"""
        val plaintext = older.encodeToByteArray()
        val envelope = json.encodeToString(
            EncryptedBackupEnvelope(data = BackupCrypto.encrypt(plaintext, "1234"))
        )

        val result = repository.importFromJson(envelope, pin = "1234")

        assertTrue(result.contains("restored"))
        assertTrue(!result.contains("sidecar"))
    }

    private fun encrypted(backup: AppBackup, pin: String): String {
        val plaintext = json.encodeToString(backup).encodeToByteArray()
        return json.encodeToString(EncryptedBackupEnvelope(data = BackupCrypto.encrypt(plaintext, pin)))
    }
}
