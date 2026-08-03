package com.karakept.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karakept.app.data.model.LayoutType

/**
 * Placeholder rows shown while the first page is still being read from the database.
 *
 * Without this a cold start renders an empty list, which is indistinguishable from a
 * genuinely empty one — the app looks like it has lost the user's bookmarks until the
 * query lands.
 */
@Composable
fun BookmarkListSkeleton(
    layoutType: LayoutType = LayoutType.LIST,
    count: Int = 6,
    modifier: Modifier = Modifier
) {
    val shimmerColor = rememberBookmarkShimmerColor()
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(count) {
            when (layoutType) {
                LayoutType.CARD -> CardSkeleton(shimmerColor)
                else -> ListSkeleton(shimmerColor)
            }
        }
    }
}

@Composable
private fun ListSkeleton(shimmerColor: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                ShimmerBar(shimmerColor, widthFraction = 0.85f, height = 18.dp)
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBar(shimmerColor, widthFraction = 0.6f, height = 14.dp)
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBar(shimmerColor, widthFraction = 0.35f, height = 12.dp)
            }
        }
    }
}

@Composable
private fun CardSkeleton(shimmerColor: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp).background(shimmerColor)
            )
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                ShimmerBar(shimmerColor, widthFraction = 0.85f, height = 18.dp)
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBar(shimmerColor, widthFraction = 0.5f, height = 14.dp)
            }
        }
    }
}

@Composable
private fun ShimmerBar(color: Color, widthFraction: Float, height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
    )
}
