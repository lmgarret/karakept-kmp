package com.karakept.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [AssetUrlUtils].
 */
class AssetUrlUtilsTest {

    @Test
    fun getAssetUrl_constructsCorrectUrl() {
        val result = AssetUrlUtils.getAssetUrl("https://server.com", "asset-123")
        assertEquals("https://server.com/api/v1/assets/asset-123", result)
    }

    @Test
    fun getAssetUrl_trimsTrailingSlash() {
        val result = AssetUrlUtils.getAssetUrl("https://server.com/", "asset-123")
        assertEquals("https://server.com/api/v1/assets/asset-123", result,
            "Trailing slash should not cause double slash")
    }

    @Test
    fun isAssetUrl_assetPath_returnsTrue() {
        assertTrue(AssetUrlUtils.isAssetUrl("https://server.com/api/v1/assets/abc"))
    }

    @Test
    fun isAssetUrl_nonAssetPath_returnsFalse() {
        assertFalse(AssetUrlUtils.isAssetUrl("https://server.com/page"))
    }
}
