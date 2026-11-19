# Vegam: testing aur release checklist
Har item ke saath "Pass hone ka matlab" likha hai. Interactive version (tick marks ke saath) preview artifact mein hai.

## Real-world tests (phone par)
- [ ] **1GB+ file, Wi-Fi**: Koi 1GB+ file jiske server par Range support ho (Add sheet mein 'Range supported' dikhe). 8 connections par poori download karo.
  - Pass: Download 100% tak pahunche, Detail mein 8 segment bars chalein, aakhir mein SHA-256 server ke published checksum se match kare.
- [ ] **Kill ke baad resume**: Download 30-40% par recents se Vegam swipe karke band karo, 1 minute ruko, app dobara kholo.
  - Pass: Notification wapas aaye ya Home mein item Paused/Running dikhe, progress pichhle % se aage badhe (0% se nahi).
- [ ] **Reboot ke baad resume**: Chalti download ke beech phone restart karo.
  - Pass: Restart ke baad app kholne par download wahin se aage chale. (Background se auto-start Android ki restriction par depend karta hai.)
- [ ] **Net toot-jud**: Download ke beech 20 second Airplane mode ON, phir OFF.
  - Pass: Download crash na ho, retry karke khud aage badhe. Final file SHA-256 match.
- [ ] **Speed limit**: Settings mein Speed limit 1 MB/s karo, download chalao.
  - Pass: Notification/Home par speed ~1 MB/s ke aas-paas rahe. Unlimited karne par turant badhe.
- [ ] **Server 429/503**: Aisa server/link jo kabhi kabhi 429/503 de (ya bahut saari parallel downloads).
  - Pass: Connections ghatke 4->2->1 ho, download poori ho, baad mein connections wapas badhein.
- [ ] **Wi-Fi only**: Wi-Fi only ON karke mobile data par download shuru karo.
  - Pass: Download ruki rahe; Wi-Fi par aate hi chale.
- [ ] **Schedule**: '1 ghante baad' ya custom time (2-3 minute baad) lagao.
  - Pass: Item Queued dikhe aur samay par khud shuru ho.
- [ ] **Queue / max parallel**: Settings mein max parallel 2 karo, 5 downloads add karo. Phir max parallel 4 karo.
  - Pass: Sirf 2 chalein; max badhane par turant 4 chalein. Ghatane par chalti wali nahi rukti.
- [ ] **Storage kam**: Phone mein kam jagah ho tab badi file add karo.
  - Pass: Saaf error dikhe (crash nahi), file adhoori nahi chhute.
- [ ] **SAF folder**: Settings se 'Folder chuno' (Downloads/Vegam). Chhoti file download karo.
  - Pass: File chune folder mein aaye aur app-private copy delete ho jaye.
- [ ] **Share -> Vegam**: Chrome mein kisi link par Share -> Vegam.
  - Pass: Add sheet link ke saath khule, naam/size aaye.
- [ ] **Notification permission deny**: Onboarding mein notification Allow na karo.
  - Pass: Download phir bhi chale; notification nahi aaye, app crash na ho.
- [ ] **Android 14/15**: Android 14 ya 15 phone par 10 minute se lambi download.
  - Pass: Foreground service band na ho. (Android 15 mein dataSync ki 6 ghante ki limit hai: 6h+ downloads par dhyan do.)

## Release build
- [ ] **Actions green**: Debug build + saare unit tests CI mein green.
  - Pass: Actions tab mein 'Vegam build' ka tick.
- [ ] **Keystore banao**: 'Create keystore' workflow chalao, secrets.txt se 3 secrets (VEGAM_KEYSTORE_BASE64, VEGAM_KEYSTORE_PASSWORD, VEGAM_KEY_ALIAS) repo mein daalo, artifact delete karo.
  - Pass: Settings -> Secrets mein teeno dikhein.
- [ ] **Keystore backup**: vegam-release.jks aur password kisi safe jagah (do jagah) rakho.
  - Pass: Kho gayi to Play Store par app update nahi hogi.
- [ ] **Release run**: 'Vegam release' workflow chalao.
  - Pass: Artifacts mein .aab, .apk aur mapping.txt aaye.
- [ ] **Release APK test**: Release APK phone par install karke t1, t2, t4 dobara chalao (R8 ke baad kuch toota to nahi).
  - Pass: Debug jaisa hi behaviour; koi crash nahi.
- [ ] **mapping.txt save**: Har release ka mapping.txt rakho (Play Console mein upload).
  - Pass: Crash reports readable rahenge.

## Play Store
- [ ] **Developer account**: Play Console developer account (ek baar ka $25 fee).
  - Pass: Account verify ho chuka.
- [ ] **Privacy policy URL**: store/PRIVACY_POLICY.md ko public URL par rakho (jaise GitHub Pages ya public gist), email apna daalo.
  - Pass: URL browser mein khule.
- [ ] **Listing**: store/STORE_LISTING.md se title, short/full description, category daalo.
  - Pass: Listing preview theek.
- [ ] **Screenshots**: Kam se kam 2 phone screenshots (Home, Detail, Add, Stats). 1080x1920 ya similar. Preview artifact se bhi liye ja sakte hain.
  - Pass: Play Console accept kare.
- [ ] **Data safety form**: store/DATA_SAFETY.md ke jawab copy karo.
  - Pass: Form submit.
- [ ] **Foreground service declaration**: Play Console mein 'dataSync' FGS ka declaration bharo, store/DATA_SAFETY.md ka justification text use karo, 30 second ka demo video (download chalte hue notification).
  - Pass: Declaration approved.
- [ ] **Content rating**: Questionnaire bharo (koi violence/ads nahi).
  - Pass: Rating mil gayi.
- [ ] **Internal testing**: AAB internal testing track par daalo, 2-3 phone par install karke dekho.
  - Pass: Install + download chale.
- [ ] **Production**: Internal test theek ho to production par bhejo.
  - Pass: Review mein bhej diya.
