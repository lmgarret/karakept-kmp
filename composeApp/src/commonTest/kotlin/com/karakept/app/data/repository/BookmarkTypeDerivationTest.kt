package com.karakept.app.data.repository

import com.karakept.app.data.local.entity.BookmarkType
import com.karakept.api.model.BookmarkContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit tests for [deriveBookmarkType] — the pure content-type → BookmarkType mapping. */
class BookmarkTypeDerivationTest {

    @Test
    fun textContentMapsToText() {
        assertEquals(
            BookmarkType.TEXT,
            deriveBookmarkType(BookmarkContent.Type.TEXT, hasVideoAsset = false, hasVideoAssetId = false)
        )
    }

    @Test
    fun plainLinkMapsToLink() {
        assertEquals(
            BookmarkType.LINK,
            deriveBookmarkType(BookmarkContent.Type.LINK, hasVideoAsset = false, hasVideoAssetId = false)
        )
    }

    @Test
    fun linkWithVideoAssetMapsToVideo() {
        assertEquals(
            BookmarkType.VIDEO,
            deriveBookmarkType(BookmarkContent.Type.LINK, hasVideoAsset = true, hasVideoAssetId = false)
        )
    }

    @Test
    fun linkWithVideoAssetIdMapsToVideo() {
        assertEquals(
            BookmarkType.VIDEO,
            deriveBookmarkType(BookmarkContent.Type.LINK, hasVideoAsset = false, hasVideoAssetId = true)
        )
    }

    @Test
    fun nullContentMapsToUnknown() {
        assertEquals(
            BookmarkType.UNKNOWN,
            deriveBookmarkType(null, hasVideoAsset = false, hasVideoAssetId = false)
        )
    }
}
