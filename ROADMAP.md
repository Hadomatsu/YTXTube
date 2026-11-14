# Roadmap

Headings are columns, bullets are cards. `[[briefs/slug]]` opens the card's full brief in `briefs/slug.md`.

## Backlog
- [[briefs/stuck-after-throttled-stream]] After a throttled stream, no video opens until the app is force-closed
- [[briefs/live-just-ended]] Opening a live that only just ended fails
- [[briefs/startup-off-main]] Move startup work off the main thread
- Compose recomposition fixes from `perf/audit-compose.md` (smaller wins, frames are GPU bound)

## Next
- [[briefs/faster-video-start]] Start playback before the details are post-processed
- [[briefs/hold-speed-keeps-going]] Hold for 2x keeps going when the finger moves (owner request)
- [[briefs/one-glass-language]] One glass look across the app: search chips, comments, every screen (owner request)

## In progress
- Speed pass items 4 to 8 (faster video start first), see `perf/HANDOFF-SPEED-2.md`

## Done
- Speed pass: hidden glass, Baseline Profile, Home feed from disk (cold start 295 to 243 ms, Home on screen 1.0-1.8 s to 0.35 s, comments and mini player jank 18-28 % to 3-5 %)
- 2.6.15: endless Shorts, tap to refresh
- 2.6.14: endless For You Shorts
- 2.6.13: Shorts stay on your own feed
- 2.6.12: full mouse support
