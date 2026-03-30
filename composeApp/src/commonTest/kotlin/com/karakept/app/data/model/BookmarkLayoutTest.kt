package com.karakept.app.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
