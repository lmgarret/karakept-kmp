package com.karakept.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                // Find URL in text (basic regex for now)
                val urlRegex = "(https?://[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/[^\\s]*)?)".toRegex()
                val match = urlRegex.find(sharedText)
                val url = match?.value ?: sharedText

                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    putExtra("shared_url", url)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(mainIntent)
            }
        }
        finish()
    }
}
