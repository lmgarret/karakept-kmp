package com.karakept.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.karakept.app.data.model.LayoutType
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
data class LayoutEditorScreen(val layoutId: String?) : NavKey {
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<LayoutEditorScreenModel>()

        LaunchedEffect(layoutId) {
            if (layoutId != null) screenModel.loadExisting(layoutId)
        }

        LayoutEditorContent(
            screenModel = screenModel,
            isEditing = layoutId != null,
            onBack = { navigator.pop() },
            onSave = {
                screenModel.save()
                navigator.pop()
            }
        )
    }
}

/**
 * Layout editor as a dialog, used on desktop to avoid 4-level settings depth.
 */
@Composable
internal fun LayoutEditorDialog(
    layoutId: String?,
    onDismiss: () -> Unit
) {
    val screenModel = org.koin.compose.koinInject<LayoutEditorScreenModel>()

    LaunchedEffect(layoutId) {
        if (layoutId != null) screenModel.loadExisting(layoutId)
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .fillMaxSize(0.9f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            LayoutEditorContent(
                screenModel = screenModel,
                isEditing = layoutId != null,
                onBack = onDismiss,
                onSave = {
                    screenModel.save()
                    onDismiss()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutEditorContent(
    screenModel: LayoutEditorScreenModel,
    isEditing: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val layout by screenModel.layout.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Layout" else "New Layout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = layout.name.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pinned preview — does not scroll
            // Card layouts include a 200.dp image area so they need more vertical space
            val previewMaxHeight = if (LayoutType.fromString(layout.layoutType) == LayoutType.CARD) 420.dp else 280.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = previewMaxHeight)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                PreviewBookmarkItem(layout = layout)
            }

            HorizontalDivider()

            // Scrollable settings
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                val isList = layout.layoutType != LayoutType.CARD.name
                LayoutInfoSection(layout, screenModel)
                LayoutTypeSection(layout, screenModel)
                if (isList) LayoutRowStyleSection(layout, screenModel)
                if (isList) LayoutThumbnailSection(layout, screenModel)
                LayoutDescriptionSection(layout, screenModel)
                LayoutLinkSection(layout, screenModel)
                LayoutFaviconSection(layout, screenModel)
                LayoutDateSection(layout, screenModel)
                LayoutReadingTimeSection(layout, screenModel)
                LayoutTagsSection(layout, screenModel)
                if (isList) LayoutMetadataPositionSection(layout, screenModel)
                LayoutAppearanceSection(layout, screenModel)

                Spacer(modifier = Modifier.height(56.dp))
            }
        }
    }
}
