package com.karakept.app.data.local

import com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType
import com.karakept.app.data.model.SANITIZED_ASSET_NAME
import com.karakept.app.data.model.SANITIZED_ASSET_TYPE
import com.karakept.app.data.model.isSanitizedAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [AssetTypeMapping], which both sync paths resolve asset rows through.
 *
 * The rule worth pinning is the narrow one: `userUploaded` covers every asset anyone attached by
 * hand, and only a sidecar's own file name admits one.
 */
class AssetTypeMappingTest {

    @Test
    fun trackedTypes_mapToTheirStoredStrings() {
        assertEquals("linkHtmlContent", AssetTypeMapping.storedAssetType(AssetType.LINK_HTML_CONTENT, null))
        assertEquals("fullPageArchive", AssetTypeMapping.storedAssetType(AssetType.FULL_PAGE_ARCHIVE, null))
        assertEquals("precrawledArchive", AssetTypeMapping.storedAssetType(AssetType.PRECRAWLED_ARCHIVE, null))
        assertEquals("pdf", AssetTypeMapping.storedAssetType(AssetType.PDF, null))
    }

    @Test
    fun untrackedTypes_areDropped() {
        assertNull(AssetTypeMapping.storedAssetType(AssetType.VIDEO, null))
        assertNull(AssetTypeMapping.storedAssetType(AssetType.AVATAR, null))
        assertNull(AssetTypeMapping.storedAssetType(AssetType.UNKNOWN, null))
        assertNull(AssetTypeMapping.storedAssetType(null, null))
    }

    @Test
    fun sidecarUpload_isAdmittedByName() {
        assertEquals(
            SANITIZED_ASSET_TYPE,
            AssetTypeMapping.storedAssetType(AssetType.USER_UPLOADED, SANITIZED_ASSET_NAME)
        )
    }

    @Test
    fun otherHandAttachedUploads_areStillIgnored() {
        assertNull(AssetTypeMapping.storedAssetType(AssetType.USER_UPLOADED, "my-notes.html"))
        assertNull(AssetTypeMapping.storedAssetType(AssetType.USER_UPLOADED, null))
    }

    @Test
    fun isSanitizedAsset_needsBothTheTypeAndTheName() {
        assertTrue(isSanitizedAsset(SANITIZED_ASSET_TYPE, SANITIZED_ASSET_NAME))
        assertFalse(isSanitizedAsset(SANITIZED_ASSET_TYPE, "my-notes.html"))
        assertFalse(isSanitizedAsset("fullPageArchive", SANITIZED_ASSET_NAME))
        assertFalse(isSanitizedAsset(null, null))
    }
}
