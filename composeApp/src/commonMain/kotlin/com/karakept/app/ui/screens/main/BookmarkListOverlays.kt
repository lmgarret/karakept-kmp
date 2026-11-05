package com.karakept.app.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.SyncProgress
import com.karakept.app.ui.components.AnimatedVisibilityOrPlain
import com.karakept.app.ui.components.EinkAwareSmallFab
import com.karakept.app.ui.components.FloatingBusyCard
import com.karakept.app.ui.components.LoadingDotsIndicator
import com.karakept.app.ui.components.borderStroke
import com.karakept.app.ui.components.floatingSurfaceStyle
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.theme.LocalEinkMode

/** "N new bookmarks" pill — tap to jump to the newly synced items at the top. */
@Composable
internal fun NewBookmarksPill(
    visible: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val einkMode = LocalEinkMode.current
    val pillStyle = floatingSurfaceStyle(4.dp)
    AnimatedVisibilityOrPlain(
        visible = visible,
        animated = !einkMode.animationsDisabled,
        durationMillis = 200,
        modifier = modifier
    ) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            // `primary` is solid ink on the e-ink scheme. A filled pill would read, but it
            // lays down — and then ghosts — a block of ink for a transient prompt; an
            // outlined one on the page colour says the same thing for a fraction of it.
            color = if (pillStyle.outlined) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = if (pillStyle.outlined) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            border = pillStyle.borderStroke(),
            shadowElevation = pillStyle.shadowElevation
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Default.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (count == 1) "1 new bookmark" else "$count new bookmarks",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
internal fun ScrollToTopFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibilityOrPlain(
        visible = visible,
        animated = !LocalEinkMode.current.animationsDisabled,
        durationMillis = 300,
        modifier = modifier
    ) {
        EinkAwareSmallFab(onClick = onClick) {
            Icon(
                imageVector = AppIcons.Default.ArrowUpward,
                contentDescription = "Scroll to top"
            )
        }
    }
}

@Composable
internal fun SyncProgressBar(
    progress: SyncProgress?,
    modifier: Modifier = Modifier
) {
    // Only the *indeterminate* bar is a problem on e-ink: it animates continuously. The
    // determinate one redraws once per progress change, which the panel handles fine.
    val einkMode = LocalEinkMode.current
    val fetched = (progress as? SyncProgress.FetchingContent)
        ?.takeIf { it.total > 0 }

    if (einkMode.animationsDisabled) {
        // A strip at the top edge of the list is easy to overlook on a monochrome panel, so the
        // whole sync state moves into one card in the middle of the page — determinate or not,
        // rather than splitting it across two places.
        FloatingBusyCard(modifier = modifier) {
            if (fetched != null) {
                Text(
                    text = "Syncing…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    progress = { fetched.current.toFloat() / fetched.total.toFloat() },
                    modifier = Modifier.width(SyncCardProgressWidth)
                )
                Text(
                    text = "${fetched.current} / ${fetched.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LoadingDotsIndicator(label = "Syncing…", dotSize = 8.dp)
            }
        }
        return
    }

    Column(modifier = modifier) {
        if (fetched != null) {
            LinearProgressIndicator(
                progress = { fetched.current.toFloat() / fetched.total.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${fetched.current} / ${fetched.total}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Wide enough to read as a progress bar, narrow enough to keep the card off the page edges. */
private val SyncCardProgressWidth = 160.dp

@Composable
internal fun EmptyBookmarkList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = AppIcons.Default.BookmarkBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Nothing here yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Bookmarks you save will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
