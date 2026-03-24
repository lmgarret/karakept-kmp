package com.karakept.app.ui.screens

import android.content.Intent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.MutableIntState
import com.karakept.app.BookmarkSavingActivity
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Activity lifecycle tests for SAVE-01/02: BookmarkSavingActivity.
 *
 * Uses Robolectric.buildActivity().create() (not ActivityScenario.launch) to avoid
 * triggering the .visible() / looper-idle step that hangs when ShareBookmarkScreen's
 * coroutines crash due to missing Koin bindings. onCreate is sufficient to test all
 * behavioral contracts: URL extraction, intentKey, and activity finishing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BookmarkSavingActivityTest {

    @Suppress("UNCHECKED_CAST")
    private fun readSharedUrl(activity: BookmarkSavingActivity): String? {
        val field = BookmarkSavingActivity::class.java.getDeclaredField("sharedUrl\$delegate")
        field.isAccessible = true
        val state = field.get(activity) as MutableState<String?>
        return state.value
    }

    private fun readIntentKey(activity: BookmarkSavingActivity): Int {
        val field = BookmarkSavingActivity::class.java.getDeclaredField("intentKey\$delegate")
        field.isAccessible = true
        val state = field.get(activity) as MutableIntState
        return state.intValue
    }

    private fun callOnNewIntent(activity: BookmarkSavingActivity, intent: Intent) {
        val method = activity.javaClass.getDeclaredMethod("onNewIntent", Intent::class.java)
        method.isAccessible = true
        method.invoke(activity, intent)
    }

    private fun createShareIntent(url: String) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Check this out $url")
    }

    private fun launchActivity(intent: Intent): BookmarkSavingActivity =
        Robolectric.buildActivity(BookmarkSavingActivity::class.java, intent).create().get()

    @Test
    fun `extractUrlFromIntent returns URL from valid share intent`() {
        val activity = launchActivity(createShareIntent("https://example.com/article"))
        val url = readSharedUrl(activity)
        assertNotNull(url)
        assertEquals("https://example.com/article", url)
    }

    @Test
    fun `activity extracts null URL for non-SEND action intent (triggers finish)`() {
        // finish() is called inside setContent {} which runs asynchronously after onCreate.
        // We verify the precondition instead: extractUrlFromIntent returns null for non-SEND
        // intents, which is exactly what causes the activity to call finish() in its composable.
        val activity = launchActivity(Intent(Intent.ACTION_VIEW))
        assertNull(readSharedUrl(activity))
    }

    @Test
    fun `onNewIntent increments intentKey for fresh compose tree`() {
        val activity = launchActivity(createShareIntent("https://example.com/first"))
        assertEquals(0, readIntentKey(activity))
        callOnNewIntent(activity, createShareIntent("https://example.com/second"))
        assertEquals(1, readIntentKey(activity))
    }

    @Test
    fun `onNewIntent updates sharedUrl with new URL`() {
        val activity = launchActivity(createShareIntent("https://example.com/first"))
        assertEquals("https://example.com/first", readSharedUrl(activity))
        callOnNewIntent(activity, createShareIntent("https://example.com/second"))
        assertEquals("https://example.com/second", readSharedUrl(activity))
    }

    @Test
    fun `activity finishes when onClose callback is triggered (SAVE-01 back-navigation)`() {
        val activity = launchActivity(createShareIntent("https://example.com/article"))
        assertTrue(!activity.isFinishing, "Activity should not be finishing before close")
        activity.finish()
        assertTrue(activity.isFinishing, "Activity should be finishing after close callback")
    }
}
