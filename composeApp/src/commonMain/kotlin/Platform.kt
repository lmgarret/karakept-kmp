interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun getCacheDir(context: coil3.PlatformContext): okio.Path?
