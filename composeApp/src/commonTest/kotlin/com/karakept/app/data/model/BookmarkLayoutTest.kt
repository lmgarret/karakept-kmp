package com.karakept.app.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookmarkLayoutTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testDefaultFieldValues() {
        val layout = BookmarkLayout(id = "test", name = "Test")
        assertEquals(true, layout.showDescription)
        assertEquals(DescriptionPosition.BELOW_TITLE.name, layout.descriptionPosition)
        assertEquals(false, layout.showUrl)
        assertEquals(UrlDisplayMode.DOMAIN_ONLY.name, layout.urlDisplayMode)
        assertEquals(UrlPosition.BELOW_TITLE.name, layout.urlPosition)
    }

    @Test
    fun testBuiltinCompactIsListType() {
        val compact = BookmarkLayout.BUILTIN_COMPACT
        assertEquals(LayoutType.LIST.name, compact.layoutType)
        assertEquals(false, compact.showDescription)
        assertEquals(false, compact.showTags)
        assertEquals(48, compact.thumbnailSize)
        assertEquals(MetadataPosition.BESIDE.name, compact.metadataPosition)
    }

    @Test
    fun testBuiltinListDefaults() {
        val list = BookmarkLayout.BUILTIN_LIST
        assertEquals(true, list.showDescription)
        assertEquals(false, list.showUrl)
    }

    @Test
    fun testBuiltinCardDefaults() {
        val card = BookmarkLayout.BUILTIN_CARD
        assertEquals(true, card.showDescription)
        assertEquals(false, card.showUrl)
    }

    @Test
    fun testSerializationRoundTrip() {
        val layout = BookmarkLayout(
            id = "custom",
            name = "Custom Layout",
            showDescription = false,
            descriptionPosition = DescriptionPosition.ABOVE_METADATA.name,
            showUrl = true,
            urlDisplayMode = UrlDisplayMode.FULL_URL.name,
            urlPosition = UrlPosition.METADATA_ROW.name
        )
        val serialized = json.encodeToString(layout)
        val deserialized = json.decodeFromString<BookmarkLayout>(serialized)
        assertEquals(layout, deserialized)
    }

    @Test
    fun testBackwardCompatDeserialization() {
        val minimalJson = """{"id":"old","name":"Old Layout"}"""
        val layout = json.decodeFromString<BookmarkLayout>(minimalJson)
        // New fields must use their declared defaults
        assertEquals(true, layout.showDescription)
        assertEquals(DescriptionPosition.BELOW_TITLE.name, layout.descriptionPosition)
        assertEquals(false, layout.showUrl)
        assertEquals(UrlDisplayMode.DOMAIN_ONLY.name, layout.urlDisplayMode)
        assertEquals(UrlPosition.BELOW_TITLE.name, layout.urlPosition)
    }



    @Test
    fun testRowStyleDefaultsAreUnchangedForNewLayouts() {
        val fresh = BookmarkLayout(id = "x", name = "x")
        assertEquals(ItemContainerStyle.CARD.name, fresh.itemContainerStyle)
        assertEquals(ReadIndicatorStyle.DIM.name, fresh.readIndicatorStyle)
        assertEquals(true, fresh.showThumbnail)
        assertEquals(BookmarkLayout.DESCRIPTION_LINES_DEFAULT, fresh.descriptionMaxLines)
    }

    @Test
    fun testBuiltInsAreOrderedDensestToRichest() {
        assertEquals(
            listOf("Compact", "Rows", "Cards", "Digest", "Magazine"),
            BookmarkLayout.ALL_BUILTIN.map { it.name }
        )
    }

    @Test
    fun testRowsIsFlatWithADivider() {
        val rows = BookmarkLayout.BUILTIN_ROWS
        assertEquals(ItemContainerStyle.FLAT.name, rows.itemContainerStyle)
        assertEquals(true, rows.showRowDivider)
        assertEquals(TitlePosition.BESIDE_THUMBNAIL.name, rows.titlePosition)
        assertEquals(2, rows.descriptionMaxLines)
        assertTrue(rows.tagsScrollable)
    }

    @Test
    fun testDigestIsFlatWithoutADivider() {
        val digest = BookmarkLayout.BUILTIN_DIGEST
        assertEquals(ItemContainerStyle.FLAT.name, digest.itemContainerStyle)
        assertEquals(false, digest.showRowDivider)
        assertEquals(TitlePosition.ABOVE_THUMBNAIL.name, digest.titlePosition)
        assertEquals(5, digest.descriptionMaxLines)
        assertTrue(digest.tagsScrollable)
    }

    @Test
    fun testEveryBuiltInResolvesById() {
        for (layout in BookmarkLayout.ALL_BUILTIN) {
            assertTrue(BookmarkLayout.isBuiltInId(layout.id), "${'$'}{layout.id} is not a built-in id")
            assertEquals(layout, BookmarkLayout.getBuiltIn(layout.id))
        }
    }

    @Test
    fun testDescriptionMaxLinesDefaultsOnOlderLayoutJson() {
        val layout = json.decodeFromString<BookmarkLayout>("""{"id":"old","name":"Old"}""")
        assertEquals(BookmarkLayout.DESCRIPTION_LINES_DEFAULT, layout.descriptionMaxLines)
    }

    @Test
    fun testAutoDescriptionLinesRoundTrips() {
        val layout = BookmarkLayout(
            id = "custom",
            name = "Custom",
            descriptionMaxLines = BookmarkLayout.DESCRIPTION_LINES_AUTO
        )
        assertEquals(layout, json.decodeFromString<BookmarkLayout>(json.encodeToString(layout)))
    }

    @Test
    fun testRowStyleFieldsRoundTrip() {
        val layout = BookmarkLayout(
            id = "custom",
            name = "Custom",
            showThumbnail = false,
            itemContainerStyle = ItemContainerStyle.FLAT.name,
            readIndicatorStyle = ReadIndicatorStyle.MARKER.name
        )
        assertEquals(layout, json.decodeFromString<BookmarkLayout>(json.encodeToString(layout)))
    }

    @Test
    fun testRowStyleFieldsDefaultOnOlderLayoutJson() {
        // Custom layouts are persisted as a JSON blob and restored from backups written before
        // these fields existed — those must keep looking exactly as they did.
        val layout = json.decodeFromString<BookmarkLayout>("""{"id":"old","name":"Old"}""")
        assertEquals(true, layout.showThumbnail)
        assertEquals(ItemContainerStyle.CARD.name, layout.itemContainerStyle)
        assertEquals(ReadIndicatorStyle.DIM.name, layout.readIndicatorStyle)
    }

    @Test
    fun testUnknownRowStyleFallsBackInsteadOfThrowing() {
        assertEquals(ItemContainerStyle.CARD, ItemContainerStyle.fromString("NOT_A_STYLE"))
        assertEquals(ReadIndicatorStyle.DIM, ReadIndicatorStyle.fromString("NOT_A_STYLE"))
    }

    @Test
    fun testDividerAndTitlePositionRoundTrip() {
        val layout = BookmarkLayout(
            id = "custom",
            name = "Custom",
            itemContainerStyle = ItemContainerStyle.FLAT.name,
            showRowDivider = false,
            titlePosition = TitlePosition.ABOVE_THUMBNAIL.name
        )
        assertEquals(layout, json.decodeFromString<BookmarkLayout>(json.encodeToString(layout)))
    }

    @Test
    fun testDividerAndTitlePositionDefaultOnOlderLayoutJson() {
        // Layouts written before these fields existed must keep rendering as they did: flat rows
        // had a divider, and the title always sat beside the thumbnail.
        val layout = json.decodeFromString<BookmarkLayout>("""{"id":"old","name":"Old"}""")
        assertEquals(true, layout.showRowDivider)
        assertEquals(TitlePosition.BESIDE_THUMBNAIL.name, layout.titlePosition)
    }


    @Test
    fun testUnknownTitlePositionFallsBackInsteadOfThrowing() {
        assertEquals(TitlePosition.BESIDE_THUMBNAIL, TitlePosition.fromString("NOT_A_POSITION"))
    }
}
