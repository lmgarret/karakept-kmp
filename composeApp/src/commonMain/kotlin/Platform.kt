interface Platform {
    val name: String
    val isDesktop: Boolean get() = false
}

expect fun getPlatform(): Platform

expect fun getCacheDir(context: coil3.PlatformContext): okio.Path?

/** The context the singleton image loader is built for, reachable outside composition. */
expect fun imageLoaderContext(): coil3.PlatformContext

expect val isDevBuild: Boolean
