package com.karakept.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.test.junit4.createComposeRule
import com.karakept.app.ui.theme.EinkMode
import com.karakept.app.ui.theme.LocalEinkMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Regression tests for [einkModalBorder], the helper that draws a border around modals
 * (`AlertDialog`, `ModalBottomSheet`) under high-contrast e-ink mode (issue #347).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EinkModalBorderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `adds a border modifier under high contrast`() {
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
    fun `leaves the modifier unchanged outside high contrast`() {
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
}
