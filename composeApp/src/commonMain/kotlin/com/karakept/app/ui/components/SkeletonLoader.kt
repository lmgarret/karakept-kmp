package com.karakept.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.theme.LocalEinkMode

@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )

    // A shimmer is an infinite animation — the worst possible case on e-ink, where it never stops
    // requesting full-panel refreshes. Fall back to flat outlined blocks, which still communicate
    // "content is coming" without a single frame of motion.
    val einkMode = LocalEinkMode.current

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        // Ensure we have a valid width, otherwise default to a reasonable value
        val targetValue = if (widthPx > 0) widthPx * 2 else 1000f

        val brush = if (einkMode.animationsDisabled) {
            SolidColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        } else {
            val transition = rememberInfiniteTransition()
            val translateAnim by transition.animateFloat(
                initialValue = 0f,
                targetValue = targetValue,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 1200,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                )
            )
            Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(translateAnim - 200f, translateAnim - 200f),
                end = Offset(translateAnim, translateAnim)
            )
        }
        val blockModifier: Modifier = if (einkMode.highContrast) {
            Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
        } else {
            Modifier
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Title skeleton
            Spacer(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
                    .then(blockModifier)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Paragraph skeletons
            repeat(5) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                        .then(blockModifier)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Image skeleton
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush)
                    .then(blockModifier)
            )
        }
    }
}
