import android.os.Build
import com.karakept.app.R
import com.karakept.app.data.local.AndroidContext

import okio.Path.Companion.toPath

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getCacheDir(context: coil3.PlatformContext): okio.Path? {
    return context.cacheDir.resolve("image_cache").absolutePath.toPath()
}

// Read once, off a resource rather than a BuildConfig field: this module is a KMP library
// now, so it has no build types to generate one from. :androidApp's devRelease source set
// overrides the value.
private val devBuild: Boolean by lazy {
    AndroidContext.context.resources.getBoolean(R.bool.karakept_is_dev)
}

actual val isDevBuild: Boolean get() = devBuild
