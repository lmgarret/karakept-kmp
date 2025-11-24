package com.karakept.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.local.entity.BookmarkEntity

data class BookmarkViewerScreen(val bookmarkId: Long) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<BookmarkViewerScreenModel>()
        
        var bookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
        
        LaunchedEffect(bookmarkId) {
            bookmark = screenModel.getBookmark(bookmarkId)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(bookmark?.title ?: "Loading...") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                bookmark?.let { b ->
                    Text(text = b.url, style = MaterialTheme.typography.labelSmall)
                    // Render HTML content here. For now, just text.
                    // To render HTML, we might need a WebView or a HTML parser.
                    // Since KMP WebView is tricky, we can use a simple text display or a library like `multiplatform-markdown-renderer` if it was markdown.
                    // For HTML, we can try to strip tags or display as is for now.
                    Text(text = b.content ?: "No content", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
