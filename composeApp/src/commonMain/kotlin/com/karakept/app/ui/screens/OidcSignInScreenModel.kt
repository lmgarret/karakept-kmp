package com.karakept.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.remote.hasHttpStatus
import com.karakept.app.utils.OidcSignInUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface OidcSignInState {
    data object WaitingForLogin : OidcSignInState
    data object CreatingKey : OidcSignInState
    data class Done(val apiKey: String) : OidcSignInState
    data class Failed(val message: String) : OidcSignInState
}

class OidcSignInScreenModel(
    private val remoteDataSource: RemoteDataSource
) : ViewModel() {

    private val _state = MutableStateFlow<OidcSignInState>(OidcSignInState.WaitingForLogin)
    val state: StateFlow<OidcSignInState> = _state.asStateFlow()

    /**
     * Called each time the WebView finishes loading a page, with the cookies it holds for the
     * server. The first load after the identity provider redirects back carries the session.
     */
    fun onPageLoaded(serverUrl: String, pageUrl: String?, cookies: List<Pair<String, String>>) {
        if (_state.value != OidcSignInState.WaitingForLogin) return
        if (!OidcSignInUtils.isOnServer(pageUrl, serverUrl)) return
        if (!OidcSignInUtils.hasSession(cookies)) return

        _state.value = OidcSignInState.CreatingKey
        viewModelScope.launch {
            _state.value = try {
                val key = remoteDataSource.createApiKeyFromSession(
                    serverUrl,
                    OidcSignInUtils.cookieHeader(cookies)
                )
                OidcSignInState.Done(key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A cookie left over from an expired session: keep waiting for the real login.
                if (e.hasHttpStatus(401)) {
                    OidcSignInState.WaitingForLogin
                } else {
                    OidcSignInState.Failed(e.message ?: "Could not create an API key")
                }
            }
        }
    }

    fun retry() {
        _state.value = OidcSignInState.WaitingForLogin
    }
}
