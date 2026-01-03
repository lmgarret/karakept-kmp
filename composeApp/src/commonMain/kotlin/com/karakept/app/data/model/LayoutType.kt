package com.karakept.app.data.model

enum class LayoutType {
    CARD,
    LIST;

    companion object {
        fun fromString(value: String): LayoutType {
            return when (value.uppercase()) {
                "CARD" -> CARD
                "LIST" -> LIST
                else -> LIST // Default to LIST layout
            }
        }
    }
}
