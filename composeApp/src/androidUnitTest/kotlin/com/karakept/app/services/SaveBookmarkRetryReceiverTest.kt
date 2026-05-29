package com.karakept.app.services

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SaveBookmarkRetryReceiverTest {

    @Test
    fun `onReceive without url returns without enqueueing or crashing`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(SaveBookmarkRetryReceiver.ACTION_RETRY)
        // No EXTRA_URL: the receiver must short-circuit before touching WorkManager.
        SaveBookmarkRetryReceiver().onReceive(context, intent)
    }
}
