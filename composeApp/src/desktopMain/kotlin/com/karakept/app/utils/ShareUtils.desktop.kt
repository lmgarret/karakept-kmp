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
            println("Copied to clipboard: $text")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
