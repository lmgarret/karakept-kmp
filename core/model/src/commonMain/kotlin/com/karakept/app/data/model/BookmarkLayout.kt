package com.karakept.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BookmarkLayout(
    val id: String,
    val name: String,
    val description: String? = null,
    val icon: String? = null,
    val layoutType: String = LayoutType.LIST.name,
    val showReadingTime: Boolean = true,
    val showDate: Boolean = true,
    val showTags: Boolean = true,
    val dimReadBookmarks: Boolean = true,
    val dateDisplayMode: String = DateDisplayMode.ELAPSED.name,
    val thumbnailSide: String = ThumbnailSide.LEFT.name,
    val showFavicon: Boolean = true,
    val thumbnailSize: Int = 80,
    val metadataPosition: String = MetadataPosition.BELOW.name,
    val tagsScrollable: Boolean = false,
    val quickActionPosition: String = QuickActionPosition.RIGHT.name,
    val showDescription: Boolean = true,
    val descriptionPosition: String = DescriptionPosition.BELOW_TITLE.name,
    val showUrl: Boolean = false,
    val urlDisplayMode: String = UrlDisplayMode.DOMAIN_ONLY.name,
    val urlPosition: String = UrlPosition.BELOW_TITLE.name,
    val urlIconMode: String = UrlIconMode.GLOBE_ONLY.name,
    val faviconByLinkSize: Int = 16,
    val showThumbnail: Boolean = true,
    val itemContainerStyle: String = ItemContainerStyle.CARD.name,
    val readIndicatorStyle: String = ReadIndicatorStyle.DIM.name,
    /** Only drawn when [itemContainerStyle] is FLAT; a card supplies its own separation. */
    val showRowDivider: Boolean = true,
    val titlePosition: String = TitlePosition.BESIDE_THUMBNAIL.name,
    /**
     * Description line cap, or [DESCRIPTION_LINES_AUTO] to fill the space left beside the
     * thumbnail. Auto needs a thumbnail to measure against and falls back to [DESCRIPTION_LINES_DEFAULT]
     * without one.
     */
    val descriptionMaxLines: Int = DESCRIPTION_LINES_DEFAULT,
    val isBuiltIn: Boolean = false
) {
    companion object {
        const val BUILTIN_LIST_ID = "builtin:list"
        const val BUILTIN_CARD_ID = "builtin:card"
        const val BUILTIN_COMPACT_ID = "builtin:compact"
        const val BUILTIN_ROWS_ID = "builtin:rows"
        const val BUILTIN_DIGEST_ID = "builtin:digest"

        /** Fill whatever vertical space the thumbnail leaves over. */
        const val DESCRIPTION_LINES_AUTO = 0
        const val DESCRIPTION_LINES_DEFAULT = 2
        const val DESCRIPTION_LINES_MAX = 8

        val BUILTIN_COMPACT = BookmarkLayout(
            id = BUILTIN_COMPACT_ID,
            name = "Compact",
            description = "Small thumbnail, title and minimal metadata",
            icon = "List",
            layoutType = LayoutType.LIST.name,
            showDescription = false,
            showTags = false,
            showDate = true,
            thumbnailSize = 48,
            metadataPosition = MetadataPosition.BESIDE.name,
            isBuiltIn = true
        )

        /**
         * Flat rows with a hairline rule between them and a short excerpt.
         *
         * The divider costs one line of ink per bookmark where an outlined card costs a whole
         * rectangle, which is what makes this the one to reach for on electronic paper — turn the
         * thumbnail off there and it collapses to plain text rows.
         */
        val BUILTIN_ROWS = BookmarkLayout(
            id = BUILTIN_ROWS_ID,
            name = "Rows",
            description = "Flat rows separated by a divider, with a short excerpt",
            icon = "Subject",
            layoutType = LayoutType.LIST.name,
            showDescription = true,
            descriptionMaxLines = 2,
            showTags = true,
            tagsScrollable = true,
            showDate = true,
            itemContainerStyle = ItemContainerStyle.FLAT.name,
            showRowDivider = true,
            titlePosition = TitlePosition.BESIDE_THUMBNAIL.name,
            isBuiltIn = true
        )

        val BUILTIN_LIST = BookmarkLayout(
            id = BUILTIN_LIST_ID,
            name = "Cards",
            description = "A card per bookmark, with a thumbnail beside the text",
            icon = "ViewList",
            layoutType = LayoutType.LIST.name,
            isBuiltIn = true
        )

        /**
         * Long excerpts with nothing between entries but whitespace.
         *
         * Enough of each article to decide without opening it, which is the whole point — so the
         * title takes the full row width and the excerpt runs to five lines. No divider: at this
         * height the entries separate themselves.
         */
        val BUILTIN_DIGEST = BookmarkLayout(
            id = BUILTIN_DIGEST_ID,
            name = "Digest",
            description = "Long excerpts with the title above the thumbnail",
            icon = "Notes",
            layoutType = LayoutType.LIST.name,
            showDescription = true,
            descriptionMaxLines = 5,
            showTags = true,
            tagsScrollable = true,
            showDate = true,
            itemContainerStyle = ItemContainerStyle.FLAT.name,
            showRowDivider = false,
            titlePosition = TitlePosition.ABOVE_THUMBNAIL.name,
            isBuiltIn = true
        )

        val BUILTIN_CARD = BookmarkLayout(
            id = BUILTIN_CARD_ID,
            name = "Magazine",
            description = "A large hero image above each bookmark",
            icon = "Window",
            layoutType = LayoutType.CARD.name,
            isBuiltIn = true
        )

        /** Ordered densest to richest, which is how the layout picker presents them. */
        val ALL_BUILTIN = listOf(
            BUILTIN_COMPACT, BUILTIN_ROWS, BUILTIN_LIST, BUILTIN_DIGEST, BUILTIN_CARD
        )

        fun isBuiltInId(id: String) = id.startsWith("builtin:")

        fun getBuiltIn(id: String) = ALL_BUILTIN.find { it.id == id }
    }
}
