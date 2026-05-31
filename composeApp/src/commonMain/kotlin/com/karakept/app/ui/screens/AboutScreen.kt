package com.karakept.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import coil3.compose.AsyncImage
import com.karakept.app.utils.FaviconUtils

data class FossLibrary(
    val name: String,
    val license: String,
    val url: String
)

@Serializable
class AboutScreen : NavKey {
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        AboutContent(onBack = { navigator.pop() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutContent(
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Karakept",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "v1.0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Client for Karakeep",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                item {
                    HorizontalDivider()
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri("https://github.com/lmgarret/karakept-kmp") }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "GitHub Repository",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "https://github.com/lmgarret/karakept-kmp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider()
                }

                item {
                    Text(
                        text = "Open Source Libraries",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                val libraries = listOf(
                    FossLibrary("Kotlin Multiplatform", "Apache 2.0", "https://github.com/JetBrains/kotlin"),
                    FossLibrary("Compose Multiplatform", "Apache 2.0", "https://github.com/JetBrains/compose-multiplatform"),
                    FossLibrary("Navigation 3", "Apache 2.0", "https://developer.android.com/guide/navigation/navigation-3"),
                    FossLibrary("AndroidX Lifecycle (Multiplatform)", "Apache 2.0", "https://github.com/JetBrains/compose-multiplatform-core"),
                    FossLibrary("Ktor", "Apache 2.0", "https://github.com/ktorio/ktor"),
                    FossLibrary("Coil", "Apache 2.0", "https://github.com/coil-kt/coil"),
                    FossLibrary("Koin", "Apache 2.0", "https://github.com/InsertKoinIO/koin"),
                    FossLibrary("Room", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/room"),
                    FossLibrary("SQLite Bundled", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/sqlite"),
                    FossLibrary("Kotlinx Coroutines", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
                    FossLibrary("Kotlinx Serialization", "Apache 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
                    FossLibrary("Kotlinx DateTime", "Apache 2.0", "https://github.com/Kotlin/kotlinx-datetime"),
                    FossLibrary("DataStore", "Apache 2.0", "https://developer.android.com/topic/libraries/architecture/datastore"),
                    FossLibrary("Ksoup", "MIT", "https://github.com/fleeksoft/ksoup"),
                    FossLibrary("ComposeWebView", "MIT", "https://github.com/kdroidFilter/ComposeNativeWebview"),
                    FossLibrary("ComposeNativeTray", "MIT", "https://github.com/kdroidFilter/ComposeNativeTray"),
                    FossLibrary("KNotify", "MIT", "https://github.com/kdroidFilter/KNotify"),
                    FossLibrary("nativefiledialog-java", "zlib", "https://github.com/WonderzGmbH/nativefiledialog-java"),
                    FossLibrary("AndroidX Activity Compose", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/activity"),
                    FossLibrary("AndroidX Browser", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/browser"),
                    FossLibrary("AndroidX Core SplashScreen", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/core"),
                    FossLibrary("JavaFX", "GPL 2.0 with Classpath Exception", "https://github.com/openjdk/jfx"),
                    FossLibrary("OkHttp", "Apache 2.0", "https://github.com/square/okhttp"),
                    FossLibrary("WorkManager", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/work"),
                )

                items(libraries) { lib ->
                    FossLibraryCard(lib) {
                        uriHandler.openUri(lib.url)
                    }
                }

                item {
                    HorizontalDivider()
                }

                item {
                    Text(
                        text = "Fonts",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                val fonts = listOf(
                    FossLibrary("JetBrains Mono", "SIL OFL 1.1", "https://github.com/JetBrains/JetBrainsMono"),
                    FossLibrary("Lora", "SIL OFL 1.1", "https://github.com/cyreal/lora"),
                    FossLibrary("Merriweather", "SIL OFL 1.1", "https://github.com/EbenSorkin/Merriweather"),
                    FossLibrary("Noto Sans", "SIL OFL 1.1", "https://github.com/googlefonts/noto-fonts"),
                    FossLibrary("OpenDyslexic", "SIL OFL 1.1", "https://github.com/antijingoist/opendyslexic"),
                )

                items(fonts) { font ->
                    FossLibraryCard(font) {
                        uriHandler.openUri(font.url)
                    }
                }
            }
        }
    }

@Composable
fun FossLibraryCard(library: FossLibrary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = FaviconUtils.getFaviconUrl(library.url),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit,
                error = null, // Fallback could be added here if needed
                placeholder = null
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = library.license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = "Open Website",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
