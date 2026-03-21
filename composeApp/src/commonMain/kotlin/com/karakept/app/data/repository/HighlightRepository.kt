package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.HighlightDao
import com.karakept.app.utils.AppLogger
import com.karakept.app.data.local.entity.HighlightEntity
import com.karakept.app.data.model.Highlight
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class HighlightRepository(
    private val highlightDao: HighlightDao,
    private val remoteDataSource: RemoteDataSource,
    private val bookmarkActionsRepository: BookmarkActionsRepository
) {
    fun getHighlightsForBookmark(bookmarkRemoteId: String, serverId: String): Flow<List<Highlight>> {
        return highlightDao.getHighlightsForBookmark(bookmarkRemoteId, serverId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getAllHighlights(serverId: String): Flow<List<Highlight>> {
        return highlightDao.getHighlightsForServer(serverId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun syncHighlights(server: Server) {
        // Full sync of all highlights - used for periodic background sync or All Highlights screen
        try {
            val remoteHighlights = remoteDataSource.fetchAllHighlights(server)
            println("HighlightRepository: syncHighlights received ${remoteHighlights.size} highlights from server")

            // Get remote IDs (excluding empty strings from null IDs)
            val remoteIds = remoteHighlights.mapNotNull { it.id }.filter { it.isNotEmpty() }

            // Delete local highlights that are no longer on server
            // Keep temp IDs (starting with "temp_") since they haven't been synced yet
            if (remoteIds.isEmpty()) {
                // If server returns empty list, delete all non-temp highlights for this server
                highlightDao.deleteAllNonTempHighlightsForServer(server.id)
                println("HighlightRepository: Deleted all non-pending highlights for server (server returned empty)")
            } else {
                // Delete highlights not in the remote list (but keep temp IDs)
                highlightDao.deleteHighlightsNotInList(server.id, remoteIds)
                println("HighlightRepository: Deleted local highlights not in remote list")
            }

            val entities = remoteHighlights.map { highlight ->
                HighlightEntity(
                    remoteId = highlight.id ?: "",
                    serverId = server.id,
                    bookmarkRemoteId = highlight.bookmarkId ?: "",
                    text = highlight.text ?: "",
                    startOffset = highlight.startOffset?.toInt() ?: 0,
                    endOffset = highlight.endOffset?.toInt() ?: 0,
                    note = highlight.note,
                    color = highlight.color?.value,  // Extract string value from enum
                    createdAt = try {
                        Instant.parse(highlight.createdAt ?: "").toEpochMilliseconds()
                    } catch (e: Exception) { 0L }
                )
            }
            highlightDao.insertHighlights(entities)
            println("HighlightRepository: Inserted ${entities.size} highlights into local DB")
        } catch (e: Exception) {
            println("Error syncing highlights: ${e.message}")
        }
    }

    suspend fun syncHighlightsForBookmark(server: Server, bookmarkRemoteId: String) {
        try {
            println("HighlightRepository: Fetching highlights for bookmark $bookmarkRemoteId")
            val remoteHighlights = remoteDataSource.fetchHighlightsForBookmark(server, bookmarkRemoteId)
            println("HighlightRepository: Received ${remoteHighlights.size} highlights from server")

            // Get remote IDs (excluding empty strings from null IDs)
            val remoteIds = remoteHighlights.mapNotNull { it.id }.filter { it.isNotEmpty() }

            // Delete local highlights that are no longer on server
            // Keep temp IDs (starting with "temp_") since they haven't been synced yet
            if (remoteIds.isEmpty()) {
                // If server returns empty list, delete all non-temp highlights for this bookmark
                highlightDao.deleteAllHighlightsForBookmark(bookmarkRemoteId, server.id)
                println("HighlightRepository: Deleted all non-pending highlights for bookmark (server returned empty)")
            } else {
                // Delete highlights not in the remote list (but keep temp IDs)
                highlightDao.deleteHighlightsNotIn(bookmarkRemoteId, server.id, remoteIds)
                println("HighlightRepository: Deleted local highlights not in remote list: $remoteIds")
            }

            val entities = remoteHighlights.map { highlight ->
                HighlightEntity(
                    remoteId = highlight.id ?: "",
                    serverId = server.id,
                    bookmarkRemoteId = highlight.bookmarkId ?: "",
                    text = highlight.text ?: "",
                    startOffset = highlight.startOffset?.toInt() ?: 0,
                    endOffset = highlight.endOffset?.toInt() ?: 0,
                    note = highlight.note,
                    color = highlight.color?.value,  // Extract string value from enum
                    createdAt = try {
                        Instant.parse(highlight.createdAt ?: "").toEpochMilliseconds()
                    } catch (e: Exception) { 0L }
                )
            }
            highlightDao.insertHighlights(entities)
            println("HighlightRepository: Inserted ${entities.size} highlights into local DB")
        } catch (e: Exception) {
            AppLogger.e("HighlightRepo", "Failed to sync highlights: ${e.message}", e)
        }
    }

    suspend fun createHighlight(
        server: Server,
        bookmarkLocalId: Long,
        bookmarkRemoteId: String,
        text: String,
        startOffset: Int,
        endOffset: Int,
        note: String? = null,
        color: String? = null
    ): String {
        // Optimistically add to local DB first with temp ID
        val tempId = "temp_${System.currentTimeMillis()}"
        highlightDao.insertHighlights(listOf(
            HighlightEntity(
                remoteId = tempId,
                serverId = server.id,
                bookmarkRemoteId = bookmarkRemoteId,
                text = text,
                startOffset = startOffset,
                endOffset = endOffset,
                note = note,
                color = color ?: "yellow",
                createdAt = System.currentTimeMillis()
            )
        ))

        // Queue action with tempId so we can update it later
        bookmarkActionsRepository.queueCreateHighlight(server, bookmarkLocalId, bookmarkRemoteId, text, startOffset, endOffset, note, color, tempId)

        return tempId
    }

    suspend fun deleteHighlight(server: Server, bookmarkLocalId: Long, highlightRemoteId: String) {
        bookmarkActionsRepository.queueDeleteHighlight(server, bookmarkLocalId, highlightRemoteId)
        
        // Optimistically delete from local DB
        highlightDao.deleteHighlightByRemoteId(highlightRemoteId)
    }

    suspend fun updateHighlight(server: Server, bookmarkLocalId: Long, highlightRemoteId: String, note: String? = null, color: String? = null) {
        println("HighlightRepository: updateHighlight called - highlightRemoteId=$highlightRemoteId, note=$note, color=$color")
        bookmarkActionsRepository.queueUpdateHighlight(server, bookmarkLocalId, highlightRemoteId, note, color)

        // Optimistically update local DB
        val existing = highlightDao.getHighlightByRemoteId(highlightRemoteId)
        println("HighlightRepository: updateHighlight - existing highlight found: ${existing != null}")
        existing?.let {
            highlightDao.updateHighlight(it.copy(
                note = note,  // Allow null to clear the note
                color = color  // Allow null to reset to default
            ))
            println("HighlightRepository: updateHighlight - local DB updated")
        }
    }

    private fun HighlightEntity.toDomain() = Highlight(
        id = remoteId,
        bookmarkId = bookmarkRemoteId,
        text = text,
        startOffset = startOffset,
        endOffset = endOffset,
        note = note,
        color = color,
        createdAt = createdAt
    )
}
