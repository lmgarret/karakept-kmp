package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
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
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
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

        val isDesktop = remember { getPlatform().name.contains("Java") }

        val pullRefreshState = rememberPullRefreshState(
            refreshing = isSyncing,
            onRefresh = { screenModel.syncBookmarks() }
        )

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(12.dp))
                    Text("Lists", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    lists.forEach { list ->
                        NavigationDrawerItem(
                            label = { Text("${list.icon} ${list.name}") },
                            selected = false,
                            onClick = {
                                // TODO: Filter bookmarks by list
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Filters", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    
                    NavigationDrawerItem(
                        label = { Text("All Bookmarks") },
                        selected = false,
                        icon = { Icon(Icons.Default.Book, contentDescription = null) },
                        onClick = {
                            screenModel.clearFilter()
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    NavigationDrawerItem(
                        label = { Text("Favorites") },
                        selected = false,
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        onClick = {
                            screenModel.applyFilter("is:fav")
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    NavigationDrawerItem(
                        label = { Text("Not Archived") },
                        selected = false,
                        icon = { Icon(Icons.Default.Inbox, contentDescription = null) },
                        onClick = {
                            screenModel.applyFilter("-is:archived")
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    NavigationDrawerItem(
                        label = { Text("Archived") },
                        selected = false,
                        icon = { Icon(Icons.Default.Archive, contentDescription = null) },
                        onClick = {
                            screenModel.applyFilter("is:archived")
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    Spacer(Modifier.weight(1f))
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        selected = false,
                        onClick = {
                            navigator.push(SettingsScreen())
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("About") },
                        selected = false,
                        onClick = {
                            navigator.push(AboutScreen())
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
                            // Remember the click action to avoid recomposing the item when MainScreen recomposes
                            // (though MainScreen shouldn't recompose often if state is stable)
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
    }
}
