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

## 2026-11-14 - 2.6.16 out; hold for 2x and the glass look done
- **Asked:** Go on with the rest after the release; then (owner's pick) switch from the small remaining speed items to the two feature requests.
- **Decided:** Item 4 (start playback right after extraction) was measured first and dropped: the gap it would remove is 4 ms, not the 100-300 ms guessed. Shorts frames are bound by the glass design (the video is recorded twice per frame for the nav bar and the buttons); cutting that needs a look change, so it waits for the owner. Hold for 2x: once the faster speed is on, the video takes every move until the finger lifts, so the page or Shorts pager behind can no longer start a drag that ends it. Glass look: the search bar is glass like the back button next to it, the search filters moved into the header in Home's topic chip style, and the comments and queue cards on the video page are glass like the action pills. **Why the header fix:** the glass search header made search scrolling 3 points jankier because a header slid off screen was still drawn; it is now skipped once fully hidden, which helps every screen with that header.
- **Shipped:** v2.6.16 released (build run succeeded, YTXTube.apk attached). Local commits 304dae3 (hold for 2x), 7f73d6d (glass look). Not pushed.
- **Open:** The owner should look at the search page and the video page cards on the phone (screenshots of the app come out black, so the look is unchecked by eye), then say whether to release them. Screen-by-screen glass check of the rest of the app. `gh` is not installed on this PC; release runs were checked through the public GitHub API instead.

## 2026-11-14 - 2.6.17 released
- **Asked:** The owner looked at the search page and the video page cards on the phone: "Looks good, release".
- **Decided:** Released as 2.6.17 with plain release notes. Checked first that the push carried no em-dashes and no personal data.
- **Shipped:** v2.6.17 on ytxtube-build with YTXTube.apk (hold for 2x stays on while the finger moves, glass search bar and filters, glass comments and queue cards).
- **Open:** Screen-by-screen glass check of the rest of the app; speed items 5 to 8 (small); a decision on the Shorts glass look if Shorts should get smoother; the stuck-after-throttled-stream bug; folded measurements.

## 2026-11-14 - Screen check for the glass look
- **Asked:** Check the rest of the screens for anything that does not match the glass look.
- **Decided:** Searched the code for stock Material pieces (screenshots of the app come out black on this phone). The mismatches were four stock dropdown menus (search filters, settings choice rows, the second captions language, the Downloads "More options" menu) and live chat's stock bottom sheet, while the app's own choices (quality, speed, audio track) open as glass sheets. **Why sheets, not restyled dropdowns:** a dropdown is a separate window, so it can not blur the app behind it; the app-wide GlassSheet layer is drawn over everything, the fullscreen player included. A small GlassChoiceSheet reuses the player's TrackRow so choices look the same everywhere. Left alone: text-only buttons in the app's colours (the download banner's VIEW, the update dialog's Cancel), and Material switches and sliders in settings.
- **Shipped:** 5bc2b64 (local, not pushed). Checked on the phone: the Length search filter opens a titled glass sheet and picking an option applies it.
- **Open:** Not checked on the phone: live chat (no live video in the feed, and searching for one would add to the owner's search history), the settings choice sheets, the second captions sheet, the Downloads menu.

## 2026-11-14 - 2.6.18 out; endless Home and endless "More videos"
- **Asked:** Home should be infinite (it ended), the videos under a video should load without end (only 15 to 20), and Shorts should be unlimited but only the owner's For You, never popular videos.
- **Decided:** Measured with temporary logs first. Signed in, YouTube's Home feed gives about 10 pages (around 245 videos), then no next-page token, so Home ended there. A plain restart of the feed brought mostly repeats (1 to 7 new per 24), so Home now continues with YouTube's own topic feeds for the account (the Home chips except "All" and "Watched"), one after the other, then starts over. A failed later page is retried instead of switching to the popular-topics feed. The feed list also re-asks when a load finishes, so a page with nothing new can not stall it. "More videos" now pages through YouTube's watch-next list (only its related part, not the comments' token) and carries on from the last new video's list. Shorts needed no change: signed in they only ever come from the account's reel sequence (the popular fallback is for signed-out use only), and their loader already retries with backoff instead of ending.
- **Shipped:** v2.6.18 released (glass menus and live chat). Local commits 6c7c6bd (endless Home) and d9331be (endless "More videos"); the owner scrolled both on the phone and confirmed they keep going. Not pushed.
- **Open:** The owner to scroll Shorts for a while to confirm they never end and stay personal. Release the two commits. The TV side-by-side video layout is not wired to the endless "More videos".

## 2026-11-14 - Shorts subscribe button, then 2.6.19
- **Asked:** Before releasing: on Shorts (mostly unfolded) the channel row sat too high and the Subscribe button was always over the video. Make it Liquid Glass, smaller (a circle with an icon) or moved, with a short animation between subscribed and not.
- **Decided:** A 32 dp liquidGlass circle using the page's backdrop like the side buttons, person-add icon to check mark; the new icon spins in from -120 degrees with a springy pop (about half a second), driven by the AnimatedContent transition so the first icon on a Short does not spin. The block sits 12 dp lower (8 dp above the nav bar space instead of 20). **Why not tested by tapping:** that would subscribe the owner's account to a real channel; the layout was checked on the phone instead.
- **Shipped:** 1a96c46, then 2.6.19 (with endless Home and "More videos") pushed and released.
- **Open:** The owner tries the subscribe animation on a Short. Shorts endlessness confirmed only by reading the code.

## 2026-11-14 - Movable captions, landscape video page
- **Asked:** Hold the captions to put them anywhere on the video, and they stay there; snap to the middle like moving a picture in a document. On the video page in landscape unfolded, stop "More videos" peeking in (end at the comments), and move Subscribe down to the right end of the action row, in Liquid Glass. Portrait unfolded unchanged; work it out from the screen so every foldable gets it.
- **Decided:** Captions: long press then drag, saved as the centre's share of the video size (so it holds in every video and in fullscreen), kept on the video, snapping to the middle across and up/down within 22 dp with a guide line and a tick. Holding the captions does not start 2x and is not a player swipe. A reset button in the caption size panel, shown only once moved, because otherwise there was no way back. The first version lost the drag at once because the captions switched to a different composable when moved; one CaptionLines now serves every placement. Landscape: "big screen" means landscape with smallest width at least 600 dp (unfolded foldables, tablets). Subscribe is glass in every layout, moved only in landscape.
- **Shipped:** fe4de8c (captions), the landscape commit after it. Local, not pushed. 2.6.19 was released before these.
- **Open:** The owner's captions were left near the middle by the test: hold the CC button and tap the round-arrow reset. Portrait unfolded not re-checked after the landscape change. Release these.
