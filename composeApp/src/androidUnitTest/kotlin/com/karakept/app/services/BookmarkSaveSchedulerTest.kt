package com.karakept.app.services

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BookmarkSaveSchedulerTest {

    @Test
    fun `buildRequest carries the url as worker input`() {
        val url = "https://example.com/article"
        val request = BookmarkSaveScheduler.buildRequest(url)
        assertEquals(url, request.workSpec.input.getString(SaveBookmarkWorker.KEY_URL))
    }

    @Test
    fun `buildRequest targets the SaveBookmarkWorker`() {
        val request = BookmarkSaveScheduler.buildRequest("https://example.com")
        assertEquals(SaveBookmarkWorker::class.java.name, request.workSpec.workerClassName)
    }
}
