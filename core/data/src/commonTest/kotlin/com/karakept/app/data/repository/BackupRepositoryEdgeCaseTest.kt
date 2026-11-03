package com.karakept.app.data.repository

import com.karakept.app.data.model.AppBackup
import com.karakept.app.data.model.AutoExportInterval
import com.karakept.app.data.model.BackupSettings
import com.karakept.app.data.model.EncryptedBackupEnvelope
import com.karakept.app.data.model.ServerBackup
import com.karakept.app.utils.BackupCrypto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupRepositoryEdgeCaseTest : BaseRepositoryTest() {

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

    // ── exportToFile ──────────────────────────────────────────────────────────

    @Test
    fun `exportToFile throws when PIN is blank`() = runTest(testDispatcher) {
        assertFailsWith<IllegalArgumentException> {
            repository.exportToFile("")
        }.also { assertTrue(it.message?.contains("PIN is required") == true) }
    }

    // ── importFromJson — setBackupPin branches ────────────────────────────────

    @Test
    fun `importFromJson calls setBackupPin when backup has pinHash`() = runTest(testDispatcher) {
        val settings = BackupSettings(backupPinHash = "aGVsbG8=:d29ybGQ=")
        val envelope = buildEncryptedEnvelope(settings, emptyList(), pin = "1234")

        repository.importFromJson(envelope, pin = "1234")

        coVerify(exactly = 1) { settingsRepository.setBackupPin("1234") }
    }

    @Test
    fun `importFromJson does not call setBackupPin when backup has no pinHash`() = runTest(testDispatcher) {
        val settings = BackupSettings() // backupPinHash defaults to null
        val envelope = buildEncryptedEnvelope(settings, emptyList(), pin = "1234")

        repository.importFromJson(envelope, pin = "1234")

        coVerify(exactly = 0) { settingsRepository.setBackupPin(any()) }
    }

    // ── importFromJson — malformed input ──────────────────────────────────────

    @Test
    fun `importFromJson throws on malformed JSON`() = runTest(testDispatcher) {
        assertFailsWith<Exception> {
            repository.importFromJson("not-valid-json", pin = "1234")
        }
    }

    // ── checkAndRunScheduledExport — due path ─────────────────────────────────

    @Test
    fun `checkAndRunScheduledExport triggers export when daily interval is due`() = runTest(testDispatcher) {
        every { settingsRepository.autoExportInterval } returns flowOf(AutoExportInterval.DAILY)
        every { settingsRepository.backupPin } returns flowOf("1234")
        every { settingsRepository.lastAutoExportTime } returns flowOf(0L)
        // Mock buildBackup() dependencies so exportToFile() progresses into the export path
        coEvery { settingsRepository.currentSettings() } returns BackupSettings()
        every { serverRepository.servers } returns flowOf(emptyList())
        every { settingsRepository.backupExportDirectory } returns flowOf(null)

        repository.checkAndRunScheduledExport()

        // backupExportDirectory is only accessed deep inside exportToFile() after buildBackup()
        // succeeds — proving the scheduled export path was entered when DAILY interval was due.
        // (FileUtils.getBackupDirectory() then throws on desktop, caught by try/catch.)
        coVerify(atLeast = 1) { settingsRepository.backupExportDirectory }
    }

    @Test
    fun `checkAndRunScheduledExport swallows export exceptions silently`() = runTest(testDispatcher) {
        every { settingsRepository.autoExportInterval } returns flowOf(AutoExportInterval.DAILY)
        every { settingsRepository.backupPin } returns flowOf("1234")
        every { settingsRepository.lastAutoExportTime } returns flowOf(0L)
        coEvery { settingsRepository.currentSettings() } throws RuntimeException("boom")

        // Must not throw — exception is swallowed by try/catch
        repository.checkAndRunScheduledExport()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
        return json.encodeToString(EncryptedBackupEnvelope(data = encrypted))
    }
}
