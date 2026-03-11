package com.karakept.app.data.repository

import com.karakept.app.data.local.dao.ServerDao
import com.karakept.app.data.local.entity.ServerEntity
import com.karakept.app.data.model.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ServerRepository(private val serverDao: ServerDao) {
    val servers: Flow<List<Server>> = serverDao.getAllServers().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun hasServers(): Boolean {
        return serverDao.getAllServersSync().isNotEmpty()
    }

    suspend fun addServer(url: String, apiKey: String, label: String) {
        val id = url.hashCode().toString() // Simple ID generation
        serverDao.insertServer(ServerEntity(id, url, apiKey, label))
    }

    suspend fun deleteServer(server: Server) {
        serverDao.deleteServer(server.toEntity())
    }

    suspend fun deleteAllServers() {
        serverDao.deleteAllServers()
    }

    private fun ServerEntity.toDomain() = Server(id, url, apiKey, label)
    private fun Server.toEntity() = ServerEntity(id, url, apiKey, label)
}
