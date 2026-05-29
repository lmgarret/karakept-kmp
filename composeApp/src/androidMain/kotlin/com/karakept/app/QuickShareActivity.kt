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
                val urls = extractUrls(sharedText)
                val message = if (urls.size > 1) "Saving ${urls.size} bookmarks..." else "Saving bookmark..."
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                urls.forEach { url -> enqueueBookmarkSave(url) }
            }
        }
        finish()
    }

    private fun extractUrls(text: String): List<String> {
        val urlRegex = "(https?://[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/[^\\s]*)?)".toRegex()
        val matches = urlRegex.findAll(text).map { it.value }.toList()
        return matches.ifEmpty { listOf(text) }
    }

    private fun enqueueBookmarkSave(url: String) {
        com.karakept.app.services.BookmarkSaveScheduler.enqueue(this, url)
    }
}
