package com.karakept.app.utils

import com.karakept.app.data.model.DateDisplayMode
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun formatBookmarkDate(epochMillis: Long, mode: DateDisplayMode): String {
    return when (mode) {
        DateDisplayMode.ELAPSED -> formatElapsed(epochMillis)
        DateDisplayMode.ABSOLUTE -> formatAbsolute(epochMillis)
    }
}

private fun formatElapsed(epochMillis: Long): String {
    return try {
        val now = Clock.System.now().toEpochMilliseconds()
        val diffMs = now - epochMillis
        when {
            diffMs < 60_000L -> "just now"
            diffMs < 3_600_000L -> "${diffMs / 60_000}m ago"
            diffMs < 86_400_000L -> "${diffMs / 3_600_000}h ago"
            diffMs < 7 * 86_400_000L -> "${diffMs / 86_400_000}d ago"
            diffMs < 30 * 86_400_000L -> "${diffMs / (7 * 86_400_000)}w ago"
            diffMs < 365 * 86_400_000L -> "${diffMs / (30 * 86_400_000)}mo ago"
            else -> formatAbsolute(epochMillis)
        }
    } catch (e: Exception) {
        formatAbsolute(epochMillis)
    }
}

fun formatAbsolute(epochMillis: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${localDate.year}-${localDate.monthNumber.toString().padStart(2, '0')}-${localDate.dayOfMonth.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        "Unknown"
    }
}
