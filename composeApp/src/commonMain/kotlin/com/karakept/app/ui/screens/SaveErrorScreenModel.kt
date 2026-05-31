package com.karakept.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.repository.BookmarkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class SaveError(val url: String, val message: String)

sealed interface SaveRetryState {
    data object Idle : SaveRetryState
    data object Retrying : SaveRetryState
    data class Success(val bookmarkId: Long) : SaveRetryState
    data class Failed(val message: String) : SaveRetryState
}

class SaveErrorScreenModel(
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {
    private val _retryStates = MutableStateFlow<Map<String, SaveRetryState>>(emptyMap())
    val retryStates: StateFlow<Map<String, SaveRetryState>> = _retryStates.asStateFlow()

    fun retry(url: String) {
        if (_retryStates.value[url] is SaveRetryState.Retrying) return
        _retryStates.update { it + (url to SaveRetryState.Retrying) }
        viewModelScope.launch {
            val result = bookmarkRepository.createBookmark(url)
            val newState = if (result.isSuccess) {
                SaveRetryState.Success(result.getOrThrow().localId)
            } else {
                SaveRetryState.Failed(result.exceptionOrNull()?.message ?: "Unknown error")
            }
            _retryStates.update { it + (url to newState) }
        }
    }
}
