package com.karakept.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FilterConfig(
    val status: FilterStatus = FilterStatus.ALL,
    val tags: List<String> = emptyList(),
    val lists: List<String> = emptyList(), // List IDs
    val sort: SortOption = SortOption.NEWEST
)

enum class FilterStatus {
    ALL,                    // Shows non-archived bookmarks
    ALL_INCLUDING_ARCHIVED, // Shows all bookmarks (used for list views)
    FAVORITES,              // Shows starred bookmarks (includes archived)
    ARCHIVED                // Shows archived bookmarks
}

enum class SortOption {
    NEWEST,
    OLDEST,
    TITLE_AZ,
    TITLE_ZA,
    READING_TIME_SHORT,
    READING_TIME_LONG
}
