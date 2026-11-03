package com.karakept.app.utils

import android.content.Intent
import androidx.core.content.ContextCompat.startActivity

actual object ShareUtils {
    private var appContext: android.content.Context? = null
    
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }
    
    actual fun shareText(text: String, title: String?) {
        val context = appContext ?: return
        
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            title?.let { putExtra(Intent.EXTRA_TITLE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val shareIntent = Intent.createChooser(sendIntent, title ?: "Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(shareIntent)
    }
}
