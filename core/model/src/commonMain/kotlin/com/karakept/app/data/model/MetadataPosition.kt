package com.karakept.app.data.model

enum class MetadataPosition {
    BELOW, BESIDE, ABOVE;

    companion object {
        fun fromString(value: String): MetadataPosition =
            entries.find { it.name == value } ?: BELOW
    }
}
