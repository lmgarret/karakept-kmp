package com.karakept.app.ui.icons

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the generated [AppIcons] against drifting away from tools/material-icons.txt — the
 * generator only runs by hand, so a manifest edit with no regeneration would otherwise land
 * silently.
 */
class AppIconsManifestTest {

    private val repoRoot: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "tools/material-icons.txt").isFile }

    private val variantPaths = mapOf(
        "filled" to "AppIcons.Filled",
        "outlined" to "AppIcons.Outlined",
        "automirrored-filled" to "AppIcons.AutoMirrored.Filled",
        "automirrored-outlined" to "AppIcons.AutoMirrored.Outlined",
    )

    private fun manifestEntries(): Set<String> =
        File(repoRoot, "tools/material-icons.txt").readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val (variant, name) = line.split(Regex("\\s+"))
                val path = variantPaths[variant] ?: error("unknown variant '$variant' in manifest")
                "$path.$name"
            }
            .toSet()

    private fun generatedEntries(): Set<String> {
        val source = File(
            repoRoot,
            "composeApp/src/commonMain/kotlin/com/karakept/app/ui/icons/AppIcons.kt",
        ).readLines()
        val scope = ArrayDeque<String>()
        val declared = mutableSetOf<String>()
        source.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("object ") ->
                    scope.addLast(trimmed.removePrefix("object ").substringBefore(" "))

                trimmed == "}" -> scope.removeLastOrNull()

                trimmed.startsWith("val ") && trimmed.contains(": ImageVector by materialIcon(") ->
                    declared += (scope + trimmed.removePrefix("val ").substringBefore(":"))
                        .joinToString(".")
            }
        }
        return declared
    }

    @Test
    fun generatedIconsMatchTheManifest() {
        val manifest = manifestEntries()
        val generated = generatedEntries()

        assertTrue(manifest.isNotEmpty(), "manifest parsed as empty")
        assertEquals(
            emptySet(),
            manifest - generated,
            "manifest icons missing from AppIcons.kt — re-run tools/generate_material_icons.py",
        )
        assertEquals(
            emptySet(),
            generated - manifest,
            "AppIcons.kt declares icons the manifest does not list",
        )
    }

    @Test
    fun everyIconReferencedByTheAppIsInTheManifest() {
        val referenced = File(repoRoot, "composeApp/src").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                Regex("\\bAppIcons\\.(?:AutoMirrored\\.)?(?:Filled|Outlined|Default)\\.[A-Za-z0-9_]+")
                    .findAll(file.readText())
                    .map { it.value.replace("AppIcons.Default.", "AppIcons.Filled.") }
            }
            .toSet()

        val manifest = manifestEntries()
        assertEquals(
            emptySet(),
            referenced - manifest,
            "icons used by the app but missing from tools/material-icons.txt",
        )
    }
}
