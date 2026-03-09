package com.karakept.app.data.repository

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies that each per-category stored settings class serializes and deserializes
 * correctly. These classes are the internal DataStore storage format used by
 * [SettingsRepository] — distinct from the backup file format ([com.karakept.app.data.model.BackupSettings]).
 *
 * Tests intentionally do not touch DataStore so they run on all platforms without
 * platform-specific setup.
 */
class StoredSettingsSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── StoredThemeSettings ───────────────────────────────────────────────────

    @Test
    fun `default StoredThemeSettings round-trips through JSON`() {
        val original = StoredThemeSettings()
        assertEquals(original, json.decodeFromString<StoredThemeSettings>(json.encodeToString(original)))
    }

    @Test
    fun `StoredThemeSettings with non-default values round-trips`() {
        val original = StoredThemeSettings(themeMode = "DARK", accentColor = "BLUE")
        assertEquals(original, json.decodeFromString<StoredThemeSettings>(json.encodeToString(original)))
    }

    @Test
    fun `StoredThemeSettings ignores unknown fields from future versions`() {
        val jsonStr = """{"themeMode":"DARK","accentColor":"BLUE","unknownFutureField":"ignored"}"""
        val settings = json.decodeFromString<StoredThemeSettings>(jsonStr)
        assertEquals("DARK", settings.themeMode)
        assertEquals("BLUE", settings.accentColor)
    }

    @Test
    fun `StoredThemeSettings missing fields use defaults`() {
        val settings = json.decodeFromString<StoredThemeSettings>("{}")
        assertEquals(StoredThemeSettings(), settings)
    }

    @Test
    fun `StoredThemeSettings encodeDefaults ensures all fields are serialized`() {
        val serialized = json.encodeToString(StoredThemeSettings())
        assertTrue(serialized.contains("\"themeMode\""))
        assertTrue(serialized.contains("\"accentColor\""))
    }

    // ── StoredDisplaySettings ─────────────────────────────────────────────────

    @Test
    fun `default StoredDisplaySettings round-trips through JSON`() {
        val original = StoredDisplaySettings()
        assertEquals(original, json.decodeFromString<StoredDisplaySettings>(json.encodeToString(original)))
    }

    @Test
    fun `StoredDisplaySettings with non-default values round-trips`() {
        val original = StoredDisplaySettings(
            layoutType = "CARD",
            hideArticleThumbnails = false,
            showReadingTimeBadge = false,
            showTags = false,
            dimReadBookmarks = false
        )
        assertEquals(original, json.decodeFromString<StoredDisplaySettings>(json.encodeToString(original)))
    }

    @Test
    fun `StoredDisplaySettings ignores unknown fields`() {
        val jsonStr = """{"layoutType":"CARD","futureField":42}"""
        val settings = json.decodeFromString<StoredDisplaySettings>(jsonStr)
        assertEquals("CARD", settings.layoutType)
        assertEquals(StoredDisplaySettings().hideArticleThumbnails, settings.hideArticleThumbnails)
    }

    @Test
    fun `StoredDisplaySettings missing fields use defaults`() {
        val settings = json.decodeFromString<StoredDisplaySettings>("{}")
        assertEquals(StoredDisplaySettings(), settings)
    }

    // ── StoredReaderSettings ──────────────────────────────────────────────────

    @Test
    fun `default StoredReaderSettings round-trips through JSON`() {
        val original = StoredReaderSettings()
        assertEquals(original, json.decodeFromString<StoredReaderSettings>(json.encodeToString(original)))
    }

    @Test
    fun `StoredReaderSettings with non-default values round-trips`() {
        val original = StoredReaderSettings(
            viewerMode = "WEB",
            htmlTextColor = 0xFF0000,
            htmlBackgroundColor = 0x000000,
            htmlFontSize = 24,
            htmlFontFamily = "SANS_SERIF",
            readingSpeedWpm = 350,
            trackReadingProgress = false,
            resetProgressOnMarkUnread = false,
            linkOpenMode = "EXTERNAL_BROWSER"
        )
        assertEquals(original, json.decodeFromString<StoredReaderSettings>(json.encodeToString(original)))
    }

    @Test
    fun `StoredReaderSettings nullable color fields round-trip as null`() {
        val settings = StoredReaderSettings(htmlTextColor = null, htmlBackgroundColor = null)
        val deserialized = json.decodeFromString<StoredReaderSettings>(json.encodeToString(settings))
        assertNull(deserialized.htmlTextColor)
        assertNull(deserialized.htmlBackgroundColor)
    }

    @Test
    fun `StoredReaderSettings nullable color fields round-trip when set`() {
        val settings = StoredReaderSettings(htmlTextColor = 0xFF0000FF.toInt(), htmlBackgroundColor = 0x000000FF.toInt())
        val deserialized = json.decodeFromString<StoredReaderSettings>(json.encodeToString(settings))
        assertEquals(0xFF0000FF.toInt(), deserialized.htmlTextColor)
        assertEquals(0x000000FF.toInt(), deserialized.htmlBackgroundColor)
    }

    @Test
    fun `StoredReaderSettings missing fields use defaults`() {
        val settings = json.decodeFromString<StoredReaderSettings>("{}")
        assertEquals(StoredReaderSettings(), settings)
    }

    // ── StoredSwipeSettings ───────────────────────────────────────────────────

    @Test
    fun `default StoredSwipeSettings round-trips through JSON`() {
        val original = StoredSwipeSettings()
        assertEquals(original, json.decodeFromString<StoredSwipeSettings>(json.encodeToString(original)))
    }

    @Test
    fun `StoredSwipeSettings with non-default values round-trips`() {
        val original = StoredSwipeSettings(
            swipeLeftAction = "ARCHIVE",
            swipeRightAction = "FAVOURITE",
            customSwipeConfigsJson = """[{"id":"abc"}]""",
            swipeLeftConfigId = "cfg-left",
            swipeRightConfigId = "cfg-right"
        )
        assertEquals(original, json.decodeFromString<StoredSwipeSettings>(json.encodeToString(original)))
    }

    @Test
    fun `StoredSwipeSettings nullable configId fields round-trip as null`() {
        val settings = StoredSwipeSettings(swipeLeftConfigId = null, swipeRightConfigId = null)
        val deserialized = json.decodeFromString<StoredSwipeSettings>(json.encodeToString(settings))
        assertNull(deserialized.swipeLeftConfigId)
        assertNull(deserialized.swipeRightConfigId)
    }

    @Test
    fun `StoredSwipeSettings nullable configId fields round-trip when set`() {
        val settings = StoredSwipeSettings(swipeLeftConfigId = "left-id", swipeRightConfigId = "right-id")
        val deserialized = json.decodeFromString<StoredSwipeSettings>(json.encodeToString(settings))
        assertEquals("left-id", deserialized.swipeLeftConfigId)
        assertEquals("right-id", deserialized.swipeRightConfigId)
    }

    @Test
    fun `StoredSwipeSettings missing fields use defaults`() {
        val settings = json.decodeFromString<StoredSwipeSettings>("{}")
        assertEquals(StoredSwipeSettings(), settings)
    }

    // ── StoredSyncSettings ────────────────────────────────────────────────────

    @Test
    fun `default StoredSyncSettings round-trips through JSON`() {
        val original = StoredSyncSettings()
        assertEquals(original, json.decodeFromString<StoredSyncSettings>(json.encodeToString(original)))
    }

    @Test
    fun `StoredSyncSettings Set fields round-trip correctly`() {
        val original = StoredSyncSettings(
            contentSyncStrategy = "ALL",
            contentSyncTargetLists = setOf("list-1", "list-2"),
            contentSyncWithChildren = setOf("list-1")
        )
        val deserialized = json.decodeFromString<StoredSyncSettings>(json.encodeToString(original))
        assertEquals("ALL", deserialized.contentSyncStrategy)
        assertEquals(setOf("list-1", "list-2"), deserialized.contentSyncTargetLists)
        assertEquals(setOf("list-1"), deserialized.contentSyncWithChildren)
    }

    @Test
    fun `StoredSyncSettings empty Set fields round-trip correctly`() {
        val original = StoredSyncSettings(contentSyncTargetLists = emptySet(), contentSyncWithChildren = emptySet())
        val deserialized = json.decodeFromString<StoredSyncSettings>(json.encodeToString(original))
        assertTrue(deserialized.contentSyncTargetLists.isEmpty())
        assertTrue(deserialized.contentSyncWithChildren.isEmpty())
    }

    @Test
    fun `StoredSyncSettings missing fields use defaults`() {
        val settings = json.decodeFromString<StoredSyncSettings>("{}")
        assertEquals(StoredSyncSettings(), settings)
    }

    // ── StoredAppSettings ─────────────────────────────────────────────────────

    @Test
    fun `default StoredAppSettings round-trips through JSON`() {
        val original = StoredAppSettings()
        assertEquals(original, json.decodeFromString<StoredAppSettings>(json.encodeToString(original)))
    }

    @Test
    fun `StoredAppSettings with non-default values round-trips`() {
        val original = StoredAppSettings(
            notificationsEnabled = false,
            offlineMode = true,
            onboardingCompleted = true,
            autoExportInterval = "WEEKLY"
        )
        assertEquals(original, json.decodeFromString<StoredAppSettings>(json.encodeToString(original)))
    }

    @Test
    fun `StoredAppSettings backupExportDirectory null by default`() {
        assertNull(StoredAppSettings().backupExportDirectory)
    }

    @Test
    fun `StoredAppSettings with backupExportDirectory round-trips`() {
        val original = StoredAppSettings(backupExportDirectory = "/custom/path")
        val deserialized = json.decodeFromString<StoredAppSettings>(json.encodeToString(original))
        assertEquals("/custom/path", deserialized.backupExportDirectory)
    }

    @Test
    fun `StoredAppSettings backupExportDirectory null survives JSON round-trip`() {
        val original = StoredAppSettings(backupExportDirectory = null)
        val deserialized = json.decodeFromString<StoredAppSettings>(json.encodeToString(original))
        assertNull(deserialized.backupExportDirectory)
    }

    @Test
    fun `StoredAppSettings ignores unknown fields`() {
        val jsonStr = """{"notificationsEnabled":false,"futureField":"value"}"""
        val settings = json.decodeFromString<StoredAppSettings>(jsonStr)
        assertEquals(false, settings.notificationsEnabled)
        assertEquals(StoredAppSettings().offlineMode, settings.offlineMode)
    }

    @Test
    fun `StoredAppSettings missing fields use defaults`() {
        val settings = json.decodeFromString<StoredAppSettings>("{}")
        assertEquals(StoredAppSettings(), settings)
    }

    // ── Cross-category isolation ──────────────────────────────────────────────

    @Test
    fun `each category class is independent - theme fields not present in display`() {
        // Verify the categories don't share field names (which could cause confusion)
        val themeJson = json.encodeToString(StoredThemeSettings(themeMode = "DARK"))
        val displaySettings = json.decodeFromString<StoredDisplaySettings>(themeJson)
        // Display settings should have all defaults since theme JSON has no display fields
        assertEquals(StoredDisplaySettings(), displaySettings)
    }
}
