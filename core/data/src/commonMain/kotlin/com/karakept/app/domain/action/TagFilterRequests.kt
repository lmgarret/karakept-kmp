package com.karakept.app.domain.action

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * A request from another screen to filter the bookmark list by a tag.
 *
 * [sourceBookmarkId] is the bookmark the tag was tapped on, so the list can scroll back to it.
 */
data class TagFilterRequest(val tag: String, val sourceBookmarkId: Long)

/**
 * Carries "filter the main list by this tag" from the reader back to the bookmark list.
 *
 * The reader cannot reach the list's `MainScreenModel`: every screen model is scoped to its own
 * Nav3 back-stack entry, so resolving one from another entry builds a *second* instance rather
 * than reaching the one on screen. Doing that through Koin also builds it outside any
 * `ViewModelStore`, which means `onCleared()` never runs and its `viewModelScope` — and every
 * repository collector started in `init` — outlives the screen for the rest of the process.
 * A `single` both screens share is the signal that does not need either of those.
 */
class TagFilterRequests {
    private val _requests = MutableSharedFlow<TagFilterRequest>(extraBufferCapacity = 1)
    val requests: SharedFlow<TagFilterRequest> = _requests

    fun request(tag: String, sourceBookmarkId: Long) {
        _requests.tryEmit(TagFilterRequest(tag, sourceBookmarkId))
    }
}
