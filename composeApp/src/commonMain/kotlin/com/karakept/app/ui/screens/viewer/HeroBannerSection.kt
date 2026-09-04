package com.karakept.app.ui.screens.viewer

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.ui.components.HeroImageBanner

@Composable
internal fun HeroBannerSection(
    title: String,
    url: String,
    tags: String,
    readingTimeMinutes: Int,
    showTags: Boolean,
    scrollState: LazyListState,
    createdAt: Long? = null,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.ELAPSED,
    onUrlClick: (() -> Unit)?,
    onTagClick: ((String) -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    bannerImageUrl: String? = null,
    screenshotUrl: String? = null,
    bannerImageLocalPath: String? = null,
    screenshotLocalPath: String? = null,
    showImage: Boolean = true,
    onImageClick: (() -> Unit)? = null
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
        createdAt = createdAt,
        dateDisplayMode = dateDisplayMode,
        onUrlClick = onUrlClick,
        onTagClick = onTagClick,
        onInfoClick = onInfoClick,
        bannerImageUrl = bannerImageUrl,
        screenshotUrl = screenshotUrl,
        bannerImageLocalPath = bannerImageLocalPath,
        screenshotLocalPath = screenshotLocalPath,
        showImage = showImage,
        onImageClick = onImageClick
    )
}
