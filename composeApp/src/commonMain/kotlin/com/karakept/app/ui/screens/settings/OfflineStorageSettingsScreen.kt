package com.karakept.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.domain.OfflineRetention
import com.karakept.app.ui.icons.AppIcons
import com.karakept.app.ui.navigation.LocalNavigator
import com.karakept.app.ui.navigation.currentOrThrow
import com.karakept.app.ui.screens.SettingsScreenModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Serializable
class OfflineStorageSettingsScreen : NavKey {
    @Composable
    fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinViewModel<SettingsScreenModel>()
        OfflineStorageSettingsContent(
            screenModel = screenModel,
            onBack = { navigator.pop() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineStorageSettingsContent(
    screenModel: SettingsScreenModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val syncStrategy by screenModel.contentSyncStrategy.collectAsState()
    val retentionEnabled by screenModel.offlineRetentionEnabled.collectAsState()
    val retentionDays by screenModel.offlineRetentionDays.collectAsState()

    val strategies = SyncStrategy.entries.filter { it != SyncStrategy.PER_LIST }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Storage") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(AppIcons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Content Sync Mode",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                strategies.forEach { strategy ->
                    SyncStrategyOptionCard(
                        strategy = strategy,
                        isSelected = strategy == syncStrategy,
                        onClick = { screenModel.setContentSyncStrategy(strategy) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You can also toggle offline sync per list. Long-press any list in the sidebar and open its settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Cleanup",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OfflineRetentionCard(
                enabled = retentionEnabled,
                days = retentionDays,
                onEnabledChange = screenModel::setOfflineRetentionEnabled,
                onDaysChange = screenModel::setOfflineRetentionDays
            )
        }
    }
}

@Composable
private fun OfflineRetentionCard(
    enabled: Boolean,
    days: Int,
    onEnabledChange: (Boolean) -> Unit,
    onDaysChange: (Int) -> Unit
) {
    // Follows the finger locally and saves once on release: writing the setting on every drag
    // frame would round-trip DataStore dozens of times per gesture.
    var dragged by remember(days) { mutableFloatStateOf(days.toFloat()) }
    val shownDays = dragged.roundToInt()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.Default.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remove read offline copies",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Frees the stored article and images of bookmarks read or archived a while ago. The bookmark itself stays.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }
            if (enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = offlineRetentionLabel(shownDays),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = dragged,
                    onValueChange = { dragged = it },
                    onValueChangeFinished = { onDaysChange(dragged.roundToInt()) },
                    valueRange = OfflineRetention.MIN_DAYS.toFloat()..OfflineRetention.MAX_DAYS.toFloat(),
                    steps = OfflineRetention.MAX_DAYS - OfflineRetention.MIN_DAYS - 1
                )
            }
        }
    }
}

internal fun offlineRetentionLabel(days: Int): String =
    if (days == 1) "After 1 day" else "After $days days"

@Composable
private fun SyncStrategyOptionCard(
    strategy: SyncStrategy,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )

            Spacer(modifier = Modifier.padding(start = 12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (strategy) {
                        SyncStrategy.NEVER -> "Never (Online Only)"
                        SyncStrategy.PER_BOOKMARK -> "Per Bookmark (When Viewed)"
                        SyncStrategy.PER_LIST -> "Per List (Specific Lists)"
                        SyncStrategy.ALL -> "All Bookmarks"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (strategy) {
                        SyncStrategy.NEVER -> "Content is fetched only when you open a bookmark. Nothing is stored locally."
                        SyncStrategy.PER_BOOKMARK -> "Content is fetched and stored locally when you open a bookmark."
                        SyncStrategy.PER_LIST -> "Content for selected lists is automatically synced and stored."
                        SyncStrategy.ALL -> "Content for all bookmarks is stored locally. WARNING: this may cause slower sync times and increased storage usage."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
