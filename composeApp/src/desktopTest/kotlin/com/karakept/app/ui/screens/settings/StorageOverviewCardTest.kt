package com.karakept.app.ui.screens.settings

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import com.karakept.app.data.repository.OfflineStorageUsage
import com.karakept.app.ui.theme.EinkMode
import com.karakept.app.ui.theme.LocalEinkMode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StorageOverviewCardTest {

    private val usage = OfflineStorageUsage(
        bookmarkCount = 12,
        articleBytes = 4_000_000,
        imageBytes = 9_000_000,
        fileBytes = 1_000_000,
        appBytes = 30_000_000,
        freeBytes = 8_000_000_000,
        thumbnailCacheBytes = 2_000_000
    )

    @Test
    fun skeletonShowsTheLegendWhileMeasuring() = runComposeUiTest {
        setContent {
            MaterialTheme {
                StorageOverviewCard(usage = null, storageCapMb = null, isWorking = false, onClear = {})
            }
        }

        onNodeWithContentDescription("Measuring storage").assertExists()
        listOf("Articles", "Images", "Archives & PDFs", "Thumbnail cache", "App & other data")
            .forEach { onNodeWithText(it).assertExists() }
        onNodeWithText("Clear offline copies").assertIsNotEnabled()
    }

    @Test
    fun cardKeepsItsHeightWhenTheMeasurementLands() = runComposeUiTest {
        val shown = mutableStateOf<OfflineStorageUsage?>(null)
        setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp).testTag("card")) {
                    StorageOverviewCard(usage = shown.value, storageCapMb = null, isWorking = false, onClear = {})
                }
            }
        }
        val skeletonHeight = onNodeWithTag("card").getBoundsInRoot().let { it.bottom - it.top }

        shown.value = usage
        waitForIdle()
        val loadedHeight = onNodeWithTag("card").getBoundsInRoot().let { it.bottom - it.top }

        assertEquals(loadedHeight, skeletonHeight)
    }

    @Test
    fun einkShowsDotsInsteadOfTheSkeleton() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalEinkMode provides EinkMode(animationsDisabled = true)) {
                MaterialTheme {
                    StorageOverviewCard(usage = null, storageCapMb = null, isWorking = false, onClear = {})
                }
            }
        }

        onNodeWithText("Measuring…").assertExists()
        onNodeWithContentDescription("Measuring storage").assertDoesNotExist()
    }
}
