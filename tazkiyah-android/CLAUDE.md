# Tazkiyah — project rules
- docs/PLAN.md is the source of truth. Read the relevant section before each task.
- Stack: Kotlin, Jetpack Compose, Material 3, Hilt, Room, WorkManager, DataStore,
  OkHttp + kotlinx.serialization, Media3, android-youtube-player (core).
- All user-facing text in Bengali via strings.xml; show numbers with toBanglaDigits().
- Never add any way to open the YouTube app or youtube.com (no share, no "open in YouTube").
- Every video must be shown inside FocusedVideoFrame (touch shield + cover card): streaming YouTube via
  FocusedYouTubePlayer, saved/direct files via FocusedFileVideoPlayer.
- Content sync must never modify user tables. Never use destructive Room migrations.
- Before finishing a task: ./gradlew lint testDebugUnitTest must pass.

## Local notes
- Content repo lives next to this one in `../tazkiyah-content`; every build copies a snapshot of it into
  the APK's `assets/seed/` (task `copy<Variant>ContentSeed`), so a fresh offline install still has data.
- Monorepo `mdhelaluddin-ctg-bd/tazkiyah`: content is synced from its `tazkiyah-content/` folder
  (`tazkiyah.content*` properties in `gradle.properties`); the content Action is the root `.github/workflows/publish.yml`.
- Playback never stops when the app is minimized or the screen is off: YouTube runs with
  `enableBackgroundPlayback(true)` (the view is not a lifecycle observer) and always publishes a
  MediaSession through PlaybackService; Back on the first screen calls `moveTaskToBack`. This breaks
  YouTube's terms for store apps — fine for this personal build, remove it before any Play Store release.
- Offline saving (`data/download/`): YouTube lectures are saved with NewPipeExtractor (JitPack,
  `newpipeExtractor` in libs.versions.toml) — like background play, this breaks YouTube's terms; personal
  build only. When saving stops working, try a newer extractor version first. NewPipeExtractor is GPL-3.0:
  handing this APK to others means its source must be released under GPL too.
  DownloadWorker fetches 5 MB ranged chunks into `files/downloads/<id>.<itag>.part` (resumable), picks the
  original-language audio (never a dub) and H.264 video, and muxes them with MediaMuxer. A saved lecture
  plays in ExoPlayer instead of YouTube (PlaybackManager `Active.saved`).
- Distribution: signed APK on GitHub Releases (tag `app-v<versionName>`), not Play Store. The app's
  `AppUpdateChecker` reads those releases once a day and shows "নতুন ভার্সন এসেছে". Every release: bump
  `versionCode` + `versionName`, `./gradlew lint testDebugUnitTest assembleRelease`, test on a phone, tag, upload.
- Release builds use R8 (`proguard-rules.pro` keeps NewPipeExtractor/Rhino whole) and the release key from
  `~/.gradle/gradle.properties` (`TAZKIYAH_RELEASE_*`, keystore in `~/.android/keystores/`); never the debug key,
  never commit either. The whole app is GPL-3.0 (root `LICENSE`, shown in-app under About).
