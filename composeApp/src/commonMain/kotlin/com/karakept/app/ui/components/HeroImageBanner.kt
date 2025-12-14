package com.karakept.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

/**
 * Extracts the domain name from a full URL.
 * Removes protocol (http/https), www prefix, and path.
 */
private fun extractDomain(url: String): String {
    return try {
        val urlWithoutProtocol = url
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")

        // Take everything before the first slash
        urlWithoutProtocol.substringBefore("/")
    } catch (e: Exception) {
        url  // Fallback to full URL if parsing fails
    }
}

/**
 * Material You hero banner with image and title overlay.
 *
 * Features:
 * - Full-width banner at 280dp height
 * - AsyncImage with Coil for thumbnail
 * - Gradient scrim overlay for title legibility
 * - Title positioned at bottom with padding
 * - Fallback: Colored surface with emoji
 */
@Composable
fun HeroImageBanner(
    imageUrl: String?,
    title: String,
    url: String? = null,
    scrollProgress: Float = 0f,
    onUrlClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp) // Increased height for better parallax effect
    ) {
        if (imageUrl != null) {
            // Image background
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Gradient scrim for title legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f) // Darker scrim for better text contrast
                            ),
                            startY = 100f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
        } else {
            // Fallback: colored surface with icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📰",
                    style = MaterialTheme.typography.displayLarge
                )
            }
        }

        // Title and metadata at bottom with scale effect
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .graphicsLayer {
                    // Scale from 1.0 to 0.85 as user scrolls
                    val scale = 1f - (scrollProgress * 0.15f)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f) // Scale from bottom-left
                }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White, // Always white on image/scrim
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (url != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .then(
                            if (onUrlClick != null) {
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onUrlClick)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            } else {
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            }
                        )
                ) {
                    AsyncImage(
                        model = com.karakept.app.utils.FaviconUtils.getFaviconUrl(url),
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = extractDomain(url),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
