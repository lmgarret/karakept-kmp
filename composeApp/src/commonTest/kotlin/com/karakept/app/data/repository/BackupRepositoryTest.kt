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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupRepositoryTest : BaseRepositoryTest() {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val repository = BackupRepository(settingsRepository, serverRepository)

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

    // ── importFromJson ────────────────────────────────────────────────────────

    @Test
    fun `importFromJson restores settings via restoreSettings`() = runTest(testDispatcher) {
        val settings = BackupSettings(themeMode = "DARK", htmlFontSize = 20)
        val backup = AppBackup(
            version = 1,
            exportedAt = "2024-01-01 00:00:00 UTC",
            settings = settings,
            servers = emptyList()
        )
        val jsonContent = json.encodeToString(backup)

        val result = repository.importFromJson(jsonContent)

        coVerify(exactly = 1) { settingsRepository.restoreSettings(settings) }
        assertTrue(result.contains("restored"))
        assertTrue(result.contains("2024-01-01"))
    }

    @Test
    fun `importFromJson mentions server count when servers are present`() = runTest(testDispatcher) {
        val backup = AppBackup(
            version = 1,
            exportedAt = "2024-01-01 00:00:00 UTC",
            settings = BackupSettings(),
            servers = listOf(ServerBackup("s1", "https://example.com", "key", "Label"))
        )
        val jsonContent = json.encodeToString(backup)

        val result = repository.importFromJson(jsonContent)

        assertTrue(result.contains("1 server configuration"))
        assertTrue(result.contains("NOT automatically restored"))
    }

    @Test
    fun `importFromJson rejects backup with newer version`() = runTest(testDispatcher) {
        val newerBackup = AppBackup(
            version = BackupRepository.CURRENT_BACKUP_VERSION + 1,
            exportedAt = "2030-01-01 00:00:00 UTC",
            settings = BackupSettings(),
            servers = emptyList()
        )
        val jsonContent = json.encodeToString(newerBackup)

        val ex = assertFailsWith<IllegalStateException> {
            repository.importFromJson(jsonContent)
        }
        assertTrue(ex.message?.contains("newer version") == true)
        coVerify(exactly = 0) { settingsRepository.restoreSettings(any()) }
    }

    @Test
    fun `importFromJson ignores unknown fields in backup JSON`() = runTest(testDispatcher) {
        // A backup JSON with an extra field that doesn't exist in AppBackup
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

        val result = repository.importFromJson(jsonWithUnknownField)

        coVerify(exactly = 1) {
            settingsRepository.restoreSettings(match { it.layoutType == "CARD" })
        }
        assertFalse(result.isEmpty())
    }

    // ── importFromJson — encrypted ────────────────────────────────────────────

    @Test
    fun `importFromJson throws when encrypted backup has no PIN`() = runTest(testDispatcher) {
        val envelope = buildEncryptedEnvelope(BackupSettings(), emptyList(), pin = "1234")

        val ex = assertFailsWith<Exception> {
            repository.importFromJson(envelope)  // no pin provided
        }
        assertTrue(ex.message?.contains("encrypted") == true || ex.message?.contains("PIN") == true)
    }

    @Test
    fun `importFromJson restores settings from encrypted backup with correct PIN`() = runTest(testDispatcher) {
        val settings = BackupSettings(themeMode = "DARK", htmlFontSize = 18)
        val envelope = buildEncryptedEnvelope(settings, emptyList(), pin = "5678")

        val result = repository.importFromJson(envelope, pin = "5678")

        coVerify(exactly = 1) { settingsRepository.restoreSettings(settings) }
        assertTrue(result.contains("restored"))
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
    fun `importFromJson throws when decrypting encrypted backup with wrong PIN`() = runTest(testDispatcher) {
        val envelope = buildEncryptedEnvelope(BackupSettings(), emptyList(), pin = "1234")

        assertFailsWith<Exception> {
            repository.importFromJson(envelope, pin = "9999")
        }
        coVerify(exactly = 0) { settingsRepository.restoreSettings(any()) }
    }

    @Test
    fun `importFromJson does NOT restore servers from unencrypted backup`() = runTest(testDispatcher) {
        val backup = AppBackup(
            version = 1,
            exportedAt = "2024-01-01 00:00:00 UTC",
            settings = BackupSettings(),
            servers = listOf(ServerBackup("s1", "https://example.com", "key", "Label"))
        )
        val jsonContent = json.encodeToString(backup)

        repository.importFromJson(jsonContent)

        coVerify(exactly = 0) { serverRepository.deleteAllServers() }
        coVerify(exactly = 0) { serverRepository.addServer(any(), any(), any()) }
    }

    // ── checkAndRunScheduledExport ────────────────────────────────────────────

    @Test
    fun `checkAndRunScheduledExport does nothing when interval is NEVER`() = runTest(testDispatcher) {
        coEvery { settingsRepository.autoExportInterval } returns flowOf(AutoExportInterval.NEVER)

        repository.checkAndRunScheduledExport()

        // exportToFile was never called (would need FileUtils which we can't call here)
        coVerify(exactly = 0) { settingsRepository.currentSettings() }
    }

    @Test
    fun `checkAndRunScheduledExport skips export when not yet due`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        coEvery { settingsRepository.autoExportInterval } returns flowOf(AutoExportInterval.DAILY)
        coEvery { settingsRepository.lastAutoExportTime } returns flowOf(now - 1_000L) // 1 second ago

        // Should not call currentSettings (which leads to exportToFile)
        repository.checkAndRunScheduledExport()

        coVerify(exactly = 0) { settingsRepository.currentSettings() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds a serialised [EncryptedBackupEnvelope] string from the given settings + servers + PIN. */
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
        val plaintext = json.encodeToString(backup).encodeToByteArray()
        val encrypted = BackupCrypto.encrypt(plaintext, pin)
        val envelope = EncryptedBackupEnvelope(data = encrypted)
        return json.encodeToString(envelope)
    }
}
