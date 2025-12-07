package com.karakept.app.data.model

/**
 * Enum representing configurable swipe actions on bookmark items.
 */
enum class SwipeAction(val displayName: String) {
    NONE("None"),
    ARCHIVE("Archive"),
    FAVOURITE("Toggle Favorite"),
    MARK_READ("Mark as Read"),
    DELETE("Delete"),
    SHARE("Share");
    
    companion object {
        fun fromString(value: String): SwipeAction {
            return entries.firstOrNull { it.name == value } ?: NONE
        }
    }
}
