package com.karakept.app.ui.components.reader

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.components.LoadingDotsIndicator
import com.karakept.app.ui.screens.viewer.BookmarkLoadingState
import com.karakept.app.ui.theme.LocalEinkMode
import kotlin.random.Random

/**
 * Skeleton screens for each loading stage with shimmer animations.
 *
 * The shimmer is a continuous animation, which on e-ink means either permanent ghosting or
 * nothing visible at all (its tonal fill collapses into the page color under high contrast), so
 * e-ink swaps the whole skeleton for [LoadingDotsIndicator] instead of just changing the shimmer's
 * brush.
 */
@Composable
fun BookmarkContentLoader(
    loadingState: BookmarkLoadingState,
    modifier: Modifier = Modifier,
    showBanner: Boolean = true
) {
    when (loadingState) {
        is BookmarkLoadingState.Initial -> {
            if (LocalEinkMode.current.animationsDisabled) {
                Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingDotsIndicator(label = "Loading…")
                }
            } else {
                // Full skeleton for everything
                Column(modifier = modifier.fillMaxWidth()) {
                    // Banner skeleton (omitted when the real hero is already shown above)
                    if (showBanner) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                        )
                    }

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
        }
        else -> {
            // Should not render - parent handles FullyLoaded and Error
        }
    }
}

/**
 * Skeleton for reopening an article at a saved reading position: the screen will land somewhere
 * in the body, so it shows nothing but paragraphs of text — no banner, no URL line — filling the
 * whole viewport from just under the top bar ([topInset]).
 */
@Composable
fun ReaderResumeSkeleton(
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp
) {
    if (LocalEinkMode.current.animationsDisabled) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LoadingDotsIndicator(label = "Loading…")
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .padding(start = 16.dp, end = 16.dp, top = topInset + 16.dp)
    ) {
        // More than a tall screen holds; clipToBounds drops the overflow.
        val paragraphs = remember { resumeSkeletonParagraphs(Random.Default) }
        paragraphs.forEach { lineWidths ->
            lineWidths.forEach { width ->
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(width)
                        .height(20.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private const val RESUME_SKELETON_PARAGRAPHS = 12

/**
 * Line widths (fractions of the column) for each skeleton paragraph, so the paragraphs read as
 * prose of uneven length rather than one block repeated: 1–6 lines, near-full lines that wrap a
 * little short, and a last line anywhere from a few words to almost full.
 */
internal fun resumeSkeletonParagraphs(random: Random): List<List<Float>> =
    List(RESUME_SKELETON_PARAGRAPHS) {
        val lines = random.nextInt(1, 7)
        List(lines) { line ->
            if (line == lines - 1) random.nextDouble(0.25, 0.9).toFloat()
            else random.nextDouble(0.9, 1.0).toFloat()
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
