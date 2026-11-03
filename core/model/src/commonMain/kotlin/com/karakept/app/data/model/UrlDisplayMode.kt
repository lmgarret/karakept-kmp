package com.karakept.app.data.model

enum class UrlDisplayMode {
    DOMAIN_ONLY, FULL_URL;

    companion object {
        fun fromString(value: String): UrlDisplayMode =
            entries.find { it.name == value } ?: DOMAIN_ONLY
    }
}
