package com.karakept.app.utils

/**
 * Platform-specific haptic feedback
 */
expect object HapticUtils {
    /**
     * Perform a light haptic feedback (like for UI interactions)
     */
    fun performLight()
    
    /**
     * Perform a medium haptic feedback (like for confirmation)
     */
    fun performMedium()
}
