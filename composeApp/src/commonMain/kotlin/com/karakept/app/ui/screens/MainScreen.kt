package com.karakept.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Menu

import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.karakept.app.data.model.FilterConfig
import com.karakept.app.data.model.FilterStatus
import com.karakept.app.ui.components.FilterBottomPanel
import com.karakept.app.ui.components.OfflineModeBadge
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.LayoutType
import com.karakept.app.ui.components.BookmarkCardLayout
import com.karakept.app.ui.components.BookmarkListLayout
import com.karakept.app.ui.components.BookmarkActionsMenu
import com.karakept.app.ui.components.BookmarkAction
import com.karakept.app.ui.components.SwipeableBookmarkItem
import com.karakept.app.data.model.SwipeAction
import kotlinx.coroutines.launch

import getPlatform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key as keyboardKey
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType

class MainScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<MainScreenModel>()
        val settingsScreenModel = koinScreenModel<SettingsScreenModel>()
        val lists by screenModel.lists.collectAsState()
        val selectedServer by screenModel.selectedServer.collectAsState()
        val bookmarks by screenModel.bookmarks.collectAsState()
        val layoutType by settingsScreenModel.layoutType.collectAsState()
        val isSyncing by screenModel.isSyncing.collectAsState()
        val savedFilters by screenModel.savedFilters.collectAsState()
        val currentFilter by screenModel.currentFilter.collectAsState()
        val offlineMode by settingsScreenModel.offlineMode.collectAsState()
        val showReadingTimeBadge by settingsScreenModel.showReadingTimeBadge.collectAsState()
        val swipeLeftAction by screenModel.swipeLeftAction.collectAsState()
        val swipeRightAction by screenModel.swipeRightAction.collectAsState()
        val dimReadBookmarks by screenModel.dimReadBookmarks.collectAsState()

        var showFilterDialog by remember { mutableStateOf(false) }
        var selectedBookmarkForActions by remember { mutableStateOf<com.karakept.app.data.local.entity.BookmarkEntity?>(null) }
        val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

        val listState = rememberLazyListState()
        val isDesktop = remember { getPlatform().name.contains("Java") }
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

        // Auto-scroll to top when new bookmarks are added at the beginning
        LaunchedEffect(bookmarks.firstOrNull()?.remoteId) {
            // Only scroll if we're near the top (first 3 items visible)
            if (listState.firstVisibleItemIndex <= 2 && bookmarks.isNotEmpty()) {
                listState.animateScrollToItem(0)
            }
        }

        val pullRefreshState = rememberPullRefreshState(
            refreshing = isSyncing,
            onRefresh = { if (!offlineMode) screenModel.syncBookmarks() }
        )

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        
        val allBookmarks by screenModel.allBookmarks.collectAsState()

        // Extract all unique tags from ALL bookmarks for filter dialog
        val allAvailableTags = remember(allBookmarks) {
            allBookmarks.flatMap { it.tags.split(",").filter { tag -> tag.isNotBlank() } }.distinct().sortedBy { it.lowercase() }
        }
        
        // Get top 10 most used tags with counts
        val topTagsWithCounts = remember(allBookmarks) {
            allBookmarks
                .flatMap { it.tags.split(",").filter { tag -> tag.isNotBlank() } }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(10)
                .map { "${it.key} (${it.value})" }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(12.dp))
                    
                    // Quick Filters Section
                    Text("Quick Filters", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    
                    NavigationDrawerItem(
                        label = { Text("All Bookmarks") },
                        selected = currentFilter == FilterConfig(),
                        icon = { Icon(Icons.Default.Book, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        onClick = {
                            screenModel.clearFilter()
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    NavigationDrawerItem(
                        label = { Text("Favorites") },
                        selected = currentFilter == FilterConfig(status = FilterStatus.FAVORITES),
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        onClick = {
                            screenModel.applyFilter(FilterConfig(status = FilterStatus.FAVORITES))
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    NavigationDrawerItem(
                        label = { Text("Not Archived") },
                        selected = currentFilter == FilterConfig(status = FilterStatus.NOT_ARCHIVED),
                        icon = { Icon(Icons.Default.Inbox, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        onClick = {
                            screenModel.applyFilter(FilterConfig(status = FilterStatus.NOT_ARCHIVED))
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    NavigationDrawerItem(
                        label = { Text("Archived") },
                        selected = currentFilter == FilterConfig(status = FilterStatus.ARCHIVED),
                        icon = { Icon(Icons.Default.Archive, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        onClick = {
                            screenModel.applyFilter(FilterConfig(status = FilterStatus.ARCHIVED))
                            scope.launch { drawerState.close() }
                        }
                    )

                    // Lists Section (Hierarchical)
                    if (lists.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Lists", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)

                        val hierarchy = remember(lists) { buildHierarchy(lists) }
                        val expandedIds by screenModel.expandedLists.collectAsState()
                        val visibleHierarchy = remember(hierarchy, expandedIds) {
                            filterExpandedHierarchy(hierarchy, expandedIds)
                        }

                        visibleHierarchy.forEach { (list, depth) ->
                            key(list.id) {
                                val hasChildLists = hasChildren(list.id, lists)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Spacer(Modifier.width((depth * 16).dp)) // Indentation

                                    // Expand/collapse icon
                                    if (hasChildLists) {
                                        IconButton(
                                            onClick = { screenModel.toggleListExpanded(list.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (expandedIds.contains(list.id)) {
                                                    Icons.Default.KeyboardArrowDown
                                                } else {
                                                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                                                },
                                                contentDescription = if (expandedIds.contains(list.id)) "Collapse" else "Expand",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.width(24.dp))
                                    }

                                    NavigationDrawerItem(
                                        label = { Text("${list.icon} ${list.name}") },
                                        selected = currentFilter.lists.contains(list.id),
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary
                                        ),
                                        onClick = {
                                            screenModel.applyFilter(FilterConfig(lists = listOf(list.id)))
                                            scope.launch { drawerState.close() }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Saved Filters Section (Shortcuts)
                    if (savedFilters.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Saved Filters", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { 
                                navigator.push(FilterManagementScreen())
                                scope.launch { drawerState.close() }
                            }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Manage Filters")
                            }
                        }
                        savedFilters.forEach { savedFilter ->
                            val isSelected = try {
                                kotlinx.serialization.json.Json.decodeFromString<FilterConfig>(savedFilter.configJson) == currentFilter
                            } catch (e: Exception) { false }
                            
                            SavedFilterItem(
                                savedFilter = savedFilter,
                                selected = isSelected,
                                onApply = {
                                    screenModel.applySavedFilter(savedFilter)
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        selected = false,
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        onClick = {
                            navigator.push(SettingsScreen())
                            scope.launch { drawerState.close() }
                        }
                    )

                }
            }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize().onKeyEvent { keyEvent ->
                    if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) &&
                        keyEvent.keyboardKey == Key.R &&
                        keyEvent.type == KeyEventType.KeyDown) {
                        screenModel.syncBookmarks()
                        true
                    } else {
                        false
                    }
                },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Karakept")
                                if (offlineMode) {
                                    Spacer(Modifier.width(8.dp))
                                    OfflineModeBadge()
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showFilterDialog = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter")
                            }
                            if (isDesktop) {
                                IconButton(
                                    onClick = { screenModel.syncBookmarks() },
                                    enabled = !offlineMode
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Sync")
                                }
                            }
                        }
                    )
                },
                snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.keyboardKey == Key.R &&
                                (event.isCtrlPressed || event.isMetaPressed) &&
                                !offlineMode) {
                                screenModel.syncBookmarks()
                                true
                            } else {
                                false
                            }
                        }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState
                    ) {
                        items(bookmarks, key = { it.remoteId }) { bookmark ->
                            val onClick = remember(bookmark.localId, navigator) {
                                { navigator.push(BookmarkViewerScreen(bookmark.localId)) }
                            }

                            Box(modifier = Modifier.animateItemPlacement()) {
                                // Dynamic icon logic for Mark Read/Unread
                                val leftIcon = if (swipeLeftAction == SwipeAction.MARK_READ) {
                                    if (bookmark.isRead) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                } else null
                                
                                val rightIcon = if (swipeRightAction == SwipeAction.MARK_READ) {
                                    if (bookmark.isRead) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                } else null

                                SwipeableBookmarkItem(
                                leftSwipeAction = swipeLeftAction,
                                rightSwipeAction = swipeRightAction,
                                leftIcon = leftIcon,
                                rightIcon = rightIcon,
                                onActionTriggered = { action ->
                                    when (action) {
                                        SwipeAction.ARCHIVE -> {
                                            screenModel.toggleBookmarkArchive(bookmark, onActionComplete = { message ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = message,
                                                        duration = androidx.compose.material3.SnackbarDuration.Short
                                                    )
                                                }
                                            })
                                        }
                                        SwipeAction.MARK_READ -> {
                                            screenModel.toggleBookmarkRead(bookmark, onActionComplete = { message ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = message,
                                                        duration = androidx.compose.material3.SnackbarDuration.Short
                                                    )
                                                }
                                            })
                                        }
                                        SwipeAction.FAVOURITE -> {
                                            screenModel.toggleBookmarkFavorite(bookmark, onActionComplete = { message ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = message,
                                                        duration = androidx.compose.material3.SnackbarDuration.Short
                                                    )
                                                }
                                            })
                                        }
                                        SwipeAction.DELETE -> selectedBookmarkForActions = bookmark // Show dialog for confirmation
                                        SwipeAction.SHARE -> {
                                            com.karakept.app.utils.ShareUtils.shareText(bookmark.url, bookmark.title)
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = "Shared",
                                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                                )
                                            }
                                        }
                                        SwipeAction.OPEN_IN_BROWSER -> {
                                            try {
                                                uriHandler.openUri(bookmark.url)
                                            } catch (e: Exception) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Could not open link")
                                                }
                                            }
                                        }
                                        SwipeAction.NONE -> {}
                                    }
                                }
                            ) {
                                when (layoutType) {
                                    LayoutType.CARD -> BookmarkCardLayout(
                                        bookmark = bookmark,
                                        onClick = onClick,
                                        onLongClick = { selectedBookmarkForActions = bookmark },
                                        showReadingTime = showReadingTimeBadge,
                                        dimRead = dimReadBookmarks
                                    )
                                    LayoutType.LIST -> BookmarkListLayout(
                                        bookmark = bookmark,
                                        onClick = onClick,
                                        onLongClick = { selectedBookmarkForActions = bookmark },
                                        showReadingTime = showReadingTimeBadge,
                                        dimRead = dimReadBookmarks
                                    )
                                }
                            }
                            }
                        }
                    }

                    if (isSyncing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        )
                    }

                    PullRefreshIndicator(
                        refreshing = isSyncing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }

        // Back Handler for filter panel
        androidx.activity.compose.BackHandler(enabled = showFilterDialog) {
            showFilterDialog = false
        }

        // Scrim for Filter Panel
        androidx.compose.animation.AnimatedVisibility(
            visible = showFilterDialog,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        showFilterDialog = false
                    }
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            FilterBottomPanel(
                visible = showFilterDialog,
                currentFilter = currentFilter,
                availableTags = topTagsWithCounts,
                allTags = allAvailableTags,
                availableLists = lists,
                onDismiss = { showFilterDialog = false },
                onFilterChange = { filter ->
                    screenModel.applyFilter(filter)
                },
                onSaveFilter = { name, icon, color, isDefault ->
                    screenModel.saveFilter(name, icon = icon, color = color, isDefault = isDefault)
                },
                onReset = {
                    screenModel.applyFilter(FilterConfig())
                }
            )
        }
        
        // Bookmark Actions Menu
        if (selectedBookmarkForActions != null) {
            BookmarkActionsMenu(
                bookmark = selectedBookmarkForActions!!,
                availableLists = lists,
                onAction = { action ->
                    when (action) {
                        is BookmarkAction.ToggleArchive -> {
                            screenModel.toggleBookmarkArchive(selectedBookmarkForActions!!) { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        }
                        is BookmarkAction.ToggleFavorite -> {
                            screenModel.toggleBookmarkFavorite(selectedBookmarkForActions!!) { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        }
                        is BookmarkAction.ToggleRead -> {
                            screenModel.toggleBookmarkRead(selectedBookmarkForActions!!) { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        }
                        is BookmarkAction.MoveToList -> {
                            screenModel.moveBookmarkToList(selectedBookmarkForActions!!, action.listId)
                        }
                        is BookmarkAction.UpdateTags -> {
                            screenModel.updateBookmarkTags(selectedBookmarkForActions!!, action.tags)
                        }
                        is BookmarkAction.Delete -> {
                            screenModel.deleteBookmark(selectedBookmarkForActions!!) { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        }
                        is BookmarkAction.Share -> {
                            com.karakept.app.utils.ShareUtils.shareText(selectedBookmarkForActions!!.url, selectedBookmarkForActions!!.title)
                        }
                        is BookmarkAction.OpenInBrowser -> {
                            // Open in browser - would need platform-specific implementation
                        }
                    }
                },
                onDismiss = { selectedBookmarkForActions = null }
            )
        }
    }
}

// Hierarchy helper functions for list display

/**
 * Builds a hierarchical list structure from flat list.
 * Returns list of (ListDto, depth) pairs in display order.
 * Reused from ListManagementScreen.kt
 */
private fun buildHierarchy(lists: List<com.karakept.app.data.remote.model.ListDto>): List<Pair<com.karakept.app.data.remote.model.ListDto, Int>> {
    val result = mutableListOf<Pair<com.karakept.app.data.remote.model.ListDto, Int>>()
    val grouped = lists.groupBy { it.parentId }
    val visited = mutableSetOf<String>() // Prevent circular refs

    fun recurse(parentId: String?, depth: Int) {
        if (depth > 10) return // Max depth protection
        val children = grouped[parentId] ?: return
        children.forEach { child ->
            if (!visited.contains(child.id)) {
                visited.add(child.id)
                result.add(child to depth)
                recurse(child.id, depth + 1)
            }
        }
    }

    recurse(null, 0)

    // Handle orphans
    val processed = result.map { it.first.id }.toSet()
    lists.filter { it.id !in processed }.forEach { list ->
        result.add(list to 0)
    }

    return result
}

/**
 * Filters hierarchy to only show expanded branches.
 * A list is visible only if ALL its ancestors are expanded.
 */
private fun filterExpandedHierarchy(
    hierarchy: List<Pair<com.karakept.app.data.remote.model.ListDto, Int>>,
    expandedIds: Set<String>
): List<Pair<com.karakept.app.data.remote.model.ListDto, Int>> {
    val result = mutableListOf<Pair<com.karakept.app.data.remote.model.ListDto, Int>>()

    // Build a map of list ID to its hierarchy entry for quick lookup
    val listMap = hierarchy.associateBy { it.first.id }

    hierarchy.forEach { (list, depth) ->
        val shouldShow = if (depth == 0) {
            true // Root items always visible
        } else {
            // Check if ALL ancestors in the parent chain are expanded
            var allAncestorsExpanded = true
            var currentParentId = list.parentId

            while (currentParentId != null && allAncestorsExpanded) {
                if (!expandedIds.contains(currentParentId)) {
                    allAncestorsExpanded = false
                }
                // Move to next ancestor
                currentParentId = listMap[currentParentId]?.first?.parentId
            }

            allAncestorsExpanded
        }

        if (shouldShow) {
            result.add(list to depth)
        }
    }

    return result
}

/**
 * Checks if a list has children
 */
private fun hasChildren(listId: String, allLists: List<com.karakept.app.data.remote.model.ListDto>): Boolean {
    return allLists.any { it.parentId == listId }
}
