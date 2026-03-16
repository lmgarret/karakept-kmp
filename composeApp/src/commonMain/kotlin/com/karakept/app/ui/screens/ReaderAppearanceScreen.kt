package com.karakept.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.karakept.app.data.model.ReaderFontFamily
import com.karakept.app.ui.theme.rememberFontFamily
import com.karakept.app.ui.components.ReaderAppearanceBottomPanel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class ReaderAppearanceScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ReaderAppearanceScreenModel>()
        val scope = rememberCoroutineScope()

        // Collect all settings
        val textColor by screenModel.htmlTextColor.collectAsState()
        val backgroundColor by screenModel.htmlBackgroundColor.collectAsState()
        val fontSize by screenModel.htmlFontSize.collectAsState()
        val fontFamily by screenModel.htmlFontFamily.collectAsState()

        var showBottomPanel by remember { mutableStateOf(true) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Reader Appearance") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Preview section with lorem ipsum
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (backgroundColor != null) {
                                Modifier.background(backgroundColor!!)
                            } else {
                                Modifier.background(MaterialTheme.colorScheme.surface)
                            }
                        )
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Preview Text",
                            color = textColor ?: MaterialTheme.colorScheme.onSurface,
                            fontSize = (fontSize * 1.5f).sp,
                            fontFamily = getFontFamily(fontFamily),
                            lineHeight = (fontSize * 2f).sp,
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Text(
                            text = "Adjust the settings below to customize how articles appear in reader mode. This is example text to help you preview your changes.",
                            color = textColor ?: MaterialTheme.colorScheme.onSurface,
                            fontSize = fontSize.sp,
                            fontFamily = getFontFamily(fontFamily),
                            lineHeight = (fontSize * 1.6f).sp
                        )

                        Text(
                            text = "Reading Experience",
                            color = textColor ?: MaterialTheme.colorScheme.onSurface,
                            fontSize = (fontSize * 1.3f).sp,
                            fontFamily = getFontFamily(fontFamily),
                            lineHeight = (fontSize * 1.8f).sp,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Text(
                            text = "The quick brown fox jumps over the lazy dog. This sentence contains every letter of the alphabet, making it perfect for previewing fonts.",
                            color = textColor ?: MaterialTheme.colorScheme.onSurface,
                            fontSize = fontSize.sp,
                            fontFamily = getFontFamily(fontFamily),
                            lineHeight = (fontSize * 1.6f).sp
                        )

                        Text(
                            text = "Customization Options",
                            color = textColor ?: MaterialTheme.colorScheme.onSurface,
                            fontSize = (fontSize * 1.3f).sp,
                            fontFamily = getFontFamily(fontFamily),
                            lineHeight = (fontSize * 1.8f).sp,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Text(
                            text = "You can change the text size, font family, text color, and background color to suit your reading preferences. All changes are saved automatically and apply only to reader mode.",
                            color = textColor ?: MaterialTheme.colorScheme.onSurface,
                            fontSize = fontSize.sp,
                            fontFamily = getFontFamily(fontFamily),
                            lineHeight = (fontSize * 1.6f).sp
                        )
                    }
                }

                // Bottom panel overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.BottomCenter
                ) {
                    ReaderAppearanceBottomPanel(
                        visible = showBottomPanel,
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                        onTextColorChange = { screenModel.setHtmlTextColor(it) },
                        onBackgroundColorChange = { screenModel.setHtmlBackgroundColor(it) },
                        onFontSizeChange = { screenModel.setHtmlFontSize(it) },
                        onFontFamilyChange = { screenModel.setHtmlFontFamily(it) },
                        onReset = {
                            scope.launch {
                                screenModel.resetReaderAppearance()
                            }
                        },
                        onDismiss = { showBottomPanel = false },
                        allowDismiss = false
                    )
                }
            }
        }
    }
}

@Composable
private fun getFontFamily(family: ReaderFontFamily): FontFamily {
    return family.rememberFontFamily()
}
