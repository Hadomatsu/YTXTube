# Landscape video page: scroll on to More videos; swipe down on the video is broken

**Goal:** Two fixes the owner reported after 2.6.20.

1. **Landscape unfolded should end at comments, then scroll on.** The owner meant: when the video page
   opens, the visible part ends at the comments card (no "More videos" title peeking in at the bottom),
   but scrolling down still shows More videos. 2.6.20 removed More videos entirely in landscape on big
   screens (`landscapeWide` in `PlayerUi.kt`). Bring them back and instead make sure the first screen
   ends at the comments, for example by padding the end of the details block so the More videos title
   starts below the bottom edge, computed from the available height. Keep Subscribe at the right of the
   action row. Portrait unfolded stays as it is.
2. **Swipe down on the video no longer goes back to Home (mini player).** Regression, seen after 2.6.20.
   Suspects, check in this order: the caption change in `VideoView.kt` that skips swipe detection when
   `change.isConsumed` (the player consumes the down itself, which may mark later changes as consumed);
   the hold-for-speed loop (`304dae3`); the mini player removal while the player is open (`bbb2ffc`,
   `playerSettledOpen` in `MainActivity.kt`). Reproduce with `input swipe` on the video before editing.

**Done when:** Both checked on the phone: landscape opens ending at comments and scrolls to More videos;
swipe down on the video goes to the mini player in portrait and landscape; captions still drag; hold for
2x still works.

**Source:** Owner report, 2026-11-14, after 2.6.20.
