package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.HighlightDao
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
        // We might still keep this for periodic background sync or All Highlights screen
        try {
            val remoteHighlights = remoteDataSource.fetchAllHighlights(server)
            val entities = remoteHighlights.map { dto ->
                HighlightEntity(
                    remoteId = dto.id,
                    serverId = server.id,
                    bookmarkRemoteId = dto.bookmarkId,
                    text = dto.text,
                    startOffset = dto.startOffset,
                    endOffset = dto.endOffset,
                    note = dto.note,
                    color = dto.color,
                    createdAt = try { Instant.parse(dto.createdAt).toEpochMilliseconds() } catch (e: Exception) { 0L }
                )
            }
            highlightDao.insertHighlights(entities)
        } catch (e: Exception) {
            println("Error syncing highlights: ${e.message}")
        }
    }

    suspend fun syncHighlightsForBookmark(server: Server, bookmarkRemoteId: String) {
        try {
            val remoteHighlights = remoteDataSource.fetchHighlightsForBookmark(server, bookmarkRemoteId)
            val entities = remoteHighlights.map { dto ->
                HighlightEntity(
                    remoteId = dto.id,
                    serverId = server.id,
                    bookmarkRemoteId = dto.bookmarkId,
                    text = dto.text,
                    startOffset = dto.startOffset,
                    endOffset = dto.endOffset,
                    note = dto.note,
                    color = dto.color,
                    createdAt = try { Instant.parse(dto.createdAt).toEpochMilliseconds() } catch (e: Exception) { 0L }
                )
            }
            highlightDao.insertHighlights(entities)
        } catch (e: Exception) {
            println("Error syncing highlights for bookmark $bookmarkRemoteId: ${e.message}")
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
        // Create local action first for offline support
        bookmarkActionsRepository.queueCreateHighlight(server, bookmarkLocalId, bookmarkRemoteId, text, startOffset, endOffset, note, color)

        // Optimistically add to local DB if we want immediate UI update
        // We'll create a temporary local ID for it
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
        return tempId
    }

    suspend fun deleteHighlight(server: Server, bookmarkLocalId: Long, highlightRemoteId: String) {
        bookmarkActionsRepository.queueDeleteHighlight(server, bookmarkLocalId, highlightRemoteId)
        
        // Optimistically delete from local DB
        highlightDao.deleteHighlightByRemoteId(highlightRemoteId)
    }

    suspend fun updateHighlight(server: Server, bookmarkLocalId: Long, highlightRemoteId: String, note: String? = null, color: String? = null) {
        bookmarkActionsRepository.queueUpdateHighlight(server, bookmarkLocalId, highlightRemoteId, note, color)

        // Optimistically update local DB
        val existing = highlightDao.getHighlightByRemoteId(highlightRemoteId)
        existing?.let {
            highlightDao.updateHighlight(it.copy(
                note = note,  // Allow null to clear the note
                color = color  // Allow null to reset to default
            ))
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
