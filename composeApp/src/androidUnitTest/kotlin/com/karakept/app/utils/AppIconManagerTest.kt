package com.karakept.app.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The alias names are spelled out rather than derived the way [AppIconManager] derives them:
 * that is the point of the test. They have to match the manifest, and the manifest expands
 * `.LauncherDefault` against the namespace, which the dev build type's `applicationIdSuffix`
 * does not touch — so anything built from `packageName` would silently miss on that variant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AppIconManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val defaultAlias = ComponentName(context.packageName, "com.karakept.app.LauncherDefault")
    private val monochromeAlias =
        ComponentName(context.packageName, "com.karakept.app.LauncherMonochrome")

    private fun enabledSetting(component: ComponentName) =
        context.packageManager.getComponentEnabledSetting(component)

    @Test
    fun `enabling monochrome hands the launcher entry to the monochrome alias`() {
        AppIconManager.setMonochrome(context, true)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, enabledSetting(monochromeAlias))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, enabledSetting(defaultAlias))
    }

    @Test
    fun `turning it back off restores the colour alias`() {
        AppIconManager.setMonochrome(context, true)
        AppIconManager.setMonochrome(context, false)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, enabledSetting(defaultAlias))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, enabledSetting(monochromeAlias))
    }

    @Test
    fun `re-applying the same value leaves exactly one alias enabled`() {
        // The setting is re-applied on every app start, so an idempotent apply is the norm and
        // not the edge case.
        repeat(3) { AppIconManager.setMonochrome(context, true) }

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, enabledSetting(monochromeAlias))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, enabledSetting(defaultAlias))
    }

    @Test
    fun `the splash flag follows the icon so a cold start does not disagree with it`() {
        AppIconManager.setMonochrome(context, true)
        assertTrue(AppIconManager.isMonochromeSplash(context))

        AppIconManager.setMonochrome(context, false)
        assertFalse(AppIconManager.isMonochromeSplash(context))
    }

    @Test
    fun `the colour icon is what an untouched install gets`() {
        assertFalse(AppIconManager.isMonochromeSplash(context))
    }
}
