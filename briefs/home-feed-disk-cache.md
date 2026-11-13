# Disk cache for the Home feed

**Goal:** Cold start shows the last Home feed straight away, then refreshes it.

**Context:** FeedState (Feeds.kt) is in memory only, so the first frame is an empty list until the network
answers. The first feed is fully drawn 1.2 to 1.5 s after launch. Ranked first in the network audit.

**Done when:** A cold start with network shows cached videos in the first frames and swaps in fresh ones
without a jump.

**Source:** `perf/audit-network.md`, `perf/audit-startup.md`.
