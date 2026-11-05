package com.karakept.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SidecarConfig
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SidecarRepository
import com.karakept.app.domain.sidecar.SidecarKind
import com.karakept.app.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One sidecar's editable fields.
 *
 * [token] starts null and stays null until the user types: a null token means "leave the stored one
 * alone", which is what lets a URL be corrected without retyping a secret that cannot be read back
 * into the field. [tokenStored] is the only thing the screen says about the existing one.
 */
data class SidecarEditorState(
    val kind: String,
    val baseUrl: String = "",
    val enabled: Boolean = false,
    val token: String? = null,
    val tokenStored: Boolean = false,
    val status: String? = null
)

/**
 * The Sidecars settings screen.
 *
 * Sidecars are per Karakeep server, and the app is single-server in practice — like
 * `ServerSettingsScreen`, this edits the first connected server's configuration.
 */
class SidecarsSettingsScreenModel(
    private val sidecarRepository: SidecarRepository,
    private val serverRepository: ServerRepository
) : ViewModel() {

    val server: StateFlow<Server?> = serverRepository.servers
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _editors = MutableStateFlow(
        SidecarKind.ALL.associateWith { SidecarEditorState(kind = it) }
    )
    val editors: StateFlow<Map<String, SidecarEditorState>> = _editors.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val serverId = serverRepository.servers.first().firstOrNull()?.id ?: return
        val stored = sidecarRepository.sidecars.first()
        _editors.update { current ->
            current.mapValues { (kind, editor) ->
                val config = stored[com.karakept.app.data.model.sidecarKey(serverId, kind)]
                editor.copy(
                    baseUrl = config?.baseUrl ?: "",
                    enabled = config?.enabled ?: false,
                    token = null,
                    tokenStored = sidecarRepository.token(serverId, kind)?.isNotBlank() == true
                )
            }
        }
    }

    fun setBaseUrl(kind: String, value: String) = edit(kind) { copy(baseUrl = value, status = null) }

    fun setToken(kind: String, value: String) = edit(kind) { copy(token = value, status = null) }

    /** Toggling the switch saves immediately: a switch that needs a second tap to stick is a bug. */
    fun setEnabled(kind: String, value: Boolean) {
        edit(kind) { copy(enabled = value, status = null) }
        save(kind)
    }

    fun save(kind: String) {
        val editor = _editors.value[kind] ?: return
        viewModelScope.launch {
            val serverId = serverRepository.servers.first().firstOrNull()?.id ?: run {
                edit(kind) { copy(status = "Connect a Karakeep server first") }
                return@launch
            }
            sidecarRepository.save(
                SidecarConfig(
                    serverId = serverId,
                    kind = kind,
                    baseUrl = editor.baseUrl.trim(),
                    enabled = editor.enabled
                ),
                token = editor.token
            )
            edit(kind) {
                copy(
                    token = null,
                    tokenStored = editor.token?.isNotBlank() ?: tokenStored,
                    status = "Saved"
                )
            }
            // A saved change can switch the service on or off for the reader straight away.
            sidecarRepository.refreshCapabilities(serverId, kind)
        }
    }

    /**
     * Probe the URL and token as typed, without saving them.
     *
     * Reports what the service says rather than just "reachable": a karhtmlscrub with no model
     * configured answers every health check happily and then cleans nothing.
     */
    fun testConnection(kind: String) {
        if (_isWorking.value) return
        val editor = _editors.value[kind] ?: return
        if (editor.baseUrl.isBlank()) {
            edit(kind) { copy(status = "Enter the service address first") }
            return
        }
        _isWorking.value = true
        viewModelScope.launch {
            try {
                val serverId = serverRepository.servers.first().firstOrNull()?.id
                val token = editor.token ?: serverId?.let { sidecarRepository.token(it, kind) }
                val result = sidecarRepository.testConnection(editor.baseUrl.trim(), token)
                edit(kind) {
                    copy(
                        status = result.fold(
                            onSuccess = { caps ->
                                when {
                                    !caps.llmEnabled ->
                                        "Reachable, but it has no model configured — it won't clean anything"
                                    caps.dryRun ->
                                        "Reachable, but running in dry-run — it won't upload anything"
                                    else -> "Connected${caps.service?.let { " to $it" } ?: ""}"
                                }
                            },
                            onFailure = { "Couldn't reach it: ${it.message}" }
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SidecarsSettings", "Sidecar test failed: ${e.message}", e)
                edit(kind) { copy(status = "Couldn't reach it: ${e.message}") }
            } finally {
                _isWorking.value = false
            }
        }
    }

    private fun edit(kind: String, transform: SidecarEditorState.() -> SidecarEditorState) {
        _editors.update { current ->
            val editor = current[kind] ?: SidecarEditorState(kind)
            current + (kind to editor.transform())
        }
    }
}
