package com.karakept.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OidcSignInUtilsTest {

    @Test
    fun webBaseUrlStripsTrailingSlashAndRestSuffix() {
        assertEquals("https://kk.example.com", OidcSignInUtils.webBaseUrl("https://kk.example.com/"))
        assertEquals("https://kk.example.com", OidcSignInUtils.webBaseUrl("https://kk.example.com/api/v1"))
        assertEquals("https://kk.example.com", OidcSignInUtils.webBaseUrl(" https://kk.example.com/api/v1/ "))
        assertEquals("https://example.com/karakeep", OidcSignInUtils.webBaseUrl("https://example.com/karakeep"))
    }

    @Test
    fun signInUrlPointsAtTheWebLoginPage() {
        assertEquals("https://kk.example.com/signin", OidcSignInUtils.signInUrl("https://kk.example.com/api/v1"))
    }

    @Test
    fun sessionCookieMatchesEveryNextAuthVariant() {
        assertTrue(OidcSignInUtils.isSessionCookie("next-auth.session-token"))
        assertTrue(OidcSignInUtils.isSessionCookie("__Secure-next-auth.session-token"))
        assertTrue(OidcSignInUtils.isSessionCookie("__Secure-next-auth.session-token.0"))
        assertFalse(OidcSignInUtils.isSessionCookie("next-auth.csrf-token"))
        assertFalse(OidcSignInUtils.isSessionCookie("__Host-next-auth.csrf-token"))
    }

    @Test
    fun hasSessionIgnoresOtherCookiesAndBlankValues() {
        assertFalse(OidcSignInUtils.hasSession(listOf("next-auth.csrf-token" to "x")))
        assertFalse(OidcSignInUtils.hasSession(listOf("next-auth.session-token" to "")))
        assertTrue(
            OidcSignInUtils.hasSession(
                listOf("next-auth.csrf-token" to "x", "__Secure-next-auth.session-token" to "jwt")
            )
        )
    }

    @Test
    fun cookieHeaderForwardsEveryCookie() {
        assertEquals(
            "__Secure-next-auth.session-token.0=a; __Secure-next-auth.session-token.1=b",
            OidcSignInUtils.cookieHeader(
                listOf(
                    "__Secure-next-auth.session-token.0" to "a",
                    "__Secure-next-auth.session-token.1" to "b"
                )
            )
        )
    }

    @Test
    fun isOnServerRejectsTheIdentityProviderAndLookalikeHosts() {
        val server = "https://kk.example.com/"
        assertTrue(OidcSignInUtils.isOnServer("https://kk.example.com", server))
        assertTrue(OidcSignInUtils.isOnServer("https://kk.example.com/dashboard/bookmarks", server))
        assertTrue(OidcSignInUtils.isOnServer("https://kk.example.com?x=1", server))
        assertFalse(OidcSignInUtils.isOnServer("https://auth.example.com/login", server))
        assertFalse(OidcSignInUtils.isOnServer("https://kk.example.com.evil.test/", server))
        assertFalse(OidcSignInUtils.isOnServer(null, server))
    }
}
