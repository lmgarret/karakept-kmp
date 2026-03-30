package com.karakept.app.data.model

enum class LayoutType {
    CARD,
    LIST,
    @Deprecated("Merged into LIST. Kept for deserialization compatibility.")
    COMPACT_LIST;

    companion object {
        fun fromString(value: String): LayoutType {
            return when (value.uppercase()) {
                "CARD" -> CARD
                "LIST" -> LIST
                "COMPACT_LIST" -> LIST // D-01/D-02 migration: compact list is now LIST with compact toggles
                else -> LIST // Default to LIST layout
            }
        }
    }
}
