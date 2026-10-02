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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
    val storageCapEnabled by screenModel.offlineStorageCapEnabled.collectAsState()
    val storageCapMb by screenModel.offlineStorageCapMb.collectAsState()

    val storageModel = koinViewModel<OfflineStorageScreenModel>()
    val usage by storageModel.usage.collectAsState()
    val estimate by storageModel.estimate.collectAsState()
    val isWorking by storageModel.isWorking.collectAsState()
    val lastResult by storageModel.lastResult.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val activeRetentionDays = retentionDays.takeIf { retentionEnabled }
    val activeCapMb = storageCapMb.takeIf { storageCapEnabled }

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
            StorageOverviewCard(
                usage = usage,
                storageCapMb = activeCapMb,
                isWorking = isWorking,
                onClear = { confirmClear = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            OfflineStorageCapCard(
                enabled = storageCapEnabled,
                megabytes = storageCapMb,
                onEnabledChange = screenModel::setOfflineStorageCapEnabled,
                onMegabytesChange = screenModel::setOfflineStorageCapMb
            )

            Spacer(modifier = Modifier.height(12.dp))

            CleanupNowCard(
                estimate = estimate,
                retentionDays = activeRetentionDays,
                storageCapMb = activeCapMb,
                isWorking = isWorking,
                lastResult = lastResult,
                onCleanUp = storageModel::cleanUpNow
            )
        }
    }

    val shownUsage = usage
    if (confirmClear && shownUsage != null) {
        ClearOfflineCopiesDialog(
            usage = shownUsage,
            syncStrategy = syncStrategy,
            onConfirm = {
                confirmClear = false
                storageModel.clearAll()
            },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun OfflineRetentionCard(
    enabled: Boolean,
    days: Int,
    onEnabledChange: (Boolean) -> Unit,
    onDaysChange: (Int) -> Unit
) {
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
                        text = "Frees the stored article and images of bookmarks once they have been read or archived for a while. The bookmark itself stays.",
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
                RetentionPeriodPicker(days = days, onDaysChange = onDaysChange)
            }
        }
    }
}

/**
 * A slider over [OfflineRetention.SLIDER_STOPS] and a field taking any number of days. A typed
 * value between two stops parks the slider on the nearest one; the label always names the value.
 */
@Composable
private fun RetentionPeriodPicker(
    days: Int,
    onDaysChange: (Int) -> Unit
) {
    // Both follow input locally. The slider saves once on release — writing the setting on every
    // drag frame would round-trip DataStore dozens of times per gesture.
    var sliderIndex by remember(days) { mutableFloatStateOf(OfflineRetention.nearestStopIndex(days).toFloat()) }
    var fieldText by remember(days) { mutableStateOf(days.toString()) }
    val stopDays = OfflineRetention.SLIDER_STOPS[sliderIndex.roundToInt()]
    val shownDays = fieldText.toIntOrNull()?.takeIf { it >= OfflineRetention.MIN_DAYS } ?: stopDays

    Text(
        text = offlineRetentionLabel(shownDays),
        style = MaterialTheme.typography.bodyMedium
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = sliderIndex,
            onValueChange = {
                sliderIndex = it
                fieldText = OfflineRetention.SLIDER_STOPS[it.roundToInt()].toString()
            },
            onValueChangeFinished = { onDaysChange(OfflineRetention.SLIDER_STOPS[sliderIndex.roundToInt()]) },
            valueRange = 0f..OfflineRetention.SLIDER_STOPS.lastIndex.toFloat(),
            steps = OfflineRetention.SLIDER_STOPS.size - 2,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedTextField(
            value = fieldText,
            onValueChange = { input ->
                val digits = input.filter(Char::isDigit).take(OfflineRetention.MAX_DAYS.toString().length)
                fieldText = digits
                digits.toIntOrNull()?.takeIf { it >= OfflineRetention.MIN_DAYS }?.let { typed ->
                    sliderIndex = OfflineRetention.nearestStopIndex(typed).toFloat()
                    onDaysChange(typed)
                }
            },
            label = { Text("Days") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp)
        )
    }
}

@Composable
private fun OfflineStorageCapCard(
    enabled: Boolean,
    megabytes: Int,
    onEnabledChange: (Boolean) -> Unit,
    onMegabytesChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.Default.OfflinePin,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Limit offline storage",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Past this, the least recently opened offline copies are removed — read and archived ones first. Opening one stores it again.",
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
                StorageCapPicker(megabytes = megabytes, onMegabytesChange = onMegabytesChange)
            }
        }
    }
}

/**
 * A slider over [OfflineRetention.CAP_SLIDER_STOPS_MB] and a field taking any size in MB or GB,
 * the unit toggled by the button beside it. Mirrors [RetentionPeriodPicker].
 */
@Composable
private fun StorageCapPicker(
    megabytes: Int,
    onMegabytesChange: (Int) -> Unit
) {
    val stops = OfflineRetention.CAP_SLIDER_STOPS_MB
    var sliderIndex by remember { mutableFloatStateOf(OfflineRetention.nearestCapStopIndex(megabytes).toFloat()) }
    var unit by remember { mutableStateOf(capUnitFor(megabytes)) }
    var fieldText by remember { mutableStateOf(capFieldText(megabytes, unit)) }
    // A value this picker wrote itself must not rewrite the field: typing 15000 MB passes
    // through 1500, which would otherwise flip the field to "1.5 GB" mid-number.
    var lastWritten by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(megabytes) {
        if (megabytes != lastWritten) {
            sliderIndex = OfflineRetention.nearestCapStopIndex(megabytes).toFloat()
            unit = capUnitFor(megabytes)
            fieldText = capFieldText(megabytes, unit)
        }
    }
    val shownMegabytes = parseCapInput(fieldText, unit) ?: stops[sliderIndex.roundToInt()]

    fun commitTyped(text: String, inUnit: CapUnit) {
        parseCapInput(text, inUnit)?.let { typed ->
            sliderIndex = OfflineRetention.nearestCapStopIndex(typed).toFloat()
            lastWritten = typed
            onMegabytesChange(typed)
        }
    }

    Text(
        text = "Keep offline copies under ${offlineStorageCapLabel(shownMegabytes)}",
        style = MaterialTheme.typography.bodyMedium
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = sliderIndex,
            onValueChange = {
                sliderIndex = it
                val stop = stops[it.roundToInt()]
                unit = capUnitFor(stop)
                fieldText = capFieldText(stop, unit)
            },
            onValueChangeFinished = {
                val stop = stops[sliderIndex.roundToInt()]
                lastWritten = stop
                onMegabytesChange(stop)
            },
            valueRange = 0f..stops.lastIndex.toFloat(),
            steps = stops.size - 2,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedTextField(
            value = fieldText,
            onValueChange = { input ->
                fieldText = input.filter { it.isDigit() || it == '.' || it == ',' }.take(7)
                commitTyped(fieldText, unit)
            },
            label = { Text("Size") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(88.dp)
        )
        TextButton(
            onClick = {
                unit = if (unit == CapUnit.MB) CapUnit.GB else CapUnit.MB
                commitTyped(fieldText, unit)
            }
        ) {
            Text(unit.name)
        }
    }
}

internal enum class CapUnit { MB, GB }

internal fun capUnitFor(megabytes: Int): CapUnit = if (megabytes >= 1000) CapUnit.GB else CapUnit.MB

internal fun capFieldText(megabytes: Int, unit: CapUnit): String = when (unit) {
    CapUnit.MB -> megabytes.toString()
    CapUnit.GB -> trimDecimals(megabytes / 1000.0)
}

/** A typed size in megabytes, or null while the field holds nothing usable. */
internal fun parseCapInput(text: String, unit: CapUnit): Int? {
    val value = text.replace(',', '.').toDoubleOrNull() ?: return null
    val megabytes = when (unit) {
        CapUnit.MB -> value
        CapUnit.GB -> value * 1000
    }.roundToInt()
    return megabytes.takeIf { it in OfflineRetention.MIN_CAP_MB..OfflineRetention.MAX_CAP_MB }
}

internal fun offlineStorageCapLabel(megabytes: Int): String =
    if (megabytes >= 1000) "${trimDecimals(megabytes / 1000.0)} GB" else "$megabytes MB"

private fun trimDecimals(value: Double): String {
    val hundredths = (value * 100).roundToInt()
    return when {
        hundredths % 100 == 0 -> (hundredths / 100).toString()
        hundredths % 10 == 0 -> "${hundredths / 100}.${(hundredths % 100) / 10}"
        else -> "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }
}

internal fun offlineRetentionLabel(days: Int): String = "After ${offlineRetentionPeriod(days)}"

/** The period in its largest whole unit: "1 month", "3 weeks", "45 days". */
internal fun offlineRetentionPeriod(days: Int): String = when {
    days % 30 == 0 -> plural(days / 30, "month")
    days % 7 == 0 -> plural(days / 7, "week")
    else -> plural(days, "day")
}

private fun plural(count: Int, unit: String) = if (count == 1) "1 $unit" else "$count ${unit}s"

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
