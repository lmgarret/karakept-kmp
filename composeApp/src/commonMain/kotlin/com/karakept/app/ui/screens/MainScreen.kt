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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.ExperimentalMaterialApi
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
import kotlinx.coroutines.launch

import getPlatform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
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

        var showFilterDialog by remember { mutableStateOf(false) }

        val isDesktop = remember { getPlatform().name.contains("Java") }

        val pullRefreshState = rememberPullRefreshState(
            refreshing = isSyncing,
            onRefresh = { screenModel.syncBookmarks() }
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

                    // Lists Section
                    if (lists.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Lists", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                        lists.forEach { list ->
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
                                }
                            )
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
                topBar = {
                    TopAppBar(
                        title = { Text("Karakept") },
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
                                IconButton(onClick = { screenModel.syncBookmarks() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Sync")
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && 
                                event.key == Key.R && 
                                (event.isCtrlPressed || event.isMetaPressed)) {
                                screenModel.syncBookmarks()
                                true
                            } else {
                                false
                            }
                        }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(bookmarks, key = { it.localId }) { bookmark ->
                            val onClick = remember(bookmark.localId, navigator) {
                                { navigator.push(BookmarkViewerScreen(bookmark.localId)) }
                            }
                            
                            when (layoutType) {
                                LayoutType.CARD -> BookmarkCardLayout(
                                    bookmark = bookmark,
                                    onClick = onClick
                                )
                                LayoutType.LIST -> BookmarkListLayout(
                                    bookmark = bookmark,
                                    onClick = onClick
                                )
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
                onSaveFilter = { name, icon, isDefault ->
                    screenModel.saveFilter(name, icon = icon, isDefault = isDefault)
                },
                onReset = {
                    screenModel.applyFilter(FilterConfig())
                }
            )
        }
    }
}
