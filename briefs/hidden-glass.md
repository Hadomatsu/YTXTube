# Stop drawing glass that is hidden or off screen (speed pass item 2)

**Goal:** Cut dropped frames in Shorts and over-video panels by removing glass layers nobody can see.

**Context:** Traces show the jank is RenderThread bound: about 19 to 20 offscreen layers per frame and about
5 ms per frame flushing them. Shorts swipes drop 25 to 34 % of frames. Edits in VideoView.kt,
ShortsScreen.kt and MainActivity.kt are in the working tree, not built or tested yet.
Audit targets: the Shorts top bar composed on every pager page (12 glass circles, 4 visible), video controls
glass kept at alpha 0, and the Liquid mini player kept at alpha 0 behind the full player.

**Done when:** Built, installed, and Shorts swipe and comments-over-video jank measure lower than the
before numbers in `perf/perf-notes.md`, with no visual change.

**Source:** `perf/perf-notes.md`, `perf/audit-glass.md`.
