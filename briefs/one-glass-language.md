# One glass look across the whole app

**Goal:** Every screen uses the same visual language (Liquid Glass or frosted glass), so nothing looks like
it belongs to a different app.

**Context:** On the search results page the search bar and the topic chips on top are mixed: some chips are
glass, some are not. On the video page, the small comments card under the video (the rectangle that says
Comments and shows one comment, before you tap it) is not glass; the owner wants it frosted or Liquid Glass,
whichever looks best. She means that small card, not the full comment panel that opens on tap. Go through every screen (search, comments, settings, sheets, dialogs, menus, Music)
and list what is not on the house style, then fix it with the existing pieces in `GlassSheet.kt`
(`frostedGlass`, `glassControl`, glass sheets), `Glass.kt` and `GlassNavBar.kt`, no new glass code.
Follow the rules in CLAUDE.md: a glass element never samples a backdrop layer it is inside of, a frosted
panel needs a solid base colour under the blur, panels that reach the bottom fade their bright edge first.
Speed matters here: the speed pass found glass layers are what drops frames (see `perf/perf-notes.md`).
One small card is cheap, but check that each change adds only a little: prefer frosted (one blur) over the
Liquid lens for anything big, and run a quick frame check on the video page before and after.

**Done when:** Search bar and every topic chip match, the small comments card on the video page is glass,
a screen-by-screen check finds nothing off style, and the video page drops no more frames than before.

**Source:** Owner request, 2026-11-13. Not for implementing in the session it was asked in.
