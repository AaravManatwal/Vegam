# Vegam (वेगम्)
Download at the speed of thought · हर डाउनलोड, वेग से।

Premium high-speed Android download manager. Custom **VegamEngine** (segmented multi-connection downloader).

## Status: Phase 5 (Testing + Release + Play Store prep) - v1.0.0 (build number `version.properties` mein; har delivery par +1)
- `:core:ui`: VegamTheme (M3, brand colors, shapes), GradientRing, NumberTicker, FileTypeTile, vegamBackground
- `:feature:home`: HomeViewModel (Hilt) + Home screen (stats, search, active/completed, swipe-to-delete, basic Add sheet)
- `:app`: Hilt, navigation, bottom bar + FAB (Stats/Settings/Detail are placeholders until 3B/3C)
- Pure helpers in engine: FuzzyMatch, UrlNames

## Phase 5 (done: testing, release, store prep)
- Tests: `ReliabilityTest` (connection cut mid-body, retries exhausted, cancel-then-resume byte-exact, speed limit really slows, 24MB x 16 segments); `RangeServer` can now drop connections
- Release: R8 + shrinkResources, `proguard-rules.pro`, signing from env vars (unsigned if absent), versionCode from CI run number
- Workflows: `release.yml` (tests -> signed AAB + APK + mapping.txt), `create-keystore.yml` (phone-only key creation; repo must be private)
- Docs: `store/` = PRIVACY_POLICY, STORE_LISTING (EN + HI), DATA_SAFETY (+ FGS dataSync declaration), TESTING_AND_RELEASE (29-item checklist); `vegam-release-checklist.html` = interactive version
- Not done: real-device run of the checklist, baseline profile, Android 15 UIDT migration for 6h+ downloads, store graphics

## Phase 4 (done: animations + branding)
- Branding: `VegamLogo` (3 converging lines = V, `LOGO_LINES` tested), adaptive launcher icon (gradient bg, monochrome for themed icons), Android 12+ splash (core-splashscreen) held until settings load
- Motion (`:core:ui`): `vegamShared` (shared element tile: card -> Detail), `SuccessCheck` (spring circle + drawn tick), `VegamHaptics` (tick/confirm), direction-aware `NumberTicker`, spring placement for list items
- Haptics: FAB, Pause/Resume, download completion
- Deviation: success animation is native Compose, not Lottie (no extra dependency or JSON asset; follows theme)
- Not done: bundled fonts (system font), Detail pause button haptic, Lottie

## Phase 3C (done: Stats, Settings, Onboarding)
- `:core:download`: `AppSettings` (+ sanitize/clamp), `SettingsStore` (DataStore), `VegamGraph.bindSettings` (speed limit, max parallel -> queue re-pump, charging, Wi-Fi default)
- `:feature:stats`: `buildStats` (7-day bars, type split, totals; pure + tested), animated bars, NumberTicker total
- `:feature:settings`: theme (System/Light/Dark), dynamic color (Android 12+), parallel, connections, speed-limit steps, Wi-Fi/charging defaults, save folder, replay onboarding
- `:feature:onboarding`: 3 parallax pages + notification permission card (Android 13+)
- `:app`: MainViewModel (theme + onboarding gate), real Stats/Settings routes; old Coming placeholders removed
- Not done: notification on/off toggle (system settings handle it), language switch (preview only)

## Phase 3B (done: Add + Detail)
- `:feature:add`: Add sheet (debounced probe + shimmer metadata, editable name, connections 1..16, priority, Wi-Fi only, schedule, SAF folder picker, Paste)
- `:feature:detail`: header ring, per-segment bars, 60s speed graph, Pause/Resume/Retry, Cancel with confirm, file info + SHA-256
- `:app`: `detail/{id}` route, Share -> Vegam (ACTION_SEND text/plain, singleTop), completed cards open Detail
- Engine extras: Checksum, UniqueName, Schedule, extractUrl; DB v2 (`destUri`)
- SAF note: file pehle app folder mein banti hai, complete hone par chune folder mein copy hoti hai (temporary extra space)

## Phase 2B (done: WorkManager + foreground service)
- `:core:download`: DownloadWorker, DownloadScheduler, Notifications (progress, speed, ETA, Pause), PauseReceiver, VegamGraph
- `:app`: temporary debug screen (URL -> Download) to test the worker on a phone
- `vegam-app-preview.html`: look-and-feel preview of the full app (open in phone browser)

## Phase 2A (done)
- `:core:database`: DownloadEntity, SegmentEntity (resume progress), DAOs, VegamDatabase, mappers
- `QueuePolicy` (engine): priority + drag order + schedule + max parallel

## Phase 1B (done)
- `:core:engine` — SegmentPlanner, SpeedMonitor, AdaptiveConcurrency, TokenBucket, RetryPolicy, ResumeValidator
- `SegmentedDownloader` (+ Prober, VegamHttp): Range segments, positional writes, work-stealing, resume, retry, 429/503 adaptive
- Tests: `EngineLogicTest` (pure logic) + `SegmentedDownloaderTest` (MockWebServer)
- `:app` — placeholder screen (APK build check)
- Known gaps (Phase 1C): throughput-drop based throttling, unknown-size (chunked) downloads

## Build (phone se, GitHub Actions)
1. GitHub par naya repo banao, is zip ke saare files upload karo (`.github` folder ke saath).
2. Repo ke **Actions** tab mein "Vegam build" run hone do.
3. Run khatam hone par **Artifacts** se `vegam-debug-apk` aur `engine-test-report` download karo.

## Local (agar kabhi PC mile)
`gradle :core:engine:test :app:assembleDebug` (Gradle 8.10+, JDK 17)
