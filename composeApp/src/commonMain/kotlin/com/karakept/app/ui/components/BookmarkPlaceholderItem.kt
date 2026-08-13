package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.LayoutType

/**
 * A row for a bookmark that was just saved and is still being fetched — the URL is real, the rest
 * of the content isn't in yet. One [LoadingDotsIndicator] communicates that, rather than a set of
 * shimmering boxes shaped like a title and thumbnail that don't exist yet.
 */
@Composable
fun BookmarkPlaceholderItem(url: String, layoutType: LayoutType = LayoutType.LIST) {
    val height = if (layoutType == LayoutType.CARD) 96.dp else 80.dp
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LoadingDotsIndicator(dotSize = 8.dp)
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
