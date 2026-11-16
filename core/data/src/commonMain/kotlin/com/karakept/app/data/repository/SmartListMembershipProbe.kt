package com.karakept.app.data.repository

import com.karakept.api.model.KarakeepList
import com.karakept.app.data.local.dao.BookmarkDao
import com.karakept.app.data.local.dao.PendingActionDao
import com.karakept.app.data.local.entity.PendingActionType
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.domain.ProbeRow
import com.karakept.app.domain.SmartListProbeUtils
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Settles a bookmark's smart-list membership right after a list edit, instead of leaving it to
 * the next sync of each smart list.
 *
 * Karakeep has no "is this bookmark in that smart list" route, but its list route pages newest
 * first with an inclusive `{id}_{createdAt}` cursor, so one row starting at the bookmark's own
 * cursor answers it (see [SmartListProbeUtils]). The answer only means something once the server
 * has the edit, so the pending-action queue is pushed first and a bookmark whose list actions are
 * still queued afterwards is not probed.
 *
 * Whatever cannot be probed — offline, a queued edit, a failed request, a page that does not read
 * as expected, a batch over [MAX_PROBE_REQUESTS] — is flagged in [needingRefresh] for a full sync
 * the next time that list is opened, and the bookmark keeps the membership it had.
 */
internal class SmartListMembershipProbe(
    private val bookmarkDao: BookmarkDao,
    private val pendingActionDao: PendingActionDao,
    private val remoteDataSource: RemoteDataSource,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val flushPendingActions: suspend (Server) -> Unit,
) {
    private val _needingRefresh = MutableStateFlow<Set<String>>(emptySet())
    val needingRefresh: StateFlow<Set<String>> = _needingRefresh.asStateFlow()

    // Bumped by every list edit of a bookmark. A probe started before the latest edit of its
    // bookmark is superseded: that edit runs a probe of its own, whose answer is the one to keep.
    private val editGenerations = HashMap<Pair<String, String>, Long>()
    private val writeMutex = Mutex()

    suspend fun noteListEdit(serverId: String, bookmarkRemoteId: String) {
        writeMutex.withLock {
            val key = serverId to bookmarkRemoteId
            editGenerations[key] = (editGenerations[key] ?: 0L) + 1L
        }
    }

    fun flag(listIds: Collection<String>) {
        if (listIds.isNotEmpty()) _needingRefresh.update { it + listIds }
    }

    fun take(listId: String): Boolean {
        var wasFlagged = false
        _needingRefresh.update { current ->
            wasFlagged = listId in current
            current - listId
        }
        return wasFlagged
    }

    suspend fun probe(serverId: String, bookmarkRemoteIds: List<String>, lists: List<KarakeepList>) {
        val affected = lists
            .filter { it.type == KarakeepList.Type.SMART && SmartListProbeUtils.canDependOnListMembership(it.query) }
            .mapNotNull { it.id }
            .distinct()
        val bookmarkIds = bookmarkRemoteIds.distinct()
        if (affected.isEmpty() || bookmarkIds.isEmpty()) return

        // A list left unprobed for one bookmark of a batch needs a full sync anyway, and that
        // sync settles every other bookmark in it too — so a batch is probed whole or not at all.
        if (bookmarkIds.size * affected.size > MAX_PROBE_REQUESTS) {
            flag(affected)
            return
        }

        val generations = writeMutex.withLock {
            bookmarkIds.associateWith { editGenerations[serverId to it] ?: 0L }
        }

        val server = if (settingsRepository.offlineMode.first()) null
        else serverRepository.servers.first().find { it.id == serverId }
        if (server == null) {
            flag(affected)
            return
        }

        val unsynced = try {
            flushPendingActions(server)
            pendingActionDao.getPendingActionsList(serverId)
                .filter { it.actionType in LIST_MEMBERSHIP_ACTIONS }
                .map { it.bookmarkRemoteId }
                .toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not push list edits before probing: ${e.message}")
            flag(affected)
            return
        }

        val unverified = mutableSetOf<String>()
        for (bookmarkId in bookmarkIds) {
            val generation = generations.getValue(bookmarkId)
            val entity = bookmarkDao.getBookmarkByRemoteId(bookmarkId, serverId)
            val cursor = entity?.let { SmartListProbeUtils.cursorFor(bookmarkId, it.createdAt) }
            if (bookmarkId in unsynced || entity == null || cursor == null) {
                unverified += affected
                continue
            }
            for (listId in affected) {
                if (isSuperseded(serverId, bookmarkId, generation)) break
                val isMember = probeOne(server, listId, bookmarkId, entity.createdAt, cursor)
                if (isMember == null) {
                    unverified += listId
                    continue
                }
                if (!writeMembership(serverId, bookmarkId, listId, isMember, generation)) {
                    unverified += listId
                }
            }
        }
        flag(unverified)
    }

    private suspend fun probeOne(
        server: Server,
        listId: String,
        bookmarkId: String,
        createdAt: Long,
        cursor: String
    ): Boolean? = try {
        val page = remoteDataSource.fetchListPage(server, listId, cursor, limit = 1)
        val rows = page.bookmarks.orEmpty().map { ProbeRow(it.id, it.createdAt) }
        SmartListProbeUtils.classifyPage(bookmarkId, createdAt, rows)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.w(TAG, "Membership probe of $bookmarkId in $listId failed: ${e.message}")
        null
    }

    private suspend fun isSuperseded(serverId: String, bookmarkId: String, generation: Long): Boolean =
        writeMutex.withLock { (editGenerations[serverId to bookmarkId] ?: 0L) != generation }

    /**
     * Adds or removes [listId] alone, keeping every other id. Returns false only when the row kept
     * changing under the write; a superseded probe or a vanished row counts as settled.
     */
    private suspend fun writeMembership(
        serverId: String,
        bookmarkId: String,
        listId: String,
        isMember: Boolean,
        generation: Long
    ): Boolean = writeMutex.withLock {
        if ((editGenerations[serverId to bookmarkId] ?: 0L) != generation) return@withLock true
        repeat(MAX_WRITE_ATTEMPTS) {
            val row = bookmarkDao.getBookmarkByRemoteId(bookmarkId, serverId) ?: return@withLock true
            val current = row.listIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val updated = when {
                isMember && listId !in current -> current + listId
                !isMember && listId in current -> current - listId
                else -> return@withLock true
            }
            if (bookmarkDao.updateListIdsIfUnchanged(row.localId, row.listIds, updated.joinToString(",")) > 0) {
                return@withLock true
            }
        }
        false
    }

    companion object {
        /**
         * Requests one edit may spend on probing: bookmarks × affected smart lists. A single
         * bookmark against a handful of smart lists is the common case and always fits; past
         * this, a full sync of each list on its next open costs no more than the probes would.
         */
        const val MAX_PROBE_REQUESTS = 20

        private const val MAX_WRITE_ATTEMPTS = 3
        private const val TAG = "SmartListMembershipProbe"
        private val LIST_MEMBERSHIP_ACTIONS = setOf(PendingActionType.MOVE_TO_LIST, PendingActionType.REMOVE_FROM_LIST)
    }
}
