package com.karakept.app.utils

import com.karakept.app.data.model.DateDisplayMode
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/** Parses an ISO-8601 timestamp from the API into epoch millis, or null if absent/malformed. */
fun parseIsoToEpochMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        Instant.parse(iso).toEpochMilliseconds()
    } catch (e: Exception) {
        null
    }
}

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
            diffMs < 365 * 86_400_000L -> "${diffMs / (30 * 86_400_000L)}mo ago"
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
        "${localDate.year}-${localDate.month.number.toString().padStart(2, '0')}-${localDate.day.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        "Unknown"
    }
}
