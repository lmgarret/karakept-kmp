package com.karakept.app.utils

/**
 * [commit] is null when neither `-Pcommit` nor a readable git checkout answered — an F-Droid
 * source tarball has no `.git`, and F-Droid passes the commit in as a build property instead.
 */
data class GitVersionInfo(
    val commit: String?,
    val dirty: Boolean
) {
    fun label(isDevBuild: Boolean): String =
        appVersionLabel(name = AppVersion.name, commit = commit, dirty = dirty, isDevBuild = isDevBuild)
}

/**
 * The version line under the app name on the About screen.
 *
 * A release shows only the version; the commit means nothing to someone who downloaded one. A dev
 * build shows `dev · abc1234`, suffixed `-dirty` when the tree had uncommitted changes.
 */
fun appVersionLabel(name: String, commit: String?, dirty: Boolean, isDevBuild: Boolean): String {
    val version = name.ifBlank { "unknown" }.let { if (it.startsWith("v")) it else "v$it" }
    if (!isDevBuild) return version

    val sha = commit?.take(7) ?: "unknown"
    return "dev · $sha${if (dirty) "-dirty" else ""}"
}
