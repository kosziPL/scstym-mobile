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

The patched core and playback resolver are maintained on `scstym-core-v15`
and `scstym-morideobfuscator-v15` in this same GitHub repository. Both submodules
pin exact commits. The other dependencies use the upstream release commits.

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

The original JVM tests do not verify the live YouTube service or visual
animation quality. See the Pixel follow-up below for subsequent device checks.
Fresh WebView sign-in, long-playlist stress testing, and tempo/pitch persistence
remain separate manual acceptance checks.

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

## Pixel diagnosis and authenticated playback fix

The device reproduced two independent authentication failures:

- Browse requests sent a personal Google ID as `X-Goog-PageId`, producing HTTP
  401. The server's `datasyncIdToken` is now preferred: personal identities stay
  non-delegated; brand identities retain both page and user session. Embedded
  selection commands inside managed-channel invitation dialogs are excluded.
- The youtubei.js HTTP bridge added an inferred user ID to the cookie signature.
  The same song failed with `LOGIN_REQUIRED` under that signature and returned
  audio under the standard cookie signature. The bridge now signs cookies using
  timestamp, cookie value and request origin, and sends the selected brand page
  independently. No account IDs or credentials are hardcoded.

Login candidates from WebView remain local until the selected backend identity
is verified. Login and account switching use request-specific snapshots, commit
DataStore, then publish the committed session. Startup repairs old identity
markers by matching their existing channel, without choosing an unrelated default.
Metadata retries no longer temporarily replace the global account. Home observes
the applied client session; application-owned sync observes persisted changes.
Unchanged proxy/DNS settings no longer close the HTTP client during startup.
The account sheet displays loading/failure/retry states, and rejected home
sessions get a specific message. Debug and Nightly retain SCSTYM branding.

The Pixel leak trace continued past the weak binder through an old Compose
keyboard callback into PlayerConnection and the destroyed MusicService.
The keyboard callback now captures a weak connection reference, cleared on
composition disposal. A second device trace exposed the same ownership through
a retained button callback. PlayerConnection.dispose() therefore releases its
service, player and local-player references as well as listeners and jobs;
late connection actions and player callbacks are ignored after disposal.
This removes the service graph regardless of which old UI callback is retained.

### Repeatable verification

```sh
./gradlew :core:test :morideobfuscator:testDebugUnitTest \
  :app:testGmsMobileUniversalDebugUnitTest \
  :app:assembleGmsMobileUniversalDebug \
  :app:assembleGmsMobileUniversalDebugAndroidTest
```

75 JVM tests pass. The opt-in `AccountSessionDeviceTest` uses the session already
stored on a device; it never exports cookies or identifiers. Without arguments
its device-dependent checks are skipped. After installing the target and test
APKs, run it using `am instrument` and the AndroidJUnitRunner:

- `verifySession=true`: verify persisted identity, every active channel's actual
  account response, and authenticated home loading.
- `mediaId=<accessible video ID>`: resolve song metadata and fetch actual audio
  bytes for every active channel through the production playback repository.
- `playlistId=<playlist ID>`: compare complete playlist entries with normal and
  shuffled queues, including duplicate entries.
- `switchChannelIndex=<index>`: explicitly opt into changing the saved channel
  through the production login repository. Run only `#switchPersistedChannel`.
- `expectedChannelIndex=<index>` with `verifySession=true`: verify persistence
  in a subsequent instrumentation process. Restore the original index afterward.
- `inspectShape=true`: output anonymized account-response structure for diagnosis.

Pixel 10 verification: authenticated home and both active channels passed;
metadata and audio bytes were fetched for both channels; the 29-entry liked
playlist matched its normal and shuffled queue. The queue check does not replace
a long-playlist pagination stress test. Fixtures cover continuation edge cases.

Channel persistence was also verified in separate instrumentation processes,
with the original selected channel restored after testing.

After the disposal fix, the first background/stop cycle produced a service
watch without a retained-service report. Reconnecting resumed real playback
with the saved 1.05 playback speed and no player error.
