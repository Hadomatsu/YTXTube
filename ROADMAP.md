# Roadmap

Headings are columns, bullets are cards. `[[briefs/slug]]` opens the card's full brief in `briefs/slug.md`.

## Backlog
- Speed pass items 5 to 8 (small gains): Shorts screen grab at swipe release, seen-Shorts list written per swipe, startup work off the main thread, Coil and compose fixes. Item 4 ([[briefs/faster-video-start]]) measured at 4 ms and dropped. Shorts glass needs a look decision first.
- [[briefs/stuck-after-throttled-stream]] After a throttled stream, no video opens until the app is force-closed
- [[briefs/live-just-ended]] Opening a live that only just ended fails
- [[briefs/startup-off-main]] Move startup work off the main thread
- Compose recomposition fixes from `perf/audit-compose.md` (smaller wins, frames are GPU bound)

## Next
- Check on the phone: live chat glass sheet (needs a live video), settings choice sheets, second captions sheet, Downloads menu

## In progress

## Done
- Hold captions to move them anywhere (snap to the middle, reset in the caption panel); landscape unfolded video page ends at comments with Subscribe in the action row; glass Subscribe (not released yet)
- 2.6.19: endless Home (topic feeds after the main feed), endless "More videos", small glass subscribe button on Shorts
- 2.6.18: glass menus everywhere
- [[briefs/one-glass-language]] One glass look: dropdown menus and live chat are glass sheets (in 2.6.18)
- 2.6.17: hold for 2x stays on while the finger moves, glass search bar and filters, glass cards on the video page
- 2.6.16: smoother and faster
- (in 2.6.16) Speed pass: hidden glass, Baseline Profile, Home feed from disk (cold start 295 to 243 ms, Home on screen 1.0-1.8 s to 0.35 s, comments and mini player jank 18-28 % to 3-5 %)
- 2.6.15: endless Shorts, tap to refresh
- 2.6.14: endless For You Shorts
- 2.6.13: Shorts stay on your own feed
- 2.6.12: full mouse support
