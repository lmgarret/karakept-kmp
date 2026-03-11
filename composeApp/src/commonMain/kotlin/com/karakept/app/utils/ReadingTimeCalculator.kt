package com.karakept.app.utils

import com.fleeksoft.ksoup.Ksoup
import kotlin.math.ceil

/**
 * Utility for calculating estimated reading time from HTML content.
 *
 * Based on Medium's reading time algorithm:
 * - Strips HTML tags and counts words
 * - Adds time penalties for images (first 10 images: 12s decreasing to 3s, remaining: 3s each)
 * - Rounds up to nearest minute (minimum 1 minute if content exists)
 */
object ReadingTimeCalculator {

    /**
     * Calculates estimated reading time from HTML content.
     *
     * Algorithm:
     * 1. Strip HTML tags and extract plain text using Ksoup
     * 2. Count words (whitespace-separated sequences)
     * 3. Apply adjustment factor for word length (longer words = slower reading)
     * 4. Count images and apply time penalties
     * 5. Calculate total seconds: (adjusted_words / WPM * 60) + image_time
     * 6. Round up to nearest minute
     *
     * Image penalty (based on Medium's algorithm):
     * - First image: 12 seconds
     * - Second image: 11 seconds
     * - Third image: 10 seconds
     * - ... (decreasing by 1 second each)
     * - Tenth image: 3 seconds
     * - All subsequent images: 3 seconds each
     *
     * @param htmlContent HTML content to analyze (can be null)
     * @param wordsPerMinute Reading speed (default 250 WPM)
     * @return Estimated reading time in minutes (minimum 1 minute if content exists, 0 for empty)
     */
    fun calculateReadingTime(
        htmlContent: String?,
        wordsPerMinute: Int = 250
    ): Int {
        if (htmlContent.isNullOrBlank()) {
            return 0
        }

        return try {
            val doc = Ksoup.parse(htmlContent)

            // 1. Extract text content (strips all HTML tags)
            val text = doc.body()?.text() ?: ""

            // 2. Count words (split by whitespace, filter empty) and analyze word length
            val wordsList = text.split("\\s+".toRegex())
                .filter { it.isNotBlank() }

            val wordCount = wordsList.size

            // 3. Adjust for word length complexity
            // Average word length in English is ~5 characters
            // Longer words take proportionally more time to read
            val avgWordLength = if (wordsList.isNotEmpty()) {
                wordsList.sumOf { it.length }.toDouble() / wordsList.size
            } else {
                5.0
            }

            // Apply a complexity factor: longer words increase reading time by up to 20%
            val complexityFactor = 1.0 + ((avgWordLength - 5.0) / 50.0).coerceIn(0.0, 0.2)
            val adjustedWords = wordCount * complexityFactor

            // 4. Count images
            val images = doc.select("img").size

            // 5. Calculate time components
            val wordReadingTimeSeconds = (adjustedWords / wordsPerMinute) * 60.0
            val imageReadingTimeSeconds = calculateImageReadingTime(images)

            val totalSeconds = wordReadingTimeSeconds + imageReadingTimeSeconds

            // 6. Convert to minutes (round up, minimum 1)
            val minutes = ceil(totalSeconds / 60.0).toInt()

            // Return at least 1 minute if there's content, otherwise 0
            if (wordCount > 0 || images > 0) {
                maxOf(1, minutes)
            } else {
                0
            }
        } catch (e: Exception) {
            // If parsing fails, return 0
            e.printStackTrace()
            0
        }
    }

    /**
     * Calculates additional time for images.
     *
     * Medium's algorithm:
     * - First image: 12 seconds
     * - Second image: 11 seconds (12 - (n-1))
     * - ...
     * - Tenth image: 3 seconds
     * - 11+ images: 3 seconds each
     *
     * @param imageCount Number of images
     * @return Additional seconds to add for images
     */
    private fun calculateImageReadingTime(imageCount: Int): Double {
        if (imageCount == 0) return 0.0

        var totalSeconds = 0.0

        for (i in 1..imageCount) {
            if (i <= 10) {
                // First 10 images: 12 seconds for first, decreasing by 1 second each
                totalSeconds += maxOf(3.0, 12.0 - (i - 1).toDouble())
            } else {
                // Remaining images: 3 seconds each
                totalSeconds += 3.0
            }
        }

        return totalSeconds
    }

    /**
     * Formats reading time as a human-readable string.
     *
     * @param minutes Reading time in minutes
     * @return Formatted string like "5mn" or "42mn"
     */
    fun formatReadingTime(minutes: Int): String {
        return if (minutes > 0) "${minutes}mn" else ""
    }
}
