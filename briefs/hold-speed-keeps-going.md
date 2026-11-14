# Hold for 2x keeps going when the finger moves

**Goal:** When the owner long-presses the video to speed it up (2x, or whatever hold speed is set), the speed-up
lasts until she lifts her finger, even if the finger moves.

**Context:** Today the speed-up stops as soon as the finger moves even about a millimetre. The hold gesture
lives in `VideoView.kt` (around lines 358 to 575, `holdSpeedMs`, `state.beginHoldSpeed()` and
`state.endHoldSpeed()`), the state in `PlayerState.kt` (`beginHoldSpeed`, `endHoldSpeed`, `holdSpeedActive`).
Shorts has its own hold for 2x in `ShortsScreen.kt`; check whether it has the same problem and fix both the
same way. Once the speed-up has started, moving the finger should do nothing else either: no scrubbing, no
seeking, no Shorts page swipe, until the finger lifts.

**Done when:** On the phone, hold on a video, wiggle and slide the finger around, and it stays at the hold
speed the whole time and goes back to normal speed only on release. Same in Shorts. A quick tap and a normal
swipe still work as before.

**Source:** Owner request, 2026-11-13. Not for implementing in the session it was asked in.
