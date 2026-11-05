# Sidecars

A **sidecar** is an optional self-hosted service that runs next to a Karakeep instance and does
extra work on bookmarks. The app assumes none is deployed: nothing about sidecars appears in the UI
until one is configured, and the actions stay hidden until a service has said it will run them.

The only sidecar the app knows about today is
[karhtmlscrub](https://github.com/lmgarret/karhtmlscrub), which sanitizes crawled article HTML —
ad nags, sign-in banners, broken iframe remnants — and uploads the result.

## The asymmetry that shapes everything here

**Reading a sidecar's output needs no sidecar configuration.** karhtmlscrub writes its result into
Karakeep as an asset named `karhtmlscrub-sanitized.html`, so it reaches the app through ordinary
sync, exactly like a crawled archive. A user whose server has a sidecar attached gets the cleaned
copy with nothing configured in the app at all.

Configuration exists for one thing only: **asking for work on demand**, rather than waiting for the
next crawl. That is why `SidecarRepository` is absent from the reading path entirely.

## Reading the cleaned copy

`ContentSource.SANITIZED` is the third content source, beside `EXTRACTED` and `FULL_PAGE_ARCHIVE`.
Its payload is a `<body>` fragment, like extracted content and unlike a full-page archive, so it
renders through the Reader path only — handing it to the web view as a local file would show a bare
fragment with no document around it.

**The asset is resolved by name, never by id.** Karakeep's asset replacement deletes the old row and
points a *new* asset id at the new blob, so the id changes every time an article is re-cleaned. The
name is the only stable handle, which is why:

- `AssetEntity.fileName` is now preserved by both sync paths (it was written as `null` before this
  feature, by a bug nothing had noticed because nothing read it);
- `AssetDao.pruneSupersededNamedAsset` drops the row and cached file left behind by a re-clean,
  otherwise the viewer sees two rows with the same name and can pick the dead one.

`AssetTypeMapping.storedAssetType` is where both sync paths decide what to keep. `userUploaded` is
the only asset type attachable over Karakeep's API, so it covers every asset anyone attached by
hand; only the sidecar's own file name admits one. Tracking *all* hand-attached uploads would start
listing unrelated files in the viewer's details panel — a separate decision, deliberately not taken
here.

### The preference

`preferSanitizedHtml` (default **off**) opens a bookmark on its cleaned copy when one exists, and
downloads it during sync so it is there offline. Precedence is **sanitized → full-page archive →
extracted**: the sanitized copy is the cleaned version of the same article the archive holds a raw
copy of.

It is a separate flag rather than a third value on `preferFullPageHtml` because turning that boolean
into an enum would be a breaking migration for a setting users have already set.

## Asking for a clean on demand

`SidecarAction.SANITIZE_HTML` appears in the reader's overflow menu and the details panel's
*Sidecars* section — and only there, for now. Like `AiAction` and `ServerCrawlAction` it bypasses
`BookmarkActionController` and the pending-action queue: there is nothing to undo and no local
optimistic result to show while offline.

The service's API (`docs` in karhtmlscrub's README):

| Route | Answer |
|---|---|
| `POST /api/v1/sanitize/{bookmarkId}` | `202` with a new job; `200` with the job already in flight; `503` when no model is configured |
| `GET /api/v1/sanitize/{bookmarkId}` | that bookmark's most recent job; `404` when the service has not been asked about it |
| `GET /api/v1/capabilities` | `{service, llmEnabled, maxContentSize, assetName, dryRun}` |

A job carries a typed **outcome**, and that is what makes this better than the crawl polling it
otherwise copies:

| Outcome | Shown as | Keeps polling? |
|---|---|---|
| `uploaded` | Article cleaned up | — (syncs and shows it) |
| `skipped_filtered` | The service's rules exclude this bookmark | no — terminal |
| `skipped_too_large` | This article is too long for the service | no — terminal |
| `skipped_llm_disabled` | The service has no model configured | no — terminal |
| `skipped_no_content` | Nothing crawled to clean up yet | no |
| `failed` | The service couldn't clean up this article | no |

A terminal outcome means retrying the same bookmark unchanged could never give a different answer,
so the loop stops and says why instead of waiting out its budget and reporting a timeout.

Jobs live in the service's memory and are lost when it restarts, so a `404` on a poll means "the
answer is unknown now", not "nothing happened". The asset in Karakeep is the only durable record,
which is what the fallback checks.

## Capability gating

An action is offered only when the sidecar is **configured, enabled, reachable, and reporting
`llmEnabled: true`**. `/healthz` answers 200 whether or not a model is configured, and a
karhtmlscrub without one silently produces nothing forever — reachability alone must not light up
the action.

`SidecarRepository.capabilities` caches the answer per `(server, kind)` **in memory**, probed when
the reader opens a bookmark (beside the existing AI probe) and when the settings screen saves. Two
rules it shares with `BookmarkActionsRepository.aiCapabilities`:

- a failed probe leaves the cached entry untouched rather than caching a "can't", which would make
  the action vanish under the user mid-session;
- in memory rather than on disk, because a re-probe on next launch is exactly when a reconfigured
  service is most likely to answer differently.

The probe is additionally guarded so hard it cannot fail: a misconfigured or unreachable sidecar
must never be able to stop a bookmark from opening. The action simply stays hidden.

## Where configuration lives

| What | Where | Why |
|---|---|---|
| Address, enabled | DataStore, `sidecars_json`, keyed `"<serverId>:<kind>"` | Mirrors `perListSettings`; no Room entity, no migration |
| Bearer token | `SecureCredentialStore`, alias `sidecar:<serverId>:<kind>` | A secret never belongs in a settings blob or a database column |

Per **server**, because a sidecar talks to one Karakeep instance — pointing two servers at the same
sidecar would have it upload assets to the wrong one.

Both travel in the encrypted backup as `AppBackup.sidecars`, handled by `BackupRepository` the way
servers are, token included. Without that, a restore brings the servers back and silently drops the
services attached to them, which looks like a working restore right up to the point the actions are
missing. Server ids are derived from the server URL, so the `"<serverId>:<kind>"` keys still resolve
on the importing device.

## Adding another sidecar kind

1. A constant in `SidecarKind`, plus its label and description.
2. A capabilities parser, if the service reports something other than karhtmlscrub's shape.
3. An action in `SidecarAction` and a branch in `BookmarkViewerScreenModel.runSidecarAction`.

The settings screen renders one card per entry in `SidecarKind.ALL`, and storage keys everything by
kind, so neither needs touching.

## Code map

| Layer | File |
|---|---|
| Models, asset-name contract | `data/model/Sidecar.kt` |
| Kinds | `domain/sidecar/SidecarKind.kt` |
| Action | `domain/action/SidecarAction.kt` |
| HTTP | `data/remote/SidecarDataSource.kt` |
| Config, token, capabilities | `data/repository/SidecarRepository.kt` |
| Asset-type rule | `data/local/AssetTypeMapping.kt` |
| Superseded-row cleanup | `data/local/AssetPruning.kt` |
| Reader wiring | `ui/screens/BookmarkViewerScreenModel.kt`, `ui/screens/viewer/` |
| Settings | `ui/screens/settings/SidecarsSettingsScreen.kt` (+ ScreenModel) |

## Not done yet

- The action is reader-only. The list surfaces (`BookmarkActionsMenu`, `BookmarkContextMenu`,
  multi-select batch) would follow the pattern `docs/ai-actions.md` describes; the repository layer
  already supports it.
- The app never *uploads* an asset, so it cannot stand in for the sidecar when the service is
  offline. It only reads what the service produced.
