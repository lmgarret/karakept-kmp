package com.karakept.app.domain

data class ServerVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ServerVersion> {
    override fun compareTo(other: ServerVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        // Release builds report "0.31.0" or "v0.31.0"; anything after the numbers (a pre-release
        // tag) is ignored, so a release candidate counts as the release it leads up to.
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?""")

        fun parse(raw: String): ServerVersion? {
            val match = PATTERN.find(raw.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return ServerVersion(major.toInt(), minor.toInt(), patch.ifEmpty { "0" }.toInt())
        }
    }
}

enum class ServerCompatibility {
    SUPPORTED,
    OUTDATED,

    /** A version string that is not a release number — "nightly", or "unknown" from a source build. */
    UNRECOGNIZED
}

/**
 * What a server said about its version. [version] is null when the server predates
 * `GET /api/version`, which is itself enough to know it is below the minimum.
 */
data class ServerVersionCheck(
    val version: String?,
    val compatibility: ServerCompatibility
)

object ServerVersionUtils {
    /**
     * The oldest Karakeep release every feature of this build works against.
     *
     * Bump it in the same change that starts relying on a newer server feature, and add the
     * feature to the list below so the next bump knows what it is protecting.
     *
     * - 0.31.0: synchronized reading progress (karakeep#2302)
     */
    val MIN_RECOMMENDED_VERSION = ServerVersion(0, 31, 0)

    fun evaluate(reported: String?): ServerVersionCheck {
        if (reported == null) return ServerVersionCheck(null, ServerCompatibility.OUTDATED)
        val parsed = ServerVersion.parse(reported)
            ?: return ServerVersionCheck(reported, ServerCompatibility.UNRECOGNIZED)
        val compatibility = if (parsed >= MIN_RECOMMENDED_VERSION) {
            ServerCompatibility.SUPPORTED
        } else {
            ServerCompatibility.OUTDATED
        }
        return ServerVersionCheck(reported, compatibility)
    }

    fun outdatedWarning(check: ServerVersionCheck): String {
        val current = check.version?.let { " ($it)" } ?: ""
        return "Your Karakeep server$current is older than $MIN_RECOMMENDED_VERSION. " +
            "Some features may not work until it is updated."
    }
}
