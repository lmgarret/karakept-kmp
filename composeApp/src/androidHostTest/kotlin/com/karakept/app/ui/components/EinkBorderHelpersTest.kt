package com.karakept.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.karakept.app.ui.theme.EinkMode
import com.karakept.app.ui.theme.LocalEinkMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Regression tests for the e-ink border helpers in `EinkAware.kt` — [einkModalBorder],
 * [einkOutlineBorder] and [einkTrailingEdgeBorder] — which draw a border around modals, menus
 * and side panels under high-contrast e-ink mode (issue #347).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EinkBorderHelpersTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `einkModalBorder adds a border modifier under high contrast`() {
        var result: Modifier? = null
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalEinkMode provides EinkMode(highContrast = true)) {
                    result = einkModalBorder(RectangleShape)
                }
            }
        }

        assertNotSame(
            Modifier,
            result,
            "Expected a border modifier to be added under high contrast"
        )
    }

    @Test
    fun `einkModalBorder leaves the modifier unchanged outside high contrast`() {
        var result: Modifier? = null
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalEinkMode provides EinkMode(highContrast = false)) {
                    result = einkModalBorder(RectangleShape)
                }
            }
        }

        assertSame(
            Modifier,
            result,
            "Expected no border modifier outside high contrast"
        )
    }

    @Test
    fun `einkOutlineBorder returns a stroke under high contrast`() {
        var result: BorderStroke? = null
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalEinkMode provides EinkMode(highContrast = true)) {
                    result = einkOutlineBorder()
                }
            }
        }

        assertNotNull(result, "Expected a border stroke under high contrast")
    }

    @Test
    fun `einkOutlineBorder returns null outside high contrast`() {
        var result: BorderStroke? = BorderStroke(1.dp, Color.Black)
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalEinkMode provides EinkMode(highContrast = false)) {
                    result = einkOutlineBorder()
                }
            }
        }

        assertNull(result, "Expected no border stroke outside high contrast")
    }

    @Test
    fun `einkTrailingEdgeBorder adds a modifier under high contrast`() {
        var result: Modifier? = null
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalEinkMode provides EinkMode(highContrast = true)) {
                    result = einkTrailingEdgeBorder()
                }
            }
        }

        assertNotSame(
            Modifier,
            result,
            "Expected a trailing-edge border modifier under high contrast"
        )
    }

    @Test
    fun `einkTrailingEdgeBorder leaves the modifier unchanged outside high contrast`() {
        var result: Modifier? = null
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalEinkMode provides EinkMode(highContrast = false)) {
                    result = einkTrailingEdgeBorder()
                }
            }
        }

        assertSame(
            Modifier,
            result,
            "Expected no trailing-edge border modifier outside high contrast"
        )
    }
}
