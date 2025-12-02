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
    ALL,
    FAVORITES,
    ARCHIVED,
    NOT_ARCHIVED
}

enum class SortOption {
    NEWEST,
    OLDEST,
    TITLE_AZ,
    TITLE_ZA
}
