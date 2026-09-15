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
