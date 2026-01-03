package com.karakept.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.karakept.app.data.repository.BookmarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class QuickShareActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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

                Toast.makeText(this, "Saving bookmark...", Toast.LENGTH_SHORT).show()
                saveBookmark(url)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    private fun saveBookmark(url: String) {
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    bookmarkRepository.createBookmark(url)
                }
                
                if (result.isSuccess) {
                    Toast.makeText(this@QuickShareActivity, "Bookmark created: ${result.getOrNull()?.title}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@QuickShareActivity, "Failed to create bookmark: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@QuickShareActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }
}
