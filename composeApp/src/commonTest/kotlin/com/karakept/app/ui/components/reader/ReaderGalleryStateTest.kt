package com.karakept.app.ui.components.reader

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for [collectGalleryImages] and its per-element resolvers, which pre-scan a page's
 * `<img>`/`<picture>` elements in document order so the full-screen viewer can swipe
 * between all of them (see [ImageGalleryOverlay]).
 */
class ReaderGalleryStateTest {

    private fun parse(html: String) = Ksoup.parse(html)

    @Test
    fun collectGalleryImages_multipleImages_returnsInDocumentOrder() {
        val doc = parse(
            """
            <body>
                <p>intro</p>
                <img src="https://x/1.jpg">
                <div><img src="https://x/2.jpg"></div>
                <img src="https://x/3.jpg">
            </body>
            """.trimIndent()
        )

        val images = collectGalleryImages(doc)

        assertEquals(
            listOf("https://x/1.jpg", "https://x/2.jpg", "https://x/3.jpg"),
            images.map { it.urls.first() }
        )
    }

    @Test
    fun collectGalleryImages_pictureElement_countedOnceNotTwice() {
        // The <picture>'s own <img> child must not also appear as a separate entry —
        // RenderBlock only dispatches "picture" to RenderPicture, never recursing into
        // its nested <img>, so the pre-scan must mirror that.
        val doc = parse(
            """
            <body>
                <picture>
                    <source srcset="https://x/photo.webp">
                    <img src="https://x/photo.jpg" alt="A photo">
                </picture>
            </body>
            """.trimIndent()
        )

        val images = collectGalleryImages(doc)

        assertEquals(1, images.size)
        assertEquals("https://x/photo.webp", images.first().urls.first())
        assertEquals("A photo", images.first().alt)
    }

    @Test
    fun collectGalleryImages_imageWithNoUsableUrl_isSkipped() {
        val doc = parse(
            """
            <body>
                <img src="data:image/svg+xml;utf8,%3Csvg/%3E">
                <img src="https://x/real.jpg">
            </body>
            """.trimIndent()
        )

        val images = collectGalleryImages(doc)

        assertEquals(listOf("https://x/real.jpg"), images.map { it.urls.first() })
    }

    @Test
    fun collectGalleryImages_figureCaption_isCarriedOnEachEntry() {
        val doc = parse(
            """
            <body>
                <figure>
                    <img src="https://x/cat.jpg">
                    <figcaption>A cat</figcaption>
                </figure>
            </body>
            """.trimIndent()
        )

        val images = collectGalleryImages(doc)

        assertEquals(1, images.size)
        assertEquals("A cat", images.first().caption)
    }

    @Test
    fun collectGalleryImages_elementIdentity_matchesSourceElement() {
        val doc = parse("<body><img src=\"https://x/1.jpg\"></body>")
        val imgElement = doc.selectFirst("img")!!

        val images = collectGalleryImages(doc)

        assertSame(imgElement, images.first().element)
    }

    @Test
    fun collectGalleryImages_noImages_returnsEmpty() {
        val doc = parse("<body><p>no pictures here</p></body>")
        assertTrue(collectGalleryImages(doc).isEmpty())
    }

    @Test
    fun resolveImgGalleryImage_noUsableUrl_returnsNull() {
        val doc = parse("<body><img src=\"data:image/svg+xml;utf8,%3Csvg/%3E\"></body>")
        val img = doc.selectFirst("img")!!
        assertNull(resolveImgGalleryImage(img))
    }

    @Test
    fun resolvePictureGalleryImage_noSourcesFallsBackToImg() {
        val doc = parse("<body><picture><img src=\"https://x/only.jpg\" alt=\"only\"></picture></body>")
        val picture = doc.selectFirst("picture")!!
        val galleryImage = resolvePictureGalleryImage(picture)
        assertEquals(listOf("https://x/only.jpg"), galleryImage?.urls)
        assertEquals("only", galleryImage?.alt)
    }

    @Test
    fun resolvePictureGalleryImage_noUsableUrl_returnsNull() {
        val doc = parse("<body><picture><img src=\"data:image/svg+xml;utf8,%3Csvg/%3E\"></picture></body>")
        val picture = doc.selectFirst("picture")!!
        assertNull(resolvePictureGalleryImage(picture))
    }

    @Test
    fun resolveImgGalleryImage_declaredTrackingPixel_returnsNull() {
        val doc = parse("<body><img src=\"https://x/pixel.gif\" width=\"1\" height=\"1\"></body>")
        assertNull(resolveImgGalleryImage(doc.selectFirst("img")!!))
    }

    @Test
    fun resolvePictureGalleryImage_declaredTrackingPixel_returnsNull() {
        val doc = parse("<body><picture><img src=\"https://x/pixel.gif\" width=\"1\" height=\"1\"></picture></body>")
        assertNull(resolvePictureGalleryImage(doc.selectFirst("picture")!!))
    }

    @Test
    fun collectGalleryImages_trackingPixelBetweenPhotos_isSkipped() {
        val doc = parse(
            """
            <body>
                <img src="https://x/1.jpg">
                <img src="https://x/pixel.gif" width="1" height="1">
                <img src="https://x/2.jpg">
            </body>
            """.trimIndent()
        )

        val images = collectGalleryImages(doc)

        assertEquals(listOf("https://x/1.jpg", "https://x/2.jpg"), images.map { it.urls.first() })
    }

    @Test
    fun heroGalleryImage_prefersBannerLocalPathOverEveryRemoteUrl() {
        val hero = heroGalleryImage(
            title = "An article",
            bannerImageUrl = "https://x/banner.jpg",
            screenshotUrl = "https://x/shot.jpg",
            bannerImageLocalPath = "/tmp/banner.jpg",
            screenshotLocalPath = "/tmp/shot.jpg"
        )

        assertEquals("/tmp/banner.jpg", hero?.localPath)
        assertEquals(emptyList<String>(), hero?.urls)
        assertEquals("An article", hero?.alt)
        assertNull(hero?.element)
    }

    @Test
    fun heroGalleryImage_fallsBackThroughBannerUrlThenScreenshot() {
        assertEquals(
            "https://x/banner.jpg",
            heroGalleryImage(
                title = "t",
                bannerImageUrl = "https://x/banner.jpg",
                screenshotUrl = "https://x/shot.jpg",
                screenshotLocalPath = "/tmp/shot.jpg"
            )?.urls?.first()
        )
        assertEquals(
            "/tmp/shot.jpg",
            heroGalleryImage(title = "t", screenshotUrl = "https://x/shot.jpg", screenshotLocalPath = "/tmp/shot.jpg")?.localPath
        )
        assertEquals(
            "https://x/shot.jpg",
            heroGalleryImage(title = "t", screenshotUrl = "https://x/shot.jpg")?.urls?.first()
        )
    }

    @Test
    fun heroGalleryImage_noImageAtAll_returnsNull() {
        assertNull(heroGalleryImage(title = "t"))
    }

    @Test
    fun openHero_opensOnTheFirstPageOfTheWholeGallery() {
        val doc = parse("<body><img src=\"https://x/1.jpg\"></body>")
        val state = GalleryViewerState()
        val hero = heroGalleryImage(title = "t", bannerImageUrl = "https://x/hero.jpg")!!
        state.heroImage = hero
        state.images = listOf(hero) + collectGalleryImages(doc)

        state.openHero()

        val request = state.request!!
        assertEquals(0, request.initialIndex)
        assertEquals(
            listOf("https://x/hero.jpg", "https://x/1.jpg"),
            request.images.map { it.urls.first() }
        )
    }

    @Test
    fun openHero_beforeTheArticleIsRendered_stillShowsTheBanner() {
        val state = GalleryViewerState()
        state.heroImage = heroGalleryImage(title = "t", bannerImageLocalPath = "/tmp/hero.jpg")

        state.openHero()

        assertEquals(1, state.request?.images?.size)
        assertEquals("/tmp/hero.jpg", state.request?.images?.first()?.localPath)
    }

    @Test
    fun openHero_withoutAHeroImage_doesNothing() {
        val state = GalleryViewerState()
        state.images = collectGalleryImages(parse("<body><img src=\"https://x/1.jpg\"></body>"))

        state.openHero()

        assertNull(state.request)
    }
}
