package com.karakept.app.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import org.koin.compose.koinInject

@Serializable
data class BookmarkViewerScreen(
    val bookmarkId: Long,
    val scrollToHighlightId: String? = null
) : NavKey {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<BookmarkViewerScreenModel>()
        val mainScreenModel = koinInject<MainScreenModel>()

        BookmarkViewerContent(
            bookmarkId = bookmarkId,
            scrollToHighlightId = scrollToHighlightId,
            screenModel = screenModel,
            onBack = { navigator.pop() },
            onTagFilterApply = { tag ->
                mainScreenModel.applyTagFilter(tag, bookmarkId)
                navigator.pop()
            }
        )
    }
}
