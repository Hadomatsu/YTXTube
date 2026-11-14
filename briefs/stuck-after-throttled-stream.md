# After a throttled stream, no video opens until the app is force-closed

**Goal:** Opening a new video always works, even after the previous one got stuck.

**Context:** Seen once on 2026-11-14 during speed testing (old 2.6.15 build). A video's stream was
throttled to about 100 KB/s and sat in BUFFERING. Opening another video then logged `open <id>` but no
details arrived after 50 s. After a force-stop the same video opened in 1.9 s. Something in the open path
(extraction, `StreamCache.getOrLoad`, or the player's loadJob in `PlayerState.kt`) seems to wait on the
stuck request. Not yet reproduced on purpose.

**Done when:** Reproduced (for example by throttling the network), the cause found, and a new open works
while the old stream is still stuck.

**Source:** `perf/perf-notes.md`, session 2 side finding.
