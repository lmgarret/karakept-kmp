package com.karakept.app.data.repository

import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.domain.ServerVersionCheck
import com.karakept.app.domain.ServerVersionUtils
import com.karakept.app.utils.AppDispatchers
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class ServerVersionRepository(
    private val remoteDataSource: RemoteDataSource,
    private val appDispatchers: AppDispatchers
) {
    // In-memory on purpose, like the AI capabilities: a server is most likely to have been
    // upgraded across a restart, which is exactly when this re-probes.
    private val _versions = MutableStateFlow<Map<String, ServerVersionCheck>>(emptyMap())

    /** Every probed server's version, keyed by server id. */
    val versions: StateFlow<Map<String, ServerVersionCheck>> = _versions

    private val warnedServerIds = MutableStateFlow<Set<String>>(emptySet())

    /** Ask the server at [url] for its version. Null when it could not be reached. */
    suspend fun check(url: String): ServerVersionCheck? = withContext(appDispatchers.io) {
        try {
            ServerVersionUtils.evaluate(remoteDataSource.fetchServerVersion(url))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("ServerVersionRepository", "Version probe failed for $url: ${e.message}")
            null
        }
    }

    /**
     * Re-probe [server] and remember the answer. A failed probe keeps the last known answer
     * rather than forgetting it, and returns it.
     */
    suspend fun refresh(server: Server): ServerVersionCheck? {
        val check = check(server.url) ?: return _versions.value[server.id]
        _versions.update { it + (server.id to check) }
        return check
    }

    /**
     * True the first time it is asked about [serverId] in this process. The startup warning goes
     * through it so recreating the list screen does not repeat a warning already shown.
     */
    fun claimOutdatedWarning(serverId: String): Boolean =
        serverId !in warnedServerIds.getAndUpdate { it + serverId }
}
