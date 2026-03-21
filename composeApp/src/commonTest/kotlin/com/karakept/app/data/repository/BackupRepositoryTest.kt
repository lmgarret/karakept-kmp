package com.karakept.app.data.repository

import com.karakept.app.data.model.AppBackup
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.data.model.BackupSettings
import com.karakept.app.data.model.EncryptedBackupEnvelope
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.ServerBackup
import com.karakept.app.utils.BackupCrypto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupRepositoryTest : BaseRepositoryTest() {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val repository = BackupRepository(settingsRepository, serverRepository)

    @BeforeTest
    override fun setup() {
        super.setup()
        mockkStatic("com.karakept.app.data.repository.SettingsRepositoryMutationsKt")
    }

    @AfterTest
    override fun tearDown() {
        unmockkStatic("com.karakept.app.data.repository.SettingsRepositoryMutationsKt")
        super.tearDown()
    }

    // ── buildBackup ───────────────────────────────────────────────────────────

    @Test
    fun `buildBackup uses currentSettings from SettingsRepository`() = runTest(testDispatcher) {
        val expectedSettings = BackupSettings(themeMode = "DARK", accentColor = "BLUE")
        coEvery { settingsRepository.currentSettings() } returns expectedSettings
        coEvery { serverRepository.servers } returns flowOf(emptyList())

        val backup = repository.buildBackup()

        assertEquals(expectedSettings, backup.settings)
        assertEquals(BackupRepository.CURRENT_BACKUP_VERSION, backup.version)
        assertTrue(backup.exportedAt.endsWith("UTC"))
    }

    @Test
    fun `buildBackup includes server list`() = runTest(testDispatcher) {
        coEvery { settingsRepository.currentSettings() } returns BackupSettings()
        val servers = listOf(
            Server("s1", "https://example.com", "key123", "My Server"),
            Server("s2", "https://other.com", "key456", "Other")
        )
        coEvery { serverRepository.servers } returns flowOf(servers)

        val backup = repository.buildBackup()

        assertEquals(2, backup.servers.size)
        assertEquals("s1", backup.servers[0].id)
        assertEquals("key123", backup.servers[0].apiKey)
    }

    // ── importFromJson — encrypted ────────────────────────────────────────────

    @Test
    fun `importFromJson restores settings from encrypted backup with correct PIN`() = runTest(testDispatcher) {
        val settings = BackupSettings(themeMode = "DARK", htmlFontSize = 20)
        val envelope = buildEncryptedEnvelope(settings, emptyList(), pin = "5678")

        val result = repository.importFromJson(envelope, pin = "5678")

        coVerify(exactly = 1) { settingsRepository.restoreSettings(settings) }
        assertTrue(result.contains("restored"))
        assertTrue(result.contains("2024-01-01"))
    }

    @Test
    fun `importFromJson restores servers from encrypted backup`() = runTest(testDispatcher) {
        val servers = listOf(ServerBackup("s1", "https://example.com", "apikey", "Server 1"))
        val envelope = buildEncryptedEnvelope(BackupSettings(), servers, pin = "1111")

        val result = repository.importFromJson(envelope, pin = "1111")

        coVerify(exactly = 1) { serverRepository.deleteAllServers() }
        coVerify(exactly = 1) { serverRepository.addServer("https://example.com", "apikey", "Server 1") }
        assertTrue(result.contains("1 server connection"))
    }

    @Test
    fun `importFromJson throws when decrypting with wrong PIN`() = runTest(testDispatcher) {
        val envelope = buildEncryptedEnvelope(BackupSettings(), emptyList(), pin = "1234")

        assertFailsWith<Exception> {
            repository.importFromJson(envelope, pin = "9999")
        }
        coVerify(exactly = 0) { settingsRepository.restoreSettings(any()) }
    }

    @Test
    fun `importFromJson rejects backup with newer version`() = runTest(testDispatcher) {
        val newerBackup = AppBackup(
            version = BackupRepository.CURRENT_BACKUP_VERSION + 1,
            exportedAt = "2030-01-01 00:00:00 UTC",
            settings = BackupSettings(),
            servers = emptyList()
        )
        val envelope = buildEncryptedEnvelope(newerBackup, pin = "1234")

        val ex = assertFailsWith<IllegalStateException> {
            repository.importFromJson(envelope, pin = "1234")
        }
        assertTrue(ex.message?.contains("newer version") == true)
        coVerify(exactly = 0) { settingsRepository.restoreSettings(any()) }
    }

    @Test
    fun `importFromJson ignores unknown fields in encrypted backup JSON`() = runTest(testDispatcher) {
        val jsonWithUnknownField = """
            {
                "version": 1,
                "exportedAt": "2024-06-15 12:00:00 UTC",
                "settings": {
                    "layoutType": "CARD",
                    "futureField": "someValue"
                },
                "servers": [],
                "unknownTopLevelField": true
            }
        """.trimIndent()

        val plaintext = jsonWithUnknownField.encodeToByteArray()
        val encrypted = BackupCrypto.encrypt(plaintext, "1234")
        val envelopeJson = json.encodeToString(EncryptedBackupEnvelope(data = encrypted))

        val result = repository.importFromJson(envelopeJson, pin = "1234")

        coVerify(exactly = 1) {
            settingsRepository.restoreSettings(match { it.layoutType == "CARD" })
        }
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `importFromJson does not restore servers when server list is empty`() = runTest(testDispatcher) {
        val envelope = buildEncryptedEnvelope(BackupSettings(), emptyList(), pin = "1234")

        repository.importFromJson(envelope, pin = "1234")

        coVerify(exactly = 0) { serverRepository.deleteAllServers() }
        coVerify(exactly = 0) { serverRepository.addServer(any(), any(), any()) }
    }

    // ── checkAndRunScheduledExport ────────────────────────────────────────────

    @Test
    fun `checkAndRunScheduledExport does nothing when interval is NEVER`() = runTest(testDispatcher) {
        coEvery { settingsRepository.autoExportInterval } returns flowOf(AutoExportInterval.NEVER)

        repository.checkAndRunScheduledExport()

        coVerify(exactly = 0) { settingsRepository.currentSettings() }
    }

    @Test
    fun `checkAndRunScheduledExport skips export when no PIN is configured`() = runTest(testDispatcher) {
        coEvery { settingsRepository.autoExportInterval } returns flowOf(AutoExportInterval.DAILY)
        coEvery { settingsRepository.backupPin } returns flowOf(null)

        repository.checkAndRunScheduledExport()

        coVerify(exactly = 0) { settingsRepository.currentSettings() }
    }

    @Test
    fun `checkAndRunScheduledExport skips export when not yet due`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        coEvery { settingsRepository.autoExportInterval } returns flowOf(AutoExportInterval.DAILY)
        coEvery { settingsRepository.backupPin } returns flowOf("1234")
        coEvery { settingsRepository.lastAutoExportTime } returns flowOf(now - 1_000L) // 1 second ago

        repository.checkAndRunScheduledExport()

        coVerify(exactly = 0) { settingsRepository.currentSettings() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds a serialised [EncryptedBackupEnvelope] from a pre-built [AppBackup]. */
    private fun buildEncryptedEnvelope(backup: AppBackup, pin: String): String {
        val plaintext = json.encodeToString(backup).encodeToByteArray()
        val encrypted = BackupCrypto.encrypt(plaintext, pin)
        return json.encodeToString(EncryptedBackupEnvelope(data = encrypted))
    }

    /** Builds a serialised [EncryptedBackupEnvelope] from settings + servers + PIN. */
    private fun buildEncryptedEnvelope(
        settings: BackupSettings,
        servers: List<ServerBackup>,
        pin: String
    ): String {
        val backup = AppBackup(
            version = 1,
            exportedAt = "2024-01-01 00:00:00 UTC",
            settings = settings,
            servers = servers
        )
        return buildEncryptedEnvelope(backup, pin)
    }
}
