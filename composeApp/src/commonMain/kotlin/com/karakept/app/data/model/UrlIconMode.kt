package com.karakept.app.data.model

enum class UrlIconMode {
    GLOBE_ONLY, FAVICON;

    companion object {
        fun fromString(value: String): UrlIconMode =
            entries.find { it.name == value } ?: GLOBE_ONLY
    }
}
