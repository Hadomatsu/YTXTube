# Opening a live that only just ended fails

**Goal:** A live that ended a moment ago opens instead of showing "This video isn't available".

**Context:** YouTube refuses extraction for a short while after a live ends. A live that ends while being
watched already turns into a static recording (LiveDvr.kt). The failure happens only when opening it fresh.

**Done when:** Opening a just-ended live either plays or retries quietly until it can.

**Source:** "Known open issues" in `CLAUDE.md`.
