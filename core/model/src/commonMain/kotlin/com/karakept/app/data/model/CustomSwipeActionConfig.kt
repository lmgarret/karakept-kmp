package com.karakept.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class CustomSwipeActionType {
    ADD_TAG,
    ADD_TO_LIST
}

@Serializable
data class CustomSwipeActionConfig(
    val id: String,
    val type: CustomSwipeActionType,
    val tagName: String? = null,
    val listId: String? = null,
    val listName: String? = null,
    val customName: String? = null,
    val colorHex: String? = null
) {
    fun getDisplayName(): String {
        return customName ?: when (type) {
            CustomSwipeActionType.ADD_TAG ->
                if (tagName != null) "Add tag '$tagName'" else "Add Tag"
            CustomSwipeActionType.ADD_TO_LIST ->
                if (listName != null) "Add to '$listName'" else "Add to List"
        }
    }
}
