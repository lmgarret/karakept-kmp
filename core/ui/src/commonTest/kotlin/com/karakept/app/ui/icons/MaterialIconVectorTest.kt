package com.karakept.app.ui.icons

import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaterialIconVectorTest {

    private fun paths(group: VectorGroup): List<VectorPath> =
        group.flatMap { node ->
            when (node) {
                is VectorPath -> listOf(node)
                is VectorGroup -> paths(node)
            }
        }

    @Test
    fun buildsA24DpIconFromPathData() {
        val icon by materialIcon("Add", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")

        assertEquals("Add", icon.name)
        assertEquals(24.dp, icon.defaultWidth)
        assertEquals(24.dp, icon.defaultHeight)
        assertEquals(24f, icon.viewportWidth)
        assertEquals(24f, icon.viewportHeight)
        assertFalse(icon.autoMirror)
    }

    @Test
    fun keepsEachSourcePathSeparate() {
        val icon by materialIcon("Two", "M0 0h4v4H0z", "M8 8h4v4H8z")

        val vectorPaths = paths(icon.root)
        assertEquals(2, vectorPaths.size)
        assertTrue(vectorPaths.all { it.pathData.isNotEmpty() })
    }

    @Test
    fun autoMirroredIconsFlipWithLayoutDirection() {
        val icon by materialIcon("Back", "M0 0h4v4H0z", autoMirrored = true)

        assertTrue(icon.autoMirror)
    }

    @Test
    fun everySpotCheckedIconHasGeometry() {
        val icons = listOf(
            AppIcons.Default.Add,
            AppIcons.Filled.Archive,
            AppIcons.Outlined.Web,
            AppIcons.AutoMirrored.Filled.ArrowBack,
            AppIcons.AutoMirrored.Outlined.MenuBook,
        )

        icons.forEach { icon ->
            val vectorPaths = paths(icon.root)
            assertTrue(vectorPaths.isNotEmpty(), "${icon.name} has no paths")
            assertTrue(
                vectorPaths.all { it.pathData.isNotEmpty() },
                "${icon.name} has an empty path",
            )
        }
    }

    @Test
    fun defaultIsAnAliasForFilled() {
        assertEquals(AppIcons.Filled.Archive, AppIcons.Default.Archive)
    }
}
