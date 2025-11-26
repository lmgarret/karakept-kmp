package com.karakept.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.screens.BookmarkLoadingState

/**
 * Skeleton screens for each loading stage with shimmer animations.
 */
@Composable
fun BookmarkContentLoader(
    loadingState: BookmarkLoadingState,
    modifier: Modifier = Modifier
) {
    when (loadingState) {
        is BookmarkLoadingState.Initial -> {
            // Full skeleton for everything
            Column(modifier = modifier.fillMaxWidth()) {
                // Banner skeleton
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )

                // Content area skeleton
                Column(modifier = Modifier.padding(16.dp)) {
                    // URL skeleton
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Content lines skeleton
                    repeat(5) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        else -> {
            // Should not render - parent handles FullyLoaded and Error
        }
    }
}

/**
 * Shimmer effect box for loading skeletons.
 */
@Composable
private fun ShimmerBox(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(shimmerColor.copy(alpha = shimmerAlpha))
    )
}
