# Vegam Smart Download Detection (design + status)

Existing Vegam ko badla nahi gaya: VegamEngine, UI, branding, navigation sab waise hi. Ye feature uske upar naye module/files mein jud raha hai.

## 1. Inspect: jo project mein pehle se hai
- Engine `:core:engine` (pure JVM): `SegmentedDownloader`, `Prober` (HEAD, phir `Range: bytes=0-0`), `ResumeValidator`, `UrlNames`, `extractUrl`, `isLikelyFileUrl`.
- `:core:database` Room v2 (`downloads`, `segments`). `:core:download`: `VegamGraph`, `DownloadScheduler` (WorkManager), `DownloadRepository`, `SettingsStore` (DataStore), `AppSettings`.
- UI: `:feature:home/add/detail/stats/settings/onboarding`, `:app` (NavHost, MainActivity handles Share + Open With + clipboard snackbar).
- SDK: min 26, target 35. Pehle se manifest mein: `ACTION_SEND` aur `ACTION_VIEW` (file extensions/MIME) filters.
- Dhyan do: engine ko **Content-Length** chahiye (unknown-size/chunked download abhi unsupported) aur HTTP (cleartext) ke liye manifest flag chahiye (neeche "Known limits").

## 2. Architecture
```
Browser / app ──Share / Open With──▶ MainActivity ─▶ SmartCoordinator ─▶ SmartAnalyzer (engine) ─▶ SmartDownloadDialog
Clipboard (foreground) ─────────────────────────────┘                          │
                                                                               ├─ LinkAnalyzer      HEAD / Range-GET, redirects, limited retries
                                                                               ├─ DuplicateDetector normalized URL, size, ETag/Last-Modified/server hash
                                                                               ├─ SecurityAssessor  GREEN / YELLOW / RED (local rules only)
                                                                               └─ SmartPolicy       engine compatibility + final decision
Dialog "Download" ─▶ existing DownloadRepository.add(...)  (VegamEngine unchanged)
```
Engine side (pure Kotlin, JVM tests): package `com.vegam.engine.smart`.

| File | Kaam |
|---|---|
| `UrlTools` | normalize (fragment, tracking params, default port), host, URL ke andar likhi signed expiry |
| `FileTypes` | extension + MIME + Content-Disposition (RFC 5987), category, web-page detection; koi bandhi list nahi |
| `LinkAnalyzer` | network jaanch, cancel-safe, max 2 retry, max 8 redirect, body kabhi nahi padhta |
| `SecurityAssessor` | 3-level local risk rules |
| `DuplicateDetector` | asli duplicate vs sirf same-naam |
| `SmartPolicy` | engine compatibility, HTTPS alternative (sirf sujhav), final decision |
| `SmartAnalyzer` | upar sab ek pipeline, 4 switches ke saath |

## 3. Browser / app compatibility report (imaandaar)
Android kisi app ko dusre browser ke downloads "chheenne" ki ijazat nahi deta. Neeche wahi hai jo pakka hai.

| Raasta | Kaise | Status |
|---|---|---|
| Share -> Vegam (link) | `ACTION_SEND text/plain` | Sab browsers (Chrome, Firefox, Edge, Samsung, Brave...) aur apps. Pakka. |
| Open With / link tap | `ACTION_VIEW` http(s) + file extension/MIME filters | Tab chalta hai jab browser/app file link ko `ACTION_VIEW` se bahar bhejta hai. Chrome/Edge download khud sambhalte hain, ye chooser nahi dikhate: **device par verify karna hai**. |
| Clipboard | link copy karo, Vegam kholo / foreground mein ho | Android 10+: sirf foreground. Android 12+: "clipboard padha" message aa sakta hai. |
| Default download handler | Android mein aisa API nahi hai | Possible nahi. Vegam sirf system ki "Open by default" settings khol sakta hai (Android 12+); wo web links ke liye hai, browser downloads ke liye nahi. |
| Firefox / Samsung "external download manager" | browser-specific, version par depend | **Claim nahi kiya**: device par test karke Browser Setup screen mein likhenge. |

Kuch bhi browser ki setting chupke se nahi badla jaata.

## 4. Security level rules (malware scanner nahi hai)
- RED (block, bypass nahi): `invoice.pdf.exe` jaisa document/media-naam par executable, ya `paypal.com@evil.example` jaisa userinfo chhal + executable.
- YELLOW (extra confirmation, kaaran ke saath): HTTP, IP host, punycode, brand-lookalike domain, executable/APK, MIME-vs-extension mismatch, HTML page jab file expected thi, HTTPS->HTTP redirect, certificate galti, userinfo.
- GREEN: "koi jaana-pehchana risk nahi mila (guarantee nahi)". "Safe" kabhi nahi likhte.
- APK signature: download se pehle padha nahi ja sakta; dialog mein ye likha hai. Kuch bhi bahar nahi bheja jaata.

## 5. Link validity (andaza nahi)
- Server ka asli HTTP code dikhta hai. "Expired" sirf tab jab URL ke andar likhi expiry beet chuki ho (S3/CloudFront/Google signed links). 403 akela = "access mana", expiry nahi.
- Temporary (timeout, 429, 502-504): max 2 retry. Phir TEMP_UNAVAILABLE. Metadata na mile to warning, download block nahi.

## 6. Known limits (abhi)
1. VegamEngine ko `Content-Length` chahiye: jo server size na de, uske liye dialog saaf bolta hai "supported nahi" (engine badla nahi).
2. Login/cookies wale links (HTTP 401) nahi chalte: Vegam cookies nahi bhejta.
3. Kharab HTTPS certificate bypass nahi hota.
4. HTTP downloads ke liye manifest mein `usesCleartextTraffic` chahiye (Android 9+ default band): ye Phase 5-7 mein jodna hai, YELLOW warning ke saath.
5. HTTPS alternative sirf sujhav hai, user ki ijazat ke baad hi.

## 7. Phase status
| Phase | Kaam | Status |
|---|---|---|
| 1-2 | Inspect + Plan | Done (ye doc) |
| 3-4 | Detection + analysis + security (engine, 79 unit tests likhe) | Done: code likha, **compile/run GitHub Actions par baaki** |
| 5 | Smart dialog + notification | Done (code) |
| 6 | History (Room v3) + 9 settings | Done (code) |
| 7 | Browser setup screen + Test Integration + fallbacks | Done (code); browser behaviour device par verify karna hai |
| 8 | Testing | Unit tests likhe; device checklist `docs/SMART_TESTING.md` |
| 9 | APK | GitHub Actions (yahan Kotlin compile nahi hota) |

## 8. Modified / added files
**Added (engine, `core/engine/.../smart/`)**: UrlTools, FileTypes, SmartModels, SecurityAssessor, DuplicateDetector, SmartPolicy, LinkAnalyzer, SmartAnalyzer (+ 3 test files).
**Added (Android)**: `core/download/SmartCoordinator.kt`; `feature/smart/` (SmartViewModel, SmartDownloadSheet, SmartText, HistoryScreen, BrowserSetup, SmartTextTest, build file).
**Modified**: `Entities.kt`, `Daos.kt`, `VegamDatabase.kt` (v3 + MIGRATION_2_3), `AppSettings.kt`, `SettingsStore.kt`, `AppSettingsTest.kt`, `VegamGraph.kt`, `DownloadModule.kt`, `VegamApp.kt`, `MainActivity.kt`, `VegamNavHost.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `AndroidManifest.xml`, `settings.gradle.kts`, `app/build.gradle.kts`, CI workflows.
**Not touched**: VegamEngine (`SegmentedDownloader`, `Prober`, resume, queue, schedule), existing Home/Detail/Stats/Add screens, branding.

## 9. Manifest changes (samjho)
- `<queries>` VIEW https: sirf Browser Integration ke "Test" ke liye (Android 11+ package visibility).
- `usesCleartextTraffic="true"`: pehle http:// links Android 9+ par fail hote the. Ab chalte hain, par Smart dialog HTTP par YELLOW + extra confirmation deta hai aur HTTPS alternative sujhata hai. Ye security-relevant hai: nahi chahiye to is line ko hata do (tab http downloads fail honge).
- minSdk 26 / target 35 / compile 35: **badla nahi**.

## 10. Build 2: "Page kholo, asli download pakdo"
Jab link file nahi balki web page (download page/countdown/button) ho (jaise vcloud.my), Add sheet aur Smart dialog ek button dete hain jo page app ke andar (WebView) kholta hai. User khud button dabata hai; WebView ka DownloadListener asli file URL + User-Agent + Referer + Cookie pakadta hai; Vegam poochta hai aur engine wahi headers bhejta hai (DB v4 `headers` column, app-private).
Limits: blob:/data: downloads pakad nahi sakte; kuch hosts ke links ek baar ya IP/session se bandhe hote hain; Vegam koi captcha/login bypass nahi karta; Cookie DB mein download ke saath rehti hai (app-private) jab tak entry delete na ho.
