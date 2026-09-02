package com.karakept.app.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.domain.action.TagFilterRequests
import org.koin.compose.koinInject

@Serializable
data class BookmarkViewerScreen(
    val bookmarkId: Long,
    val scrollToHighlightId: String? = null,
    // Known from the caller (e.g. the bookmark list already has this in memory) so the top bar
    // can show a title immediately instead of waiting on this screen's own DB query to resolve.
    val initialTitle: String? = null,
    val initialUrl: String? = null
) : NavKey {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<BookmarkViewerScreenModel>()
        // A single, not the bookmark list's own model: resolving that here builds a second
        // MainScreenModel outside any ViewModelStore, so it is never cleared and its repository
        // collectors keep refreshing for the rest of the process (one leaked per bookmark opened).
        val tagFilterRequests = koinInject<TagFilterRequests>()

        BookmarkViewerContent(
            bookmarkId = bookmarkId,
            scrollToHighlightId = scrollToHighlightId,
            initialTitle = initialTitle,
            initialUrl = initialUrl,
            screenModel = screenModel,
            onBack = { navigator.pop() },
            onTagFilterApply = { tag ->
                tagFilterRequests.request(tag, bookmarkId)
                navigator.pop()
            }
        )
    }
}
