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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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

class MainScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<MainScreenModel>()
        val settingsScreenModel = koinScreenModel<SettingsScreenModel>()
        val lists by screenModel.lists.collectAsState()
        val selectedServer by screenModel.selectedServer.collectAsState()
        val bookmarks by screenModel.bookmarks.collectAsState()
        val layoutType by settingsScreenModel.layoutType.collectAsState()

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
                            IconButton(onClick = { screenModel.syncBookmarks() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync")
                            }
                        }
                    )
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize()
                ) {
                    items(bookmarks) { bookmark ->
                        when (layoutType) {
                            LayoutType.CARD -> BookmarkCardLayout(
                                bookmark = bookmark,
                                onClick = { navigator.push(BookmarkViewerScreen(bookmark.localId)) }
                            )
                            LayoutType.LIST -> BookmarkListLayout(
                                bookmark = bookmark,
                                onClick = { navigator.push(BookmarkViewerScreen(bookmark.localId)) }
                            )
                        }
                    }
                }
            }
        }
    }
}
