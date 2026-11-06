package com.karakept.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.app.domain.ServerCompatibility
import com.karakept.app.domain.ServerVersionCheck
import com.karakept.app.domain.ServerVersionUtils
import com.karakept.app.ui.icons.AppIcons

/** Explains an outdated server; draws nothing for any other answer. */
@Composable
fun ServerVersionWarning(check: ServerVersionCheck?, modifier: Modifier = Modifier) {
    if (check?.compatibility != ServerCompatibility.OUTDATED) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = AppIcons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = ServerVersionUtils.outdatedWarning(check),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
