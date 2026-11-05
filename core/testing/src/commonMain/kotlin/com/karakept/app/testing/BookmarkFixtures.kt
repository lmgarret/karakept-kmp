package com.karakept.app.testing

import com.karakept.app.data.model.Bookmark

/**
 * A domain [Bookmark] with every field defaulted, derived from [localId] where a test is
 * likely to tell two bookmarks apart. Pass only what the test is about.
 */
fun testBookmark(
    localId: Long = 1L,
    remoteId: String = "remote-$localId",
    serverId: String = "server-1",
    url: String = "https://example.com/$localId",
    title: String = "Bookmark $localId",
    content: String? = null,
    imageUrl: String? = null,
    bannerImageAssetId: String? = null,
    screenshotAssetId: String? = null,
    description: String? = null,
    createdAt: Long = localId,
    isArchived: Boolean = false,
    isStarred: Boolean = false,
    isRead: Boolean = false,
    tags: String = "",
    listIds: String = "",
    readingTimeMinutes: Int = 0,
    readingProgress: Float = 0f,
    readingScrollIndex: Int = 0,
    readingScrollOffset: Int = 0,
    crawlStatus: String? = null,
    crawledAt: Long? = null,
    summary: String? = null,
    summarizationStatus: String? = null,
) = Bookmark(
    localId = localId,
    remoteId = remoteId,
    serverId = serverId,
    url = url,
    title = title,
    content = content,
    imageUrl = imageUrl,
    bannerImageAssetId = bannerImageAssetId,
    screenshotAssetId = screenshotAssetId,
    description = description,
    createdAt = createdAt,
    isArchived = isArchived,
    isStarred = isStarred,
    isRead = isRead,
    tags = tags,
    listIds = listIds,
    readingTimeMinutes = readingTimeMinutes,
    readingProgress = readingProgress,
    readingScrollIndex = readingScrollIndex,
    readingScrollOffset = readingScrollOffset,
    crawlStatus = crawlStatus,
    crawledAt = crawledAt,
    summary = summary,
    summarizationStatus = summarizationStatus,
)
