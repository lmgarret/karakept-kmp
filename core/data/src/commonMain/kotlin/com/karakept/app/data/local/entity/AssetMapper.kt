package com.karakept.app.data.local.entity

import com.karakept.app.data.model.BookmarkAsset

// Lossless both ways: the UI hands an asset back to be downloaded or deleted, and the row is
// rewritten from it.
internal fun AssetEntity.toAsset() = BookmarkAsset(
    id = id,
    bookmarkRemoteId = bookmarkRemoteId,
    serverId = serverId,
    assetType = assetType,
    fileName = fileName,
    contentType = contentType,
    localPath = localPath
)

internal fun BookmarkAsset.toEntity() = AssetEntity(
    id = id,
    bookmarkRemoteId = bookmarkRemoteId,
    serverId = serverId,
    assetType = assetType,
    fileName = fileName,
    contentType = contentType,
    localPath = localPath
)
