package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.repository.ServerRepository
import kotlinx.coroutines.launch

class LoginScreenModel(
    private val serverRepository: ServerRepository,
    private val remoteDataSource: com.karakept.app.data.remote.RemoteDataSource
) : ScreenModel {
    
    fun addServer(url: String, apiKey: String, onSuccess: () -> Unit) {
        screenModelScope.launch {
            serverRepository.addServer(url.trim(), apiKey.trim(), url.trim())
            onSuccess()
        }
    }

    fun testConnection(url: String, apiKey: String, onResult: (Boolean) -> Unit) {
        screenModelScope.launch {
            val success = remoteDataSource.testConnection(url.trim(), apiKey.trim())
            onResult(success)
        }
    }
}
