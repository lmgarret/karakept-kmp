package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.karakept.app.ui.components.HeroImageBanner

@Composable
internal fun HeroBannerSection(
    title: String,
    url: String,
    tags: String,
    readingTimeMinutes: Int,
    showTags: Boolean,
    scrollState: LazyListState,
    bannerHeight: Dp,
    onUrlClick: (() -> Unit)?,
    bannerImageUrl: String? = null,
    screenshotUrl: String? = null,
    bannerImageLocalPath: String? = null,
    screenshotLocalPath: String? = null
) {
    if (scrollState.firstVisibleItemIndex == 0) {
        Box(
            modifier = Modifier
                .height(bannerHeight)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = -scrollState.firstVisibleItemScrollOffset * 0.5f
                    alpha = 1f - (scrollState.firstVisibleItemScrollOffset / 1000f).coerceIn(0f, 1f)
                }
        ) {
            HeroImageBanner(
                title = title,
                url = url,
                tags = tags,
                readingTimeMinutes = readingTimeMinutes,
                scrollProgress = (scrollState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 1f),
                showTags = showTags,
                onUrlClick = onUrlClick,
                bannerImageUrl = bannerImageUrl,
                screenshotUrl = screenshotUrl,
                bannerImageLocalPath = bannerImageLocalPath,
                screenshotLocalPath = screenshotLocalPath
            )
        }
    }
}
