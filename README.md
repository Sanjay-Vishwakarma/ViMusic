# ViMusic (revived fork)

YouTube Music se gaane stream karne wala Android app. Ye original
[vfsfitvnm/ViMusic](https://github.com/vfsfitvnm/ViMusic) **v0.5.4** (Sept 2023, ab archived) ka fork hai,
jise November 2026 mein dobara chalu kiya gaya.

- **Koi server ya Supabase nahi.** Saara data phone ke local database mein rehta hai.
- **App ka flow wahi hai.** Sirf wo hisse theek kiye gaye jo YouTube ne tod diye the, aur ek naya Explore tab joda gaya.
- Current version: [`app/version.properties`](app/version.properties) dekho. Har build pe ye apne aap badhta hai.

---

## Index

1. [Kya badla gaya hai](#1-kya-badla-gaya-hai)
2. [Gaana kaise stream hota hai](#2-gaana-kaise-stream-hota-hai)
3. [Zaroori cheezein](#3-zaroori-cheezein)
4. [Sabse aasaan build (script)](#4-sabse-aasaan-build-script)
5. [Android Studio mein shuru se build tak](#5-android-studio-mein-shuru-se-build-tak)
6. [Phone pe install](#6-phone-pe-install)
7. [Jab YouTube kuch tod de (update aur fix)](#7-jab-youtube-kuch-tod-de-update-aur-fix)
8. [Tests](#8-tests)
9. [Explore catalog badalna](#9-explore-catalog-badalna)
10. [Project ka naksha](#10-project-ka-naksha)
11. [Dikkatein aur hal](#11-dikkatein-aur-hal)

---

## 1. Kya badla gaya hai

| Dikkat | Wajah | Fix |
|---|---|---|
| Koi gaana nahi bajta tha | YouTube ne purane `ANDROID_MUSIC 5.28` / `TVHTML5` clients band kar diye, aur Piped server bhi khatam ho gaya | Stream URL ab **NewPipeExtractor** se aate hain |
| Audio quality | Purana code list ka aakhri 251/140 format uthata tha | Ab Opus ko preference milti hai, phir sabse zyada bitrate. Free YouTube pe ye **itag 251, ~150 kbps, 48 kHz** hai |
| Normalization on hone pe phati aawaz | Loudness ki value na milne pe har gaane pe +5 dB boost lagta tha | Value na ho to ab koi boost nahi |
| Dhundhle thumbnails | Images `lh3` se `yt3.googleusercontent.com` pe shift hui, aur app 60×60 image kheench raha tha | Kisi bhi Google image host pe sahi size maangi jaati hai |
| Quick picks khaali | YouTube ne naya "Comments" tab joda, isliye "Related" tab ki jagah badal gayi | Tab ab uske ID (`MPTR…`) se dhundha jaata hai. Lyrics (`MPLY…`) bhi isi tarah |
| Album/playlist page khaali | Naya `twoColumnBrowseResultsRenderer` layout | Naya aur purana, dono layout parse hote hain. Badi playlist mein "load more" bhi chalta hai |
| Playlist khulte hi crash | Videos ka title ab "Browse" link deta hai (ClassCastException) | Safe type check, aur videoId `playlistItemData` se |
| Artist page pe gaane nahi | Section ke naam badle: "Songs" se "Top songs", "Singles" se "Singles & EPs" | Naye aur purane, dono naam |
| Search mein duration ki jagah "442M plays" | Song result mein teesra column aaya | Ab doosra column padha jaata hai |
| **Naya: Explore tab** | — | Hindi, Bhojpuri, Marathi, Haryanvi, English aur Old Hindi. Har category mein Popular songs, Albums aur Artists, sab asli YouTube Music data se |
| **Naya: Album pe "Play all"** | — | Enqueue aur Shuffle ke saath |
| **Naya: Version auto-increment** | — | Har APK build pe `versionCode` +1 |
| Gaana chalte-chalte ruk jaata tha, Next dabane pe chalta | Player mein error-recovery thi hi nahi. Kisi bhi network glitch ya expire URL pe player ruk jaata tha | Error pe naya URL lekar usi jagah se dobara (3 koshish), phir agla gaana. Offline ho to rukta hai, queue skip nahi karta. Saare requests 512 KB tukdon mein, read timeout 16 s |
| Left side ke "Artists" aur "Albums" tab khaali | Ye sirf bookmark kiye items dikhate the | Dono tab hataye |
| Left side ki navigation patti bahut jagah leti thi | — | Portrait: saare controls **neeche bottom bar** mein (mini player ke theek upar), icon + naam. Home pe aakhir mein Settings, andar ke screens pe pehle Back. Zyada tabs hon to bar scroll hoti hai. Upar kuch nahi. Landscape mein side patti hi |
| Search box gayab / type nahi hota tha (0.6.5–0.6.7) | Header ko ek Row bana diya tha, search ke actions ka weighted Spacer poori width le leta tha | Header wapas original layout pe. Search suggestions aur results ke live tests (`ExploreNavigationTest`) |
| Pata nahi chalta tha ki gaana baj raha hai | — | Mini player ke cover pe chalti MusicBars. Full player: chalte waqt cover "saans leta" hai, pause pe chhota. "Playing/Paused" badge. Cover na ho to accent background pe badi animated bars |
| Local gaano ka cover nahi dikhta tha | Android 10+ pe purana albumart URI nahi chalta | `LocalArtworkFetcher` (Coil): file mein embedded cover, list, player aur notification teeno mein. Chalte gaane pe MusicBars animation |
| **Naya: Local tab** | — | Phone ke gaane, folder ke hisaab se. Play all, Shuffle, favourites, playlists aur queue mein bhi chalte hain (id `local:<MediaStore id>`) |

---

## 2. Gaana kaise stream hota hai

```
Song pe tap
  → MediaItem (sirf videoId)
  → PlayerService (foreground service + ExoPlayer)
  → ResolvingDataSource:
       cache mein hai?          → disk se bajao (offline bhi chalega)
       haal mein URL nikala?    → wahi URL (pichle 2 gaane yaad rehte hain)
       nahi to                  → Innertube.player(videoId)
                                    → NewPipeExtractor: YouTube ka JS chala ke
                                      signature/throttling "taala" kholta hai
                                    → best format: Opus 251
  → googlevideo.com se 512 KB ke tukde (HTTP Range → 206), saath mein cache (2 GB)
  → WebM/MP4 se audio → Opus decoder → PCM → speaker
```

- Beech mein koi apna server nahi hai. Audio seedha Google ke servers se aata hai.
- Naye gaane ka URL nikalne mein 2 se 6 second lagte hain. Cache hua gaana turant chalta hai.
- Login nahi hota, isliye Premium ka 256 kbps nahi milta. **YouTube Data API v3 key ya Google OAuth se audio quality nahi badhti**, kyunki wo API stream URL deti hi nahi.

---

## 3. Zaroori cheezein

| Cheez | Kyun | Kahan |
|---|---|---|
| **JDK 17** | Gradle 7.5.1 sirf JDK 11 se 18 pe chalta hai. **JDK 21 ya 25 pe nahi chalega** | `C:\Users\Sj\.jdks\jdk-17.0.21+9` (pehle se installed). Naye PC pe: [Adoptium Temurin 17](https://adoptium.net/temurin/releases/?version=17) |
| **Android SDK Platform 33** | `compileSdk = 33` | Android Studio → SDK Manager → *Android 13.0 (Tiramisu)* |
| Android SDK Build-Tools | APK banane ke liye | SDK Manager (koi bhi naya version chalega) |
| `local.properties` | SDK ka path | Android Studio khud banata hai. Command line ke liye: `sdk.dir=C\:/Users/Sj/AppData/Local/Android/Sdk` |
| Internet | Pehle build mein dependencies download hoti hain (Google, Maven Central, JitPack) | — |

> ⚠️ **Signing key ka backup lo.** Release APK `C:\Users\Sj\.android\debug.keystore` se sign hota hai.
> Ye file kho gayi (naya PC ya Windows reinstall) to naye APK **purane installed app ke upar install nahi honge**.
> Tab uninstall karna padega aur phone ka saara data (playlists, favourites) chala jaayega.
> Is file ko kahin surakshit copy kar ke rakho.

---

## 4. Sabse aasaan build (script)

PowerShell mein `ViMusic` folder kholo aur chalao:

```powershell
powershell -ExecutionPolicy Bypass -File tools\build-apk.ps1
```

Ye script:
1. JDK 17 khud dhundh leti hai.
2. Live stream test chalati hai. Gaane nahi baj rahe hon to build rok deti hai.
3. Release APK banati hai. Version apne aap badhta hai.
4. APK ko `dist\ViMusic-0.6.X.apk` naam se copy karti hai.

Test chhodna ho to: `tools\build-apk.ps1 -SkipTests`

Haath se (Git Bash) build karna ho to:
```bash
JAVA_HOME="C:/Users/Sj/.jdks/jdk-17.0.21+9" ./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Pehla build ~17 minute leta hai, uske baad ~6 se 8 minute.

---

## 5. Android Studio mein shuru se build tak

### Step 1: Project kholo
1. Android Studio → **File → Open**
2. **`ViMusic` folder** chuno, yaani wo folder jisme `settings.gradle.kts` aur `gradlew.bat` hain.
   Bahar wala `ViMusic` (web app wala, jisme `backend` aur `frontend` hain) **mat** chunna.
3. "Trust Gradle project?" puchhe to **Trust Project** chuno.

### Step 2: Gradle JDK 17 set karo (sabse zaroori)
Android Studio 2024 ke baad apne saath **Java 21** laata hai, jis pe ye project sync nahi hoga. Error kuch aisa aayega:
`Unsupported class file major version 65` ya `Incompatible Gradle JVM`.

1. **File → Settings** (Mac pe *Preferences*)
2. **Build, Execution, Deployment → Build Tools → Gradle**
3. **Gradle JDK** dropdown mein:
   - `jdk-17.0.21+9` dikhe to use chuno.
   - Na dikhe to **Add JDK from disk…** → `C:\Users\Sj\.jdks\jdk-17.0.21+9`
   - Ya **Download JDK…** → Version **17** → Vendor *Eclipse Temurin*
4. **OK**, phir upar **Sync Now** (ya hathi wala icon *Sync Project with Gradle Files*).

### Step 3: Upgrade wale popups ko mana karo
Sync ke baad Android Studio ye suggest kar sakta hai:
- **"Android Gradle Plugin upgrade available" / AGP Upgrade Assistant** → **Don't ask again for this project** ya Cancel.
- **Kotlin plugin ya Compose update** → mana karo.

Wajah: project jaan-boojh ke **AGP 7.4.2, Gradle 7.5.1 aur Kotlin 1.7.20** pe hai. In versions ke saath Compose 1.3 aur baaki libraries test ho chuki hain.
Upgrade karne se Kotlin, Compose, Room aur kapt sab ek saath badalne padte hain. Ye ek alag, bada kaam hai.

### Step 4: SDK
Agar "Failed to find target android-33" aaye, to error ke saath aane wala **Install missing SDK** link dabao, ya
**Tools → SDK Manager → SDK Platforms → Android 13.0 (API 33)** tick karke Apply.

### Step 5: Build variant chuno
**Build → Select Build Variant…** (ya baayi taraf *Build Variants* panel) → `:app` ke liye:

| Variant | Kya milta hai |
|---|---|
| **release** | Asli app "ViMusic", R8 se chhota (~4 MB). Purane installed app ke upar install hota hai. **Yahi use karo** |
| debug | Alag app "Debug" (`it.vfsfitvnm.vimusic.debug`), bada aur slow. Sirf debugging ke liye |

### Step 6: APK banao
**Build → Build App Bundle(s) / APK(s) → Build APK(s)**

Build khatam hone pe neeche popup aata hai, wahan **locate** dabao. APK yahan milega:
```
ViMusic\app\build\outputs\apk\release\app-release.apk
```
Har build pe version apne aap +1 hota hai ([`app/version.properties`](app/version.properties)).

### Step 7 (optional): Phone pe seedha chalana
1. Phone mein **Developer options → USB debugging** on karo aur USB se jodo.
2. Upar device dropdown mein apna phone chuno → **Run ▶**.
   (Run aksar debug variant chalata hai. Asli app ke liye Step 6 ka APK install karo.)

> **Android Studio "AGP 7.4.2 is not supported" bole to:** bahut naye Android Studio purane AGP chhod dete hain.
> Tab [section 4](#4-sabse-aasaan-build-script) ki script se build karo. Wo Android Studio ke bina chalti hai aur hamesha kaam karegi.

---

## 6. Phone pe install

1. APK phone mein copy karo (USB, Drive, WhatsApp-to-self, kuch bhi).
2. File manager se kholo. "Unknown apps" ki permission maange to us app ko allow karo.
3. **Purana ViMusic is fork ka hai** to seedha upar se install ho jaayega aur data bacha rahega.
   **Original vfsfitvnm ka ViMusic** installed ho to pehle uninstall karna padega, kyunki signing key alag hai.

---

## 7. Jab YouTube kuch tod de (update aur fix)

YouTube apna system badalta rehta hai. Neeche wali table se pehchano ki kya toota, aur kahan theek karna hai.

### 7.1 Gaane nahi baj rahe (sabse aam)
**Nishaan:** har naye gaane pe error. Cache wale gaane chal jaate hain.
**Wajah:** YouTube ne stream ka "taala" badla.
**Hal:** NewPipeExtractor update karo. NewPipe team aksar kuch dino mein fix nikaal deti hai.

```powershell
powershell -ExecutionPolicy Bypass -File tools\update-newpipe.ps1
```
Ye script:
1. GitHub pe latest release dhundhti hai.
2. Check karti hai ki JitPack pe wo version build ho chuka hai.
3. `settings.gradle.kts` mein version badalti hai.
4. Live stream test chalati hai. **Fail ho to purana version wapas laga deti hai.**
5. Pass ho to APK bana deti hai.

Kisi khaas version ke liye: `tools\update-newpipe.ps1 -Version v0.26.6`. Sirf test ke liye, bina APK: `-NoBuild`.

**Agar latest pe bhi fail ho:** iska matlab NewPipe ne abhi fix nahi nikaala. [NewPipeExtractor issues](https://github.com/TeamNewPipe/NewPipeExtractor/issues) dekho aur naye release ka intezaar karo.

**Agar compile error aaye (`Unresolved reference ...`):** NewPipe ne apne function ke naam badal diye hain. Library se juda saara code
**ek hi file** mein hai:
[`innertube/src/main/kotlin/it/vfsfitvnm/innertube/newpipe/NewPipeStreams.kt`](innertube/src/main/kotlin/it/vfsfitvnm/innertube/newpipe/NewPipeStreams.kt).
Sirf use naye API ke hisaab se theek karo. Baaki app ko pata bhi nahi ki NewPipe use ho raha hai.

**Agar "metadata is 1.9.0, expected 1.7.1" jaisa error aaye:** naye version ki koi dependency naya Kotlin laa rahi hai.
Us dependency ka purana version [`settings.gradle.kts`](settings.gradle.kts) mein pin karo (jaise OkHttp ko `4.10.0` pe pin kiya gaya hai).

**Agar sirf release APK crash ho, debug nahi:** R8 ne koi class hata di. [`app/proguard-rules.pro`](app/proguard-rules.pro)
mein `-keep class <package>.** { *; }` jodo.

### 7.2 Baaki screens

| Nishaan | Kahan dekhna hai | Kya hua hoga |
|---|---|---|
| Quick picks khaali | [`requests/RelatedPage.kt`](innertube/src/main/kotlin/it/vfsfitvnm/innertube/requests/RelatedPage.kt) | "Related" tab ka ID (`MPTR`) ya sections ke naam ("You might also like", "Similar artists"…) badle |
| Lyrics nahi aate | [`requests/Lyrics.kt`](innertube/src/main/kotlin/it/vfsfitvnm/innertube/requests/Lyrics.kt) | Lyrics tab ka ID (`MPLY`) badla |
| Album/playlist page khaali | [`requests/PlaylistPage.kt`](innertube/src/main/kotlin/it/vfsfitvnm/innertube/requests/PlaylistPage.kt) | Layout ya field mask badla. **Galat field mask pe YouTube poori request ko HTTP 400 de deta hai** |
| Artist page pe songs/albums nahi | [`requests/ArtistPage.kt`](innertube/src/main/kotlin/it/vfsfitvnm/innertube/requests/ArtistPage.kt) | Section ke naam ("Top songs", "Albums", "Singles & EPs") badle |
| Search mein naam ya duration galat | [`models/MusicShelfRenderer.kt`](innertube/src/main/kotlin/it/vfsfitvnm/innertube/models/MusicShelfRenderer.kt), [`utils/FromMusicShelfRendererContent.kt`](innertube/src/main/kotlin/it/vfsfitvnm/innertube/utils/FromMusicShelfRendererContent.kt) | Columns ka order badla |
| Thumbnails dhundhle | [`models/Thumbnail.kt`](innertube/src/main/kotlin/it/vfsfitvnm/innertube/models/Thumbnail.kt) | Image host ya size format badla |
| Explore mein galat ya gayab album/artist | [`catalog/CatalogResolver.kt`](innertube/src/main/kotlin/it/vfsfitvnm/innertube/catalog/CatalogResolver.kt), [`catalog/IndianMusicCatalog.kt`](innertube/src/main/kotlin/it/vfsfitvnm/innertube/catalog/IndianMusicCatalog.kt) | Naam ki spelling badli. Entry mein `aliases` jodo |

**Fix ka tareeka:** pehle sambandhit test chalao ([section 8](#8-tests)). Wo dikhata hai ki kya null ya khaali aa raha hai.
Phir YouTube ka asli response dekh ke parser theek karo, test dobara chalao, phir `tools\build-apk.ps1`.

---

## 8. Tests

Ye **live tests** hain: asli YouTube se baat karte hain, isliye internet chahiye. Inse phone ke bina pata chal jaata hai ki kya toota.

```bash
JAVA_HOME="C:/Users/Sj/.jdks/jdk-17.0.21+9" ./gradlew :innertube:test                          # sab
JAVA_HOME="C:/Users/Sj/.jdks/jdk-17.0.21+9" ./gradlew :innertube:test --tests PlayerStreamTest # sirf streaming
```

| Test | Kya check karta hai |
|---|---|
| `PlayerStreamTest` | 3 gaano ka URL milta hai, best format Opus 251 hai, aur ExoPlayer jaisi request pe HTTP 206 |
| `QuickPicksTest` | Quick picks ke 4 sections, album, playlist aur artist pages, lyrics, badi playlist ka load more |
| `ExploreNavigationTest` | Explore ka har album aur artist page khulta hai. Existing search (songs, videos, albums, artists, playlists) sahi hai |
| `CatalogResolverTest` | Catalog ki har entry kis cheez se match hui (galat match yahin pakde jaate hain) |
| `ThumbnailResizeTest` | Thumbnail URL ka size badalna |
| `LongSessionTest` | 12 gaane lagaataar, har ek shuru se aakhir tak 512 KB tukdon mein ("gaana ruk jaata hai" jaisi dikkat ki jaanch) |

Report: `innertube/build/reports/tests/test/index.html`

---

## 9. Explore catalog badalna

Saari list sirf **naamon** ki hai, yahan:
[`innertube/src/main/kotlin/it/vfsfitvnm/innertube/catalog/IndianMusicCatalog.kt`](innertube/src/main/kotlin/it/vfsfitvnm/innertube/catalog/IndianMusicCatalog.kt)

```kotlin
CatalogAlbum("Aashiqui 2", "Mithoon", year = "2013")          // title, artist hint, saal
CatalogArtist("Avadhoot Gupte", aliases = listOf("Avdhoot Gupte")) // YouTube ki spelling alag ho to
```
- Artwork, gaane, ID, kuch bhi hardcoded nahi hai. App ye sab YouTube Music se dhundh ke Room mein cache karta hai.
- Nayi entry jodne ke baad `CatalogResolverTest` chalao aur dekho ki sahi cheez match hui ya "NOT FOUND" aaya.
- Matching ke niyam: naam, artist, saal ka score. Remix, cover, karaoke, lofi, "(From …)" singles aur chhote fan channels pe penalty. Kam score ho to entry chhupa di jaati hai, galat cheez nahi dikhti.
- Cache: mile hue items 30 din, na mile items 3 din baad dobara check hote hain. Popular songs 6 ghante tak memory mein rehte hain.
- Nayi category: `CatalogCategory(...)` banao aur `categories` list mein jodo.

---

## 10. Project ka naksha

```
ViMusic/
├─ app/                         Android app (UI, player, Room database)
│  ├─ src/main/kotlin/it/vfsfitvnm/vimusic/
│  │  ├─ service/PlayerService.kt     ExoPlayer, cache, stream resolve, notification
│  │  ├─ catalog/CatalogRepository.kt Explore: resolve + Room cache
│  │  ├─ models/CatalogItem.kt        Explore cache table (DB v24)
│  │  ├─ ui/screens/home/Explore.kt   Explore tab
│  │  ├─ ui/screens/home/HomeLocal.kt Local tab (folders)
│  │  ├─ local/LocalMusic.kt          MediaStore se phone ke gaane
│  │  ├─ service/LocalOrRemoteDataSource.kt  file:// / content:// vs YouTube routing
│  │  └─ Database.kt                  Room (version 24, auto-migrations)
│  ├─ version.properties        Auto-increment version (haath se mat badlo)
│  └─ proguard-rules.pro        R8 rules (NewPipe/Rhino keep)
├─ innertube/                   YouTube Music API + parsing (pure Kotlin/JVM, tests yahin)
│  ├─ newpipe/NewPipeStreams.kt Stream URL (NewPipeExtractor)
│  ├─ requests/                 player, search, album, artist, playlist, related, lyrics
│  ├─ catalog/                  Explore catalog + matching
│  └─ src/test/kotlin/          Live tests
├─ kugou/                       Synced lyrics (KuGou)
├─ compose-*/                   Chhoti UI libraries (routing, persist, reordering)
├─ tools/build-apk.ps1          Test + build + dist copy
├─ tools/update-newpipe.ps1     NewPipe update + test + build
├─ settings.gradle.kts          Saare library versions yahin hain
└─ dist/                        Script se bane APK (git mein nahi jaate)
```

**Versions jo jaan-boojh ke nahi badle gaye:** AGP 7.4.2, Gradle 7.5.1, Kotlin 1.7.20, Compose 1.3, Media3 1.0.0-beta03,
compileSdk/targetSdk 33, minSdk 21 (Android 5.0+).

---

## 11. Dikkatein aur hal

| Dikkat | Hal |
|---|---|
| `Unsupported class file major version 65/69` | Gradle galat JDK (21 ya 25) pe chal raha hai. [Step 2](#step-2-gradle-jdk-17-set-karo-sabse-zaroori) dekho |
| `SDK location not found` | `local.properties` mein `sdk.dir=C\:/Users/Sj/AppData/Local/Android/Sdk` (forward slashes) |
| `Failed to find target android-33` | SDK Manager se Android 13 (API 33) install karo |
| Install pe "App not installed" / conflict | Signing key alag hai (original ViMusic ya naya PC). Uninstall karke install karo, aur [backup](#3-zaroori-cheezein) rakho |
| Gaana shuru hone mein der | Naye gaane ka URL nikalne mein 2 se 6 sec lagte hain, ye normal hai. Cache wale turant chalte hain |
| Screen band hone pe gaana ruke | Phone settings → Apps → ViMusic → Battery → **Unrestricted** |
| Aawaz kharab | Player mein album art long-press → *Stats for nerds* → Itag 251 aur ~150 kbps hona chahiye. Volume normalization off karke dekho. Phone ka Dolby/EQ check karo |
| Local tab mein gaane nahi | "Allow" dabao. Pehle mana kiya tha to "Open settings" → Permissions → Music and audio → Allow. Sirf 30 second se lambe music files dikhte hain (ringtone aur voice notes nahi) |
| Explore khaali, "Retry" | Internet check karo. Pehli baar data YouTube se aata hai, uske baad cache se offline bhi dikhta hai |

---

## Credit aur license

- Original app: [vfsfitvnm/ViMusic](https://github.com/vfsfitvnm/ViMusic), [GPL-3.0](LICENSE).
- Stream extraction: [TeamNewPipe/NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0).
- Ye fork bhi GPL-3.0 ke tahat hai.

Ye app YouTube ka official app nahi hai. Personal use ke liye hai. YouTube ki Terms of Service ke hisaab se apni zimmedari pe use karo.
#   V i M u s i c  
 