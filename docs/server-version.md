# Server version check

Some features need a recent Karakeep server. The app does not refuse an older one — most of it
still works — but it tells the user when something may not.

## Where the version comes from

Karakeep serves `GET {server}/api/version`, unauthenticated, returning `{"version": "0.31.0"}`.
Release builds report the release name (with or without a leading `v`); Docker images built off
`main` report `nightly`, and source builds without `SERVER_VERSION` report `unknown`.

`RemoteDataSource.fetchServerVersion` returns the string, `null` when the route is a 404, and
throws on anything else. `ServerVersionUtils.evaluate` turns that into a `ServerVersionCheck`:

| Server answer | `ServerCompatibility` | Warned? |
|---|---|---|
| a release `>= MIN_RECOMMENDED_VERSION` | `SUPPORTED` | no |
| a release below it | `OUTDATED` | yes |
| 404 — the server predates the route | `OUTDATED` (version `null`) | yes |
| `nightly`, `unknown`, anything unparseable | `UNRECOGNIZED` | no |
| unreachable, 5xx, unreadable body | no check at all | no |

A server we cannot reach is never called outdated: that is a connectivity problem, and the sync
error already says so.

## Where it is shown

- **Login / onboarding** — the "Test" button reports the version under the connection result.
- **Startup** — `MainScreenModel` probes the selected server and shows a long snackbar if it is
  outdated, once per server per process (`ServerVersionRepository.claimOutdatedWarning`).
- **Settings → Server Connection** — the server's version and the minimum recommended one,
  re-probed whenever the screen opens.

Results are kept in memory only (`ServerVersionRepository.versions`), like the AI capabilities —
a server is most likely to have been upgraded across a restart, which is when it is re-probed.

## Bumping the minimum

`MIN_RECOMMENDED_VERSION` is the first Karakeep release that ships every server feature the app
uses. Bump it in the same change that starts depending on something newer, and list the feature
in its KDoc so the next bump knows what it is protecting.

The switch is global: the app warns, it does not turn individual features off by version. Where a
feature can detect that the server lacks it — as the AI actions do — it should still degrade on
that signal, since a nightly server reports no comparable version.
