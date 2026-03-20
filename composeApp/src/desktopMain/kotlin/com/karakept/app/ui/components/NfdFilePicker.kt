package com.karakept.app.ui.components

import tv.wunderbox.nfd.FileDialog
import tv.wunderbox.nfd.FileDialogResult
import tv.wunderbox.nfd.nfd.NfdFileDialog

/**
 * Isolated in its own class so the JVM only attempts to load [NfdFileDialog] when
 * this object is first referenced.  When nativefiledialog is not on the classpath
 * (e.g. during `./gradlew run`) the caller catches the resulting [NoClassDefFoundError]
 * and falls back to Swing.
 */
internal object NfdFilePicker {
    fun pickJsonFile(): String? {
        val result = NfdFileDialog().pickFile(
            filters = listOf(FileDialog.Filter("JSON backup files", listOf("json"))),
        )
        return when (result) {
            is FileDialogResult.Success<*> -> runCatching { (result.value as java.io.File).readText() }.getOrNull()
            else -> null
        }
    }

    fun pickDirectory(): String? {
        val result = NfdFileDialog().pickDirectory()
        return when (result) {
            is FileDialogResult.Success<*> -> (result.value as java.io.File).absolutePath
            else -> null
        }
    }
}
