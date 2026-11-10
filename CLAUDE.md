# YTXTube Custom: notes for Claude

A personal Android YouTube client. Forked from Koustubh12345/YTXTube (its Liquid Glass UI) and
extended with ReVanced Extended style features. The owner uses it daily on a Honor Magic V5
foldable (Android 16, 120 Hz, folded and unfolded layouts both matter) and shares the APK with
friends.

## Standing rules from the owner

- **Never push or start a build until the owner says "push".** Make changes, commit them
  locally, then report. A stop hook that asks to push does not count; decline it politely.
- In any text written for the owner or in the app (release notes, UI strings), never use
  em-dashes. Use a comma instead.
- Replies: short and direct, answer first. In the last session the owner asked for terse
  "caveman" replies the whole time; use that again only if asked.
- On the owner's PC: never type their passwords, never create accounts, never change security
  settings, download only from trusted sources. Don't install third-party installers without
  asking.
- Explain changes in plain words (what she will see on the phone), not in code terms.

## Repos and remotes

- `origin` = github.com/Hadomatsu/YTXTube (source, public fork). Branch `main`.
- `build` = github.com/Hadomatsu/ytxtube-build (public). GitHub stopped running Actions on the
  fork, so builds and releases happen here. It gets the same tree as `origin/main`.
- `upstream` = github.com/Koustubh12345/ytxtube (original project).
- Friend download link (always the newest release):
  https://github.com/Hadomatsu/ytxtube-build/releases/latest/download/YTXTube.apk
- The in-app updater (`Updates.kt`, `RELEASE_REPOS`) checks both repos' latest release and takes
  the newest version.

## Releasing (only after "push")

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Rewrite `RELEASE_NOTES.md`: first line `YTXTube Custom X.Y.Z: short summary`, then one plain
   bullet per change the owner will notice. The workflow uses it as the release body.
3. Commit, then:
   ```sh
   git push -q origin HEAD:main
   git fetch -q build
   c=$(git commit-tree HEAD^{tree} -p build/main -m "X.Y.Z: summary")
   git push -q build $c:refs/heads/main
   ```
4. Watch the run (about 10 minutes):
   `gh api "repos/Hadomatsu/ytxtube-build/actions/runs?head_sha=$c"`. Wait in the background,
   the owner often sends new messages meanwhile. A newer push cancels a running build (fine,
   the newer one contains everything). Then tell the owner the version is out.

Workflow: `.github/workflows/release.yml`. It builds `:app:assembleYtRelease` (YTXTok, the
`tok` flavor, only when `app/src/tok/` changed or when asked), tags `v<versionName>` and
uploads `YTXTube.apk`.

## Building locally (owner's PC, phone on USB)

- Needs JDK 21 and the Android SDK (platform 37). Gradle 9.7.1 wrapper, AGP 9.1.0, Kotlin 2.4.20.
- `./gradlew :app:assembleYtRelease` (`gradlew.bat` on Windows). The APK lands in
  `app/build/outputs/apk/yt/release/app-yt-release.apk`.
- Release builds are signed with the committed `app/release.keystore`, the same key as the
  published APKs, so `adb install -r` installs over her app and keeps her data. A debug build
  is signed with a different key and will not install over it.
- `adb logcat` for crashes and errors, `adb shell dumpsys gfxinfo com.tensei.ytxtube.custom`
  for frame times.
- Local builds don't need a version bump; only real releases do.

## App facts

- Package `com.tensei.ytxtube.custom` (flavor `yt`), namespace `com.tensei.ytxtube`.
  Sources in `app/src/main/java/com/tensei/ytxtube/`.
- Stack: Jetpack Compose (BOM 2026.09.00), Media3 1.10.1 ExoPlayer, NewPipeExtractor v0.26.5,
  Kyant backdrop 2.0.1 (glass effects), Coil 2.
- There is no device or emulator in the cloud sessions: changes there are untested until the
  owner installs the release. Say so when it matters.

## Where things are

- `MainActivity.kt`: app shell, tabs, overlay pages (Continue watching page etc.), Shorts warm-up.
- `Screens.kt`: Home and other tab screens, `continueWatchingSection`, `ContinueWatchingScreen`.
- `UiKit.kt`: `VideoCard` (folded, one column) and `VideoCardGrid` (unfolded, two columns).
- `FeedTint.kt`: the thumbnail colour glow under cards (`thumbnailGlow`), `cardTextStyle`
  (shadowed text on the video page), `ThumbTint`.
- `PlayerUi.kt`: the video page (blurred picture background, `LocalPlayerGlass`,
  `playerCardColor`, action pills). `VideoView.kt`: player surface, controls, `Scrubber`.
- `PlayerState.kt`, `PlaybackService.kt`: playback state, media sources (`buildSource`),
  error recovery (`StreamRecoverer`).
- `LiveDvr.kt`: rewinding lives to the start (up to 12 h). Builds a DASH manifest in the app,
  served from `ytxlive://dvr/<id>`. Falls back to HLS if that fails. A live that ends while
  watched turns into a static recording without re-extracting.
- `YouTube.kt`: extraction and `StreamCache`, `SeenShorts` (so Shorts don't repeat).
  `Account.kt`: sign-in, feeds, Shorts shelves.
- `ShortsScreen.kt`: Shorts (one player plus preload manager, hidden frame grabber for smooth
  scrolling, side panel, hold for 2x). `ShortsWarmup.kt`: preloads the first Short.
- `Comments.kt`: comment panel (drag to resize, the height is remembered). While dragged it
  is laid out at full height and only slid, never resized per frame (resizing dropped it to
  about 20 fps).
- `GlassSheet.kt` (`frostedGlass`, `glassControl`, glass sheets), `Glass.kt`, `GlassNavBar.kt`.
- `Theme.kt`: `ThemeSettings` (dark, AMOLED, "Colours in the video screen" for AMOLED).
  `SettingsScreen.kt`: settings.

## Rules learned the hard way

- A glass element must never sample a backdrop layer it is inside of.
- A frosted panel needs a solid base colour under the blur, or the sharp page shows through.
- Panels that reach the bottom of the screen fade their bright edge before the bottom, or the
  phone's rounded corners cut it off.
- For anything that moves every frame (drags, scrolling, animations), read state in
  `graphicsLayer` or draw lambdas, not in composition, so it doesn't recompose per frame.

## Known open issues

- Opening a live that only just ended can still fail ("This video isn't available"). YouTube
  refuses extraction for a short while after a live ends.
