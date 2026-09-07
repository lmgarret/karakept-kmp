package com.karakept.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.karakept.app.ui.icons.AppIcons
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.DescriptionPosition
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.MetadataPosition
import com.karakept.app.data.model.QuickActionPosition
import com.karakept.app.data.model.ItemContainerStyle
import com.karakept.app.data.model.ReadIndicatorStyle
import com.karakept.app.data.model.ThumbnailSide
import com.karakept.app.data.model.TitlePosition
import com.karakept.app.data.model.UrlDisplayMode
import com.karakept.app.data.model.UrlIconMode
import com.karakept.app.data.model.UrlPosition
import getPlatform
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.saveLayout
import com.karakept.app.ui.components.BookmarkCardLayout
import com.karakept.app.ui.components.BookmarkListLayout
import karakept.composeapp.generated.resources.Res
import karakept.composeapp.generated.resources.preview_thumbnail
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

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
            // when Voyager disposes the ScreenModel immediately after navigator.pop().
            withContext(NonCancellable) {
                settingsRepository.saveLayout(_layout.value)
            }
        }
    }

    companion object {
        private fun generateId(): String = "profile_${System.currentTimeMillis()}"
    }
}

/** Mock bookmark used for live preview in the editor. */
private val PREVIEW_BOOKMARK = BookmarkEntity(
    localId = -1L,
    remoteId = "preview",
    serverId = "preview",
    url = "https://karakeep.app/article/bookmarking-best-practices",
    title = "Bookmarking Best Practices",
    content = null,
    imageUrl = null,
    bannerImageAssetId = null,
    screenshotAssetId = null,
    description = "A short preview of the bookmark description text to illustrate the layout.",
    createdAt = System.currentTimeMillis() - 3_600_000L,
    isArchived = false,
    isStarred = false,
    isRead = false,
    tags = "technology,design",
    listIds = "",
    readingTimeMinutes = 5,
    readingProgress = 0f,
    readingScrollIndex = 0,
    readingScrollOffset = 0
)

@Serializable
data class LayoutEditorScreen(val layoutId: String?) : NavKey {
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<LayoutEditorScreenModel>()

        LaunchedEffect(layoutId) {
            if (layoutId != null) screenModel.loadExisting(layoutId)
        }

        LayoutEditorContent(
            screenModel = screenModel,
            isEditing = layoutId != null,
            onBack = { navigator.pop() },
            onSave = {
                screenModel.save()
                navigator.pop()
            }
        )
    }
}

/**
 * Layout editor as a dialog, used on desktop to avoid 4-level settings depth.
 */
@Composable
internal fun LayoutEditorDialog(
    layoutId: String?,
    onDismiss: () -> Unit
) {
    val screenModel = org.koin.compose.koinInject<LayoutEditorScreenModel>()

    LaunchedEffect(layoutId) {
        if (layoutId != null) screenModel.loadExisting(layoutId)
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .fillMaxSize(0.9f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            LayoutEditorContent(
                screenModel = screenModel,
                isEditing = layoutId != null,
                onBack = onDismiss,
                onSave = {
                    screenModel.save()
                    onDismiss()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutEditorContent(
    screenModel: LayoutEditorScreenModel,
    isEditing: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val layout by screenModel.layout.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Layout" else "New Layout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = layout.name.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pinned preview — does not scroll
            // Card layouts include a 200.dp image area so they need more vertical space
            val previewMaxHeight = if (LayoutType.fromString(layout.layoutType) == LayoutType.CARD) 420.dp else 280.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = previewMaxHeight)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                PreviewBookmarkItem(layout = layout)
            }

            HorizontalDivider()

            // Scrollable settings
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                val isList = layout.layoutType != LayoutType.CARD.name
                val urlIconMode = UrlIconMode.fromString(layout.urlIconMode)
                val faviconEnabled = layout.showFavicon || urlIconMode != UrlIconMode.NONE

                // ── Layout Info ───────────────────────────────────────────────
                SettingsSection(title = "Layout Info") {
                    OutlinedTextField(
                        value = layout.name,
                        onValueChange = { screenModel.updateName(it) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = layout.description ?: "",
                        onValueChange = { screenModel.updateDescription(it) },
                        label = { Text("Description (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Layout Type ───────────────────────────────────────────────
                SettingsSection(title = "Layout Type") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            LayoutRadioOption(
                                title = "Card",
                                description = "Large hero image with title and metadata below",
                                icon = AppIcons.Default.Window,
                                isSelected = layout.layoutType == LayoutType.CARD.name,
                                onClick = { screenModel.updateLayoutType(LayoutType.CARD) }
                            )
                            HorizontalDivider()
                            LayoutRadioOption(
                                title = "List",
                                description = "Row layout with thumbnail, title, and metadata",
                                icon = AppIcons.AutoMirrored.Filled.ViewList,
                                isSelected = isList,
                                onClick = { screenModel.updateLayoutType(LayoutType.LIST) }
                            )
                        }
                    }
                }

                // ── Row style (list only) ─────────────────────────────────────
                if (isList) {
                    SettingsSection(title = "Row Style") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                LayoutRadioOption(
                                    title = "Card",
                                    description = "Each bookmark in its own raised container",
                                    icon = AppIcons.Default.Window,
                                    isSelected = layout.itemContainerStyle == ItemContainerStyle.CARD.name,
                                    onClick = { screenModel.updateItemContainerStyle(ItemContainerStyle.CARD) }
                                )
                                HorizontalDivider()
                                LayoutRadioOption(
                                    title = "Flat rows",
                                    description = "No container — rows separated by a divider. Best on e-ink",
                                    icon = AppIcons.Default.Reorder,
                                    isSelected = layout.itemContainerStyle == ItemContainerStyle.FLAT.name,
                                    onClick = { screenModel.updateItemContainerStyle(ItemContainerStyle.FLAT) }
                                )
                                // A card already separates itself; the divider is flat-only.
                                if (layout.itemContainerStyle == ItemContainerStyle.FLAT.name) {
                                    HorizontalDivider()
                                    ToggleRow(
                                        icon = AppIcons.Default.Remove,
                                        title = "Divider between rows",
                                        checked = layout.showRowDivider,
                                        onCheckedChange = { screenModel.updateShowRowDivider(it) }
                                    )
                                }
                            }
                        }
                    }

                    if (layout.showThumbnail) {
                        SettingsSection(title = "Title Position") {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    LayoutRadioOption(
                                        title = "Beside thumbnail",
                                        description = "Title shares the row with the image",
                                        icon = AppIcons.Default.VerticalSplit,
                                        isSelected = layout.titlePosition == TitlePosition.BESIDE_THUMBNAIL.name,
                                        onClick = {
                                            screenModel.updateTitlePosition(TitlePosition.BESIDE_THUMBNAIL)
                                        }
                                    )
                                    HorizontalDivider()
                                    LayoutRadioOption(
                                        title = "Above thumbnail",
                                        description = "Title spans the full width, image sits below it",
                                        icon = AppIcons.Default.Title,
                                        isSelected = layout.titlePosition == TitlePosition.ABOVE_THUMBNAIL.name,
                                        onClick = {
                                            screenModel.updateTitlePosition(TitlePosition.ABOVE_THUMBNAIL)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    SettingsSection(title = "Read Indicator") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                LayoutRadioOption(
                                    title = "Dim",
                                    description = "Fade read bookmarks",
                                    icon = AppIcons.Default.Visibility,
                                    isSelected = layout.readIndicatorStyle == ReadIndicatorStyle.DIM.name,
                                    onClick = { screenModel.updateReadIndicatorStyle(ReadIndicatorStyle.DIM) }
                                )
                                HorizontalDivider()
                                LayoutRadioOption(
                                    title = "Marker",
                                    description = "Bullet beside unread titles, full contrast throughout",
                                    icon = AppIcons.Default.Circle,
                                    isSelected = layout.readIndicatorStyle == ReadIndicatorStyle.MARKER.name,
                                    onClick = { screenModel.updateReadIndicatorStyle(ReadIndicatorStyle.MARKER) }
                                )
                            }
                        }
                    }
                }

                // ── Thumbnail (list only) ─────────────────────────────────────
                if (isList) {
                    SettingsSection(title = "Thumbnail") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                ToggleRow(
                                    icon = AppIcons.Default.Image,
                                    title = "Show thumbnail",
                                    checked = layout.showThumbnail,
                                    onCheckedChange = { screenModel.updateShowThumbnail(it) }
                                )
                                HorizontalDivider()
                                LayoutRadioOption(
                                    title = "Left",
                                    description = "Thumbnail on the left side",
                                    icon = AppIcons.AutoMirrored.Filled.ViewList,
                                    isSelected = layout.thumbnailSide == ThumbnailSide.LEFT.name,
                                    onClick = { screenModel.updateThumbnailSide(ThumbnailSide.LEFT) }
                                )
                                HorizontalDivider()
                                LayoutRadioOption(
                                    title = "Right",
                                    description = "Thumbnail on the right side",
                                    icon = AppIcons.AutoMirrored.Filled.ViewList,
                                    isSelected = layout.thumbnailSide == ThumbnailSide.RIGHT.name,
                                    onClick = { screenModel.updateThumbnailSide(ThumbnailSide.RIGHT) }
                                )
                                HorizontalDivider()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = AppIcons.Default.Photo,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 16.dp)
                                            )
                                            Text("Size", style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text(
                                            text = "${layout.thumbnailSize}dp",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Slider(
                                        value = layout.thumbnailSize.toFloat(),
                                        onValueChange = { screenModel.updateThumbnailSize(it.toInt()) },
                                        valueRange = 32f..120f,
                                        steps = 10,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Description ───────────────────────────────────────────────
                SettingsSection(title = "Description") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            ToggleRow(
                                icon = AppIcons.AutoMirrored.Filled.Notes,
                                title = "Show description",
                                checked = layout.showDescription,
                                onCheckedChange = { screenModel.updateShowDescription(it) }
                            )
                            if (layout.showDescription && isList) {
                                HorizontalDivider()
                                SubsectionHeader("Position")
                                SimpleRadioOption(
                                    title = "Below title",
                                    isSelected = DescriptionPosition.fromString(layout.descriptionPosition) == DescriptionPosition.BELOW_TITLE,
                                    onClick = { screenModel.updateDescriptionPosition(DescriptionPosition.BELOW_TITLE) }
                                )
                                SimpleRadioOption(
                                    title = "Above metadata",
                                    isSelected = DescriptionPosition.fromString(layout.descriptionPosition) == DescriptionPosition.ABOVE_METADATA,
                                    onClick = { screenModel.updateDescriptionPosition(DescriptionPosition.ABOVE_METADATA) }
                                )

                                HorizontalDivider()
                                val isAuto = layout.descriptionMaxLines == BookmarkLayout.DESCRIPTION_LINES_AUTO
                                // Auto has nothing to measure against without a thumbnail setting
                                // the row height, so it is only offered when there is one.
                                if (layout.showThumbnail) {
                                    ToggleRow(
                                        icon = null,
                                        title = "Fill space beside thumbnail",
                                        checked = isAuto,
                                        onCheckedChange = {
                                            screenModel.updateDescriptionMaxLines(
                                                if (it) BookmarkLayout.DESCRIPTION_LINES_AUTO
                                                else BookmarkLayout.DESCRIPTION_LINES_DEFAULT
                                            )
                                        }
                                    )
                                }
                                if (!isAuto || !layout.showThumbnail) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(
                                            text = "Maximum lines: ${layout.descriptionMaxLines.coerceAtLeast(1)}",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Slider(
                                            value = layout.descriptionMaxLines.coerceAtLeast(1).toFloat(),
                                            onValueChange = { screenModel.updateDescriptionMaxLines(it.toInt()) },
                                            valueRange = 1f..BookmarkLayout.DESCRIPTION_LINES_MAX.toFloat(),
                                            steps = BookmarkLayout.DESCRIPTION_LINES_MAX - 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Link ──────────────────────────────────────────────────────
                SettingsSection(title = "Link") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            ToggleRow(
                                icon = AppIcons.Default.Link,
                                title = "Show link",
                                checked = layout.showUrl,
                                onCheckedChange = { screenModel.updateShowUrl(it) }
                            )
                            if (layout.showUrl) {
                                HorizontalDivider()
                                SubsectionHeader("Format")
                                SimpleRadioOption(
                                    title = "Domain only",
                                    description = "e.g., karakeep.app",
                                    isSelected = UrlDisplayMode.fromString(layout.urlDisplayMode) == UrlDisplayMode.DOMAIN_ONLY,
                                    onClick = { screenModel.updateUrlDisplayMode(UrlDisplayMode.DOMAIN_ONLY) }
                                )
                                SimpleRadioOption(
                                    title = "Full URL",
                                    description = "e.g., https://karakeep.app/article/...",
                                    isSelected = UrlDisplayMode.fromString(layout.urlDisplayMode) == UrlDisplayMode.FULL_URL,
                                    onClick = { screenModel.updateUrlDisplayMode(UrlDisplayMode.FULL_URL) }
                                )
                                HorizontalDivider()
                                SubsectionHeader("Position")
                                SimpleRadioOption(
                                    title = "Below title",
                                    isSelected = UrlPosition.fromString(layout.urlPosition) == UrlPosition.BELOW_TITLE,
                                    onClick = { screenModel.updateUrlPosition(UrlPosition.BELOW_TITLE) }
                                )
                                SimpleRadioOption(
                                    title = "In metadata row",
                                    isSelected = UrlPosition.fromString(layout.urlPosition) == UrlPosition.METADATA_ROW,
                                    onClick = { screenModel.updateUrlPosition(UrlPosition.METADATA_ROW) }
                                )
                            }
                        }
                    }
                }

                // ── Favicon ───────────────────────────────────────────────────
                SettingsSection(title = "Favicon") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            ToggleRow(
                                icon = AppIcons.Default.Language,
                                title = "Show favicon",
                                checked = faviconEnabled,
                                onCheckedChange = { screenModel.updateFaviconEnabled(it) }
                            )
                            if (faviconEnabled) {
                                if (isList) {
                                    HorizontalDivider()
                                    SubsectionHeader("Placement")
                                    ToggleRow(
                                        icon = null,
                                        title = "On thumbnail",
                                        checked = layout.showFavicon,
                                        onCheckedChange = { screenModel.updateShowFavicon(it) }
                                    )
                                }
                                HorizontalDivider()
                                SubsectionHeader("By link")
                                SimpleRadioOption(
                                    title = "Globe icon",
                                    isSelected = urlIconMode == UrlIconMode.GLOBE_ONLY,
                                    onClick = { screenModel.updateUrlIconMode(UrlIconMode.GLOBE_ONLY) }
                                )
                                SimpleRadioOption(
                                    title = "Site favicon",
                                    isSelected = urlIconMode == UrlIconMode.FAVICON,
                                    onClick = { screenModel.updateUrlIconMode(UrlIconMode.FAVICON) }
                                )
                                if (urlIconMode != UrlIconMode.NONE) {
                                    HorizontalDivider()
                                    SubsectionHeader("Icon size")
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${layout.faviconByLinkSize}dp", style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Slider(
                                            value = layout.faviconByLinkSize.toFloat(),
                                            onValueChange = { screenModel.updateFaviconByLinkSize(it.toInt()) },
                                            valueRange = 10f..24f,
                                            steps = 6
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Date ──────────────────────────────────────────────────────
                SettingsSection(title = "Date") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            ToggleRow(
                                icon = AppIcons.Default.CalendarToday,
                                title = "Show date",
                                checked = layout.showDate,
                                onCheckedChange = { screenModel.updateShowDate(it) }
                            )
                            if (layout.showDate) {
                                HorizontalDivider()
                                SubsectionHeader("Format")
                                SimpleRadioOption(
                                    title = "Relative",
                                    description = "e.g., 33m ago, 2h ago",
                                    isSelected = layout.dateDisplayMode == DateDisplayMode.ELAPSED.name,
                                    onClick = { screenModel.updateDateDisplayMode(DateDisplayMode.ELAPSED) }
                                )
                                SimpleRadioOption(
                                    title = "Absolute",
                                    description = "e.g., 2024-01-15",
                                    isSelected = layout.dateDisplayMode == DateDisplayMode.ABSOLUTE.name,
                                    onClick = { screenModel.updateDateDisplayMode(DateDisplayMode.ABSOLUTE) }
                                )
                            }
                        }
                    }
                }

                // ── Reading Time ──────────────────────────────────────────────
                SettingsSection(title = "Reading Time") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ToggleRow(
                            icon = AppIcons.AutoMirrored.Outlined.MenuBook,
                            title = "Show reading time",
                            checked = layout.showReadingTime,
                            onCheckedChange = { screenModel.updateShowReadingTime(it) }
                        )
                    }
                }

                // ── Tags ──────────────────────────────────────────────────────
                SettingsSection(title = "Tags") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            ToggleRow(
                                icon = AppIcons.AutoMirrored.Filled.Label,
                                title = "Show tags",
                                checked = layout.showTags,
                                onCheckedChange = { screenModel.updateShowTags(it) }
                            )
                            if (layout.showTags) {
                                HorizontalDivider()
                                ToggleRowWithDescription(
                                    title = "Tags on one line",
                                    description = "Keep tags in a single row that scrolls sideways, " +
                                        "instead of wrapping onto more lines",
                                    checked = layout.tagsScrollable,
                                    onCheckedChange = { screenModel.updateTagsScrollable(it) }
                                )
                            }
                        }
                    }
                }

                // ── Metadata Position (list only) ─────────────────────────────
                if (isList) {
                    SettingsSection(title = "Metadata Position") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                LayoutRadioOption(
                                    title = "Below",
                                    description = "Tags, date and reading time below thumbnail and text",
                                    icon = AppIcons.AutoMirrored.Filled.List,
                                    isSelected = layout.metadataPosition == MetadataPosition.BELOW.name,
                                    onClick = { screenModel.updateMetadataPosition(MetadataPosition.BELOW) }
                                )
                                HorizontalDivider()
                                LayoutRadioOption(
                                    title = "Beside",
                                    description = "Tags, date and reading time beside the thumbnail, under the title",
                                    icon = AppIcons.Default.VerticalSplit,
                                    isSelected = layout.metadataPosition == MetadataPosition.BESIDE.name,
                                    onClick = { screenModel.updateMetadataPosition(MetadataPosition.BESIDE) }
                                )
                                HorizontalDivider()
                                LayoutRadioOption(
                                    title = "Above",
                                    description = "Tags, date and reading time above the thumbnail and text",
                                    icon = AppIcons.AutoMirrored.Filled.List,
                                    isSelected = layout.metadataPosition == MetadataPosition.ABOVE.name,
                                    onClick = { screenModel.updateMetadataPosition(MetadataPosition.ABOVE) }
                                )
                            }
                        }
                    }
                }

                // ── Appearance ────────────────────────────────────────────────
                SettingsSection(title = "Appearance") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            ToggleRow(
                                icon = AppIcons.Default.VisibilityOff,
                                title = "Dim read bookmarks",
                                checked = layout.dimReadBookmarks,
                                onCheckedChange = { screenModel.updateDimReadBookmarks(it) }
                            )
                            if (getPlatform().isDesktop) {
                                HorizontalDivider()
                                LayoutRadioOption(
                                    title = "Quick actions — Left",
                                    description = "Quick action buttons on the left side",
                                    icon = AppIcons.AutoMirrored.Filled.ViewList,
                                    isSelected = layout.quickActionPosition == QuickActionPosition.LEFT.name,
                                    onClick = { screenModel.updateQuickActionPosition(QuickActionPosition.LEFT) }
                                )
                                HorizontalDivider()
                                LayoutRadioOption(
                                    title = "Quick actions — Right",
                                    description = "Quick action buttons on the right side",
                                    icon = AppIcons.AutoMirrored.Filled.ViewList,
                                    isSelected = layout.quickActionPosition == QuickActionPosition.RIGHT.name,
                                    onClick = { screenModel.updateQuickActionPosition(QuickActionPosition.RIGHT) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(56.dp))
            }
        }
    }
}

@Composable
private fun PreviewBookmarkItem(layout: BookmarkLayout) {
    // The preview pane's own width, so an auto line count previews the way it will render.
    BoxWithConstraints {
    val previewRowWidth = maxWidth
    val layoutType = LayoutType.fromString(layout.layoutType)
    val dateMode = DateDisplayMode.fromString(layout.dateDisplayMode)
    val thumbnailSide = ThumbnailSide.fromString(layout.thumbnailSide)
    val metadataPos = MetadataPosition.fromString(layout.metadataPosition)
    val descriptionPos = DescriptionPosition.fromString(layout.descriptionPosition)
    val urlPos = UrlPosition.fromString(layout.urlPosition)
    val urlMode = UrlDisplayMode.fromString(layout.urlDisplayMode)
    val urlIconMode = UrlIconMode.fromString(layout.urlIconMode)
    val previewThumbnail = painterResource(Res.drawable.preview_thumbnail)
    when (layoutType) {
        LayoutType.CARD -> BookmarkCardLayout(
            bookmark = PREVIEW_BOOKMARK,
            onClick = {},
            showReadingTime = layout.showReadingTime,
            showReadingProgress = false,
            showTags = layout.showTags,
            showDate = layout.showDate,
            dateDisplayMode = dateMode,
            dimRead = false,
            offlineMode = false,
            bannerImageUrl = null,
            thumbnailPainter = previewThumbnail,
            tagsScrollable = layout.tagsScrollable,
            showDescription = layout.showDescription,
            showUrl = layout.showUrl,
            urlDisplayMode = urlMode,
            urlPosition = urlPos,
            urlIconMode = urlIconMode,
            faviconByLinkSize = layout.faviconByLinkSize
        )
        LayoutType.LIST -> BookmarkListLayout(
            bookmark = PREVIEW_BOOKMARK,
            onClick = {},
            showReadingTime = layout.showReadingTime,
            showReadingProgress = false,
            showTags = layout.showTags,
            showDate = layout.showDate,
            dateDisplayMode = dateMode,
            dimRead = false,
            offlineMode = false,
            bannerImageUrl = null,
            thumbnailPainter = previewThumbnail,
            thumbnailSide = thumbnailSide,
            showFavicon = layout.showFavicon,
            thumbnailSize = layout.thumbnailSize,
            metadataPosition = metadataPos,
            tagsScrollable = layout.tagsScrollable,
            showDescription = layout.showDescription,
            descriptionPosition = descriptionPos,
            showUrl = layout.showUrl,
            urlDisplayMode = urlMode,
            urlPosition = urlPos,
            urlIconMode = urlIconMode,
            faviconByLinkSize = layout.faviconByLinkSize,
            showThumbnail = layout.showThumbnail,
            itemContainerStyle = ItemContainerStyle.fromString(layout.itemContainerStyle),
            readIndicatorStyle = ReadIndicatorStyle.fromString(layout.readIndicatorStyle),
            showRowDivider = layout.showRowDivider,
            titlePosition = TitlePosition.fromString(layout.titlePosition),
            descriptionMaxLines = layout.descriptionMaxLines,
            rowWidth = previewRowWidth
        )
        @Suppress("DEPRECATION")
        LayoutType.COMPACT_LIST -> BookmarkListLayout(
            bookmark = PREVIEW_BOOKMARK,
            onClick = {},
            showReadingTime = layout.showReadingTime,
            showReadingProgress = false,
            showTags = layout.showTags,
            showDate = layout.showDate,
            dateDisplayMode = dateMode,
            dimRead = false,
            offlineMode = false,
            bannerImageUrl = null,
            thumbnailPainter = previewThumbnail,
            thumbnailSide = thumbnailSide,
            showFavicon = layout.showFavicon,
            thumbnailSize = layout.thumbnailSize,
            metadataPosition = metadataPos,
            tagsScrollable = layout.tagsScrollable,
            showDescription = layout.showDescription,
            descriptionPosition = descriptionPos,
            showUrl = layout.showUrl,
            urlDisplayMode = urlMode,
            urlPosition = urlPos,
            urlIconMode = urlIconMode,
            faviconByLinkSize = layout.faviconByLinkSize,
            showThumbnail = layout.showThumbnail,
            itemContainerStyle = ItemContainerStyle.fromString(layout.itemContainerStyle),
            readIndicatorStyle = ReadIndicatorStyle.fromString(layout.readIndicatorStyle),
            showRowDivider = layout.showRowDivider,
            titlePosition = TitlePosition.fromString(layout.titlePosition),
            descriptionMaxLines = layout.descriptionMaxLines,
            rowWidth = previewRowWidth
        )
    }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun LayoutRadioOption(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        RadioButton(selected = isSelected, onClick = onClick)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SubsectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SimpleRadioOption(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    description: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(text = description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ToggleRowWithDescription(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
