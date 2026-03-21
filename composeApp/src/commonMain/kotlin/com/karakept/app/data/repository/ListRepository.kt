package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.ListDao
import com.karakept.app.utils.AppLogger
import com.karakept.app.data.local.entity.ListEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.api.model.KarakeepList
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ListRepository(
    private val remoteDataSource: RemoteDataSource,
    private val listDao: ListDao,
    private val settingsRepository: SettingsRepository
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Maintain StateFlow for backward compatibility with existing UI code
    private val _lists = MutableStateFlow<List<KarakeepList>>(emptyList())
    val lists: StateFlow<List<KarakeepList>> = _lists.asStateFlow()

    // Track the current server ID for which lists are loaded
    private var currentServerId: String? = null

    /**
     * Refresh lists from the remote server and update local database.
     * The StateFlow will be updated with the new lists.
     * Skips network call if offline mode is enabled, but still loads from local database.
     */
    suspend fun refreshLists(server: Server) {
        currentServerId = server.id

        // Check if offline mode is enabled
        val isOffline = settingsRepository.offlineMode.first()

        if (!isOffline) {
            // Fetch from network and update local database
            try {
                val remoteLists = remoteDataSource.fetchLists(server)
                val entities = remoteLists.map { it.toEntity(server.id) }

                // Upsert all lists
                listDao.insertLists(entities)

                // Remove lists that no longer exist on the server
                val remoteIds = remoteLists.mapNotNull { it.id }
                if (remoteIds.isNotEmpty()) {
                    listDao.deleteRemovedLists(server.id, remoteIds)
                }
            } catch (e: Exception) {
                AppLogger.e("ListRepo", "Failed to sync lists: ${e.message}", e)
                // Continue to load from local database on error
            }
        }

        // Load from local database and update StateFlow
        loadListsFromDatabase(server.id)
    }

    /**
     * Load lists from the local database for the given server.
     * Updates the StateFlow with the loaded lists.
     */
    private suspend fun loadListsFromDatabase(serverId: String) {
        try {
            val entities = listDao.getListsForServerOnce(serverId)
            _lists.value = entities.map { it.toKarakeepList() }
        } catch (e: Exception) {
            AppLogger.e("ListRepo", "Failed to create list: ${e.message}", e)
        }
    }

    /**
     * Get lists once (not as a Flow) for immediate use.
     */
    suspend fun getListsOnce(serverId: String): List<KarakeepList> {
        return listDao.getListsForServerOnce(serverId).map { it.toKarakeepList() }
    }

    /**
     * Clear all lists for a specific server.
     */
    suspend fun clearListsForServer(serverId: String) {
        listDao.deleteAllForServer(serverId)
        if (currentServerId == serverId) {
            _lists.value = emptyList()
        }
    }

    /**
     * Rename a list and optionally update its icon.
     * Updates the server and then reloads from the local database.
     */
    suspend fun renameList(server: Server, listId: String, newName: String, newIcon: String?): Result<Unit> {
        return try {
            val updated = remoteDataSource.updateList(server, listId, newName, newIcon)
            val entity = updated.toEntity(server.id)
            listDao.insertLists(listOf(entity))
            loadListsFromDatabase(server.id)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("ListRepo", "Failed to update list: ${e.message}", e)
            // Update locally for immediate feedback even when remote fails
            listDao.updateListNameAndIcon(listId, server.id, newName, newIcon, System.currentTimeMillis())
            loadListsFromDatabase(server.id)
            Result.failure(e)
        }
    }

    /**
     * Observe lists changes in the database for a specific server.
     * This is useful for reacting to database changes in real-time.
     */
    fun observeLists(serverId: String) {
        repositoryScope.launch {
            listDao.getListsForServer(serverId).collect { entities ->
                if (currentServerId == serverId) {
                    _lists.value = entities.map { it.toKarakeepList() }
                }
            }
        }
    }
}

// Extension functions for mapping between entity and API model

private fun ListEntity.toKarakeepList(): KarakeepList = KarakeepList(
    id = remoteId,
    name = name,
    icon = icon,
    parentId = parentId,
    public = isPublic,
    description = description,
    type = when (type) {
        "smart" -> KarakeepList.Type.SMART
        else -> KarakeepList.Type.MANUAL
    },
    query = query
)

private fun KarakeepList.toEntity(serverId: String): ListEntity = ListEntity(
    remoteId = id ?: "",
    serverId = serverId,
    name = name ?: "",
    icon = icon,
    parentId = parentId,
    isPublic = public ?: false,
    description = description,
    type = type?.value ?: "manual",
    query = query,
    updatedAt = System.currentTimeMillis()
)
