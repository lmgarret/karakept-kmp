// Platform and getPlatform() live in :core:ui (PlatformInfo.kt); these stay with the app, which
// owns the image loader and the dev-build resource.
expect fun getCacheDir(context: coil3.PlatformContext): okio.Path?

/** The context the singleton image loader is built for, reachable outside composition. */
expect fun imageLoaderContext(): coil3.PlatformContext

expect val isDevBuild: Boolean
