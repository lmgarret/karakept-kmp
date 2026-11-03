package com.karakept.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

/**
 * Thin wrapper over the Nav3 back stack exposing push/pop/replaceAll. Provided via
 * [LocalNavigator] so screens navigate without owning the back stack themselves — this keeps
 * the call-site ergonomics that the codebase had under Voyager's `LocalNavigator`.
 */
class AppNavigator(private val backStack: MutableList<NavKey>) {
    fun push(key: NavKey) {
        backStack.add(key)
    }

    /** Pops the top entry unless it is the only one left. Returns true if it popped. */
    fun pop(): Boolean =
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            true
        } else {
            false
        }

    fun replaceAll(keys: List<NavKey>) {
        backStack.clear()
        backStack.addAll(keys)
    }

    fun replaceAll(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }

    val canPop: Boolean get() = backStack.size > 1
}

val LocalNavigator: ProvidableCompositionLocal<AppNavigator?> = staticCompositionLocalOf { null }

/** Mirrors Voyager's `LocalNavigator.currentOrThrow` so migrated call sites change by import only. */
val ProvidableCompositionLocal<AppNavigator?>.currentOrThrow: AppNavigator
    @Composable get() = current ?: error("No AppNavigator provided via LocalNavigator")
