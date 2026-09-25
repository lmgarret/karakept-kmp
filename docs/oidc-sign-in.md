# SSO / OIDC sign-in

Karakeep can sign users in through an OIDC provider (Authelia, Authentik, Keycloak, …), but only
in its web app. Its REST API accepts nothing but API keys and has no OAuth token exchange, and the
official mobile app offers password or API key only.

## How the app gets a key anyway

Karakeep's tRPC routes accept the NextAuth session cookie as well as a Bearer key
(`apps/web/server/api/client.ts` falls through to cookie auth). So:

1. `OidcSignInPane` opens `<server>/signin` in an embedded WebView (ComposeWebView — Android
   `WebView`, WKWebView / WebView2 / WebKitGTK on desktop). The user signs in however the server
   offers: SSO button, auto-redirect, or even a password.
2. After every page load on the server's own origin, `OidcSignInScreenModel` reads the WebView's
   cookies. Once a `next-auth.session-token` cookie is present (with or without the `__Secure-`
   prefix, possibly chunked `.0`/`.1`), it forwards them to
   `POST /api/trpc/apiKeys.create?batch=1` — the call the web UI's *API Keys* page makes.
3. The returned key is saved as a normal server entry, and the WebView's cookies for the server
   are cleared so no web session is left behind.

A 401 from `apiKeys.create` means the cookie was left over from an expired session; the model goes
back to waiting for the real login.

## Why not a Custom Tab or the system browser

NextAuth's default `redirect` callback only allows same-origin callback URLs, so a
`karakept://` callback is rewritten to the server's home page — the app is never called back.
And a Custom Tab's cookies are not readable by the app. An embedded WebView is the only place the
session can be read from without a server-side change.

## Limits

- Identity providers that refuse embedded WebViews (Google in particular) will not work.
- `apiKeys.create` is an internal tRPC route, not the documented API, so a Karakeep change can
  break this. API-key sign-in stays available as the fallback.
- Every successful sign-in creates a new API key named `Karakept` on the server.
