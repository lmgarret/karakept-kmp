package com.karakept.app.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Source-text guards for the layer boundaries described in AGENTS.md, across every app module.
 * Offenders that predate a rule are listed in [KNOWN_VIOLATIONS] as
 * `"<rule-id>:<path relative to the repository root>"`; a
 * rule fails on any offender not listed and on any listed entry that no longer offends, so
 * whoever fixes a violation must delete its entry.
 */
class LayerRulesTest {

    private val repoRoot: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "tools/material-icons.txt").isFile }

    // Every module holding app code. Packages do not follow modules, so each rule runs over all.
    private val srcRoots = listOf("composeApp", "core/model", "core/data").map { File(repoRoot, "$it/src") }

    private val sourceSets = listOf("commonMain", "androidMain", "desktopMain")

    private fun packageDirs(sourceSet: String) =
        srcRoots.map { File(it, "$sourceSet/kotlin/com/karakept/app") }

    private fun kotlinFiles(sourceSet: String, subDir: String? = null): List<File> =
        packageDirs(sourceSet).flatMap { dir ->
            val base = if (subDir == null) dir else File(dir, subDir)
            base.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

    private fun relative(file: File) = file.relativeTo(repoRoot).invariantSeparatorsPath

    private fun check(ruleId: String, offenders: Collection<String>) {
        val actual = offenders.map { "$ruleId:$it" }.toSet()
        val known = KNOWN_VIOLATIONS.filter { it.startsWith("$ruleId:") }.toSet()
        val unexpected = (actual - known).sorted()
        val stale = (known - actual).sorted()
        assertTrue(
            unexpected.isEmpty() && stale.isEmpty(),
            buildString {
                if (unexpected.isNotEmpty()) {
                    appendLine("New $ruleId violations:")
                    unexpected.forEach { appendLine("  $it") }
                }
                if (stale.isNotEmpty()) {
                    appendLine("Stale allowlist entry — delete it:")
                    stale.forEach { appendLine("  $it") }
                }
            },
        )
    }

    private fun filesContaining(subDirs: List<String>, needles: List<String>): List<String> =
        sourceSets.flatMap { set ->
            subDirs.flatMap { dir ->
                kotlinFiles(set, dir).filter { file ->
                    val text = file.readText()
                    needles.any { text.contains(it) }
                }
            }
        }.map(::relative)

    @Test
    fun uiDoesNotReachDataInternals() {
        check(
            "ui-data",
            filesContaining(
                listOf("ui"),
                listOf(
                    "com.karakept.app.data.local.dao.",
                    "com.karakept.app.data.remote.RemoteDataSource",
                ),
            ),
        )
    }

    // The UI renders the domain `Bookmark`; the Room row stays behind the repositories.
    @Test
    fun uiDoesNotUseTheBookmarkEntity() {
        check(
            "ui-bookmark-entity",
            filesContaining(listOf("ui"), listOf("com.karakept.app.data.local.entity.BookmarkEntity")),
        )
    }

    @Test
    fun lowerLayersDoNotImportUi() {
        check(
            "lower-ui",
            filesContaining(listOf("data", "domain", "utils"), listOf("com.karakept.app.ui.")),
        )
    }

    @Test
    fun screensRootHoldsNoFiles() {
        val offenders = sourceSets.flatMap { set ->
            packageDirs(set).flatMap { dir ->
                File(dir, "ui/screens").listFiles { f -> f.isFile && f.extension == "kt" }
                    .orEmpty().toList()
            }
        }.map(::relative)
        check("screens-root", offenders)
    }

    @Test
    fun everyExpectHasActualsInSamePackage() {
        val expectDecl = Regex("""(?m)^\s*(?:(?:public|internal)\s+)?expect\s+(?:fun|object|class)\b""")
        val offenders = kotlinFiles("commonMain").filter { expectDecl.containsMatchIn(it.readText()) }
            .filter { file ->
                val srcRoot = srcRoots.first { file.startsWith(it) }
                val dir = file.parentFile.relativeTo(File(srcRoot, "commonMain")).path
                val base = file.nameWithoutExtension
                fun exists(set: String, vararg suffixes: String) = suffixes.any {
                    File(File(srcRoot, "$set/$dir"), "$base.$it.kt").isFile
                }
                !exists("androidMain", "android") || !exists("desktopMain", "jvm", "desktop")
            }
            .map(::relative)
        check("expect-actual", offenders)
    }

    companion object {
        // expect-actual entries are expects whose actuals sit in differently named files
        // (Database.*, AppLoggerPlatform.kt, ...) or whose file name lacks the .android/.jvm suffix.
        val KNOWN_VIOLATIONS: Set<String> = setOf(
            "expect-actual:core/data/src/commonMain/kotlin/com/karakept/app/data/local/AppDatabase.kt",
            "expect-actual:composeApp/src/commonMain/kotlin/com/karakept/app/ui/theme/WindowThemeSync.kt",
            "expect-actual:core/data/src/commonMain/kotlin/com/karakept/app/utils/AppLogger.kt",
            "expect-actual:core/data/src/commonMain/kotlin/com/karakept/app/data/cache/ImageCacheManager.kt",
        )
    }
}
