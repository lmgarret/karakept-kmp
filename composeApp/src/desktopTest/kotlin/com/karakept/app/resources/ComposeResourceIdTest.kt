package com.karakept.app.resources

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Compose Resources derives a resource ID from a file's base name, so two files in the same
 * qualifier directory that differ only by extension — `drawable/icon.png` and
 * `drawable/icon.svg` — both claim `drawable:icon`. The generated `Res` accessor compiles
 * either way and the collision only surfaces as an IllegalStateException the first time
 * something actually resolves that ID, which may be long after the duplicate landed.
 *
 * Qualifier variants are still fine: they live in sibling directories (`drawable-dark/`,
 * `drawable-en/`), so uniqueness is per directory, not across the resource set.
 */
class ComposeResourceIdTest {

    private val repoRoot: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "composeApp/src/commonMain/composeResources").isDirectory }

    private val composeResources = File(repoRoot, "composeApp/src/commonMain/composeResources")

    @Test
    fun noTwoResourcesInADirectoryShareAnId() {
        val collisions = composeResources.walkTopDown()
            .filter { it.isDirectory }
            .mapNotNull { directory ->
                val duplicates = directory.listFiles()
                    .orEmpty()
                    .filter { it.isFile }
                    .groupBy { it.nameWithoutExtension }
                    .filterValues { it.size > 1 }
                if (duplicates.isEmpty()) {
                    null
                } else {
                    duplicates.entries.joinToString("; ") { (id, files) ->
                        "${directory.name}:$id <- ${files.map { it.name }.sorted()}"
                    }
                }
            }
            .toList()

        assertTrue(
            collisions.isEmpty(),
            "Compose resource ID collisions (same base name in one directory): $collisions",
        )
    }
}
