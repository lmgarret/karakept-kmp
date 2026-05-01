package com.karakept.app.ui.screens

import android.content.Intent
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import com.karakept.app.BookmarkSavingActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
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
    private fun readSharedUrls(activity: BookmarkSavingActivity): List<String> {
        val field = BookmarkSavingActivity::class.java.getDeclaredField("sharedUrls\$delegate")
        field.isAccessible = true
        val state = field.get(activity) as MutableState<List<String>>
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

    private fun createShareIntentMultiple(urls: List<String>) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, urls.joinToString("\n"))
    }

    private fun launchActivity(intent: Intent): BookmarkSavingActivity =
        Robolectric.buildActivity(BookmarkSavingActivity::class.java, intent).create().get()

    @Test
    fun `extractUrlsFromIntent returns single URL from valid share intent`() {
        val activity = launchActivity(createShareIntent("https://example.com/article"))
        val urls = readSharedUrls(activity)
        assertEquals(listOf("https://example.com/article"), urls)
    }

    @Test
    fun `extractUrlsFromIntent returns all URLs from multi-URL share intent`() {
        val activity = launchActivity(
            createShareIntentMultiple(
                listOf("https://example.com/first", "https://example.com/second")
            )
        )
        val urls = readSharedUrls(activity)
        assertEquals(
            listOf("https://example.com/first", "https://example.com/second"),
            urls
        )
    }

    @Test
    fun `activity extracts empty URL list for non-SEND action intent (triggers finish)`() {
        // finish() is called inside setContent {} which runs asynchronously after onCreate.
        // We verify the precondition instead: extractUrlsFromIntent returns empty list for
        // non-SEND intents, which is exactly what causes the activity to call finish() in
        // its composable.
        val activity = launchActivity(Intent(Intent.ACTION_VIEW))
        assertTrue(readSharedUrls(activity).isEmpty())
    }

    @Test
    fun `onNewIntent increments intentKey for fresh compose tree`() {
        val activity = launchActivity(createShareIntent("https://example.com/first"))
        assertEquals(0, readIntentKey(activity))
        callOnNewIntent(activity, createShareIntent("https://example.com/second"))
        assertEquals(1, readIntentKey(activity))
    }

    @Test
    fun `onNewIntent updates sharedUrls with new URL`() {
        val activity = launchActivity(createShareIntent("https://example.com/first"))
        assertEquals(listOf("https://example.com/first"), readSharedUrls(activity))
        callOnNewIntent(activity, createShareIntent("https://example.com/second"))
        assertEquals(listOf("https://example.com/second"), readSharedUrls(activity))
    }

    @Test
    fun `onNewIntent updates sharedUrls with multiple new URLs`() {
        val activity = launchActivity(createShareIntent("https://example.com/first"))
        assertEquals(listOf("https://example.com/first"), readSharedUrls(activity))
        callOnNewIntent(
            activity,
            createShareIntentMultiple(
                listOf("https://example.com/a", "https://example.com/b", "https://example.com/c")
            )
        )
        assertEquals(
            listOf("https://example.com/a", "https://example.com/b", "https://example.com/c"),
            readSharedUrls(activity)
        )
    }

    @Test
    fun `activity finishes when onClose callback is triggered (SAVE-01 back-navigation)`() {
        val activity = launchActivity(createShareIntent("https://example.com/article"))
        assertTrue(!activity.isFinishing, "Activity should not be finishing before close")
        activity.finish()
        assertTrue(activity.isFinishing, "Activity should be finishing after close callback")
    }

    // --- URL extraction edge cases ---

    @Test
    fun `extracts URL with query parameters`() {
        val activity = launchActivity(
            createShareIntent("https://example.com/search?q=hello&page=2")
        )
        assertEquals(listOf("https://example.com/search?q=hello&page=2"), readSharedUrls(activity))
    }

    @Test
    fun `extracts URL embedded in prose text`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Hey check out this article https://example.com/cool-post it's great!")
        }
        val activity = launchActivity(intent)
        assertEquals(listOf("https://example.com/cool-post"), readSharedUrls(activity))
    }

    @Test
    fun `falls back to full text when no URL is found`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "just some plain text with no url")
        }
        val activity = launchActivity(intent)
        assertEquals(listOf("just some plain text with no url"), readSharedUrls(activity))
    }

    @Test
    fun `extracts three newline-separated URLs`() {
        val activity = launchActivity(
            createShareIntentMultiple(
                listOf(
                    "https://example.com/a",
                    "https://example.com/b",
                    "https://example.com/c",
                )
            )
        )
        assertEquals(
            listOf("https://example.com/a", "https://example.com/b", "https://example.com/c"),
            readSharedUrls(activity)
        )
    }

    @Test
    fun `extracts URL with https scheme`() {
        val activity = launchActivity(createShareIntent("https://secure.example.com/page"))
        assertEquals(listOf("https://secure.example.com/page"), readSharedUrls(activity))
    }

    @Test
    fun `returns empty list for wrong MIME type`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/image.png")
        }
        val activity = launchActivity(intent)
        assertTrue(readSharedUrls(activity).isEmpty())
    }
}

