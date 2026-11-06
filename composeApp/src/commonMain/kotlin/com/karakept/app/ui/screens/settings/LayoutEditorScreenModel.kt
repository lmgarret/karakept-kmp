package com.karakept.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.MetadataPosition
import com.karakept.app.data.model.QuickActionPosition
import com.karakept.app.data.model.ReadIndicatorStyle
import com.karakept.app.data.model.ThumbnailSide
import com.karakept.app.data.model.TitlePosition
import com.karakept.app.data.model.UrlDisplayMode
import com.karakept.app.data.model.UrlIconMode
import com.karakept.app.data.model.UrlPosition
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.saveLayout
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LayoutEditorScreenModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _layout = MutableStateFlow(
        BookmarkLayout(
            id = generateId(),
            name = ""
        )
    )
    val layout: StateFlow<BookmarkLayout> = _layout.asStateFlow()

    fun loadExisting(id: String) {
        viewModelScope.launch {
            val list = settingsRepository.customLayouts.first()
            val found = list.find { it.id == id }
            if (found != null) {
                _layout.value = found
            }
        }
    }

    fun updateName(name: String) { _layout.value = _layout.value.copy(name = name) }
    fun updateDescription(desc: String) { _layout.value = _layout.value.copy(description = desc.ifBlank { null }) }
    fun updateLayoutType(type: LayoutType) { _layout.value = _layout.value.copy(layoutType = type.name) }
    fun updateShowReadingTime(show: Boolean) { _layout.value = _layout.value.copy(showReadingTime = show) }
    fun updateShowDate(show: Boolean) { _layout.value = _layout.value.copy(showDate = show) }
    fun updateShowTags(show: Boolean) { _layout.value = _layout.value.copy(showTags = show) }
    fun updateDimReadBookmarks(dim: Boolean) { _layout.value = _layout.value.copy(dimReadBookmarks = dim) }
    fun updateDateDisplayMode(mode: DateDisplayMode) { _layout.value = _layout.value.copy(dateDisplayMode = mode.name) }
    fun updateThumbnailSide(side: ThumbnailSide) { _layout.value = _layout.value.copy(thumbnailSide = side.name) }
    fun updateShowThumbnail(show: Boolean) { _layout.value = _layout.value.copy(showThumbnail = show) }
    fun updateItemContainerStyle(style: ItemContainerStyle) {
        _layout.value = _layout.value.copy(itemContainerStyle = style.name)
    }
    fun updateReadIndicatorStyle(style: ReadIndicatorStyle) {
        _layout.value = _layout.value.copy(readIndicatorStyle = style.name)
    }
    fun updateShowRowDivider(show: Boolean) { _layout.value = _layout.value.copy(showRowDivider = show) }
    fun updateTitlePosition(position: TitlePosition) {
        _layout.value = _layout.value.copy(titlePosition = position.name)
    }
    fun updateThumbnailSize(size: Int) { _layout.value = _layout.value.copy(thumbnailSize = size) }
    fun updateMetadataPosition(position: MetadataPosition) { _layout.value = _layout.value.copy(metadataPosition = position.name) }
    fun updateTagsScrollable(v: Boolean) { _layout.value = _layout.value.copy(tagsScrollable = v) }
    fun updateDescriptionMaxLines(lines: Int) {
        _layout.value = _layout.value.copy(
            descriptionMaxLines = if (lines == BookmarkLayout.DESCRIPTION_LINES_AUTO) lines
            else lines.coerceIn(1, BookmarkLayout.DESCRIPTION_LINES_MAX)
        )
    }
    fun updateQuickActionPosition(pos: QuickActionPosition) { _layout.value = _layout.value.copy(quickActionPosition = pos.name) }
    fun updateShowDescription(show: Boolean) { _layout.value = _layout.value.copy(showDescription = show) }
    fun updateDescriptionPosition(pos: DescriptionPosition) { _layout.value = _layout.value.copy(descriptionPosition = pos.name) }
    fun updateShowUrl(show: Boolean) { _layout.value = _layout.value.copy(showUrl = show) }
    fun updateUrlDisplayMode(mode: UrlDisplayMode) { _layout.value = _layout.value.copy(urlDisplayMode = mode.name) }
    fun updateUrlPosition(pos: UrlPosition) { _layout.value = _layout.value.copy(urlPosition = pos.name) }
    /** Master favicon toggle: ON defaults to GLOBE_ONLY, OFF clears both. */
    fun updateFaviconEnabled(enabled: Boolean) {
        if (enabled) {
            if (UrlIconMode.fromString(_layout.value.urlIconMode) == UrlIconMode.NONE) {
                _layout.value = _layout.value.copy(urlIconMode = UrlIconMode.GLOBE_ONLY.name)
            }
        } else {
            _layout.value = _layout.value.copy(showFavicon = false, urlIconMode = UrlIconMode.NONE.name)
        }
    }
    /** Independently toggle the "on thumbnail" favicon. */
    fun updateShowFavicon(show: Boolean) {
        _layout.value = _layout.value.copy(showFavicon = show)
    }
    /** Set the by-link icon mode (GLOBE_ONLY or FAVICON). */
    fun updateUrlIconMode(mode: UrlIconMode) {
        _layout.value = _layout.value.copy(urlIconMode = mode.name)
    }
    fun updateFaviconByLinkSize(size: Int) {
        _layout.value = _layout.value.copy(faviconByLinkSize = size)
    }
    fun save() {
        viewModelScope.launch {
            // Use NonCancellable so the DataStore write survives scope cancellation
            // when Nav3 clears this entry's ViewModelStore right after the screen is popped.
            withContext(NonCancellable) {
                settingsRepository.saveLayout(_layout.value)
            }
        }
    }

    companion object {
        private fun generateId(): String = "profile_${System.currentTimeMillis()}"
    }
}
