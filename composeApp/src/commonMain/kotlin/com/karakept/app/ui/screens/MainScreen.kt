package com.karakept.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.local.entity.BookmarkEntity
import kotlinx.coroutines.launch

class MainScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<MainScreenModel>()
        val servers by screenModel.servers.collectAsState()
        val selectedServer by screenModel.selectedServer.collectAsState()
        val bookmarks by screenModel.bookmarks.collectAsState()
        
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(12.dp))
                    Text("Servers", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    servers.forEach { server ->
                        NavigationDrawerItem(
                            label = { Text(server.label) },
                            selected = server.id == selectedServer?.id,
                            onClick = {
                                screenModel.selectServer(server.id)
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    NavigationDrawerItem(
                        label = { Text("Add Server") },
                        selected = false,
                        onClick = {
                            navigator.push(LoginScreen())
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
                        title = { Text(selectedServer?.label ?: "Karakept") },
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
                        BookmarkItem(bookmark) {
                             navigator.push(BookmarkViewerScreen(bookmark.localId))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarkItem(bookmark: BookmarkEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = bookmark.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = bookmark.url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
