package com.karakept.app.data.local

import java.io.File

/**
 * Returns the root data directory for the app, respecting XDG on Linux.
 *
 * - Inside a Flatpak sandbox: XDG_DATA_HOME is already scoped to this app
 *   (e.g. ~/.var/app/com.karakept.app/data), so no --filesystem=home permission is needed.
 * - Bare Linux: XDG_DATA_HOME defaults to ~/.local/share; we append "karakept".
 * - macOS / other: keeps the existing ~/.karakept location.
 */
internal fun appDataDir(): File = File(
    when {
        System.getenv("FLATPAK_ID") != null ->
            System.getenv("XDG_DATA_HOME")
                ?: "${System.getProperty("user.home")}/.var/app/${System.getenv("FLATPAK_ID")}/data"
        System.getProperty("os.name").lowercase().contains("linux") ->
            "${System.getenv("XDG_DATA_HOME") ?: "${System.getProperty("user.home")}/.local/share"}/karakept"
        else ->
            "${System.getProperty("user.home")}/.karakept"
    }
).also { it.mkdirs() }
