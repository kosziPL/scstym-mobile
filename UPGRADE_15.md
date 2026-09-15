# ArchiveTune 15.0.0 migration

Base: upstream tag `v15.0.0` (`f045340428911bb21695d9678556c2ccf7bb9f14`).
Rollback checkpoint: `c3132deb9`; see `patches/README.md` for the original
uncommitted core changes included in that checkpoint.

## Changes

- Playlist playback reads the playlist's browse pages, rather than relying on
  the shorter watch-next queue. Empty pages do not terminate pagination, and
  failed pages cannot silently produce a successful partial playlist.
- Continuation requests are serialized and cannot append to a replacement
  queue. The first song of a page is no longer dropped unconditionally.
  Radio cannot replace a playlist while its initial load is pending.
- Account parsing recognizes `pageIdToken` identities and retains the Google
  session alongside the YouTube channel ID. Saved v14 channel IDs are resolved
  to the v15 representation without substituting the default channel.
- Account refresh observes channel identity as well as the Google cookie.
  Account sheet actions wait for the closing animation.
- SCSTYM branding and the existing tempo/pitch controls are retained, including
  the `playbackTempo`, `playbackPitch`, and `playbackPitchManual` preference keys.
  Network hand-off prefetch uses v15's stream resolver.

## Dependency checkout

The patched core is maintained on `scstym-core-v15` in this same GitHub
repository. The submodule pins an exact commit; the other dependencies use the
commits from the upstream release.

```sh
git submodule update --init --recursive
```

## Verification

```sh
./gradlew :app:assembleFossMobileUniversalDebug :app:assembleGmsMobileUniversalDebug :app:testFossMobileUniversalDebugUnitTest :core:test
```

Core regression tests cover modern channel tokens, distinct channels sharing
one Google session, legacy DataSyncId parsing, pagination through empty pages,
repeated songs with distinct playlist-entry IDs, and failed/repeated continuation
responses. Account fixtures are synthetic and contain no login credentials.

Device checks still needed: real Google/YouTube login and channel switching,
sheet dismissal, a long playlist with shuffle on/off, and tempo/pitch after
restarting the app. Automated tests do not verify the live YouTube service or
visual animation quality.

## Account persistence and service lifecycle follow-up

- A `pageIdToken` without `mainAppWebResponseContext.dataSyncId` is represented
  as `channel||`, preserving its delegated identity through normalization and
  disk reload. Both request headers and the serialized request context use the
  same delegated-ID parser. A personal Google session remains non-delegated.
- Channel changes commit the session to DataStore before publishing it to the
  client. Login/account mutations are serialized; failed verification restores
  the committed session. Delayed player repairs cannot overwrite a newer
  account or silently replace a selected channel with the default channel.
- Application-scoped account observation invalidates playback sessions and
  refreshes the remote library on startup and channel changes, including changes sharing
  the same Google cookie. This no longer depends on HomeViewModel being alive.
- MusicBinder is a nested class with a weak service reference, explicitly cleared
  on service destruction. Normal unbinding disposes PlayerConnection listeners
  and its coroutine scope. Reconnection, activity destruction and AOD teardown
  also release old connections, while AOD does not bind twice on activity restart.

Regression tests additionally serialize actual YouTube request contexts, reopen
an on-disk preference store, and simulate stale playback repairs after a channel
switch. Device validation should include switching channels, restarting the app,
repeated background/foreground cycles, and inspecting a new LeakCanary report.
