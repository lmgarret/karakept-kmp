package com.karakept.app.utils

/**
 * Helpers for signing in through Karakeep's web login (OIDC/SSO) and trading the resulting
 * browser session for an API key.
 *
 * Karakeep's REST API only takes API keys and offers no OAuth token exchange, but its tRPC
 * routes also accept the NextAuth session cookie the web login sets. So the app runs the
 * normal web sign-in in an embedded WebView, reads that cookie back out, and calls
 * `apiKeys.create` with it — the same thing the web UI's "API Keys" settings page does.
 */
object OidcSignInUtils {

    const val API_KEY_NAME = "Karakept"

    private const val SESSION_COOKIE = "next-auth.session-token"

    /** The server's web origin: the stored URL may point at the REST base (`…/api/v1`). */
    fun webBaseUrl(serverUrl: String): String =
        serverUrl.trim().removeSuffix("/").removeSuffix("/api/v1").removeSuffix("/")

    fun signInUrl(serverUrl: String): String = "${webBaseUrl(serverUrl)}/signin"

    /**
     * NextAuth names the cookie `__Secure-next-auth.session-token` over https and splits it into
     * `….0`, `….1` chunks once the JWT outgrows one cookie, so match on the stem.
     */
    fun isSessionCookie(name: String): Boolean = name.contains(SESSION_COOKIE)

    fun hasSession(cookies: List<Pair<String, String>>): Boolean =
        cookies.any { (name, value) -> isSessionCookie(name) && value.isNotBlank() }

    fun cookieHeader(cookies: List<Pair<String, String>>): String =
        cookies.joinToString("; ") { (name, value) -> "$name=$value" }

    /** Only pages on the server itself can have set its session; the IdP's pages cannot. */
    fun isOnServer(url: String?, serverUrl: String): Boolean {
        if (url == null) return false
        val base = webBaseUrl(serverUrl)
        return url == base || url.startsWith("$base/") || url.startsWith("$base?")
    }
}
