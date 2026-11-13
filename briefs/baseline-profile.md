# Generate a Baseline Profile (speed pass item 1)

**Goal:** Ship a Baseline Profile so cold start and first scrolls run precompiled code.

**Context:** The `baselineprofile` module and Gradle wiring are in the working tree, not committed.
Generation failed twice. Run 1 crawled for an hour (UiAutomator idle waits, fixed with shell input and
idle timeouts 0 in Journeys.kt). Run 2 died after 16 minutes when wireless adb dropped.
Benchmark helper apps are installed on the phone. They are not the owner's app and are safe to uninstall.

**Next step:** Reconnect adb, run `./gradlew :app:generateYtReleaseBaselineProfile` in a terminal tab
(its log is UTF-16), then measure cold start against the baseline.

**Done when:** The profile is generated and committed, and cold start measures faster than the
before numbers (unfolded median 290 ms to first frame, 95 ms warm).

**Source:** `perf/perf-notes.md`, `perf/audit-startup.md`.
