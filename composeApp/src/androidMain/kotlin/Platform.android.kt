import android.os.Build
import com.karakept.app.BuildConfig

import okio.Path.Companion.toPath

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getCacheDir(context: coil3.PlatformContext): okio.Path? {
    return context.cacheDir.resolve("image_cache").absolutePath.toPath()
}

actual val isDevBuild: Boolean = BuildConfig.IS_DEV
