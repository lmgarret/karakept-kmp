package com.karakept.app.ui.screens.viewer

import com.karakept.app.data.local.entity.AssetEntity
import com.karakept.app.data.model.Bookmark
import com.karakept.app.data.model.ContentSource
import com.karakept.app.data.model.ViewerMode
import com.karakept.app.data.remote.OfflineModeException
import com.karakept.app.data.repository.AssetRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.domain.action.ActionSnackbarManager
import com.karakept.app.utils.AppLogger
import com.karakept.app.utils.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The bookmark viewer's assets: the rows the details panel lists, the cached copies the reader
 * shows, and the downloads and deletions behind them. Work runs on the owning screen's [scope].
 *
 * Also the single owner of the content source the reader shows ([selectedSource] and
 * [sourceContentOverride]): an archive arriving or disappearing switches it here, and the screen
 * asks for a switch through [setContentSource] rather than writing the flows itself.
 */
internal class ViewerAssetsController(
    private val scope: CoroutineScope,
    private val assetRepository: AssetRepository,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val snackbarManager: ActionSnackbarManager,
    private val viewerMode: StateFlow<ViewerMode>,
    private val isOffline: () -> Boolean,
    private val currentBookmark: () -> Bookmark?
) {
    // Session-only content source selection (not persisted to settings)
    private val _selectedSource = MutableStateFlow(ContentSource.EXTRACTED)
    val selectedSource: StateFlow<ContentSource> = _selectedSource.asStateFlow()

    // HTML string override for FULL_PAGE_ARCHIVE + READER mode (session-only)
    private val _sourceContentOverride = MutableStateFlow<String?>(null)
    val sourceContentOverride: StateFlow<String?> = _sourceContentOverride.asStateFlow()

    private val _precrawledAssetPath = MutableStateFlow<String?>(null)
    val precrawledAssetPath: StateFlow<String?> = _precrawledAssetPath.asStateFlow()

    // True when a full page archive exists on the server for this bookmark
    // (localPath may still be null — use precrawledAssetPath != null for "locally cached")
    private val _archiveAvailable = MutableStateFlow(false)
    val archiveAvailable: StateFlow<Boolean> = _archiveAvailable.asStateFlow()

    // Full list of asset entities for this bookmark (server metadata + local cache status)
    private val _assets = MutableStateFlow<List<AssetEntity>>(emptyList())
    val assets: StateFlow<List<AssetEntity>> = _assets.asStateFlow()

    // True while archive is being downloaded for source switching
    private val _isLoadingSource = MutableStateFlow(false)
    val isLoadingSource: StateFlow<Boolean> = _isLoadingSource.asStateFlow()

    // Assets currently downloading, keyed by asset id. The value is the 0f..1f progress
    // fraction, or null when the response has no Content-Length (indeterminate).
    private val _assetDownloads = MutableStateFlow<Map<String, Float?>>(emptyMap())
    val assetDownloads: StateFlow<Map<String, Float?>> = _assetDownloads.asStateFlow()

    private val _bannerImageLocalPath = MutableStateFlow<String?>(null)
    val bannerImageLocalPath: StateFlow<String?> = _bannerImageLocalPath.asStateFlow()

    private val _screenshotLocalPath = MutableStateFlow<String?>(null)
    val screenshotLocalPath: StateFlow<String?> = _screenshotLocalPath.asStateFlow()

    init {
        // When viewer mode switches to READER while FULL_PAGE_ARCHIVE is selected,
        // ensure the archive string is loaded for the native renderer.
        scope.launch {
            viewerMode.collect { mode ->
                if (_selectedSource.value == ContentSource.FULL_PAGE_ARCHIVE
                    && mode == ViewerMode.READER
                    && _sourceContentOverride.value == null
                ) {
                    val localPath = _precrawledAssetPath.value
                    if (localPath != null) loadArchiveContentString(localPath)
                }
            }
        }
    }

    fun setContentSource(source: ContentSource, bookmark: Bookmark?) {
        scope.launch {
            _selectedSource.value = source
            if (source == ContentSource.FULL_PAGE_ARCHIVE) {
                val localPath = _precrawledAssetPath.value
                if (localPath != null) {
                    // Archive is cached — load as string if READER mode needs it
                    if (viewerMode.value == ViewerMode.READER && _sourceContentOverride.value == null) {
                        loadArchiveContentString(localPath)
                    }
                } else if (!isOffline() && bookmark != null) {
                    fetchAndCacheArchive(bookmark)
                } else {
                    snackbarManager.showSnackbar("Full page archive not available offline")
                    _selectedSource.value = ContentSource.EXTRACTED
                }
            } else {
                _sourceContentOverride.value = null
            }
        }
    }

    private suspend fun loadArchiveContentString(localPath: String) {
        try {
            _sourceContentOverride.value = FileUtils.readFileAsText(localPath)
        } catch (e: Exception) {
            AppLogger.e("ViewerModel", "Failed to read archive as string: ${e.message}", e)
        }
    }

    fun fetchAndCacheArchive(bookmark: Bookmark) {
        scope.launch {
            _isLoadingSource.value = true
            try {
                val servers = serverRepository.servers.first()
                val server = servers.find { it.id == bookmark.serverId } ?: run {
                    snackbarManager.showSnackbar("Server not found")
                    _selectedSource.value = ContentSource.EXTRACTED
                    return@launch
                }
                val archive = assetRepository.fetchArchive(server, bookmark)
                if (archive == null) {
                    snackbarManager.showSnackbar("No full page archive available for this bookmark")
                    _selectedSource.value = ContentSource.EXTRACTED
                    return@launch
                }
                _precrawledAssetPath.value = archive.asset.localPath
                _archiveAvailable.value = true
                if (viewerMode.value == ViewerMode.READER) {
                    _sourceContentOverride.value = archive.bytes.decodeToString()
                }
                _selectedSource.value = ContentSource.FULL_PAGE_ARCHIVE
                // Refresh the exposed asset list so the details panel reflects the new localPath
                _assets.value = assetRepository.assetsFor(bookmark)
            } catch (e: Exception) {
                AppLogger.e("ViewerModel", "Failed to fetch archive: ${e.message}", e)
                snackbarManager.showSnackbar("Couldn't load full page archive")
                _selectedSource.value = ContentSource.EXTRACTED
            } finally {
                _isLoadingSource.value = false
            }
        }
    }

    /**
     * Re-reads this bookmark's asset rows and republishes every piece of state derived from
     * them. Called after anything that adds, removes, or caches an asset.
     *
     * [applyPreferredSource] is only true on initial load — later reloads must not yank the
     * user out of the content source they picked.
     */
    suspend fun reloadAssets(
        bookmark: Bookmark,
        applyPreferredSource: Boolean = false
    ) {
        val assets = assetRepository.assetsFor(bookmark)
        _assets.value = assets

        val archive = assets.find { it.assetType == "precrawledArchive" }
            ?: assets.find { it.assetType == "fullPageArchive" }
        _precrawledAssetPath.value = archive?.localPath
        // Archive is "available" if ANY archive row exists (even without localPath),
        // so the "Load full page archive" button appears when the server has it.
        _archiveAvailable.value = archive != null

        if (applyPreferredSource) {
            // Default to FULL_PAGE_ARCHIVE only when the user has explicitly opted in.
            // Without the setting, extracted content is always the default so that
            // "Extracted" source selection reliably shows extracted HTML (or no content).
            val preferArchive = settingsRepository.preferFullPageHtml.first()
            if (archive?.localPath != null && preferArchive && selectedSource.value == ContentSource.EXTRACTED) {
                _selectedSource.value = ContentSource.FULL_PAGE_ARCHIVE
            }
        } else if (selectedSource.value == ContentSource.FULL_PAGE_ARCHIVE && archive?.localPath == null) {
            // The archive we were displaying is gone (deleted locally or on the server).
            _selectedSource.value = ContentSource.EXTRACTED
            _sourceContentOverride.value = null
        }

        _bannerImageLocalPath.value = assets.find { it.assetType == "bannerImage" }?.localPath
        _screenshotLocalPath.value = assets.find { it.assetType == "screenshot" }?.localPath
    }

    fun deleteAssetLocal(asset: AssetEntity) {
        scope.launch {
            try {
                assetRepository.clearLocalCopy(asset)
                val bookmark = currentBookmark() ?: return@launch
                reloadAssets(bookmark)
            } catch (e: Exception) {
                AppLogger.e("ViewerModel", "Failed to delete asset local copy: ${e.message}", e)
                snackbarManager.showSnackbar("Couldn't delete local file")
            }
        }
    }

    /**
     * Delete an asset on the server (not just the local copy). Irreversible — the UI gates
     * this behind a confirmation dialog.
     */
    fun deleteAssetOnServer(asset: AssetEntity, bookmark: Bookmark) {
        scope.launch {
            try {
                val server = serverRepository.servers.first().find { it.id == bookmark.serverId }
                if (server == null) {
                    snackbarManager.showSnackbar("Server not found")
                    return@launch
                }
                assetRepository.detachOnServer(server, bookmark, asset)
                reloadAssets(bookmark)
                snackbarManager.showSnackbar("Deleted from server")
            } catch (e: OfflineModeException) {
                snackbarManager.showSnackbar("Not available in offline mode")
            } catch (e: Exception) {
                AppLogger.e("ViewerModel", "Failed to delete asset on server: ${e.message}", e)
                snackbarManager.showSnackbar("Couldn't delete from server")
            }
        }
    }

    /**
     * Downloads [asset] and stores the local path against that exact row.
     *
     * Archives used to route through [fetchAndCacheArchive], which re-resolved the asset from
     * the server and could stamp the local path onto a *different* archive row — leaving the
     * one the user tapped still reading "On server" with no way to delete its local copy.
     * Downloading by id is also what lets any asset type work here, PDFs included.
     */
    fun downloadOrRefreshAsset(
        asset: AssetEntity,
        bookmark: Bookmark,
        // True when the download was started by tapping the row rather than by picking
        // "Download a copy" from the menu: finish the job the tap implied.
        useWhenDone: Boolean = false
    ) {
        // Claim the slot synchronously — two taps in the same frame would both pass a check
        // made inside the coroutine and start the download twice.
        val before = _assetDownloads.getAndUpdate { current ->
            if (current.containsKey(asset.id)) current else current + (asset.id to null)
        }
        if (before.containsKey(asset.id)) return

        scope.launch {
            try {
                val server = serverRepository.servers.first().find { it.id == bookmark.serverId }
                if (server == null) {
                    snackbarManager.showSnackbar("Server not found")
                    return@launch
                }
                val result = assetRepository.download(server, asset) { fraction ->
                    _assetDownloads.update { it + (asset.id to fraction) }
                }
                val bytes = result.bytes
                reloadAssets(bookmark)

                val isArchive = asset.assetType == "fullPageArchive" || asset.assetType == "precrawledArchive"
                when {
                    // Tapping the row meant "I want to use this", so finish the thought once
                    // the bytes are here rather than making the user tap again.
                    useWhenDone && isArchive -> {
                        _sourceContentOverride.value = bytes.decodeToString()
                        _selectedSource.value = ContentSource.FULL_PAGE_ARCHIVE
                    }
                    useWhenDone && asset.assetType == "pdf" -> openAssetExternally(result.asset)
                    // Not a tap, but the reader is already showing this archive — swap in the
                    // fresh bytes so a re-download is reflected immediately.
                    isArchive && selectedSource.value == ContentSource.FULL_PAGE_ARCHIVE &&
                        viewerMode.value == ViewerMode.READER -> {
                        _sourceContentOverride.value = bytes.decodeToString()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineModeException) {
                snackbarManager.showSnackbar("Not available in offline mode")
            } catch (e: Exception) {
                AppLogger.e("ViewerModel", "Failed to download asset: ${e.message}", e)
                snackbarManager.showSnackbar("Couldn't download asset")
            } finally {
                _assetDownloads.update { it - asset.id }
            }
        }
    }

    /**
     * Hands a downloaded asset to whatever app the platform has registered for it — the app
     * has no PDF renderer of its own.
     */
    fun openAssetExternally(asset: AssetEntity) {
        val localPath = asset.localPath
        if (localPath == null) {
            scope.launch { snackbarManager.showSnackbar("Download it first") }
            return
        }
        scope.launch {
            val opened = FileUtils.openFileExternally(localPath, mimeTypeFor(asset.assetType))
            if (!opened) snackbarManager.showSnackbar("No app available to open this file")
        }
    }

    private fun mimeTypeFor(assetType: String): String = when (assetType) {
        "pdf" -> "application/pdf"
        "fullPageArchive", "precrawledArchive", "linkHtmlContent" -> "text/html"
        "bannerImage", "screenshot" -> "image/*"
        else -> "*/*"
    }
}
