# Brain

Front door for every agent working on YTXTube Custom. Read this first, then the last few entries of
the project log and the whole roadmap. House rules, release steps and the code map live in `CLAUDE.md`.

## Knowledge paths
- [[PROJECT_LOG]]: append-only log of what was asked, what was decided and why, and what shipped. See `PROJECT_LOG.md`.
- [[ROADMAP]]: the board. Headings are columns, bullets are cards. See `ROADMAP.md`.
- [[briefs/]]: the full brief for each roadmap card, one file per card.
- `CLAUDE.md`: standing rules from the owner, repos and remotes, releasing, building, where things are.
- `perf/`: speed pass measurements, audits and notes. Local only, not in git.

## What this project is
A personal Android YouTube and YouTube Music client, forked from Koustubh12345/YTXTube (its Liquid Glass UI)
and extended with ReVanced Extended style features. The owner uses it daily on a Honor Magic V5 foldable
(Android 16, 120 Hz, folded and unfolded layouts both matter) and shares the APK with friends.
Stack: Kotlin, Jetpack Compose, Media3 ExoPlayer, NewPipeExtractor, Kyant backdrop for glass effects, Coil.

## Key decisions
- 2026-11-13: The speed pass found that dropped frames are GPU bound (too many glass blur layers redrawn each frame),
  not caused by Compose recomposition. Glass work comes first. Details in `perf/perf-notes.md`.
- 2026-11-13: Project memory set up (this file, the log and the roadmap). See PROJECT_LOG.md.
