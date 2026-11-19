# Smart Detection: testing report

## Automated (GitHub Actions: `gradle :core:engine:test ... :feature:smart:testDebugUnitTest`)
Status: **likhe hue, abhi chalaye nahi gaye** (is workspace mein compiler nahi). Pehla CI run result yahan likhna hai.
- `SmartLogicTest` (50): URL normalize, file type, Content-Disposition, security rules, duplicate rules, policy.
- `LinkAnalyzerTest` (19, MockWebServer): HEAD, 405 fallback, presigned 403, range ignore, 401/403/404, expiry evidence, 503 retry, redirects/loop, digest, HTML page, steps, cancel.
- `SmartAnalyzerTest` (6), `FileUrlTest` (4), `AppSettingsTest` (+1), `SmartTextTest` (5).

## Device checklist (tumhare phone par, ~15 min)
1. Chrome mein file link long-press -> Share -> Vegam: dialog aaya? (Functional)
2. Link copy karke Vegam kholo: dialog aaya? Wahi link dobara copy: dobara nahi aana chahiye. (Clipboard)
3. Settings -> Clipboard Detection OFF: ab dialog nahi. (Settings persist: app band-khol ke dekho)
4. Same file do baar: "Pehle download ho chuki" chetavani + Download anyway. (Duplicate)
5. `http://` link: YELLOW + extra confirm. (Security)
6. Airplane mode: "Network dikkat", block nahi, Download phir bhi possible. (Failure)
7. Analysis chalte hue Home dabao: Background Analysis ON = notification, OFF = ruk jaata hai.
8. Smart Link Analysis OFF: dialog sirf naam dikhata hai, koi network call nahi.
9. History: search, filter, ek entry hatao, Sab saaf.
10. Regression: normal Add-sheet download, pause/resume, queue, schedule, notification Pause button, theme.
11. Browser Integration -> "Test chalao": result padho. Firefox/Samsung/Edge par kya hota hai, mujhe batao: report update hogi.
12. Update test: purana APK install karke naya upar install: purani downloads bachi? (DB migration 2->3)

## Performance / battery (design se)
Ek link = ek jaanch (dedupe). Timeouts 8s connect/read, 20s call, max 2 retry. Body kabhi nahi padhta (HEAD ya bytes=0-0). Koi polling nahi.
