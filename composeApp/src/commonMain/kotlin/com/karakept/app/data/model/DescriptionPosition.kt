package com.karakept.app.data.model

enum class DescriptionPosition {
    BELOW_TITLE, ABOVE_METADATA;

    companion object {
        fun fromString(value: String): DescriptionPosition =
            entries.find { it.name == value } ?: BELOW_TITLE
    }
}
