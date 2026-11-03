package com.karakept.app.data.local.entity

import com.karakept.app.data.model.Bookmark

// A field-by-field copy: strings, the article body included, are shared by reference.
internal fun BookmarkEntity.toBookmark(): Bookmark = Bookmark(
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
    summarizationStatus = summarizationStatus
)

internal fun List<BookmarkEntity>.toBookmarks(): List<Bookmark> = map { it.toBookmark() }
