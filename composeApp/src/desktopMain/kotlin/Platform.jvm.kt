import okio.Path.Companion.toPath

actual val isDevBuild: Boolean = System.getProperty("karakept.dev")?.toBoolean() == true

actual fun getCacheDir(context: coil3.PlatformContext): okio.Path? {
    val cacheDir = java.io.File(System.getProperty("user.home"), ".karakept/image_cache")
    if (!cacheDir.exists()) {
        cacheDir.mkdirs()
    }
    return cacheDir.absolutePath.toPath()
}

actual fun imageLoaderContext(): coil3.PlatformContext = coil3.PlatformContext.INSTANCE
