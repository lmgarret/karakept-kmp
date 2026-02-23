package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.karakept.app.ui.components.HeroImageBanner

@Composable
internal fun HeroBannerSection(
    title: String,
    url: String,
    tags: String,
    readingTimeMinutes: Int,
    showTags: Boolean,
    scrollState: LazyListState,
    onUrlClick: (() -> Unit)?,
    onTagClick: ((String) -> Unit)? = null,
    bannerImageUrl: String? = null,
    screenshotUrl: String? = null,
    bannerImageLocalPath: String? = null,
    screenshotLocalPath: String? = null
) {
    HeroImageBanner(
        title = title,
        url = url,
        tags = tags,
        readingTimeMinutes = readingTimeMinutes,
        scrollProgress = if (scrollState.firstVisibleItemIndex == 0) {
            (scrollState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 1f)
        } else {
            1f
        },
        showTags = showTags,
        onUrlClick = onUrlClick,
        onTagClick = onTagClick,
        bannerImageUrl = bannerImageUrl,
        screenshotUrl = screenshotUrl,
        bannerImageLocalPath = bannerImageLocalPath,
        screenshotLocalPath = screenshotLocalPath
    )
}
