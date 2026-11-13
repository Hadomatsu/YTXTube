# Project log

Append-only. New entries go at the bottom. Never edit or delete earlier entries.
Each entry records what was asked, what was decided and why, and what shipped.

## 2026-11-13 - Earlier history, summarised once
- **Asked:** Nothing new. This entry gives later agents a starting point.
- **Decided:** History before this log lives in git commits and `CLAUDE.md`. Recent releases were 2.6.12 (full mouse support), 2.6.13 (Shorts stay on your own feed), 2.6.14 (endless For You Shorts) and 2.6.15 (endless Shorts, tap to refresh).
- **Shipped:** Speed pass started on branch `speed-pass`: measurement aids committed (32e4922). The Baseline Profile module and the hidden-glass edits are in the working tree, not committed, not built or tested.
- **Open:** Baseline Profile generation failed twice (UiAutomator idle waits, then wireless adb dropped). See the roadmap cards.

## 2026-11-13 - Set up project memory
- **Asked:** Set up the memory system from a TikTok video: a brain file, an append-only project log and a markdown roadmap, so agents never forget why things were built.
- **Decided:** Files live at the repo root, with card briefs in `briefs/`. `CLAUDE.md` stays the place for house rules and the code map, and BRAIN.md links to it instead of copying it. **Why:** one source for each kind of fact. The roadmap was seeded from `perf/perf-notes.md`, the five perf audits and the known open issue in `CLAUDE.md`. The work went on a new branch `project-memory`, made from `speed-pass`, so `speed-pass` itself is unchanged.
- **Shipped:** BRAIN.md, PROJECT_LOG.md, ROADMAP.md, six briefs, and one line at the top of CLAUDE.md. Committed locally, not pushed.
- **Open:** The uncommitted speed pass edits are still in the working tree, untouched. origin is a public repo, so these files become public when pushed.

## 2026-11-13 - Memory files moved onto speed-pass
- **Asked:** Update speed-pass so it includes the project memory commit.
- **Decided:** Fast-forward only, no merge commit. **Why:** speed-pass had no commits of its own after the branch point, so its history stays linear. The uncommitted speed pass edits stayed in the working tree untouched.
- **Shipped:** speed-pass now points at the memory commit, with this entry on top. The project-memory branch is left in place and has nothing extra. Not pushed.
- **Open:** None.

## 2026-11-13 - Caveman replies, push rule, tools from two TikTok videos
- **Asked:** Reply in caveman style from now on. Agents may push changes that do not touch the app; app changes still wait for the owner's "push". Set up the tools from two TikTok videos: five free skills (Matt Pocock engineering, Apple design, Humanizer, Lemo-Opuscar, Corey Haines marketing) and four usage savers (Ponytail, RTK, Graphify, OmniRoute).
- **Decided:** Both rules went into the standing rules in CLAUDE.md. All tools are installed for the whole Claude setup, not just this repo, from their official repos only (lookalike forks exist for most). OmniRoute was skipped: it sends code to unknown free model providers and needs API keys, so it waits for the owner's explicit yes. The project-memory commits were not pushed, because speed-pass also carries an unreleased app commit (measurement aids).
- **Shipped:** Apple design skill copied to ~/.claude/skills/apple-design. RTK installed with winget. Plugin marketplaces added for mattpocock, humanizer, lemolab and ponytail. Ponytail hooks were reviewed first: local git calls only, its webhook is off unless a URL is set.
- **Open:** Claude Code's auto mode blocked four steps, so the owner runs them: installing the plugins, `rtk init -g`, `uv tool install graphifyy` with `graphify install`, and the marketing skills marketplace if its retry fails.
