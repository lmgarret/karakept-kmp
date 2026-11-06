package com.karakept.app.ui.screens.settings

import getPlatform
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.karakept.app.ui.icons.AppIcons

@Composable
internal fun LayoutInfoSection(layout: BookmarkLayout, screenModel: LayoutEditorScreenModel) {
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
}

@Composable
internal fun LayoutTypeSection(layout: BookmarkLayout, screenModel: LayoutEditorScreenModel) {
    val isList = layout.layoutType != LayoutType.CARD.name
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
}

@Composable
internal fun LayoutRowStyleSection(layout: BookmarkLayout, screenModel: LayoutEditorScreenModel) {
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

@Composable
internal fun LayoutThumbnailSection(layout: BookmarkLayout, screenModel: LayoutEditorScreenModel) {
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

@Composable
internal fun LayoutDescriptionSection(layout: BookmarkLayout, screenModel: LayoutEditorScreenModel) {
    val isList = layout.layoutType != LayoutType.CARD.name
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
}

@Composable
internal fun LayoutLinkSection(layout: BookmarkLayout, screenModel: LayoutEditorScreenModel) {
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
}

@Composable
internal fun LayoutFaviconSection(layout: BookmarkLayout, screenModel: LayoutEditorScreenModel) {
    val isList = layout.layoutType != LayoutType.CARD.name
    val urlIconMode = UrlIconMode.fromString(layout.urlIconMode)
    val faviconEnabled = layout.showFavicon || urlIconMode != UrlIconMode.NONE
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
}

@Composable
internal fun LayoutDateSection(layout: BookmarkLayout, screenModel: LayoutEditorScreenModel) {
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
}

@Composable
internal fun LayoutReadingTimeSection(layout: BookmarkLayout, screenModel: LayoutEditorScreenModel) {
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
}

@Composable
internal fun LayoutTagsSection(layout: BookmarkLayout, screenModel: LayoutEditorScreenModel) {
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
}

@Composable
internal fun LayoutMetadataPositionSection(layout: BookmarkLayout, screenModel: LayoutEditorScreenModel) {
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

@Composable
internal fun LayoutAppearanceSection(layout: BookmarkLayout, screenModel: LayoutEditorScreenModel) {
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
}
