# Start playback before the details are post-processed

**Goal:** A shorter wait from tapping a video to its first frame.

**Context:** Opening a video takes about 2.2 to 2.8 s to the first frame. In PlayerState.kt `show()`,
`startPlayback` runs last, after HDR install, chapter and description parsing, related prefetch and comments.
Premium users also wait up to 3 s for PremiumAudio in PlaybackService.

**Done when:** Playback starts right after the stream data arrives, and the "first frame +N ms" log
(tag YTXTubeTime) shows a clear drop.

**Source:** `perf/audit-video-shorts.md` items 1 and 2.
