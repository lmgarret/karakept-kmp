package com.karakept.app.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.setHtmlTextColor
import com.karakept.app.data.repository.setHtmlBackgroundColor
import com.karakept.app.data.repository.setHtmlFontSize
import com.karakept.app.data.repository.setHtmlFontFamily
import com.karakept.app.data.repository.resetReaderAppearance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReaderAppearanceScreenModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val htmlTextColor: StateFlow<Color?> = settingsRepository.htmlTextColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val htmlBackgroundColor: StateFlow<Color?> = settingsRepository.htmlBackgroundColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val htmlFontSize: StateFlow<Int> = settingsRepository.htmlFontSize.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 16
    )

    val htmlFontFamily: StateFlow<ReaderFontFamily> = settingsRepository.htmlFontFamily.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderFontFamily.SYSTEM
    )

    fun setHtmlTextColor(color: Color?) {
        viewModelScope.launch {
            settingsRepository.setHtmlTextColor(color)
        }
    }

    fun setHtmlBackgroundColor(color: Color?) {
        viewModelScope.launch {
            settingsRepository.setHtmlBackgroundColor(color)
        }
    }

    fun setHtmlFontSize(size: Int) {
        viewModelScope.launch {
            settingsRepository.setHtmlFontSize(size)
        }
    }

    fun setHtmlFontFamily(family: ReaderFontFamily) {
        viewModelScope.launch {
            settingsRepository.setHtmlFontFamily(family)
        }
    }

    suspend fun resetReaderAppearance() {
        settingsRepository.resetReaderAppearance()
    }
}
