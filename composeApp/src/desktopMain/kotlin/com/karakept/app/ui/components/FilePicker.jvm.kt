package com.karakept.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Opens a JSON file picker using the XDG Desktop Portal (via gdbus) when running
 * inside a Flatpak sandbox, falling back to JFileChooser otherwise.
 *
 * The portal flow:
 *  1. `gdbus call` on `org.freedesktop.portal.FileChooser.OpenFile` — returns a handle path.
 *  2. `gdbus monitor` watches for the `Response` signal on that handle path.
 *  3. The signal body contains `(ua{sv})` where index 0 is the response code (0 = success)
 *     and the map may contain "uris" with the selected file URIs.
 */
@Composable
actual fun rememberJsonFilePicker(onContent: (String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val isInFlatpak = System.getenv("FLATPAK_ID") != null
    return {
        scope.launch(Dispatchers.IO) {
            val content = if (isInFlatpak) {
                pickFileViaPortal()
            } else {
                pickFileViaJFileChooser()
            }
            withContext(Dispatchers.Main) {
                onContent(content)
            }
        }
    }
}

@Composable
actual fun rememberDirectoryPicker(onDirectorySelected: (String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch(Dispatchers.IO) {
            val path = pickDirectoryViaJFileChooser()
            withContext(Dispatchers.Main) {
                onDirectorySelected(path)
            }
        }
    }
}

/**
 * Opens a file picker via the XDG Desktop Portal.
 * Returns the file content as a String, or null if cancelled / error.
 */
private fun pickFileViaPortal(): String? {
    return try {
        // Step 1: call OpenFile and get the handle object path
        val callProcess = ProcessBuilder(
            "gdbus", "call",
            "--session",
            "--dest", "org.freedesktop.portal.Desktop",
            "--object-path", "/org/freedesktop/portal/desktop",
            "--method", "org.freedesktop.portal.FileChooser.OpenFile",
            "",                  // parent_window (empty = no parent)
            "Import Backup",     // title
            "{'filters': <[('JSON files', [(uint32 1, '*.json')])]>}"  // options
        ).redirectErrorStream(true).start()

        val callOutput = callProcess.inputStream.bufferedReader().readText().trim()
        callProcess.waitFor()

        // Output looks like: (objectpath '/org/freedesktop/portal/desktop/request/...',)
        val handlePath = Regex("""objectpath '([^']+)'""").find(callOutput)?.groupValues?.get(1)
            ?: return null

        // Step 2: monitor for the Response signal on the handle path
        val latch = CountDownLatch(1)
        var selectedUri: String? = null

        val monitorProcess = ProcessBuilder(
            "gdbus", "monitor",
            "--session",
            "--dest", "org.freedesktop.portal.Desktop",
            "--object-path", handlePath
        ).redirectErrorStream(true).start()

        val monitorThread = Thread {
            try {
                monitorProcess.inputStream.bufferedReader().use { reader ->
                    for (line in reader.lines()) {
                        // Signal line contains "Response" and the response tuple
                        if (line.contains("Response")) {
                            // Response code 0 = success; extract first file:// URI
                            val responseCode = Regex("""Response \((\d+),""").find(line)?.groupValues?.get(1)?.toIntOrNull()
                            if (responseCode == 0) {
                                // URIs look like: 'file:///path/to/file.json'
                                selectedUri = Regex("""'(file://[^']+)'""").find(line)?.groupValues?.get(1)
                            }
                            latch.countDown()
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                latch.countDown()
            }
        }
        monitorThread.isDaemon = true
        monitorThread.start()

        // Wait up to 5 minutes for user to pick a file
        latch.await(5, TimeUnit.MINUTES)
        monitorProcess.destroyForcibly()

        // Convert file:// URI to local path and read content
        val uri = selectedUri ?: return null
        val localPath = java.net.URI(uri).path
        java.io.File(localPath).readText()
    } catch (_: Exception) {
        null
    }
}

private fun pickFileViaJFileChooser(): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Import Backup"
        fileSelectionMode = JFileChooser.FILES_ONLY
        fileFilter = FileNameExtensionFilter("JSON backup files (*.json)", "json")
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) {
        try { chooser.selectedFile.readText() } catch (_: Exception) { null }
    } else null
}

private fun pickDirectoryViaJFileChooser(): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select Backup Export Directory"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else null
}
