import okio.Path.Companion.toPath

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val isDesktop: Boolean = true
}

actual fun getPlatform(): Platform = JVMPlatform()

actual val isDevBuild: Boolean = false

actual fun getCacheDir(context: coil3.PlatformContext): okio.Path? {
    val cacheDir = java.io.File(System.getProperty("user.home"), ".karakept/image_cache")
    if (!cacheDir.exists()) {
        cacheDir.mkdirs()
    }
    return cacheDir.absolutePath.toPath()
}
