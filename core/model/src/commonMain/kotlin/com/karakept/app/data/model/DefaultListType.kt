package com.karakept.app.data.model

enum class DefaultListType {
    ALL_BOOKMARKS,
    FAVORITES,
    ARCHIVED,
    SPECIFIC_LIST,
    LAST_VIEWED;

    companion object {
        fun fromString(value: String): DefaultListType =
            entries.firstOrNull { it.name == value } ?: ALL_BOOKMARKS
    }
}
