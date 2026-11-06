package com.karakept.app.data.model

/**
 * A non-fatal problem encountered during a sync that would otherwise be swallowed
 * (content download failed, a per-list pass errored, highlights couldn't be fetched).
 */
data class SyncWarning(
    val phase: String,
    val message: String
)

/**
 * Outcome of one sync pipeline run, surfaced so partial failures are visible to the
 * user instead of the app silently reporting "sync complete".
 */
data class SyncReport(
    val key: SyncKey,
    val newBookmarks: Int,
    val warnings: List<SyncWarning>
) {
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
}
