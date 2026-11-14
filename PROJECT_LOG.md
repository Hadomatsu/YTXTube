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

## 2026-11-13 - OmniRoute installed after all
- **Asked:** Install OmniRoute anyway. The owner is not worried about free providers seeing code and needs the reroute because tokens run out.
- **Decided:** Installed the npm package `omniroute` 3.8.53, checked against the maintainer diegosouza.pw (the repo now lives at cheaper-inference/OmniRoute). **Why:** the owner's explicit choice after hearing the risks.
- **Shipped:** OmniRoute installed globally and its server started on http://localhost:20128.
- **Open:** The owner signs in to the dashboard and changes the default password, connects the Claude subscription and free providers, then runs `omniroute configure claude`. The server only runs while started, so Claude breaks if it is pointed at OmniRoute while OmniRoute is off.

## 2026-11-13 - Plugins installed, skill list pruned
- **Asked:** Run the remaining installs instead of handing them over. Work out how many tokens the ~100 extra skills waste per message, and remove what the owner will never use.
- **Decided:** Measured every enabled plugin's skill, command and agent descriptions, which ride along on every request. ECC listed 314 skills, 98 commands and 71 agents (about 34k tokens). Marketing skills listed 53 (about 5k). Past sessions used none of them: only the claude-api and caveman skills, /model and general-purpose subagents. Both were uninstalled; ECC's hooks (GateGuard fact prompts, cost notices, bash logging) went with it. Kept Matt Pocock skills, Humanizer, Lemo-Opuscar, Ponytail, Apple design, Graphify and project-memory: about 2.8k tokens together, down from about 41.7k.
- **Shipped:** Plugins installed: mattpocock-skills, humanizer, lemo-opuscar, ponytail. RTK hook added to ~/.claude/settings.json (PreToolUse on Bash) with rtk.exe copied into ~/.local/bin so rewritten commands find it. Graphify installed with uv and its skill registered. Settings backups are in ~/.claude/backups/.
- **Open:** Restart Claude to load the changes. The rtk.exe copy in ~/.local/bin does not update with winget; re-copy after an RTK upgrade. The ECC marketplace is still listed, so ECC can be reinstalled in one command. OmniRoute still needs the owner's dashboard sign-in.

## 2026-11-13 - ECC restored, OmniRoute tested
- **Asked:** Put ECC back (the owner uses it, including handoffs and subagents). Finish OmniRoute setup now that the owner is signed in.
- **Decided:** Removing ECC was a mistake. The usage check only searched 10 local transcript files for ECC skill calls by name, so it missed handoffs, subagents and sessions stored elsewhere, and the owner was not asked first. ECC is reinstalled with its earlier data folder intact. Rule for next time: never remove something the owner installed without asking, whatever the usage data says. Marketing skills stay removed. For OmniRoute, made a gateway key named "Claude Code" (saved in ~/.omniroute/claude-key.txt) and installed Playwright Chromium so the keyless Cloudflare Playground route works. Plain chat works (Kimi K2.6), but tool calls come back as text instead of real tool calls, so Claude Code cannot edit files or run commands on it. **Why not switched on:** pointing Claude at OmniRoute now would break every session.
- **Shipped:** ECC 2.2.3 back on. OmniRoute key, Chromium runtime, keyless providers on by default.
- **Open:** A free provider with real tool calling is needed first, for example a free Google AI Studio (Gemini) key that the owner creates and pastes into OmniRoute. The OmniRoute server still runs only while this session's background task lives.

## 2026-11-13 - Free Gemini fallback for Claude Code
- **Asked:** Finish OmniRoute so Claude keeps working when the limit runs out.
- **Decided:** The owner made a Google AI Studio key and pasted it into OmniRoute (agents may not type keys). Gemini 3.8 Flash makes real tool calls through OmniRoute, so it was chosen; 3.1 Pro is rate limited on the free tier and 2.5 is retired. Built a separate `claude-free` launcher instead of rerouting the normal Claude app. **Why:** if OmniRoute is down or the free quota is gone, normal Claude must keep working. The launcher uses its own empty profile (~/.claude/profiles/free) so no plugins ride along. **Why:** the first test with all plugins loaded used up the free Gemini quota within minutes (503 high demand, then 429 quota).
- **Shipped:** ~/.local/bin/claude-free.cmd. It starts OmniRoute if needed, then runs Claude Code on gemini/gemini-3.8-flash.
- **Open:** Not verified end to end: Gemini was still cooling down (about 30 minutes). Auto-start of OmniRoute with Windows was blocked by auto mode; the owner has the one-line command.

## 2026-11-13 - Final check of the whole setup
- **Asked:** Check every plugin, skill and the OmniRoute routing, and say what the owner still has to do.
- **Decided:** Caveman replies were added to the global ~/.claude/CLAUDE.md as well, so they apply outside this repo too.
- **Shipped:** Verified: plugins ecc, mattpocock-skills, humanizer, lemo-opuscar and ponytail are enabled; marketing skills are gone; personal skills apple-design, graphify and project-memory are present; the RTK hook is in settings.json and RTK has compressed 15 commands; Graphify 0.9.108 and OmniRoute 3.8.53 run; settings.json is valid JSON; the OmniRoute server answers. Earlier, Gemini 3.8 Flash returned a real tool call through OmniRoute.
- **Open:** Both Gemini Flash models were in OmniRoute's 30 minute cooldown (until about 17:27 UTC), so `claude-free` is not yet proven end to end. The OmniRoute server dies when this session ends; `claude-free` restarts it on demand. A Claude restart is needed to load the plugin and hook changes.

## 2026-11-13 - Setup steps made automatic
- **Asked:** Automate the remaining manual steps so they happen in every project, new or old: the Matt Pocock skills setup, OmniRoute starting with Windows, and the switch to free Gemini when the Claude limit runs out.
- **Decided:** Matt Pocock setup is now a global always-on rule in ~/.claude/CLAUDE.md with fixed answers (local markdown tracker in `.scratch/`, default triage labels, single-context docs), so no questions are asked. YTXTube got it now. OmniRoute auto-start was blocked twice by Claude Code's auto mode, so it stays manual; `claude-free` starts OmniRoute on demand, so nothing breaks. **Why no automatic limit switch:** Claude Code has no hook that fires on a usage limit and moves a running session to another model, so the fallback stays one command.
- **Shipped:** docs/agents/issue-tracker.md, domain.md and triage-labels.md; an Agent skills block in CLAUDE.md; the global setup rule.
- **Open:** None for these steps.

## 2026-11-13 - Speed pass session 1 ended, handoff written
- **Asked:** Remember the speed-pass state and write a handoff for the next conversation, including the newly installed skills.
- **Decided:** The handoff lives in `perf/HANDOFF-SPEED-2.md` (local only) with a copy in Downloads, because `perf/` already holds every number and script. The Baseline Profile generation failed twice (first run crawled on UiAutomator idle waits, fixed with shell input; second run lost the wireless adb link after 16 minutes), so items 1 and 2 of the plan stay uncommitted and unbuilt in the tree. **Why not committed:** item 2 is not compiled or measured yet, and the rule is one measured improvement per commit.
- **Shipped:** `perf/HANDOFF-SPEED-2.md`, session memory updated (`ytxtube-speed-pass`, `honor-magic-v5-adb-quirks`).
- **Open:** Reconnect adb, rerun `:app:generateYtReleaseBaselineProfile` in a terminal tab, then build and measure the hidden-glass change, then plan items 3 to 8. Folded measurements still need the owner to fold the phone. Benchmark copies `com.tensei.ytxtube.custom.benchmark` and `com.tensei.ytxtube.baselineprofile` are still installed on the phone (harmless).

## 2026-11-13 - Speed pass session 2 paused before any work
- **Asked:** Continue the speed pass from `perf/HANDOFF-SPEED-2.md`. When testing, if the app drops to the phone's home screen (video then plays in picture-in-picture), bring the app back and carry on instead of swiping on the home screen. Then the owner paused: only about 10 percent of the Claude limit was left.
- **Decided:** Stop and wait for the limit reset (about 9:50) rather than run builds and phone tests on the free Gemini fallback, which is not proven end to end. **Why:** the build, profile run, measurements and review need far more than what was left, and a half-finished run gives no usable numbers.
- **Shipped:** Nothing. Phone check only: adb was already connected through mDNS, and connecting by the port typed in chat was refused, so that port was not needed. Her app is still 2.6.15.
- **Open:** Next session adds a foreground guard to `perf/measure-*.sh` (check `dumpsys window` focus before each swipe, `am start` the app back if it left, redo that run), then follows the handoff: compile-check item 2 and build it without item 1, measure, review, commit; then the profile run.

## 2026-11-13 - Two owner requests written down for the next session
- **Asked:** Note for later, not to build now: holding the video for 2x must keep going when the finger moves, and the whole app must use one glass look (search bar and topic chips are mixed, comments should be glass too).
- **Decided:** Written as two briefs with roadmap cards in "Next", plus a section in the local handoff `perf/HANDOFF-SPEED-2.md` so the session after the limit reset finds them. **Why:** The owner is out of usage until about 9:50 pm. The next session asks her whether these come before or after speed pass items 3 to 8. The glass brief carries a speed warning, because glass layers are what drop frames.
- **Shipped:** `briefs/hold-speed-keeps-going.md`, `briefs/one-glass-language.md`, two roadmap cards, handoff section 7. No app code changed.
- **Open:** Both requests, next session.

## 2026-11-13 - Correction: which comments part should be glass
- **Asked:** The owner clarified the glass request. She means the small comments card on the video page (the rectangle that says Comments and shows one comment, before tapping it), not the full comment panel that opens.
- **Decided:** This corrects the previous entry. The brief and handoff now name the small card. **Why:** one small card costs little, so the earlier warning about the comment panel's frame drops no longer applies; a quick frame check before and after is enough.
- **Shipped:** `briefs/one-glass-language.md` and `perf/HANDOFF-SPEED-2.md` section 7 updated. No app code changed.
- **Open:** Same as before, next session.

## 2026-11-14 - Speed pass items 1 to 3 shipped as 2.6.16
- **Asked:** Continue the speed pass; when the current items are done, release ("push") and carry on with the rest. Bring the app back if a test drops it to the home screen. The two owner requests (hold for 2x, one glass look) come after the speed work.
- **Decided:** Every change was measured against the old build in the same session with the same scripts, because the first baseline's Shorts numbers came from different swipe settings and one old-build round was fake (stream stuck buffering). The scripts now log playback state per round and bring the app back from picture-in-picture. The Baseline Profile generator hung on UiAutomator a third time, so it was dropped for a hand-written profile (all app code plus NewPipe, Media3, Kyant), verified on the phone (80,303 profile entries, status speed-profile). A review subagent read the whole release diff: no blockers, one one-frame Shorts top bar blink, fixed. The owner's first name was replaced with "the owner" in docs before they went public.
- **Shipped:** bbb2ffc hidden glass (comments over video 23-28 % janky to about 5 %, Home with mini player 18-21 % to 3-5 %, Shorts 15-19 % to 13-17 %), d344065 Baseline Profile (cold start 295 to 243 ms), d4e222c Home feed from disk (Home on screen 1.0-1.8 s to 0.35 s), 2b66d2a Shorts top bar fix, then 2.6.16 released.
- **Open:** Speed items 4 to 8 (faster video start first; it needs care because the player reads HDR and premium audio info set during the details step). Folded measurements. New card: no video opens after a throttled stream until a force-stop. Then the two owner requests.
