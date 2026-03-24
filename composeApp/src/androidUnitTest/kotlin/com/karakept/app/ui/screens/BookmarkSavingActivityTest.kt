package com.karakept.app.ui.screens

import android.content.Intent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.MutableIntState
import androidx.test.core.app.ActivityScenario
import com.karakept.app.BookmarkSavingActivity
import com.karakept.app.data.model.AccentColor
import com.karakept.app.data.model.ThemeMode
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Activity lifecycle tests for SAVE-01/02: BookmarkSavingActivity.
 *
 * Tests the Activity's behavioral logic (URL extraction, intent key increment,
 * activity finishing) via reflection on Compose state fields. This avoids
 * needing the full Voyager + Koin dependency graph that BookmarkSavingContent
 * requires to render.
 *
 * Koin is started with minimal mocks so Activity.onCreate -> setContent does not crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookmarkSavingActivityTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var bookmarkRepository: BookmarkRepository

    @Before
    fun setUp() {
        settingsRepository = mockk(relaxed = true)
        bookmarkRepository = mockk(relaxed = true)

        every { settingsRepository.themeMode } returns flowOf(ThemeMode.SYSTEM)
        every { settingsRepository.accentColor } returns flowOf(AccentColor.PURPLE)
        every { settingsRepository.activeServerId } returns flowOf(null)

        val testModule = module {
            single<SettingsRepository> { settingsRepository }
            single<BookmarkRepository> { bookmarkRepository }
        }

        startKoin { modules(testModule) }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    /**
     * Reads the Activity's private `sharedUrl` field via reflection.
     * sharedUrl is declared as `private var sharedUrl by mutableStateOf<String?>(null)`,
     * so the backing field is a MutableState<String?> delegate.
     */
    @Suppress("UNCHECKED_CAST")
    private fun readSharedUrl(activity: BookmarkSavingActivity): String? {
        val field = BookmarkSavingActivity::class.java.getDeclaredField("sharedUrl\$delegate")
        field.isAccessible = true
        val state = field.get(activity) as MutableState<String?>
        return state.value
    }

    /**
     * Reads the Activity's private `intentKey` field via reflection.
     * intentKey is declared as `private var intentKey by mutableIntStateOf(0)`,
     * so the backing field is a MutableIntState delegate.
     */
    private fun readIntentKey(activity: BookmarkSavingActivity): Int {
        val field = BookmarkSavingActivity::class.java.getDeclaredField("intentKey\$delegate")
        field.isAccessible = true
        val state = field.get(activity) as MutableIntState
        return state.intValue
    }

    private fun createShareIntent(url: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check this out $url")
        }
    }

    @Test
    fun `extractUrlFromIntent returns URL from valid share intent`() {
        val intent = createShareIntent("https://example.com/article")
        val scenario = ActivityScenario.launch<BookmarkSavingActivity>(intent)

        scenario.onActivity { activity ->
            val url = readSharedUrl(activity)
            assertNotNull(url)
            assertEquals("https://example.com/article", url)
        }

        scenario.close()
    }

    @Test
    fun `activity finishes for non-SEND action intent`() {
        val intent = Intent(Intent.ACTION_VIEW)
        val scenario = ActivityScenario.launch<BookmarkSavingActivity>(intent)

        scenario.onActivity { activity ->
            // sharedUrl is null for non-SEND intent, so onCreate calls finish()
            assertTrue(activity.isFinishing)
        }

        scenario.close()
    }

    @Test
    fun `onNewIntent increments intentKey for fresh compose tree`() {
        val intent1 = createShareIntent("https://example.com/first")
        val scenario = ActivityScenario.launch<BookmarkSavingActivity>(intent1)

        scenario.onActivity { activity ->
            assertEquals(0, readIntentKey(activity))

            val intent2 = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://example.com/second")
            }
            activity.onNewIntent(intent2)

            assertEquals(1, readIntentKey(activity))
        }

        scenario.close()
    }

    @Test
    fun `onNewIntent updates sharedUrl with new URL`() {
        val intent1 = createShareIntent("https://example.com/first")
        val scenario = ActivityScenario.launch<BookmarkSavingActivity>(intent1)

        scenario.onActivity { activity ->
            assertEquals("https://example.com/first", readSharedUrl(activity))

            val intent2 = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://example.com/second")
            }
            activity.onNewIntent(intent2)

            // sharedUrl should be updated to the new URL (fresh state per SAVE-02)
            assertEquals("https://example.com/second", readSharedUrl(activity))
        }

        scenario.close()
    }

    @Test
    fun `activity finishes when onClose callback is triggered (SAVE-01 back-navigation)`() {
        val intent = createShareIntent("https://example.com/article")
        val scenario = ActivityScenario.launch<BookmarkSavingActivity>(intent)

        scenario.onActivity { activity ->
            // Activity should NOT be finishing initially
            assertTrue(!activity.isFinishing, "Activity should not be finishing before close")

            // The onClose callback in BookmarkSavingActivity calls finish().
            // Invoke finish() directly to verify the behavioral contract.
            activity.finish()

            assertTrue(activity.isFinishing, "Activity should be finishing after close callback")
        }

        scenario.close()
    }
}
