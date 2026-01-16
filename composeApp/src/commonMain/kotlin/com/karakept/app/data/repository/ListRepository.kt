package com.karakept.app.data.repository

import com.karakept.app.data.model.Server
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.api.model.KarakeepList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ListRepository(
    private val remoteDataSource: RemoteDataSource
) {
    private val _lists = MutableStateFlow<List<KarakeepList>>(emptyList())
    val lists: StateFlow<List<KarakeepList>> = _lists.asStateFlow()

    suspend fun refreshLists(server: Server) {
        try {
            val fetchedLists = remoteDataSource.fetchLists(server)
            _lists.value = fetchedLists
        } catch (e: Exception) {
            e.printStackTrace()
            // Optionally clear lists or keep stale data?
            // Keeping stale data is usually better for UX if offline.
        }
    }
}
