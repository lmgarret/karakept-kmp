package com.karakept.app.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies that [BackupSettings] serializes and deserializes correctly.
 *
 * These tests intentionally do not touch DataStore so they run on all platforms
 * without platform-specific setup.
 */
class BackupSettingsSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `default BackupSettings round-trips through JSON`() {
        val original = BackupSettings()
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<BackupSettings>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `all non-default field values survive round-trip`() {
        val original = BackupSettings(
            layoutType = "CARD",
            hideArticleThumbnails = false,
            showReadingTimeBadge = false,
            showTags = false,
            dimReadBookmarks = false,
            viewerMode = "WEB",
            htmlTextColor = 0xFF0000,
            htmlBackgroundColor = 0x000000,
            htmlFontSize = 24,
            htmlFontFamily = "SANS_SERIF",
            readingSpeedWpm = 350,
            trackReadingProgress = false,
            resetProgressOnMarkUnread = false,
            linkOpenMode = "EXTERNAL_BROWSER",
            themeMode = "DARK",
            accentColor = "BLUE",
            swipeLeftAction = "ARCHIVE",
            swipeRightAction = "FAVOURITE",
            customSwipeConfigsJson = """[{"id":"abc"}]""",
            swipeLeftConfigId = "cfg-left",
            swipeRightConfigId = "cfg-right",
            contentSyncStrategy = "ALL",
            contentSyncTargetLists = setOf("list-1", "list-2"),
            contentSyncWithChildren = setOf("list-1"),
            notificationsEnabled = false,
            offlineMode = true,
            onboardingCompleted = true,
            autoExportInterval = "WEEKLY"
        )

        val deserialized = json.decodeFromString<BackupSettings>(json.encodeToString(original))
        assertEquals(original, deserialized)
    }

    // ── Nullable fields ───────────────────────────────────────────────────────

    @Test
    fun `nullable Int fields round-trip when set`() {
        val settings = BackupSettings(htmlTextColor = 0xFF0000FF.toInt(), htmlBackgroundColor = 0x000000FF.toInt())
        val deserialized = json.decodeFromString<BackupSettings>(json.encodeToString(settings))

        assertEquals(0xFF0000FF.toInt(), deserialized.htmlTextColor)
        assertEquals(0x000000FF.toInt(), deserialized.htmlBackgroundColor)
    }

    @Test
    fun `nullable Int fields round-trip as null`() {
        val settings = BackupSettings(htmlTextColor = null, htmlBackgroundColor = null)
        val deserialized = json.decodeFromString<BackupSettings>(json.encodeToString(settings))

        assertNull(deserialized.htmlTextColor)
        assertNull(deserialized.htmlBackgroundColor)
    }

    @Test
    fun `nullable String fields round-trip when set`() {
        val settings = BackupSettings(swipeLeftConfigId = "left-id", swipeRightConfigId = "right-id")
        val deserialized = json.decodeFromString<BackupSettings>(json.encodeToString(settings))

        assertEquals("left-id", deserialized.swipeLeftConfigId)
        assertEquals("right-id", deserialized.swipeRightConfigId)
    }

    @Test
    fun `nullable String fields round-trip as null`() {
        val settings = BackupSettings(swipeLeftConfigId = null, swipeRightConfigId = null)
        val deserialized = json.decodeFromString<BackupSettings>(json.encodeToString(settings))

        assertNull(deserialized.swipeLeftConfigId)
        assertNull(deserialized.swipeRightConfigId)
    }

    @Test
    fun `backupExportDirectory null by default`() {
        assertNull(BackupSettings().backupExportDirectory)
    }

    @Test
    fun `backupExportDirectory round-trips when set`() {
        val settings = BackupSettings(backupExportDirectory = "/custom/backup/dir")
        val deserialized = json.decodeFromString<BackupSettings>(json.encodeToString(settings))
        assertEquals("/custom/backup/dir", deserialized.backupExportDirectory)
    }

    @Test
    fun `backupExportDirectory null survives JSON round-trip`() {
        val settings = BackupSettings(backupExportDirectory = null)
        val deserialized = json.decodeFromString<BackupSettings>(json.encodeToString(settings))
        assertNull(deserialized.backupExportDirectory)
    }

    // ── Set fields ────────────────────────────────────────────────────────────

    @Test
    fun `Set fields round-trip correctly`() {
        val settings = BackupSettings(
            contentSyncTargetLists = setOf("alpha", "beta", "gamma"),
            contentSyncWithChildren = setOf("alpha")
        )
        val deserialized = json.decodeFromString<BackupSettings>(json.encodeToString(settings))

        assertEquals(setOf("alpha", "beta", "gamma"), deserialized.contentSyncTargetLists)
        assertEquals(setOf("alpha"), deserialized.contentSyncWithChildren)
    }

    @Test
    fun `empty Set fields round-trip correctly`() {
        val settings = BackupSettings(contentSyncTargetLists = emptySet(), contentSyncWithChildren = emptySet())
        val deserialized = json.decodeFromString<BackupSettings>(json.encodeToString(settings))

        assertTrue(deserialized.contentSyncTargetLists.isEmpty())
        assertTrue(deserialized.contentSyncWithChildren.isEmpty())
    }

    // ── Forward / backward compatibility ─────────────────────────────────────

    @Test
    fun `unknown fields from future backup versions are ignored`() {
        val jsonWithUnknown = """
            {
                "layoutType": "CARD",
                "themeMode": "DARK",
                "fieldFromFutureVersion": "ignored",
                "anotherFutureField": 9999
            }
        """.trimIndent()

        val settings = json.decodeFromString<BackupSettings>(jsonWithUnknown)

        assertEquals("CARD", settings.layoutType)
        assertEquals("DARK", settings.themeMode)
        // remaining fields fall back to their defaults
        assertEquals(BackupSettings().htmlFontSize, settings.htmlFontSize)
    }

    @Test
    fun `missing fields in older backup use defaults`() {
        val minimalJson = """{"layoutType": "CARD"}"""

        val settings = json.decodeFromString<BackupSettings>(minimalJson)

        assertEquals("CARD", settings.layoutType)
        // All other fields must use their declared defaults
        assertEquals(BackupSettings().themeMode, settings.themeMode)
        assertEquals(BackupSettings().accentColor, settings.accentColor)
        assertEquals(BackupSettings().htmlFontSize, settings.htmlFontSize)
        assertEquals(BackupSettings().readingSpeedWpm, settings.readingSpeedWpm)
        assertEquals(BackupSettings().autoExportInterval, settings.autoExportInterval)
        assertNull(settings.htmlTextColor)
        assertNull(settings.htmlBackgroundColor)
        assertNull(settings.swipeLeftConfigId)
    }

    @Test
    fun `encodeDefaults ensures all fields are always present in JSON`() {
        val settings = BackupSettings()
        val serialized = json.encodeToString(settings)

        // Key fields must always be serialized so future parsers have explicit values
        assertTrue(serialized.contains("\"layoutType\""))
        assertTrue(serialized.contains("\"themeMode\""))
        assertTrue(serialized.contains("\"accentColor\""))
        assertTrue(serialized.contains("\"autoExportInterval\""))
    }

    // ── AppBackup envelope ────────────────────────────────────────────────────

    @Test
    fun `AppBackup round-trips with nested BackupSettings`() {
        val settings = BackupSettings(themeMode = "DARK", htmlFontSize = 18)
        val backup = AppBackup(
            version = 1,
            exportedAt = "2024-06-15 10:30:00 UTC",
            settings = settings,
            servers = emptyList()
        )

        val serialized = json.encodeToString(backup)
        val deserialized = json.decodeFromString<AppBackup>(serialized)

        assertEquals(backup, deserialized)
        assertEquals("DARK", deserialized.settings.themeMode)
        assertNotNull(deserialized.exportedAt)
    }
}
