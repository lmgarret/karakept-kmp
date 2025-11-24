package com.karakept.app.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.karakept.app.data.repository.ServerRepository
import kotlinx.coroutines.launch

class LoginScreenModel(
    private val serverRepository: ServerRepository
) : ScreenModel {
    
    fun addServer(url: String, apiKey: String, label: String, onSuccess: () -> Unit) {
        screenModelScope.launch {
            serverRepository.addServer(url, apiKey, label)
            onSuccess()
        }
    }
}
