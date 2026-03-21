package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.ServerDao
import com.karakept.app.data.local.entity.ServerEntity
import com.karakept.app.data.model.Server
import com.karakept.app.data.secure.SecureCredentialStore
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ServerRepository(
    private val serverDao: ServerDao,
    private val secureStore: SecureCredentialStore
) {
    private var migrated = false
    private val migrationMutex = Mutex()

    val servers: Flow<List<Server>> = serverDao.getAllServers().map { entities ->
        entities.map { entity ->
            val secureKey = try { secureStore.getApiKey(entity.id) } catch (_: Exception) { null }
            val effectiveKey = secureKey ?: entity.apiKey // fallback to DB
            Server(entity.id, entity.url, effectiveKey, entity.label)
        }
    }

    suspend fun hasServers(): Boolean {
        return serverDao.getAllServersSync().isNotEmpty()
    }

    suspend fun addServer(url: String, apiKey: String, label: String) {
        val id = url.hashCode().toString() // Simple ID generation
        try {
            secureStore.storeApiKey(id, apiKey)
            serverDao.insertServer(ServerEntity(id, url, "", label)) // empty apiKey in DB
        } catch (e: Exception) {
            AppLogger.w("ServerRepository", "Secure store unavailable, using DB fallback: ${e.message}")
            serverDao.insertServer(ServerEntity(id, url, apiKey, label)) // fallback: store in DB
        }
    }

    suspend fun deleteServer(server: Server) {
        try { secureStore.removeApiKey(server.id) } catch (_: Exception) {}
        serverDao.deleteServer(server.toEntity())
    }

    suspend fun deleteAllServers() {
        try {
            serverDao.getAllServersSync().forEach { secureStore.removeApiKey(it.id) }
        } catch (_: Exception) {}
        serverDao.deleteAllServers()
    }

    suspend fun triggerMigration() {
        ensureMigrated()
    }

    private suspend fun ensureMigrated() {
        if (migrated) return
        migrationMutex.withLock {
            if (migrated) return@withLock
            try {
                val allServers = serverDao.getAllServersSync()
                for (server in allServers) {
                    if (server.apiKey.isNotBlank() && !secureStore.hasKey(server.id)) {
                        secureStore.storeApiKey(server.id, server.apiKey)
                        serverDao.insertServer(server.copy(apiKey = ""))
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("SecureMigration", "Failed to migrate credentials: ${e.message}")
            }
            migrated = true
        }
    }

    private fun Server.toEntity() = ServerEntity(id, url, apiKey, label)
}
