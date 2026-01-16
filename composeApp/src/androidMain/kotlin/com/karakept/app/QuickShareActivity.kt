package com.karakept.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.karakept.app.data.repository.BookmarkRepository
import org.koin.android.ext.android.inject

class QuickShareActivity : ComponentActivity() {
    private val bookmarkRepository: BookmarkRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                val urlRegex = "(https?://[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/[^\\s]*)?)".toRegex()
                val match = urlRegex.find(sharedText)
                val url = match?.value ?: sharedText

                saveBookmark(url)
            }
        }
        finish()
    }

    private fun saveBookmark(url: String) {
        Toast.makeText(this, "Saving bookmark...", Toast.LENGTH_SHORT).show()
        
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.karakept.app.services.SaveBookmarkWorker>()
            .setInputData(androidx.work.workDataOf(com.karakept.app.services.SaveBookmarkWorker.KEY_URL to url))
            .build()
            
        androidx.work.WorkManager.getInstance(this).enqueue(workRequest)
    }
}
