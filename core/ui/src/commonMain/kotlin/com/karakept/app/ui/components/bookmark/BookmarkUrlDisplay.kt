package com.karakept.app.ui.components.bookmark

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.karakept.app.data.model.UrlDisplayMode
import com.karakept.app.data.model.UrlIconMode
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.theme.LocalEinkMode
import com.karakept.app.ui.utils.extractDomain
import com.karakept.app.utils.FaviconUtils

@Composable
internal fun UrlDisplay(
    url: String,
    urlDisplayMode: UrlDisplayMode,
    urlIconMode: UrlIconMode = UrlIconMode.GLOBE_ONLY,
    iconSize: Int = 16,
    faviconPainter: Painter? = null,
    modifier: Modifier = Modifier
) {
    val displayText = when (urlDisplayMode) {
        UrlDisplayMode.DOMAIN_ONLY -> extractDomain(url)
        UrlDisplayMode.FULL_URL -> url
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (urlIconMode != UrlIconMode.NONE) {
            Box(modifier = Modifier.size(iconSize.dp)) {
                if (urlIconMode == UrlIconMode.FAVICON) {
                    if (faviconPainter != null) {
                        Image(
                            painter = faviconPainter,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        val faviconUrl = FaviconUtils.getFaviconUrl(url)
                        if (faviconUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(faviconUrl)
                                    .crossfade(!LocalEinkMode.current.animationsDisabled)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            // Fallback to globe when no favicon URL available
                            Icon(
                                imageVector = AppIcons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // GLOBE_ONLY mode
                    Icon(
                        imageVector = AppIcons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.size(4.dp))
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
