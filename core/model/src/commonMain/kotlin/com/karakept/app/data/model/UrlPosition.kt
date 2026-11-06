package com.karakept.app.data.model

enum class UrlPosition {
    BELOW_TITLE, METADATA_ROW;

    companion object {
        fun fromString(value: String): UrlPosition =
            entries.find { it.name == value } ?: BELOW_TITLE
    }
}
