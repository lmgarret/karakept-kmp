package com.karakept.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineHeroImagesTest {

    private val remote = "https://karakeep.example/api/v1/assets/abc"

    @Test
    fun rowsUseTheFileTheOfflineCopyStored() {
        val stored = setOf("/cache/hero_banner_abc")

        val url = OfflineHeroImages.resolve(remote, "/cache", OfflineHeroImages.bannerFileName("abc"), stored::contains)

        assertEquals("file:///cache/hero_banner_abc", url)
    }

    @Test
    fun withoutAnOfflineCopyTheServerUrlIsUsed() {
        val url = OfflineHeroImages.resolve(remote, "/cache", OfflineHeroImages.screenshotFileName("abc")) { false }

        assertEquals(remote, url)
    }

    @Test
    fun namesMatchWhatOfflineCleanupManages() {
        assertEquals("hero_banner_abc", OfflineHeroImages.bannerFileName("abc"))
        assertEquals("hero_screenshot_abc", OfflineHeroImages.screenshotFileName("abc"))
    }
}
