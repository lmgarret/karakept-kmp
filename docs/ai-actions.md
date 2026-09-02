# AI actions on bookmarks

Karakeep can generate a summary for a bookmark and can re-run its AI tagging. Karakept exposes
both, from the reader, from a bookmark's long-press / right-click menu, and over a hand-picked
multi-selection.

The two are not symmetric, and most of the design follows from that.

## What the server actually offers

| | Generate summary | Re-run AI tagging |
|---|---|---|
| Route | `POST /bookmarks/{id}/summarize` (documented REST) | `admin.adminRetagBookmark` (tRPC) |
| Who may call it | any account | **admins only** |
| Shape | synchronous — returns the finished summary | enqueues a job; the result arrives later |
| Writes | `bookmarks.summary` | the bookmark's tags |

There is no user-level route that re-runs tagging on a single bookmark. The only alternative,
`bookmarks.recrawlBookmark`, re-downloads the page as a side effect and is already the app's
"Refresh" action, so it would be a second, more expensive way to spell something the user can
already do. Hence the admin route, and hence the action being hidden for everyone else.

## Why the summary is its own column

The inference worker writes `bookmarks.summary`. It *reads* `description` as prompt input and
never writes it — `description` only ever comes from the page's own meta tags during a crawl.

So the two are different things and can both be present: an AI summary of the article, and the
publisher's teaser. `BookmarkEntity` therefore carries `summary` and `summarizationStatus`
alongside `description` (schema v12, `Migration11To12`). The reader shows the summary in its own
labelled card above the description card.

Both fields are mapped in the ordinary sync path, so a summary generated anywhere — the Karakeep
web UI, another client, the crawler's own auto-summarization — reaches the app without anyone
pressing anything here. `isUnchanged` compares the summary explicitly: Karakeep does not reliably
bump `modifiedAt` for a summary, and without that comparison the write is skipped as "nothing
changed" and the summary never lands.

## Capability probing

Neither `GET /users/me` nor tRPC `users.whoami` reports the user's role, so admin status is
discovered by calling something only an admin may call. `admin.getAdminNoticies` is the cheapest
such route — its handler returns an empty object and touches no data.

`BookmarkActionsRepository.aiCapabilities` caches the answer per server, in memory:

- `isAdmin` — set by `refreshAiCapabilities`, run once per server when the list selects it and
  when the reader opens a bookmark. A transport failure leaves the entry untouched rather than
  caching a wrong "not an admin", which would make the action vanish mid-session.
- `canSummarize` — starts true and is turned off the first time the server answers
  `400 "No inference client configured"`. That is the one refusal that will never change without
  the operator reconfiguring the instance, so it is worth remembering; an ordinary failure is not.

In memory rather than on disk on purpose: a re-probe on next launch is exactly when a
reconfigured server is most likely to give a different answer.

## Where the actions live

| Surface | Code |
|---|---|
| Reader — overflow menu | `ui/screens/viewer/ViewerTopBar.kt` |
| Reader — details panel | `ui/screens/viewer/BookmarkDetailsPanel.kt`, in the AI group beside the server actions |
| Long press (touch) | `ui/components/BookmarkActionsMenu.kt` |
| Right click (desktop) | `ui/components/BookmarkContextMenu.kt` |
| Multi-select | `ui/screens/main/MainScreenTopBar.kt`, run by `MainScreenModelBatch.batchAiAction` |

Each entry is hidden unless the matching capability is present. Offering an action the server will
only refuse is worse than not offering it.

## No undo, and no offline queue

These bypass `BookmarkActionController` and the pending-action queue, like
`ServerCrawlAction` does. A generated summary has no previous value worth restoring, a re-tag is
the server's own judgement, and neither has a local optimistic result to show while offline. What
they do share with every other mutation is the ending: write the row, then
`notifyBookmarkChanged`, which is what refreshes the list and the reader without a full sync.

## Multi-selection

`batchAiAction` runs the selection **one bookmark at a time**. Each item is an inference call the
server pays for, and firing a selection's worth in parallel is how a self-hosted instance starts
refusing them. A failure is counted rather than fatal — the run finishes and reports how many
landed — because abandoning it halfway leaves the user with no idea which half worked. The one
exception is `UnsupportedServerActionException`, which will apply identically to every remaining
item, so the run stops there.

**Select All never reaches these actions.** `selectAll()` sets `selectedViaSelectAll`, cleared by
every manual selection entry point, and the AI items are hidden while it is set. Select All can
pull the entire library into the selection; one tap should not be able to start hundreds of
inference jobs.

Progress is reported as text in the selection bar (`Generating summary 3 of 12`) with the Close
button becoming Cancel. Text rather than a progress bar so it needs no separate e-ink treatment.
