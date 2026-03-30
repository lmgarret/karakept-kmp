interface Platform {
    val name: String
    val isDesktop: Boolean get() = false
}

expect fun getPlatform(): Platform

expect fun getCacheDir(context: coil3.PlatformContext): okio.Path?

expect val isDevBuild: Boolean
