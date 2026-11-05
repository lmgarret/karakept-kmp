package com.karakept.app.data.local

import com.karakept.api.model.BookmarksBookmarkIdAssetsPost201Response.AssetType
import com.karakept.app.data.model.SANITIZED_ASSET_NAME
import com.karakept.app.data.model.SANITIZED_ASSET_TYPE

/**
 * Which of Karakeep's asset types the app keeps a local row for, and the string it stores.
 *
 * Both sync paths (`BookmarkRepository.insertContentAssetMetadata` and
 * `BookmarkSyncPipeline.insertAssetMetadata`) resolve through [storedAssetType], so the set of
 * tracked types and the rule for sidecar assets are stated once. An unlisted type is dropped: the
 * viewer only offers what it knows how to render or download.
 */
internal object AssetTypeMapping {

    private val TRACKED: Map<AssetType, String> = mapOf(
        AssetType.LINK_HTML_CONTENT to "linkHtmlContent",
        AssetType.FULL_PAGE_ARCHIVE to "fullPageArchive",
        AssetType.PRECRAWLED_ARCHIVE to "precrawledArchive",
        AssetType.PDF to "pdf"
    )

    /**
     * The string [apiType] is stored under, or null for an asset the app does not track.
     *
     * `userUploaded` covers every asset anyone attached by hand, so it is admitted only for a
     * sidecar's own file, identified by [fileName]. Tracking all hand-attached uploads would start
     * listing unrelated files in the viewer's details panel, which is a separate decision.
     */
    fun storedAssetType(apiType: AssetType?, fileName: String?): String? = when {
        apiType == null -> null
        apiType == AssetType.USER_UPLOADED -> SANITIZED_ASSET_TYPE.takeIf { fileName == SANITIZED_ASSET_NAME }
        else -> TRACKED[apiType]
    }
}
