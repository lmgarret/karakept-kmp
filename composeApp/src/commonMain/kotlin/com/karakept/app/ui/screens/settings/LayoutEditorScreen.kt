package com.karakept.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.outlined.MenuBook
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
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.data.model.BookmarkLayout
import com.karakept.app.data.model.LayoutType
import com.karakept.app.data.model.MetadataPosition
import com.karakept.app.data.model.QuickActionPosition
import com.karakept.app.data.model.ThumbnailSide
import getPlatform
import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.data.repository.saveLayout
import com.karakept.app.ui.components.BookmarkCardLayout

import com.karakept.app.ui.components.BookmarkListLayout
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LayoutEditorScreenModel(
    private val settingsRepository: SettingsRepository
) : ScreenModel {

    private val _layout = MutableStateFlow(
        BookmarkLayout(
            id = generateId(),
            name = ""
        )
    )
    val layout: StateFlow<BookmarkLayout> = _layout.asStateFlow()

    fun loadExisting(id: String) {
        screenModelScope.launch {
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
    fun updateShowFavicon(show: Boolean) { _layout.value = _layout.value.copy(showFavicon = show) }
    fun updateThumbnailSize(size: Int) { _layout.value = _layout.value.copy(thumbnailSize = size) }
    fun updateMetadataPosition(position: MetadataPosition) { _layout.value = _layout.value.copy(metadataPosition = position.name) }
    fun updateTagsScrollable(v: Boolean) { _layout.value = _layout.value.copy(tagsScrollable = v) }
    fun updateQuickActionPosition(pos: QuickActionPosition) { _layout.value = _layout.value.copy(quickActionPosition = pos.name) }

    fun save() {
        screenModelScope.launch {
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
    remoteId = -1L,
    originalRemoteId = "preview",
    serverId = "preview",
    url = "https://example.com/article",
    title = "Example Article Title",
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

data class LayoutEditorScreen(val layoutId: String?) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<LayoutEditorScreenModel>()

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
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
                verticalArrangement = Arrangement.Top
            ) {
                // Layout name + description
                Text(
                    text = "Layout Info",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
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

                Spacer(modifier = Modifier.height(24.dp))

                // Layout
                Text(
                    text = "Layout",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        LayoutRadioOption(
                            title = "Card",
                            description = "Large hero images with title below",
                            icon = Icons.Default.Window,
                            isSelected = layout.layoutType == LayoutType.CARD.name,
                            onClick = { screenModel.updateLayoutType(LayoutType.CARD) }
                        )
                        HorizontalDivider()
                        LayoutRadioOption(
                            title = "List",
                            description = "Thumbnails with title and description",
                            icon = Icons.AutoMirrored.Filled.ViewList,
                            isSelected = layout.layoutType == LayoutType.LIST.name,
                            onClick = { screenModel.updateLayoutType(LayoutType.LIST) }
                        )
                        HorizontalDivider()
                        LayoutRadioOption(
                            title = "Compact List",
                            description = "Small thumbnails with just the title and date",
                            icon = Icons.AutoMirrored.Filled.List,
                            isSelected = layout.layoutType == LayoutType.COMPACT_LIST.name,
                            onClick = { screenModel.updateLayoutType(LayoutType.COMPACT_LIST) }
                        )
                    }
                }

                // Thumbnail settings — only for LIST and COMPACT_LIST
                if (layout.layoutType != LayoutType.CARD.name) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Thumbnail",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            // Thumbnail Position
                            LayoutRadioOption(
                                title = "Left",
                                description = "Thumbnail on the left side",
                                icon = Icons.AutoMirrored.Filled.ViewList,
                                isSelected = layout.thumbnailSide == ThumbnailSide.LEFT.name,
                                onClick = { screenModel.updateThumbnailSide(ThumbnailSide.LEFT) }
                            )
                            HorizontalDivider()
                            LayoutRadioOption(
                                title = "Right",
                                description = "Thumbnail on the right side",
                                icon = Icons.AutoMirrored.Filled.ViewList,
                                isSelected = layout.thumbnailSide == ThumbnailSide.RIGHT.name,
                                onClick = { screenModel.updateThumbnailSide(ThumbnailSide.RIGHT) }
                            )
                            HorizontalDivider()
                            // Thumbnail Size slider
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
                                            imageVector = Icons.Default.Photo,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 16.dp)
                                        )
                                        Text(
                                            text = "Size",
                                            style = MaterialTheme.typography.titleMedium
                                        )
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

                Spacer(modifier = Modifier.height(24.dp))

                // What to show
                Text(
                    text = "Show",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ToggleRow(
                            icon = Icons.Outlined.MenuBook,
                            title = "Reading Time",
                            checked = layout.showReadingTime,
                            onCheckedChange = { screenModel.updateShowReadingTime(it) }
                        )
                        HorizontalDivider()
                        ToggleRow(
                            icon = Icons.Default.CalendarToday,
                            title = "Date",
                            checked = layout.showDate,
                            onCheckedChange = { screenModel.updateShowDate(it) }
                        )
                        HorizontalDivider()
                        ToggleRow(
                            icon = Icons.Default.Label,
                            title = "Tags",
                            checked = layout.showTags,
                            onCheckedChange = { screenModel.updateShowTags(it) }
                        )
                        if (layout.showTags) {
                            HorizontalDivider()
                            ToggleRow(
                                icon = Icons.AutoMirrored.Filled.List,
                                title = "Scrollable Tags",
                                checked = layout.tagsScrollable,
                                onCheckedChange = { screenModel.updateTagsScrollable(it) }
                            )
                        }
                        HorizontalDivider()
                        ToggleRow(
                            icon = Icons.Default.Language,
                            title = "Favicon",
                            checked = layout.showFavicon,
                            onCheckedChange = { screenModel.updateShowFavicon(it) }
                        )
                    }
                }

                // Metadata position — only for LIST and COMPACT_LIST
                if (layout.layoutType != LayoutType.CARD.name) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Metadata Position",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            LayoutRadioOption(
                                title = "Below",
                                description = "Tags, date and reading time below thumbnail and text",
                                icon = Icons.AutoMirrored.Filled.List,
                                isSelected = layout.metadataPosition == MetadataPosition.BELOW.name,
                                onClick = { screenModel.updateMetadataPosition(MetadataPosition.BELOW) }
                            )
                            HorizontalDivider()
                            LayoutRadioOption(
                                title = "Beside",
                                description = "Tags, date and reading time beside the thumbnail, under the title",
                                icon = Icons.Default.VerticalSplit,
                                isSelected = layout.metadataPosition == MetadataPosition.BESIDE.name,
                                onClick = { screenModel.updateMetadataPosition(MetadataPosition.BESIDE) }
                            )
                            HorizontalDivider()
                            LayoutRadioOption(
                                title = "Above",
                                description = "Tags, date and reading time above the thumbnail and text",
                                icon = Icons.AutoMirrored.Filled.List,
                                isSelected = layout.metadataPosition == MetadataPosition.ABOVE.name,
                                onClick = { screenModel.updateMetadataPosition(MetadataPosition.ABOVE) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Customization
                Text(
                    text = "Customization",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ToggleRow(
                            icon = Icons.Default.VisibilityOff,
                            title = "Dim Read Bookmarks",
                            checked = layout.dimReadBookmarks,
                            onCheckedChange = { screenModel.updateDimReadBookmarks(it) }
                        )
                    }
                }

                // Quick Action Position — desktop only
                if (getPlatform().isDesktop) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Quick Action Position",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            LayoutRadioOption(
                                title = "Left",
                                description = "Quick action buttons on the left side",
                                icon = Icons.AutoMirrored.Filled.ViewList,
                                isSelected = layout.quickActionPosition == QuickActionPosition.LEFT.name,
                                onClick = { screenModel.updateQuickActionPosition(QuickActionPosition.LEFT) }
                            )
                            HorizontalDivider()
                            LayoutRadioOption(
                                title = "Right",
                                description = "Quick action buttons on the right side",
                                icon = Icons.AutoMirrored.Filled.ViewList,
                                isSelected = layout.quickActionPosition == QuickActionPosition.RIGHT.name,
                                onClick = { screenModel.updateQuickActionPosition(QuickActionPosition.RIGHT) }
                            )
                        }
                    }
                }

                if (layout.showDate) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { screenModel.updateDateDisplayMode(DateDisplayMode.ELAPSED) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = layout.dateDisplayMode == DateDisplayMode.ELAPSED.name,
                                    onClick = { screenModel.updateDateDisplayMode(DateDisplayMode.ELAPSED) }
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text("Relative date", style = MaterialTheme.typography.bodyLarge)
                                    Text("e.g., 33m ago, 2h ago", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (layout.dateDisplayMode == DateDisplayMode.ELAPSED.name) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { screenModel.updateDateDisplayMode(DateDisplayMode.ABSOLUTE) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = layout.dateDisplayMode == DateDisplayMode.ABSOLUTE.name,
                                    onClick = { screenModel.updateDateDisplayMode(DateDisplayMode.ABSOLUTE) }
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text("Absolute date", style = MaterialTheme.typography.bodyLarge)
                                    Text("e.g., 2024-01-15", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (layout.dateDisplayMode == DateDisplayMode.ABSOLUTE.name) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun PreviewBookmarkItem(layout: BookmarkLayout) {
    val layoutType = LayoutType.fromString(layout.layoutType)
    val dateMode = DateDisplayMode.fromString(layout.dateDisplayMode)
    val thumbnailSide = ThumbnailSide.fromString(layout.thumbnailSide)
    val metadataPos = MetadataPosition.fromString(layout.metadataPosition)
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
            tagsScrollable = layout.tagsScrollable
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
            thumbnailSide = thumbnailSide,
            showFavicon = layout.showFavicon,
            thumbnailSize = layout.thumbnailSize,
            metadataPosition = metadataPos,
            tagsScrollable = layout.tagsScrollable
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
            thumbnailSide = thumbnailSide,
            showFavicon = layout.showFavicon,
            thumbnailSize = layout.thumbnailSize,
            metadataPosition = metadataPos,
            tagsScrollable = layout.tagsScrollable
        )
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
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
