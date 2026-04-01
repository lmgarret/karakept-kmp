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
    val isBuiltIn: Boolean = false
) {
    companion object {
        const val BUILTIN_LIST_ID = "builtin:list"
        const val BUILTIN_CARD_ID = "builtin:card"
        const val BUILTIN_COMPACT_ID = "builtin:compact"

        val BUILTIN_LIST = BookmarkLayout(
            id = BUILTIN_LIST_ID,
            name = "List",
            description = "List layout with thumbnails and description",
            icon = "ViewList",
            layoutType = LayoutType.LIST.name,
            isBuiltIn = true
        )

        val BUILTIN_CARD = BookmarkLayout(
            id = BUILTIN_CARD_ID,
            name = "Card",
            description = "Card layout with large hero images",
            icon = "Window",
            layoutType = LayoutType.CARD.name,
            isBuiltIn = true
        )

        val BUILTIN_COMPACT = BookmarkLayout(
            id = BUILTIN_COMPACT_ID,
            name = "Compact List",
            description = "Compact list with title and minimal metadata",
            icon = "List",
            layoutType = LayoutType.LIST.name,
            showDescription = false,
            showTags = false,
            showDate = true,
            thumbnailSize = 48,
            metadataPosition = MetadataPosition.BESIDE.name,
            isBuiltIn = true
        )

        val ALL_BUILTIN = listOf(BUILTIN_LIST, BUILTIN_CARD, BUILTIN_COMPACT)

        fun isBuiltInId(id: String) = id.startsWith("builtin:")

        fun getBuiltIn(id: String) = ALL_BUILTIN.find { it.id == id }
    }
}
