# Move startup work off the main thread

**Goal:** A faster cold start, by moving about 38 serial inits off the main thread where order allows.

**Context:** 14 SharedPreferences files, a Keystore decrypt for the account, a MediaCodecList walk for HDR,
the Downloads JSON parse and a PoToken WebView all run at launch. The audit lists the order constraints and a
suggested sequence.

**Done when:** Cold start to first frame is measurably faster with no change in behaviour.

**Source:** `perf/audit-startup.md`.
