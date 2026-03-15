package com.karakept.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class TagsDisplayStyle {
    COMPACT,  // Main screen cards/lists
    READER    // Reader mode banner
}

/**
 * Displays bookmark tags as chips in a flow layout or a single scrollable row.
 *
 * @param tags Comma-separated tag string from BookmarkEntity
 * @param style Visual style variant (COMPACT for main screen, READER for banner)
 * @param scrollable When true, renders tags in a single horizontally-scrollable line
 * @param modifier Modifier for the container
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookmarkTagsDisplay(
    tags: String,
    style: TagsDisplayStyle = TagsDisplayStyle.COMPACT,
    onTagClick: ((String) -> Unit)? = null,
    scrollable: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Parse tags and filter empty strings
    val tagList = tags.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    // Don't render if no tags
    if (tagList.isEmpty()) {
        return
    }

    if (scrollable) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tagList) { tag ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small,
                    shadowElevation = 2.dp,
                    modifier = if (onTagClick != null) Modifier.clickable { onTagClick(tag) } else Modifier
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    } else {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tagList.forEach { tag ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small,
                    shadowElevation = 2.dp,
                    modifier = if (onTagClick != null) Modifier.clickable { onTagClick(tag) } else Modifier
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
