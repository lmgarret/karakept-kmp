package com.karakept.app.data.repository

import com.karakept.app.data.local.entity.BookmarkEntity
import com.karakept.app.data.model.SanitizeJob
import com.karakept.app.data.model.SidecarCapabilities
import com.karakept.app.data.model.SidecarConfig
import com.karakept.app.data.model.sidecarKey
import com.karakept.app.data.remote.SidecarDataSource
import com.karakept.app.data.remote.SidecarUnavailableException
import com.karakept.app.data.secure.SecureCredentialStore
import com.karakept.app.utils.AppDispatchers
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * The optional sidecar services configured for each Karakeep server.
 *
 * Three things live here and nowhere else: where a sidecar is (DataStore, through
 * [SettingsRepository]), its bearer token (the platform credential store, never a settings or
 * database field), and what each deployment says it can do (in memory).
 *
 * Nothing in this class is needed to *read* a sidecar's output — that arrives as an ordinary
 * Karakeep asset. It is needed only to ask for work on demand.
 */
class SidecarRepository(
    private val settingsRepository: SettingsRepository,
    private val secureStore: SecureCredentialStore,
    private val dataSource: SidecarDataSource,
    private val appDispatchers: AppDispatchers
) {
    /** Every configured sidecar, keyed by [sidecarKey]. */
    val sidecars: Flow<Map<String, SidecarConfig>> = settingsRepository.sidecarConfigs

    private val _capabilities = MutableStateFlow<Map<String, SidecarCapabilities>>(emptyMap())

    /** What each configured sidecar says it can do, keyed by [sidecarKey]. */
    val capabilities: StateFlow<Map<String, SidecarCapabilities>> = _capabilities

    /** What this sidecar will do right now, or null before any successful probe. */
    fun capabilitiesFor(serverId: String?, kind: String): SidecarCapabilities? =
        serverId?.let { _capabilities.value[sidecarKey(it, kind)] }

    suspend fun config(serverId: String, kind: String): SidecarConfig? =
        sidecars.first()[sidecarKey(serverId, kind)]

    /** Suspending because reading it touches the platform keystore, and the data layer stays main-safe. */
    suspend fun token(serverId: String, kind: String): String? = withContext(appDispatchers.io) {
        try {
            secureStore.getApiKey(credentialAlias(serverId, kind))
        } catch (e: Exception) {
            AppLogger.d("SidecarRepository", "Reading sidecar token failed: ${e.message}")
            null
        }
    }

    /**
     * Store [config], and [token] when one is given.
     *
     * A null [token] leaves the stored one alone, so saving a URL change does not require the user
     * to retype a token they cannot read back. A blank one clears it.
     */
    suspend fun save(config: SidecarConfig, token: String? = null) {
        settingsRepository.setSidecarConfig(config)
        if (token != null) {
            val alias = credentialAlias(config.serverId, config.kind)
            try {
                withContext(appDispatchers.io) {
                if (token.isBlank()) secureStore.removeApiKey(alias) else secureStore.storeApiKey(alias, token)
            }
            } catch (e: Exception) {
                AppLogger.e("SidecarRepository", "Storing sidecar token failed: ${e.message}", e)
            }
        }
    }

    suspend fun remove(serverId: String, kind: String) {
        settingsRepository.removeSidecarConfig(serverId, kind)
        try {
            secureStore.removeApiKey(credentialAlias(serverId, kind))
        } catch (e: Exception) {
            AppLogger.d("SidecarRepository", "Removing sidecar token failed: ${e.message}")
        }
        _capabilities.update { it - sidecarKey(serverId, kind) }
    }

    /**
     * Ask this sidecar what it can do, caching the answer for the session.
     *
     * A failed probe leaves the cached entry untouched rather than writing a "can't do anything":
     * demoting a known-good sidecar on one bad request would make the action disappear under the
     * user mid-session. In memory on purpose — a re-probe on next launch is exactly when a
     * reconfigured service is most likely to answer differently.
     */
    suspend fun refreshCapabilities(serverId: String, kind: String) {
        val config = config(serverId, kind) ?: return
        if (!config.enabled || config.baseUrl.isBlank()) {
            _capabilities.update { it - sidecarKey(serverId, kind) }
            return
        }
        val probed = try {
            withContext(appDispatchers.io) {
                dataSource.fetchCapabilities(config.baseUrl, token(serverId, kind))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d("SidecarRepository", "Sidecar probe failed for $serverId/$kind: ${e.message}")
            return
        }
        _capabilities.update { it + (sidecarKey(serverId, kind) to probed) }
    }

    /** Whether an on-demand action for this server should be offered at all. */
    suspend fun isUsable(serverId: String?, kind: String): Boolean {
        if (serverId == null) return false
        val config = config(serverId, kind) ?: return false
        if (!config.enabled || config.baseUrl.isBlank()) return false
        return capabilitiesFor(serverId, kind)?.usable == true
    }

    /** Probe an unsaved URL/token pair, for the settings screen's "Test connection" button. */
    suspend fun testConnection(baseUrl: String, token: String?): Result<SidecarCapabilities> =
        withContext(appDispatchers.io) {
            runCatching { dataSource.fetchCapabilities(baseUrl, token?.takeIf { it.isNotBlank() }) }
        }

    suspend fun requestSanitize(bookmark: BookmarkEntity, kind: String): SanitizeJob {
        val config = requireConfig(bookmark.serverId, kind)
        return withContext(appDispatchers.io) {
            dataSource.requestSanitize(config.baseUrl, token(bookmark.serverId, kind), bookmark.remoteId)
        }
    }

    suspend fun fetchSanitizeStatus(bookmark: BookmarkEntity, kind: String): SanitizeJob? {
        val config = requireConfig(bookmark.serverId, kind)
        return withContext(appDispatchers.io) {
            dataSource.fetchSanitizeStatus(config.baseUrl, token(bookmark.serverId, kind), bookmark.remoteId)
        }
    }

    /** Every configured sidecar with its token, for the encrypted backup. */
    suspend fun exportAll(): List<SidecarExport> = sidecars.first().values.map { config ->
        SidecarExport(config, token(config.serverId, config.kind))
    }

    suspend fun restoreAll(entries: List<SidecarExport>) {
        entries.forEach { save(it.config, it.token ?: "") }
    }

    private suspend fun requireConfig(serverId: String, kind: String): SidecarConfig {
        val config = config(serverId, kind)
        if (config == null || !config.enabled || config.baseUrl.isBlank()) {
            throw SidecarUnavailableException("No cleanup service is configured for this server")
        }
        return config
    }

    private fun credentialAlias(serverId: String, kind: String): String =
        "sidecar:$serverId:${kind.lowercase()}"
}

/** A sidecar configuration together with its secret, used only when crossing the backup boundary. */
data class SidecarExport(val config: SidecarConfig, val token: String?)
