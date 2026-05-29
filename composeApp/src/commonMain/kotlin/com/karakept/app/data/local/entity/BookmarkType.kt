package com.karakept.app.data.local.entity

enum class BookmarkType(val storageValue: String) {
    LINK("link"),
    TEXT("text"),
    VIDEO("video"),
    ASSET("asset"),
    UNKNOWN("unknown");

    companion object {
        fun fromStorageValue(value: String?): BookmarkType =
            entries.firstOrNull { it.storageValue == value } ?: LINK
    }
}

val BookmarkEntity.bookmarkType: BookmarkType
    get() = BookmarkType.fromStorageValue(type)
