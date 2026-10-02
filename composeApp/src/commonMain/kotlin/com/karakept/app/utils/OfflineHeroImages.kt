package com.karakept.app.utils

/**
 * Where an offline copy keeps a bookmark's banner and screenshot, and how a row finds them.
 *
 * One place for the names: the list used to look for `img_<hash of the asset URL>`, a name nothing
 * writes, so it never found the stored file. Every row then fetched the image from the server —
 * missing offline, and stored a second time in the image loader's disk cache.
 */
object OfflineHeroImages {
    fun bannerFileName(assetId: String) = "hero_banner_$assetId"

    fun screenshotFileName(assetId: String) = "hero_screenshot_$assetId"

    /** The offline copy as a `file://` URL when it is on disk, else [remoteUrl]. */
    fun resolve(
        remoteUrl: String,
        directory: String,
        fileName: String,
        exists: (String) -> Boolean = ::fileExists
    ): String {
        val localPath = "$directory/$fileName"
        return if (exists(localPath)) "file://$localPath" else remoteUrl
    }
}
