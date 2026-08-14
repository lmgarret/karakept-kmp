package com.karakept.app.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.karakept.app.MainActivity

/**
 * Flips the launcher entry between the `.LauncherDefault` and `.LauncherMonochrome` aliases
 * declared in the manifest. An icon cannot be re-pointed at runtime; enabling one component
 * and disabling another is the only mechanism Android offers.
 */
actual object AppIconManager {

    actual val isSupported: Boolean = true

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun setMonochrome(enabled: Boolean) {
        setMonochrome(appContext ?: return, enabled)
    }

    internal fun setMonochrome(context: Context, enabled: Boolean) {
        // On a cold start the splash theme is resolved before any app code runs, so the flag is
        // mirrored into SharedPreferences where MainActivity can read it synchronously.
        context.iconPrefs().edit().putBoolean(KEY_MONOCHROME, enabled).apply()

        // Enable before disabling: with neither alias enabled the package has no launcher entry
        // at all, and a launcher that samples the system in that window drops the app.
        val (turnOn, turnOff) =
            if (enabled) MONOCHROME_ALIAS to DEFAULT_ALIAS else DEFAULT_ALIAS to MONOCHROME_ALIAS
        context.setAliasEnabled(turnOn, true)
        context.setAliasEnabled(turnOff, false)
    }

    /** Whether the monochrome splash theme should be used, cheap enough for `onCreate`. */
    fun isMonochromeSplash(context: Context): Boolean =
        context.iconPrefs().getBoolean(KEY_MONOCHROME, false)

    private fun Context.iconPrefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun Context.setAliasEnabled(alias: String, enabled: Boolean) {
        val component = ComponentName(packageName, ALIAS_PACKAGE + alias)
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        // Writing the state a component already has still costs a PackageManager round trip and
        // wakes every launcher, and the setting is re-applied on every app start.
        if (packageManager.getComponentEnabledSetting(component) == state) return
        packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }

    /**
     * The manifest's `.LauncherDefault` is expanded against the *namespace*, which the dev build
     * type's `applicationIdSuffix` does not touch — so `packageName` is the wrong base for the
     * class half of the component name.
     */
    private val ALIAS_PACKAGE: String =
        MainActivity::class.java.name.substringBeforeLast('.')

    internal const val DEFAULT_ALIAS = ".LauncherDefault"
    internal const val MONOCHROME_ALIAS = ".LauncherMonochrome"

    private const val PREFS_NAME = "app_icon"
    private const val KEY_MONOCHROME = "monochrome"
}
