package com.karakept.app.utils

import java.awt.Desktop
import java.net.URI

actual object ShareUtils {
    actual fun shareText(text: String, title: String?) {
        // Desktop doesn't have a native share sheet, so we'll copy to clipboard
        try {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val stringSelection = java.awt.datatransfer.StringSelection(text)
            clipboard.setContents(stringSelection, null)
            AppLogger.i("ShareUtils", "Copied to clipboard")
        } catch (e: Exception) {
            AppLogger.e("ShareUtils", "Failed to copy to clipboard: ${e.message}", e)
        }
    }
}
