package com.karakept.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppVersionTest {

    @Test
    fun releaseBuild_showsOnlyTheVersion() {
        assertEquals(
            "v2.7.0",
            appVersionLabel(name = "2.7.0", commit = "abc1234", dirty = true, isDevBuild = false)
        )
    }

    @Test
    fun releaseBuild_leavesAnAlreadyPrefixedVersionAlone() {
        assertEquals(
            "v2.7.0",
            appVersionLabel(name = "v2.7.0", commit = "abc1234", dirty = false, isDevBuild = false)
        )
    }

    @Test
    fun devBuild_namesTheCommit() {
        assertEquals(
            "dev · abc1234",
            appVersionLabel(name = "2.7.0", commit = "abc1234", dirty = false, isDevBuild = true)
        )
    }

    @Test
    fun devBuild_marksADirtyTree() {
        assertEquals(
            "dev · abc1234-dirty",
            appVersionLabel(name = "2.7.0", commit = "abc1234", dirty = true, isDevBuild = true)
        )
    }

    @Test
    fun devBuild_withoutACommit_stillSaysWhatItIs() {
        assertEquals(
            "dev · unknown",
            appVersionLabel(name = "2.7.0", commit = null, dirty = false, isDevBuild = true)
        )
    }

    @Test
    fun devBuild_pairingGithubCommitShowsTheShortSha() {
        assertEquals(
            "dev · 6b59f18",
            appVersionLabel(
                name = "2.7.0",
                commit = "6b59f18a1c4e9d2f7b3a5c8e0d1f2a3b4c5d6e7f",
                dirty = false,
                isDevBuild = true
            )
        )
    }

    @Test
    fun releaseBuild_withoutAVersionFallsBackToUnknown() {
        assertEquals(
            "vunknown",
            appVersionLabel(name = "", commit = "abc1234", dirty = false, isDevBuild = false)
        )
    }

    @Test
    fun gitAbsent_doesNotReportDirty() {
        val git = GitVersionInfo(commit = null, dirty = false)

        assertFalse(git.dirty)
        assertEquals("dev · unknown", git.label(isDevBuild = true))
    }
}
