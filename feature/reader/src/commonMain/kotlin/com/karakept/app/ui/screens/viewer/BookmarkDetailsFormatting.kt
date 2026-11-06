package com.karakept.app.ui.screens.viewer

import com.karakept.app.data.model.DateDisplayMode
import com.karakept.app.utils.formatBookmarkDate
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/** One-line summary of the server's crawl state, e.g. "Failed · 3d ago". */
internal fun crawlSummary(crawlStatus: String?, crawledAt: Long?): String {
    val state = when (crawlStatus) {
        "success" -> "Success"
        "failure" -> "Failed"
        "pending" -> "Pending"
        null -> null
        else -> crawlStatus.replaceFirstChar { it.uppercase() }
    }
    val when_ = crawledAt?.let { formatBookmarkDate(it, DateDisplayMode.ELAPSED) }
    return listOfNotNull(state, when_).joinToString(" · ").ifEmpty { "Unknown" }
}

internal fun serverAssetLabel(assetType: String): String = when (assetType) {
    "fullPageArchive" -> "The full page archive"
    "precrawledArchive" -> "The crawled archive"
    "pdf" -> "The PDF"
    "bannerImage" -> "The banner image"
    "screenshot" -> "The screenshot"
    else -> "This asset"
}

internal fun formatEpochMillis(epochMillis: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${localDate.year}-${localDate.month.number.toString().padStart(2, '0')}-${localDate.day.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        "Unknown"
    }
}
