package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Dialog for adjusting reading speed (WPM - Words Per Minute).
 *
 * @param currentWpm Current reading speed value
 * @param onDismiss Callback when dialog is dismissed without saving
 * @param onConfirm Callback when user confirms the new reading speed
 */
@Composable
fun ReadingSpeedDialog(
    currentWpm: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(currentWpm.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = einkModalBorder(AlertDialogDefaults.shape),
        title = { Text("Reading Speed") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Adjust how fast you typically read to get more accurate time estimates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Current value display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${sliderValue.roundToInt()} WPM",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = getSpeedLabel(sliderValue.roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Slider
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 100f..500f,
                    steps = 39, // 10 WPM increments (400 range / 10)
                    modifier = Modifier.fillMaxWidth()
                )

                // Range labels
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "100",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "500",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Info text
                Text(
                    text = "Average adult reading speed is 200-300 WPM. Professional speed readers can exceed 400 WPM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(sliderValue.roundToInt())
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Returns a label describing the reading speed.
 */
private fun getSpeedLabel(wpm: Int): String {
    return when {
        wpm < 150 -> "Very Slow"
        wpm < 200 -> "Slow"
        wpm < 250 -> "Below Average"
        wpm < 300 -> "Average"
        wpm < 350 -> "Above Average"
        wpm < 400 -> "Fast"
        else -> "Very Fast"
    }
}
