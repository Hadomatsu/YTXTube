# Roadmap

Headings are columns, bullets are cards. `[[briefs/slug]]` opens the card's full brief in `briefs/slug.md`.

## Backlog
- Speed pass items 5 to 8 (small gains): Shorts screen grab at swipe release, seen-Shorts list written per swipe, startup work off the main thread, Coil and compose fixes. Item 4 measured at 4 ms and dropped. Shorts glass needs a look decision first.
- [[briefs/stuck-after-throttled-stream]] After a throttled stream, no video opens until the app is force-closed
- [[briefs/live-just-ended]] Opening a live that only just ended fails
- [[briefs/startup-off-main]] Move startup work off the main thread
- Compose recomposition fixes from `perf/audit-compose.md` (smaller wins, frames are GPU bound)

## Next
- [[briefs/faster-video-start]] Start playback before the details are post-processed
- [[briefs/one-glass-language]] One glass look: search page and video page cards done; screen-by-screen check of the rest still open

## In progress

## Done
- Hold for 2x keeps going while the finger moves (video and Shorts), search page and video page cards in glass (not released yet)
- 2.6.16: smoother and faster
- (in 2.6.16) Speed pass: hidden glass, Baseline Profile, Home feed from disk (cold start 295 to 243 ms, Home on screen 1.0-1.8 s to 0.35 s, comments and mini player jank 18-28 % to 3-5 %)
- 2.6.15: endless Shorts, tap to refresh
- 2.6.14: endless For You Shorts
- 2.6.13: Shorts stay on your own feed
- 2.6.12: full mouse support
