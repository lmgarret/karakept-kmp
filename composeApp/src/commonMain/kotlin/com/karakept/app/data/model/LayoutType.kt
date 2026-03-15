package com.karakept.app.data.model

enum class LayoutType {
    CARD,
    LIST,
    COMPACT_LIST;

    companion object {
        fun fromString(value: String): LayoutType {
            return when (value.uppercase()) {
                "CARD" -> CARD
                "LIST" -> LIST
                "COMPACT_LIST" -> COMPACT_LIST
                else -> LIST // Default to LIST layout
            }
        }
    }
}
