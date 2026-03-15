package com.karakept.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ListSettings(
    val syncOffline: Boolean = false,
    val notifyOnNewBookmarks: Boolean = false,
    val scrollAction: SwipeAction = SwipeAction.NONE,
    val scrollActionConfigId: String? = null,
    val openAction: SwipeAction = SwipeAction.NONE,
    val openActionConfigId: String? = null,
    val includeChildListBookmarks: Boolean = false,
    val countOnlyUnread: Boolean = false
)
