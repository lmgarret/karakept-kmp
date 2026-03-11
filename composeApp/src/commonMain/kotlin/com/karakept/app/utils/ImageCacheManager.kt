package com.karakept.app.utils

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import com.fleeksoft.ksoup.Ksoup

/**
 * Manages offline image caching for bookmark HTML content.
 *
 * Parses HTML to find <img> tags, downloads the images to local storage,
 * and rewrites the HTML src attributes to point to local file:// paths.
 */
class ImageCacheManager(
    private val httpClient: HttpClient
) {
    companion object {
        private const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB
        private const val TAG = "ImageCacheManager"

        /**
         * Generates a deterministic cache file name from a URL.
         * Uses a simple hash to avoid filesystem issues with long/special-character URLs.
         */
        fun generateCacheFileName(url: String): String {
            val hash = url.hashCode().toUInt().toString(16)
            val extension = extractFileExtension(url)
            return "img_$hash$extension"
        }

        /**
         * Extracts a reasonable file extension from a URL, defaulting to empty.
         */
        fun extractFileExtension(url: String): String {
            val path = url.substringBefore("?").substringBefore("#")
            val lastDot = path.lastIndexOf('.')
            if (lastDot == -1) return ""
            val ext = path.substring(lastDot).lowercase()
            // Only allow common image extensions
            return when {
                ext in listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".avif", ".bmp") -> ext
                else -> ""
            }
        }
    }

    /**
     * Downloads all images referenced in the HTML, saves them locally,
     * and returns the HTML with src attributes rewritten to file:// paths.
     *
     * @param html The raw HTML content
     * @param authHeader Optional authorization header for authenticated image downloads
     * @return The HTML with image sources rewritten to local file paths
     */
    suspend fun cacheImagesInHtml(html: String, authHeader: String? = null): String {
        if (html.isBlank()) return html

        return try {
            val doc = Ksoup.parse(html)
            val imgElements = doc.select("img[src]")

            if (imgElements.isEmpty()) {
                return html
            }

            val cacheDir = FileUtils.getImageCacheDirectory()

            for (img in imgElements) {
                val src = img.attr("src")
                if (src.isBlank()) continue

                // Skip data: URIs (already inline) and relative URLs
                if (src.startsWith("data:") || (!src.startsWith("http://") && !src.startsWith("https://"))) {
                    continue
                }

                try {
                    val localPath = downloadAndCacheImage(src, cacheDir, authHeader)
                    if (localPath != null) {
                        img.attr("src", "file://$localPath")
                    }
                } catch (e: Exception) {
                    println("$TAG: Failed to cache image '$src': ${e.message}")
                    // Leave original URL — image won't load offline but won't break anything
                }
            }

            // Return the modified HTML preserving original structure
            doc.outputSettings().prettyPrint(false)
            doc.body().html()
        } catch (e: Exception) {
            println("$TAG: Failed to process HTML for image caching: ${e.message}")
            html // Return original on failure
        }
    }

    /**
     * Downloads a single image and saves it to the cache directory.
     *
     * @return The absolute local file path, or null if download failed
     */
    private suspend fun downloadAndCacheImage(
        url: String,
        cacheDir: String,
        authHeader: String? = null
    ): String? {
        // Generate deterministic filename from URL
        val fileName = generateCacheFileName(url)
        val expectedPath = "$cacheDir/$fileName"

        // Check if already cached (avoid re-download)
        if (isFileCached(expectedPath)) {
            return expectedPath
        }

        val response: HttpResponse = httpClient.get(url) {
            if (authHeader != null) {
                header("Authorization", authHeader)
            }
        }

        if (!response.status.isSuccess()) {
            println("$TAG: HTTP ${response.status} for image: $url")
            return null
        }

        val bytes: ByteArray = response.body()

        // Skip excessively large images
        if (bytes.size > MAX_IMAGE_SIZE_BYTES) {
            println("$TAG: Skipping oversized image (${bytes.size} bytes): $url")
            return null
        }

        return try {
            FileUtils.saveFile(cacheDir, fileName, bytes)
        } catch (e: Exception) {
            println("$TAG: Failed to save image to disk: ${e.message}")
            null
        }
    }

    /**
     * Downloads a hero image (banner or screenshot) by URL and caches it locally.
     *
     * @return The absolute local file path, or null if download failed
     */
    suspend fun cacheHeroImage(url: String, authHeader: String? = null): String? {
        val cacheDir = FileUtils.getImageCacheDirectory()
        return downloadAndCacheImage(url, cacheDir, authHeader)
    }



    /**
     * Checks if a file already exists in the cache.
     * Uses expect/actual pattern via FileUtils.
     */
    private fun isFileCached(path: String): Boolean {
        return try {
            // Use a simple check — if saveFile would overwrite, we skip
            // We need a platform-specific check
            fileExists(path)
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Platform-specific file existence check.
 */
expect fun fileExists(path: String): Boolean
