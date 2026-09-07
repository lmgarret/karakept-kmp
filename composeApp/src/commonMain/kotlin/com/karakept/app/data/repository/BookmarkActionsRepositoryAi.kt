package com.karakept.app.data.repository

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.UnsupportedServerActionException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Server-side AI actions for BookmarkActionsRepository.
 *
 * These deliberately skip the pending-action queue: an AI job has no local optimistic result to
 * show while offline and nothing to undo once it lands, exactly like
 * [com.karakept.app.domain.action.ServerCrawlAction]. What they do share with every other mutation
 * is the finish: write the row, then [BookmarkActionsRepository.notifyBookmarkChanged], which is
 * what makes the list and the reader pick the change up without a full sync.
 */

/** Which AI actions a given server will accept from this account. */
data class AiCapabilities(
    /** False once the server has told us it has no inference client configured. */
    val canSummarize: Boolean = true,
    /** Re-running AI tagging is an admin-only tRPC route. */
    val isAdmin: Boolean = false
)

/** How long to keep re-syncing after a retag request before giving up on seeing new tags. */
private const val RETAG_POLL_ATTEMPTS = 10
private const val RETAG_POLL_INTERVAL_MS = 2000L

/**
 * Generate an AI summary for [bookmark] and store it.
 *
 * The call is synchronous server-side — it returns the finished summary rather than enqueuing a
 * job — so the summary is written and announced before this returns.
 */
suspend fun BookmarkActionsRepository.summarizeBookmark(bookmark: BookmarkEntity): String? =
    withContext(appDispatchers.io) {
        val server = requireServer(bookmark.serverId)
        val result = try {
            remoteDataSource.summarizeBookmark(server, bookmark.remoteId)
        } catch (e: UnsupportedServerActionException) {
            narrowAiCapabilities(server.id) { copy(canSummarize = false) }
            throw e
        }
        bookmarkDao.updateSummary(bookmark.localId, result.summary, result.summarizationStatus)
        notifyBookmarkChanged(bookmark.remoteId)
        result.summary
    }

/**
 * Ask the server to re-run AI tagging for [bookmark].
 *
 * The server only enqueues the job, so the tags arrive later. With [awaitResult] the call re-syncs
 * the single bookmark until its tag list changes and returns whether it did — false meaning "still
 * working on it", not a failure. A batch passes false: waiting up to [RETAG_POLL_ATTEMPTS] polls
 * per bookmark would turn a ten-item selection into minutes of staring at a progress counter, and
 * the tags arrive on the next sync regardless.
 */
suspend fun BookmarkActionsRepository.requestAiRetag(
    bookmark: BookmarkEntity,
    awaitResult: Boolean = true
): Boolean =
    withContext(appDispatchers.io) {
        val server = requireServer(bookmark.serverId)
        remoteDataSource.requestAiRetag(server, bookmark.remoteId)

        if (!awaitResult) return@withContext false
        val repository = bookmarkRepository ?: return@withContext false
        val tagsBefore = bookmark.tags
        repeat(RETAG_POLL_ATTEMPTS) {
            delay(RETAG_POLL_INTERVAL_MS)
            repository.syncSingleBookmark(bookmark.remoteId, bookmark.serverId)
            val current = bookmarkDao.getBookmarkByRemoteId(bookmark.remoteId, bookmark.serverId)
            if (current != null && current.tags != tagsBefore) {
                notifyBookmarkChanged(bookmark.remoteId)
                return@withContext true
            }
        }
        false
    }

private suspend fun BookmarkActionsRepository.requireServer(serverId: String): Server =
    serverRepository.servers.first().firstOrNull { it.id == serverId }
        ?: throw Exception("Server $serverId is no longer configured")
