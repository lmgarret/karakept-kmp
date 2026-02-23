package com.karakept.app.ui.screens.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.components.OfflineModeBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreenTopBar(
    offlineMode: Boolean,
    isAutoOffline: Boolean = false,
    onMenuClick: () -> Unit,
    onFilterClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onOfflineBadgeClick: () -> Unit = {},
    isDesktop: Boolean,
    hasActiveFilter: Boolean = false
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Karakept")
                if (offlineMode) {
                    Spacer(Modifier.width(8.dp))
                    OfflineModeBadge(
                        isAutoOffline = isAutoOffline,
                        onClick = onOfflineBadgeClick
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            IconButton(onClick = onFilterClick) {
                BadgedBox(badge = { if (hasActiveFilter) Badge() }) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filter")
                }
            }
            if (isDesktop) {
                IconButton(
                    onClick = onRefreshClick,
                    enabled = !offlineMode
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync")
                }
            }
        }
    )
}
