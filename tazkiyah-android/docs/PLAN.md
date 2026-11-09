# তাযকিয়া — বাংলা ইসলামিক লেকচার অ্যাপের বিস্তারিত প্ল্যান

Nov 7, 2026 · @Md Helal Uddin

## সারসংক্ষেপ ও লক্ষ্য

এটি একটি ব্যক্তিগত Android অ্যাপ, যেখানে বাংলা ইসলামিক বক্তাদের ভিডিও ও অডিও লেকচার বক্তা ও বিষয় অনুযায়ী সাজানো থাকবে। লেকচারের তালিকা থাকবে একটি পাবলিক GitHub রিপোতে JSON ফাইল হিসেবে। অ্যাপ সেটি ফেচ করে লোকাল ডেটাবেসে রাখবে, তাই নতুন লেকচার যোগ করতে অ্যাপ নতুন করে বিল্ড করতে হবে না।

মূল নীতি:

- **কনটেন্ট অ্যাপের বাইরে:** রিপোতে JSON আপডেট করে `dataVersion` বাড়ালেই অ্যাপ নিজে থেকে নতুন তালিকা নামিয়ে নেবে।
- **অফলাইন-ফার্স্ট:** তালিকা সবসময় লোকাল Room ডেটাবেস থেকে দেখাবে; নেট না থাকলেও ব্রাউজ করা যাবে।
- **ফোকাস:** ইউটিউব ভিডিও অ্যাপের ভেতরেই চলবে; রিলেটেড ভিডিও, কমেন্ট বা ইউটিউব অ্যাপে চলে যাওয়ার কোনো পথ থাকবে না।
- **বাংলা-প্রথম:** পুরো UI বাংলায়, বাংলা সংখ্যা ও বাংলা সার্চ।
- **খরচহীন রক্ষণাবেক্ষণ:** কোনো সার্ভার নেই; GitHub আর একটি ফ্রি CDN দিয়েই সব চলবে।

প্রথম ভার্সনের (v1) পরিধি: বক্তা ও বিষয়ভিত্তিক তালিকা, ভিডিও ও অডিও প্লেয়ার, ফেভারিট, শোনার ইতিহাস ও যেখানে থেমেছিলেন সেখান থেকে চালু, এবং অটো আপডেট। পরের ভার্সনে: অডিও ডাউনলোড, লেকচারভিত্তিক নোট, নিজের প্লেলিস্ট।

## অ্যাপের নাম

চূড়ান্ত নাম: **তাযকিয়া** (Tazkiyah) — ট্যাগলাইন "আত্মশুদ্ধির পথে প্রতিদিন"। কুরআনি শব্দ, অর্থ আত্মশুদ্ধি; সূরা আশ-শামসের ৯ নম্বর আয়াতের মর্ম — সে-ই সফল, যে নিজেকে পরিশুদ্ধ করল। লেকচার শুনে জীবন গঠনের উদ্দেশ্যটা এক শব্দে ধরে। নিচের তালিকায় বিবেচনা করা অন্য নামগুলো রইল।

| নাম | ইংরেজি বানান | অর্থ | কেন মানানসই |
| --- | --- | --- | --- |
| পাথেয় | Patheyo | যাত্রার রসদ, সম্বল | বাংলা-প্রথম, নরম উচ্চারণ, আইকনে সহজে ফুটে ওঠে |
| তাযকিয়া | Tazkiyah | আত্মশুদ্ধি | কুরআনি শব্দ (সূরা আশ-শামস ৯১:৯); "জীবন গঠন"-এর সরাসরি অর্থ |
| মজলিস | Majlis | ইলমের আসর | লেকচার শোনার জায়গা হিসেবে সবচেয়ে স্বাভাবিক |
| নসীহা | Naseeha | আন্তরিক উপদেশ | "দ্বীন হলো নসীহা" হাদিসের (সহীহ মুসলিম ৫৫) সাথে যুক্ত |
| রাহবার | Rahbar | পথপ্রদর্শক | ছোট, শক্ত শব্দ; বক্তাদের পথনির্দেশনার ভাব |
| নূরের পথ | Noorer Poth | আলোর পথ | সহজবোধ্য, তবে দুই শব্দের বলে আইকন-লেবেলে লম্বা |

নাম চূড়ান্ত হলে যা ঠিক করতে হবে:

- **প্যাকেজ নাম:** `com.inovixlabs.tazkiyah`।
- **লঞ্চার লেবেল:** বাংলায় "তাযকিয়া"; `strings.xml`-এ `app_name`।
- **আইকন — "তারা-প্রদীপ":** দুটি সমান বর্গ মিলে আট কোনা তারা, মাঝে প্রদীপের শিখা কাটা; সিগন্যাল হলুদ টাইলে চারকোল রঙে। ওয়েবসাইটের `docs/assets/mark.svg`-ই অ্যাপের adaptive icon (foreground ০.৭৫ গুণে, safe zone-এর ভেতরে); এক রঙে আঁকা বলে থিমড আইকনেও একই চিহ্ন।

## কালার থিম ও টাইপোগ্রাফি

থিম ওয়েবসাইটের মতোই: চারকোল আর সিগন্যাল হলুদ, চৌকো (৪dp) কোনা, সরু ভারী শিরোনাম। টপ বার আর মিনি প্লেয়ার দুই থিমেই চারকোল (`#111111`), তাই স্ট্যাটাস বারের আইকন সবসময় সাদা; প্লে বাটন হলুদ টাইলে চারকোল আইকন। Dynamic Color (ওয়ালপেপার থেকে রং) বন্ধ, যাতে ব্র্যান্ড রং সবসময় এক থাকে। আসল মান `core/designsystem/Color.kt`-এ।

| ভূমিকা (M3 role) | লাইট | ডার্ক | কোথায় |
| --- | --- | --- | --- |
| primary | `#0A0A0A` | `#FACC15` | প্রধান বাটন, জোর দেওয়া লেখা |
| onPrimary | `#FFFFFF` | `#0A0A0A` | primary-র ওপর লেখা |
| primaryContainer | `#FACC15` | `#FACC15` | সিলেক্টেড ট্যাব, আজকের লেকচার, আপডেট নোটিস |
| onPrimaryContainer | `#0A0A0A` | `#0A0A0A` | হলুদের ওপর লেখা |
| secondary | `#8A6200` | `#FACC15` | প্রোগ্রেস, ফেভারিট, স্লাইডার |
| secondaryContainer | `#FEF3C7` | `#2B2611` | নতুন কনটেন্টের নোটিস, চলতি পর্ব |
| background | `#F4F4F0` | `#0A0A0A` | পুরো স্ক্রিনের পটভূমি |
| surface | `#FFFFFF` | `#111111` | কার্ড, বটম শিট, নিচের মেনু |
| surfaceVariant | `#EDEDE8` | `#27272A` | ব্যাজ, ইনপুট |
| onSurface | `#0A0A0A` | `#F4F4F5` | মূল লেখা |
| onSurfaceVariant | `#52525B` | `#A1A1AA` | গৌণ লেখা |
| outlineVariant | `#DEDED7` | `#333333` | ডিভাইডার, বর্ডার |
| error | `#B3261E` | `#F2B8B5` | সিঙ্ক ব্যর্থ, ভুল লিংক |

কন্ট্রাস্ট: হলুদ `#FACC15` হালকা পটভূমিতে লেখা হিসেবে কখনো নয় (১.৪:১); লাইট থিমে হলুদ-ঘেঁষা লেখা মানে অকার `#8A6200` (সাদায় ৫.৫:১)। হলুদের ওপর লেখা সবসময় চারকোল (১২.৯:১); চারকোলের ওপর গৌণ লেখা `#A1A1AA` (৭.৪:১)।

টাইপোগ্রাফি:

- **বডি ও UI:** Settings-এ বাছাই করা পড়ার ফন্ট (ডিফল্ট Noto Sans Bengali)।
- **শিরোনাম:** Anek Bangla-র সবচেয়ে সরু (wdth 75) ExtraBold রূপ, ওয়েবসাইটের শিরোনামের মতো — display, headline (Small বাদে) আর titleLarge-এ, পড়ার ফন্ট যা-ই হোক। ফাইল `res/font/anek_bangla_condensed_extrabold.ttf`, শুধু বাংলা অক্ষর (ইংরেজি অক্ষর সিস্টেম ফন্টে)।
- **আরবি (আয়াত/দোয়া থাকলে):** Amiri বা Noto Naskh Arabic।
- **লাইন-হাইট:** বাংলায় কার ও মাত্রা উঁচু-নিচু হয়, তাই font size-এর ১.৪–১.৫ গুণ রাখুন।
- **ফন্ট বান্ডল:** TTF ফাইল `res/font/`-এ রাখুন (downloadable font নয়), যাতে অফলাইনেও একই রকম দেখায়।
- **সংখ্যা:** সময়, পর্ব নং, গণনা সব বাংলা অঙ্কে (০–৯) দেখাতে একটি `toBanglaDigits()` এক্সটেনশন।

## সামগ্রিক আর্কিটেকচার

অ্যাপের প্রতিটি স্ক্রিন শুধু লোকাল Room ডেটাবেস পড়ে; GitHub থেকে আসা ডেটা কেবল সিঙ্কের মাধ্যমে Room-এ ঢোকে। তাই নেট না থাকলেও অ্যাপ পুরোপুরি চলে।

&#91;embedded content: সামগ্রিক আর্কিটেকচার · কনটেন্ট রিপো, ডেটা স্তর, UI ও প্লেয়ার\]

প্লেয়ার রিজিউম পজিশন ও ইতিহাস Room-এর ইউজার টেবিলে লেখে; সিঙ্ক শুধু ক্যাটালগ টেবিল বদলায়, তাই ইউজারের ডেটা কখনো হারায় না।

## রিসোর্স রিপো ও ফাইল ফরম্যাট

কনটেন্ট থাকবে আলাদা একটি পাবলিক রিপোতে, যেমন `tazkiyah-content`। অ্যাপ প্রথমে শুধু ছোট `manifest.json` পড়বে; তাতে প্রতিটি ডেটা ফাইলের sha256 হ্যাশ থাকবে, তাই যে ফাইল বদলেছে শুধু সেটাই নামবে।

রিপোর গঠন:

```
tazkiyah-content/
├── manifest.json              # ভার্সন + ফাইল তালিকা (অ্যাপ সবার আগে এটা পড়ে)
├── data/
│   ├── speakers.json          # বক্তার তালিকা
│   ├── categories.json        # বিষয়/ক্যাটাগরি
│   └── lectures/
│       ├── speaker-001.json   # প্রতি বক্তার লেকচার আলাদা ফাইলে
│       └── speaker-002.json
├── images/speakers/           # বক্তার ছবি (.webp, ২৫৬×২৫৬)
├── schema/                    # JSON Schema — ভুল ফরম্যাট ধরার জন্য
├── scripts/
│   ├── add_lecture.py         # ইউটিউব লিংক দিলে এন্ট্রি বানায়
│   └── build_manifest.py      # হ্যাশ হিসাব করে manifest আপডেট করে
└── .github/workflows/publish.yml
```

### manifest.json

```json
{
  "schemaVersion": 1,
  "minAppVersionCode": 1,
  "dataVersion": 42,
  "updatedAt": "2026-11-07T10:00:00Z",
  "changelog": "৩টি নতুন লেকচার যোগ হয়েছে",
  "files": [
    { "path": "data/speakers.json", "sha256": "9f2c…", "size": 4210 },
    { "path": "data/categories.json", "sha256": "a71b…", "size": 1830 },
    { "path": "data/lectures/speaker-001.json", "sha256": "c04e…", "size": 51234 }
  ]
}
```

- `schemaVersion` — ফরম্যাটে ভাঙা পরিবর্তন (breaking change) হলে তবেই বাড়বে।
- `minAppVersionCode` — এর চেয়ে পুরনো অ্যাপ হলে ডেটা আপডেট না করে "অ্যাপ আপডেট করুন" দেখাবে।
- `dataVersion` — প্রতিটি কনটেন্ট আপডেটে ১ বাড়বে; অ্যাপ এটা দেখেই বোঝে নতুন কিছু এসেছে।

### speakers.json

```json
{
  "speakers": [
    {
      "id": "speaker-001",
      "name": "বক্তার নাম",
      "nameEn": "Speaker Name",
      "bio": "সংক্ষিপ্ত পরিচিতি",
      "photo": "images/speakers/speaker-001.webp",
      "order": 1
    }
  ]
}
```

### categories.json

```json
{
  "categories": [
    { "id": "ibadah", "name": "ইবাদত", "icon": "mosque", "order": 2, "parentId": null },
    { "id": "salah", "name": "সালাত", "icon": "prayer", "order": 1, "parentId": "ibadah" }
  ]
}
```

শুরুর ক্যাটাগরি তালিকা: আকীদা · ইবাদত (সালাত, সাওম, যাকাত, হজ) · আত্মশুদ্ধি · আখলাক · কুরআন ও তাফসীর · হাদিস · সীরাত · পরিবার ও দাম্পত্য · সন্তান প্রতিপালন · হালাল রিযিক · দোয়া ও যিকির · মৃত্যু ও আখিরাত · তরুণদের জন্য।

### data/lectures/speaker-001.json

```json
{
  "speakerId": "speaker-001",
  "lectures": [
    {
      "id": "spk001-0001",
      "title": "তাওবার শর্ত ও পদ্ধতি",
      "description": "ঐচ্ছিক সারাংশ",
      "categoryIds": ["tazkiyah"],
      "tags": ["তাওবা"],
      "media": { "type": "youtube", "videoId": "XXXXXXXXXXX", "startSec": 0 },
      "durationSec": 2730,
      "publishedAt": "2025-03-14",
      "addedAt": "2026-11-07",
      "series": { "id": "series-tawbah", "title": "তাওবা সিরিজ", "part": 1 }
    },
    {
      "id": "spk001-0002",
      "title": "সালাতে খুশু",
      "categoryIds": ["salah"],
      "media": { "type": "audio", "url": "https://archive.org/download/ITEM/file.mp3", "mimeType": "audio/mpeg" },
      "durationSec": 1980,
      "addedAt": "2026-11-07"
    }
  ]
}
```

`media.type`-এর তিনটি মান: `youtube` (videoId), `audio` (সরাসরি mp3/m4a লিংক), `video` (সরাসরি mp4 লিংক, ঐচ্ছিক)।

ফরম্যাটের নিয়ম:

1. লেকচারের `id` কখনো বদলাবে না, আবার ব্যবহারও হবে না — ফেভারিট, ইতিহাস ও রিজিউম পজিশন এই id-তে বাঁধা।
2. অ্যাপ অজানা ফিল্ড উপেক্ষা করবে (`ignoreUnknownKeys = true`), তাই নতুন ঐচ্ছিক ফিল্ড যোগ করলে পুরনো অ্যাপ ভাঙবে না।
3. কোনো লেকচার ফাইল থেকে মুছে দিলে অ্যাপের ক্যাটালগ থেকেও সরবে; তবে ইউজারের নোট/ইতিহাস মুছবে না, শুধু লুকানো থাকবে।
4. তারিখ ISO ফরম্যাটে (`YYYY-MM-DD`), সময়কাল সেকেন্ডে; বাংলা অঙ্ক শুধু UI-তে।

## আপডেট মেকানিজম

অ্যাপ প্রতিবার চালু হলে এবং প্রতি ১২ ঘণ্টায় একবার নিজে থেকে manifest দেখে; ইউজারকে কিছু করতে হয় না। তিনটি যাচাই পাস না করা পর্যন্ত কোনো নতুন ডেটা লোকাল ডেটাবেসে ঢোকে না, তাই ভুল বা অর্ধেক নামানো ফাইল কখনো অ্যাপ ভাঙবে না।

&#91;embedded content: আপডেট ফ্লো · ৩টি যাচাই, ৪টি থামার পথ\]

খুঁটিনাটি:

- **ট্রিগার:** অ্যাপ চালু হলে একটি one-shot `WorkRequest`; সাথে ১২ ঘণ্টার `PeriodicWorkRequest` (`NetworkType.CONNECTED`, সেটিংসে শুধু Wi-Fi চালু করলে `UNMETERED`); হোমে pull-to-refresh ও সেটিংসে "এখনই দেখুন"।
- **প্রথম চালু:** `assets/seed/` থেকে ডেটা import, তাই নেট ছাড়া প্রথমবার খুললেও তালিকা থাকে।
- **সামঞ্জস্য:** অ্যাপে `SUPPORTED_SCHEMA_VERSION = 1` কনস্ট্যান্ট; manifest-এর `schemaVersion` বেশি হলে বা `minAppVersionCode` বর্তমান `VERSION_CODE`-এর চেয়ে বেশি হলে ডেটা অপরিবর্তিত রেখে ব্যানার দেখাবে।
- **অংশবিশেষ সাফল্য:** কিছু ফাইল বসলে, কিছু বাতিল হলে `dataVersion` সেভ হবে না; পরের সিঙ্কে `synced_files`-এর হ্যাশ দেখে শুধু বাকিগুলো নামবে।
- **মুছে যাওয়া ফাইল:** manifest-এ নেই কিন্তু লোকালে আছে এমন ফাইলের ক্যাটালগ সারি সরানো হবে, ইউজার ডেটা নয়।
- **ব্যর্থ হলে:** Worker `Result.retry()` ফেরত দেবে, WorkManager নিজে exponential backoff সামলাবে।

```kotlin
suspend fun sync(): SyncResult {
    val manifest = api.fetchManifest() ?: return SyncResult.Offline  // মূল, তারপর fallback URL
    if (manifest.schemaVersion > SUPPORTED_SCHEMA_VERSION ||
        manifest.minAppVersionCode > BuildConfig.VERSION_CODE) return SyncResult.AppUpdateRequired
    if (manifest.dataVersion <= prefs.dataVersion()) return SyncResult.UpToDate

    val local = dao.syncedFiles().associateBy { it.path }
    val remotePaths = manifest.files.map { it.path }.toSet()
    (local.keys - remotePaths).forEach { path ->
        db.withTransaction { importer.remove(path); dao.deleteSynced(path) }
    }

    var allOk = true
    manifest.files.filter { local[it.path]?.sha256 != it.sha256 }.forEach { file ->
        val bytes = api.download(file.path)
        if (bytes == null || sha256(bytes) != file.sha256) { allOk = false; return@forEach }
        db.withTransaction { importer.import(file.path, bytes); dao.upsertSynced(file) }
    }
    if (!allOk) return SyncResult.Partial

    prefs.saveDataVersion(manifest.dataVersion, manifest.changelog)
    return SyncResult.Updated
}
```

## ইউটিউব প্লেয়ার: অ্যাপের ভেতরে আটকে রাখা

ইউটিউব ভিডিও চলবে [android-youtube-player](https://github.com/PierfrancescoSoffritti/android-youtube-player) লাইব্রেরি দিয়ে (core `13.0.0`, MIT লাইসেন্স, minSdk 21)। এটি WebView-এর ভেতরে ইউটিউবের অফিসিয়াল IFrame Player API চালায়; Google-এর পুরনো নেটিভ Player API এখন deprecated। ইউটিউব অ্যাপে চলে যাওয়ার সব পথ চারটি স্তরে বন্ধ হবে।

**স্তর ১ — ইউটিউবের নিজস্ব UI বন্ধ:**

```kotlin
val options = IFramePlayerOptions.Builder(context)
    .controls(0)      // ইউটিউবের বাটন, টাইটেল-বার ও লোগো-লিংক বন্ধ
    .rel(0)           // শেষে রিলেটেড ভিডিও শুধু একই চ্যানেল থেকে
    .ivLoadPolicy(3)  // অ্যানোটেশন বন্ধ
    .fullscreen(0)    // ফুলস্ক্রিন আমরা নিজেরা সামলাব
    .build()
```

সীমাবদ্ধতা: ২০১৮ সাল থেকে `rel=0` রিলেটেড ভিডিও পুরোপুরি বন্ধ করে না, আর `showinfo`/`modestbranding` প্যারামিটার আর কাজ করে না ([YouTube player parameters](https://developers.google.com/youtube/player_parameters))। তাই প্যারামিটারের ওপর ভরসা না করে নিচের স্তরগুলো জরুরি।

**স্তর ২ — টাচ-শিল্ড (মূল সুরক্ষা):** প্লেয়ারের ঠিক ওপরে পুরো জায়গা জুড়ে একটি স্বচ্ছ Compose লেয়ার থাকবে, যা সব টাচ নিজে নিয়ে নেবে। ফলে ইউটিউবের লোগো, টাইটেল, "Watch on YouTube" বা এন্ড-স্ক্রিন কার্ডে কোনো ক্লিক পৌঁছাবেই না। প্লে/পজ, ১০ সেকেন্ড সামনে-পেছনে, সিকবার, স্পিড — সব কন্ট্রোল আমাদের নিজেদের।

```kotlin
Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
    AndroidView(factory = { ytView }, modifier = Modifier.matchParentSize())
    // এই লেয়ার সব টাচ খেয়ে ফেলে — নিচের WebView কিছুই পায় না
    Box(
        Modifier.matchParentSize().pointerInput(Unit) {
            detectTapGestures(
                onTap = { controlsVisible = !controlsVisible },
                onDoubleTap = { offset -> seekBy(if (offset.x < size.width / 2) -10 else 10) }
            )
        }
    )
    PlayerControls(state, onPlayPause, onSeek, onSpeed, onFullscreen)
    if (state == PlayerState.ENDED || state == PlayerState.PAUSED) CoverCard(lecture)
}
```

**স্তর ৩ — এন্ড ও পজ কভার:** ভিডিও শেষ (`ENDED`) বা পজ হলে বক্তার ছবি আর লেকচারের নাম দিয়ে একটি নিজস্ব কার্ড পুরো প্লেয়ার ঢেকে দেবে। এতে ইউটিউবের "More videos" গ্রিড দেখা যাবে না; কার্ডে থাকবে "আবার শুনুন" ও একই সিরিজের "পরের পর্ব"।

**স্তর ৪ — ইন্টেন্ট গার্ড (শেষ নিরাপত্তা):** কোনোভাবে ইউটিউব লিংক খোলার চেষ্টা হলে `MainActivity` সেটা আটকে দেবে।

```kotlin
private val blockedHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "music.youtube.com")

override fun startActivity(intent: Intent, options: Bundle?) {
    if (intent.isYouTubeEscape()) { showSnackbar("ফোকাস মোড: ইউটিউবে যাওয়া বন্ধ"); return }
    super.startActivity(intent, options)
}
override fun startActivity(intent: Intent) = startActivity(intent, null)

private fun Intent.isYouTubeEscape(): Boolean {
    val uri = data ?: return `package` == "com.google.android.youtube"
    return uri.scheme == "vnd.youtube" || uri.host?.lowercase() in blockedHosts
}
```

আরও যা মানতে হবে:

- অ্যাপে কোথাও "ইউটিউবে খুলুন" বা ইউটিউব লিংক শেয়ার বাটন থাকবে না।
- `YouTubePlayerView`-কে `lifecycle.addObserver()` দিয়ে যুক্ত করুন এবং `loadOrCueVideo(lifecycle, videoId, resumeSec)` দিয়ে চালান, যাতে ব্যাকগ্রাউন্ডে ভুল করে চালু না হয়।
- ফুলস্ক্রিনের জন্য Activity-তে `android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout"` দিন; নইলে ঘোরালে ভিডিও আবার শুরু হবে।
- মোবাইলে IFrame API দিয়ে ভিডিও কোয়ালিটি বদলানো যায় না, আর ইউটিউবের বিজ্ঞাপন আসতে পারে — এ দুটো অফিসিয়াল উপায়ে নিয়ন্ত্রণযোগ্য নয়।

## অডিও প্লেব্যাক

অডিওর জন্য সেরা পথ: লেকচারের mp3 সরাসরি লিংকে রাখা (`media.type = audio`) এবং Media3 ExoPlayer দিয়ে চালানো। এতে স্ক্রিন বন্ধ থাকলেও চলবে, লক-স্ক্রিন ও নোটিফিকেশনে কন্ট্রোল থাকবে, আর অফলাইনে ডাউনলোড করা যাবে।

| অডিও সোর্স | কীভাবে চলবে | ব্যাকগ্রাউন্ড | অফলাইন | মন্তব্য |
| --- | --- | --- | --- | --- |
| সরাসরি mp3/m4a লিংক | Media3 ExoPlayer + `MediaSessionService` | হ্যাঁ, পূর্ণ | হ্যাঁ | সুপারিশকৃত |
| ইউটিউব — "শুধু শুনুন" মোড | একই ইউটিউব প্লেয়ার, ভিডিও বক্তার ছবি দিয়ে ঢাকা | হ্যাঁ, `enableBackgroundPlayback(true)` দিয়ে | না | শুধু ব্যক্তিগত বিল্ডে চলবে (নিচে দেখুন) |
| ইউটিউব থেকে অডিও স্ট্রিম বের করা | NewPipeExtractor ধরনের লাইব্রেরি | হ্যাঁ | হ্যাঁ | সুপারিশ নয় — প্রায়ই ভাঙে, ইউটিউবের শর্ত ভঙ্গ |

অডিও কোথায় হোস্ট করবেন:

- **archive.org:** ফ্রি, স্থায়ী, সরাসরি mp3 লিংক দেয় এবং মাঝখান থেকে চালানো (range request) সাপোর্ট করে। প্রথম পছন্দ।
- **GitHub Releases:** কনটেন্ট রিপোর রিলিজে অডিও ফাইল রাখা যায়; প্রতিটি ফাইল ২ GiB-এর কম হতে হবে।
- **Google Drive এড়িয়ে চলুন:** বড় ফাইলে ভাইরাস-স্ক্যান পেজ আসে, সরাসরি লিংক নির্ভরযোগ্য নয়।

ইউটিউবের "শুধু শুনুন" মোড নিয়ে: লাইব্রেরির README নিজেই বলে, ব্যাকগ্রাউন্ডে ইউটিউব চালানো YouTube-এর শর্তের বাইরে, তাই এমন অ্যাপ Play Store-এ দেওয়া যাবে না; শুধু ব্যক্তিগত ব্যবহারের জন্য চলবে। আপনার অ্যাপ ব্যক্তিগত, তাই এটা রাখা যায়, তবে দুটি সীমাবদ্ধতা আছে: অ্যাপ recent থেকে সরিয়ে দিলে থেমে যাবে, আর সিস্টেম নোটিফিকেশন কন্ট্রোলের জন্য আলাদা MediaSession বানাতে হবে (ফেজ ৪)।

প্লেয়ারের ফিচার:

- **একটাই প্লেব্যাক কন্ট্রোলার:** `PlaybackController` ইন্টারফেস, যার দুটি ইমপ্লিমেন্টেশন — `ExoAudioController` ও `YouTubeController`। UI জানবে না কোনটা চলছে।
- **মিনি প্লেয়ার:** সব স্ক্রিনের নিচে স্থায়ী; ট্যাপ করলে পূর্ণ প্লেয়ার।
- **স্পিড:** ০.৭৫×, ১×, ১.২৫×, ১.৫×, ২×।
- **স্লিপ টাইমার:** ১৫ / ৩০ / ৪৫ / ৬০ মিনিট, অথবা "এই লেকচার শেষে"।
- **রিজিউম:** প্রতি ৫ সেকেন্ডে এবং পজ করলেই পজিশন Room-এ সেভ; ৯৫% শোনা হলে "শেষ হয়েছে" চিহ্ন।
- **ম্যানিফেস্ট:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` পারমিশন এবং সার্ভিসে `foregroundServiceType="mediaPlayback"`।

## স্ক্রিন ও ফিচার

নিচে চারটি ট্যাব থাকবে — হোম, বক্তা, বিষয়, আমার — আর তার ওপরে স্থায়ী মিনি প্লেয়ার। ফেজ নম্বর মিলবে "ধাপে ধাপে বাস্তবায়ন" সেকশনের সাথে।

| স্ক্রিন | কী থাকবে | ফেজ |
| --- | --- | --- |
| হোম | "চালিয়ে যান" (অর্ধেক শোনা), "নতুন যোগ হয়েছে", "আজকের লেকচার" (না-শোনা থেকে দিনে একটি), বিষয়ের শর্টকাট, নতুন ডেটা এলে changelog ব্যানার | ২ |
| বক্তা তালিকা | ছবিসহ গ্রিড, প্রত্যেকের লেকচার সংখ্যা | ২ |
| বক্তার পাতা | পরিচিতি, লেকচার তালিকা, বিষয়ভিত্তিক চিপ ফিল্টার, সাজানো (নতুন / পুরনো / সিরিজ), সিরিজ গ্রুপ করে দেখানো | ২ |
| বিষয় | বিষয় → উপ-বিষয় → লেকচার; বক্তা দিয়ে ফিল্টার | ২ |
| লেকচার ও প্লেয়ার | প্লেয়ার, শিরোনাম, বক্তা, বিবরণ, সিরিজের অন্য পর্ব, ফেভারিট, "শেষ হয়েছে" চিহ্ন | ৩ |
| সার্চ | শিরোনাম, ট্যাগ, বক্তার নামে বাংলা ও ইংরেজি সার্চ | ২ |
| আমার | ফেভারিট, ইতিহাস, শেষ করা লেকচার, সাপ্তাহিক শোনার সময় | ৩ |
| আমার (পরে) | অডিও ডাউনলোড, লেকচারভিত্তিক নোট, নিজের প্লেলিস্ট | ৫ |
| সেটিংস | থিম (লাইট / ডার্ক / সিস্টেম), শুধু Wi-Fi-তে আপডেট, "এখনই আপডেট দেখুন", বর্তমান ডেটা ভার্সন, ডিফল্ট স্পিড | ৪ |

বাংলা সার্চ ঠিকমতো কাজ করাতে:

- Room-এ FTS4 টেবিল (`unicode61` tokenizer) — শিরোনাম, ট্যাগ, বক্তার বাংলা ও ইংরেজি নাম মিলিয়ে।
- সার্চের আগে লেখা ও কোয়েরি দুটোই নরমালাইজ: Unicode NFC, zero-width joiner/non-joiner বাদ, এবং "য়" ও "ড়/ঢ়"-এর দুই রকম এনকোডিং (একক অক্ষর বনাম অক্ষর + নুকতা) একই করা।
- টাইপ করার সাথে সাথে ফল (৩০০ ms debounce), সাম্প্রতিক সার্চ মনে রাখা।

ফোকাসের জন্য ডিজাইন সিদ্ধান্ত:

- লেকচার শেষে অন্য বক্তার কিছু নিজে থেকে চালু হবে না; শুধু একই সিরিজের পরের পর্বের প্রস্তাব।
- কোনো প্রমোশনাল নোটিফিকেশন নেই; নোটিফিকেশন শুধু প্লেব্যাক কন্ট্রোল আর (ঐচ্ছিক) নতুন লেকচারের খবর।
- কমেন্ট, লাইক, ভিউ সংখ্যা — কিছুই দেখাবে না।

## টেক স্ট্যাক ও প্রজেক্ট স্ট্রাকচার

একটি `app` মডিউলে পরিষ্কার প্যাকেজ বিভাজন যথেষ্ট — ব্যক্তিগত অ্যাপে মাল্টি-মডিউলের জটিলতা লাভের চেয়ে বেশি। minSdk 26 (Android 8.0); compileSdk ও targetSdk সর্বশেষ স্টেবল। প্রজেক্ট তৈরির সময় Claude Code-কে প্রতিটি লাইব্রেরির সর্বশেষ স্টেবল ভার্সন দেখে `libs.versions.toml`-এ বসাতে বলুন।

| স্তর | লাইব্রেরি | ভূমিকা |
| --- | --- | --- |
| ভাষা ও বিল্ড | Kotlin 2.x, Gradle Kotlin DSL, Version Catalog, KSP | আধুনিক, টাইপ-সেফ বিল্ড |
| UI | Jetpack Compose, Material 3, Navigation Compose (type-safe route) | সব স্ক্রিন |
| DI | Hilt | ViewModel, Repository, Worker-এ ইনজেকশন |
| নেটওয়ার্ক | OkHttp + kotlinx.serialization | manifest ও JSON ফেচ; এন্ডপয়েন্ট কম, তাই Retrofit লাগবে না |
| ডেটাবেস | Room (+ FTS4) | অফলাইন ক্যাটালগ, ইউজার ডেটা, বাংলা সার্চ |
| ব্যাকগ্রাউন্ড কাজ | WorkManager | পর্যায়ক্রমিক আপডেট চেক |
| সেটিংস | DataStore Preferences | থিম, ডেটা ভার্সন, স্পিড |
| ছবি | Coil 3 | বক্তার ছবি, ইউটিউব থাম্বনেইল |
| ইউটিউব | android-youtube-player core 13.0.0 | ফোকাসড ভিডিও প্লেয়ার |
| অডিও | Media3 ExoPlayer + Media3 Session | ব্যাকগ্রাউন্ড অডিও, নোটিফিকেশন |
| টেস্ট | JUnit, Turbine, OkHttp MockWebServer, Room in-memory, Compose UI Test | সিঙ্ক ও UI যাচাই |

প্যাকেজ গঠন:

```
com.inovixlabs.tazkiyah
├── TazkiyahApp.kt                  # @HiltAndroidApp, WorkManager কনফিগ
├── MainActivity.kt                # NavHost + ইউটিউব ইন্টেন্ট গার্ড
├── core/
│   ├── designsystem/              # Color.kt, Type.kt, Theme.kt, কমন কম্পোনেন্ট
│   └── util/                      # BanglaDigits.kt, BanglaNormalizer.kt, DurationFormat.kt
├── data/
│   ├── remote/                    # ContentApi.kt, dto/ (Manifest, Speaker, Category, Lecture)
│   ├── local/                     # TazkiyahDatabase.kt, entity/, dao/
│   ├── sync/                      # ContentSyncer.kt, ContentSyncWorker.kt, Sha256.kt
│   └── repository/                # CatalogRepository.kt, UserDataRepository.kt
├── playback/
│   ├── PlaybackController.kt      # সাধারণ ইন্টারফেস + PlaybackState
│   ├── audio/                     # PlaybackService (MediaSessionService), ExoAudioController
│   └── youtube/                   # YouTubeController, FocusedYouTubePlayer (টাচ-শিল্ডসহ composable)
└── feature/
    ├── home/  speakers/  speaker/  categories/
    ├── search/  player/  library/  settings/
    └── (প্রতিটিতে XxxScreen.kt + XxxViewModel.kt, StateFlow<UiState>)
```

কনফিগারেশন (`build.gradle.kts`-এ `buildConfigField`):

- `CONTENT_BASE_URL` = `https://raw.githubusercontent.com/<owner>/tazkiyah-content/main/`
- `CONTENT_FALLBACK_URL` = `https://cdn.jsdelivr.net/gh/<owner>/tazkiyah-content@main/`

## লোকাল ডেটাবেস ডিজাইন

Room ডেটাবেসে দুই দল টেবিল: ক্যাটালগ টেবিল সিঙ্কের সময় বদলে যায়, ইউজার টেবিল সিঙ্ক কখনো ছোঁয় না। ইউজার টেবিলে ক্যাটালগের সাথে foreign key রাখা হবে না, যাতে কোনো লেকচার মুছলে ইউজারের ইতিহাস cascade হয়ে মুছে না যায়।

| টেবিল | মূল কলাম | দল | নোট |
| --- | --- | --- | --- |
| `speakers` | id (PK), name, nameEn, bio, photoUrl, sortOrder | ক্যাটালগ | speakers.json পুরোটা বদলায় |
| `categories` | id (PK), name, icon, sortOrder, parentId | ক্যাটালগ | উপ-বিষয়ের জন্য parentId |
| `lectures` | id (PK), speakerId (index), title, description, mediaType, youtubeId, audioUrl, startSec, durationSec, publishedAt, addedAt, seriesId, seriesTitle, seriesPart | ক্যাটালগ | এক বক্তার ফাইল বদলালে শুধু তার লেকচার বদলায় |
| `lecture_category` | lectureId + categoryId (যৌথ PK) | ক্যাটালগ | many-to-many সংযোগ |
| `lectures_fts` | title, tags, speakerName, speakerNameEn (নরমালাইজড) | ক্যাটালগ | FTS4, বাংলা সার্চ |
| `synced_files` | path (PK), sha256, syncedAt | সিঙ্ক | কোন ফাইল নামানো আছে তার হিসাব |
| `playback_progress` | lectureId (PK), positionSec, durationSec, completed, lastPlayedAt | ইউজার | রিজিউম, ইতিহাস, "চালিয়ে যান" |
| `favorites` | lectureId (PK), addedAt | ইউজার | ফেভারিট |
| `listen_log` | date (PK), listenedSec | ইউজার | সাপ্তাহিক শোনার সময় |
| `notes` | id, lectureId, positionSec, text, createdAt | ইউজার | ফেজ ৫ |
| `playlists`, `playlist_items` | id, name / playlistId, lectureId, position | ইউজার | ফেজ ৫ |
| `downloads` | lectureId (PK), localPath, status, bytes | ইউজার | ফেজ ৫, শুধু অডিও |

নিয়ম:

- `exportSchema = true` রাখুন এবং প্রতিটি স্কিমা পরিবর্তনে Room AutoMigration লিখুন; `fallbackToDestructiveMigration()` কখনো নয় — তাতে ইউজারের ইতিহাস মুছে যাবে।
- এক বক্তার লেকচার ফাইল আপডেট = একটি transaction: সেই বক্তার পুরনো লেকচার, সংযোগ ও FTS সারি মুছে নতুনগুলো বসানো।
- স্ক্রিনগুলো DAO থেকে `Flow` নেবে, তাই সিঙ্ক শেষ হলে তালিকা নিজে থেকেই রিফ্রেশ হবে।

## কনটেন্ট যোগ করার ওয়ার্কফ্লো

নতুন লেকচার যোগ করা মানে একটি JSON ফাইলে এন্ট্রি বসিয়ে push করা; বাকি সব — যাচাই, হ্যাশ, ভার্সন বাড়ানো, CDN ক্যাশ পরিষ্কার — GitHub Action নিজে করবে।

1. ইউটিউব লিংক কপি করুন।
2. স্ক্রিপ্ট চালান: `python scripts/add_lecture.py --speaker speaker-001 --category tazkiyah "https://youtu.be/XXXXXXXXXXX"`
   - লিংক থেকে videoId বের করে (watch, youtu.be, shorts, live — সব রকম লিংক)।
   - ইউটিউবের oEmbed (`https://www.youtube.com/oembed?url=…&format=json`) থেকে শিরোনাম আনে — কোনো API key লাগে না।
   - পরের ক্রমিক id বানায় (`spk001-0043`), `addedAt` আজকের তারিখ দেয়, ফাইলে যোগ করে।
   - `durationSec` ফাঁকা থাকতে পারে; প্রথমবার চালালে অ্যাপ প্লেয়ার থেকে সময়কাল নিয়ে লোকালি রেখে দেবে।
3. দরকার হলে শিরোনাম বাংলায় ঠিক করুন, তারপর commit ও push। ফোন থেকেও GitHub অ্যাপ বা github.dev দিয়ে সরাসরি এডিট করা যায়।
4. GitHub Action চলবে: যাচাই → manifest তৈরি → commit → jsDelivr ক্যাশ purge।
5. পরের সিঙ্কে অ্যাপ নতুন লেকচার পাবে (raw.githubusercontent.com সাধারণত প্রায় ৫ মিনিট ক্যাশ রাখে)।

`scripts/validate.py` যা যাচাই করবে:

- প্রতিটি ফাইল তার JSON Schema মানে কিনা।
- সব লেকচার id অনন্য কিনা (সব ফাইল মিলিয়ে)।
- `speakerId` ও `categoryIds` আসলে বিদ্যমান কিনা।
- videoId ঠিক ১১ অক্ষরের `[A-Za-z0-9_-]` কিনা; অডিও লিংক `https://` দিয়ে শুরু কিনা।
- কোনো ভুল হলে Action ব্যর্থ হবে, manifest আপডেট হবে না — ফলে ভুল ডেটা কখনো অ্যাপে পৌঁছাবে না।

`scripts/build_manifest.py` প্রতিটি ডেটা ফাইলের sha256 ও size হিসাব করবে; কোনো হ্যাশ বদলালে তবেই `dataVersion` ১ বাড়াবে এবং `updatedAt` বসাবে।

`.github/workflows/publish.yml`:

```yaml
name: publish-content
on:
  push:
    branches: [main]
    paths: ["data/**", "images/**", "schema/**"]
  workflow_dispatch:
permissions:
  contents: write
jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
      - run: pip install jsonschema
      - name: Validate
        run: python scripts/validate.py
      - name: Build manifest
        run: python scripts/build_manifest.py
      - name: Commit manifest
        run: |
          git config user.name "content-bot"
          git config user.email "content-bot@users.noreply.github.com"
          git add manifest.json
          git diff --cached --quiet || (git commit -m "chore: update manifest [skip ci]" && git push)
      - name: Purge jsDelivr cache
        run: curl -s "https://purge.jsdelivr.net/gh/${{ github.repository }}@main/manifest.json"
```

jsDelivr ব্রাঞ্চের ফাইল ডিফল্টে প্রায় ১২ ঘণ্টা ক্যাশ রাখে, তাই purge ধাপটা জরুরি ([jsDelivr purge](https://www.jsdelivr.com/tools/purge))। মূল সোর্স raw.githubusercontent.com, jsDelivr শুধু ব্যাকআপ।

## ধাপে ধাপে বাস্তবায়ন (Claude Code-এর জন্য)

কাজ ছয়টি ফেজে ভাগ; প্রতিটি ফেজ আলাদা Claude Code সেশনে, আগের ফেজ চলমান অবস্থায় শেষ করে পরেরটা। দুটি রিপো লাগবে: `tazkiyah-content` (পাবলিক) আর `tazkiyah-android` (প্রাইভেট রাখতে পারেন)।

শুরুর আগে প্রস্তুতি:

1. এই ডকটি Markdown হিসেবে এক্সপোর্ট করে দুই রিপোতেই `docs/PLAN.md` নামে রাখুন।
2. `tazkiyah-android` রিপোর রুটে নিচের `CLAUDE.md` রাখুন — প্রতিটি সেশনে Claude Code এটা নিজে পড়ে।
3. প্রতিটি ফেজের প্রম্পট দেওয়ার পর আগে প্ল্যান দেখাতে বলুন, তারপর কোড লেখাতে দিন।

```markdown
# Tazkiyah — project rules
- docs/PLAN.md is the source of truth. Read the relevant section before each task.
- Stack: Kotlin, Jetpack Compose, Material 3, Hilt, Room, WorkManager, DataStore,
  OkHttp + kotlinx.serialization, Media3, android-youtube-player (core).
- All user-facing text in Bengali via strings.xml; show numbers with toBanglaDigits().
- Never add any way to open the YouTube app or youtube.com (no share, no "open in YouTube").
- Every YouTube video must be shown through FocusedYouTubePlayer (touch shield + cover card).
- Content sync must never modify user tables. Never use destructive Room migrations.
- Before finishing a task: ./gradlew lint testDebugUnitTest must pass.
```

| ফেজ | লক্ষ্য | সম্পন্ন হলে যা হবে |
| --- | --- | --- |
| ০ | কনটেন্ট রিপো | নমুনা ডেটা, স্কিমা, স্ক্রিপ্ট, Action — push করলে manifest নিজে আপডেট হয় |
| ১ | অ্যাপ কাঠামো, থিম, ডেটা ও সিঙ্ক | অ্যাপ খুললে রিপো থেকে ডেটা নেমে Room-এ বসে; ডিবাগ স্ক্রিনে সংখ্যা দেখায় |
| ২ | ব্রাউজ UI | হোম, বক্তা, বিষয়, সার্চ — সব বাংলায়, লাইট ও ডার্ক |
| ৩ | প্লেয়ার | ফোকাসড ইউটিউব ও ব্যাকগ্রাউন্ড অডিও, মিনি প্লেয়ার, রিজিউম, ফেভারিট, ইতিহাস |
| ৪ | পরিমার্জন | সেটিংস, আপডেট ব্যানার, স্লিপ টাইমার, "শুধু শুনুন" মোড, আইকন, seed ডেটা |
| ৫ | বাড়তি | অডিও ডাউনলোড, নোট, প্লেলিস্ট, ইউজার ডেটার ব্যাকআপ |

**ফেজ ০ — কনটেন্ট রিপো** (`tazkiyah-content` ফোল্ডারে):

```text
Create the content repository described in docs/PLAN.md, sections "রিসোর্স রিপো ও ফাইল ফরম্যাট" and "কনটেন্ট যোগ করার ওয়ার্কফ্লো".
Include: manifest.json; data/speakers.json (2 sample speakers); data/categories.json (the starter category list, Bengali names, stable English ids, parentId for sub-categories); data/lectures/speaker-001.json and speaker-002.json (3 sample lectures each, mixing youtube and audio types); JSON Schemas in schema/.
Scripts: add_lecture.py (parse videoId from watch/youtu.be/shorts/live URLs, fetch title via YouTube oEmbed without an API key, generate the next sequential id, set addedAt), validate.py (schema, unique ids across files, valid speaker/category references, videoId format, https audio URLs), build_manifest.py (sha256 + size per file, bump dataVersion only when a hash changed).
Add .github/workflows/publish.yml exactly as in the plan, pytest tests for all scripts, and a Bengali README explaining how to add a lecture.
```

**ফেজ ১ — অ্যাপ কাঠামো, ডেটা ও সিঙ্ক:**

```text
Create a new Android project "Tazkiyah" (package com.inovixlabs.tazkiyah, minSdk 26, latest stable compileSdk/targetSdk) following docs/PLAN.md "টেক স্ট্যাক ও প্রজেক্ট স্ট্রাকচার". Use a version catalog with the latest stable versions of every library.
Implement:
1. Theme from "কালার থিম ও টাইপোগ্রাফি": light and dark color schemes with the exact hex values, dynamic color off, bundled Bengali fonts (add TODO placeholders if font files are missing), line height about 1.45x.
2. Room database from "লোকাল ডেটাবেস ডিজাইন" with exportSchema = true.
3. DTOs matching the JSON format, ignoreUnknownKeys = true.
4. ContentSyncer implementing "আপডেট মেকানিজম" exactly: manifest fetch, schemaVersion/minAppVersionCode checks, download only changed files, sha256 verification, one transaction per file, fallback URL, seed import from assets on first launch.
5. ContentSyncWorker: periodic every 12 hours with a network constraint, plus a one-shot run on app start.
6. A temporary debug screen showing dataVersion and speaker/category/lecture counts.
Tests with MockWebServer: fresh install, no change, one file changed, hash mismatch, schemaVersion too new.
```

**ফেজ ২ — ব্রাউজ UI:**

```text
Build the browse UI from docs/PLAN.md "স্ক্রিন ও ফিচার" (phase 2 rows): bottom navigation (হোম, বক্তা, বিষয়, আমার), Home, Speakers grid, Speaker detail (category chips, sort by newest/oldest/series, series grouping), Categories tree, and Search on lectures_fts using a BanglaNormalizer (NFC, strip ZWJ/ZWNJ, unify the two encodings of য়, ড়, ঢ়).
All strings in Bengali in strings.xml, numbers via toBanglaDigits(). Each screen = Screen + ViewModel exposing StateFlow<UiState> with loading, empty and error states. Add Compose previews in light and dark. Unit-test BanglaNormalizer and toBanglaDigits().
```

**ফেজ ৩ — প্লেয়ার:**

```text
Implement playback per docs/PLAN.md "ইউটিউব প্লেয়ার" and "অডিও প্লেব্যাক".
1. PlaybackController interface with ExoAudioController (Media3 ExoPlayer inside a MediaSessionService with media notification) and YouTubeController (android-youtube-player core 13.0.0).
2. FocusedYouTubePlayer composable with all four lock layers: IFramePlayerOptions (controls 0, rel 0, ivLoadPolicy 3, fullscreen 0); a full-size touch shield with our own controls (play/pause, double-tap seek ±10s, seek bar, speed, fullscreen); a cover card on ENDED and PAUSED; and the startActivity intent guard in MainActivity.
3. Mini player above the bottom bar, full player screen, speed options, resume position saved every 5 seconds and on pause, completed at 95%, favorites, history and the "আমার" tab.
Tests: Intent.isYouTubeEscape() for youtube.com, m.youtube.com, youtu.be, vnd.youtube and a normal https URL.
```

**ফেজ ৪ — পরিমার্জন:**

```text
From docs/PLAN.md: add the Settings screen (theme, Wi-Fi-only updates, "check now", current data version), an update banner showing the manifest changelog, a sleep timer, and the YouTube "শুধু শুনুন" mode (speaker artwork covers the video, enableBackgroundPlayback(true), not registered as a lifecycle observer in this mode, manual release) with a MediaSession bridge for notification controls.
Also add the adaptive app icon (foreground/background layers) and a Gradle task that copies a snapshot of tazkiyah-content into app/src/main/assets/seed before release builds.
```

**ফেজ ৫ — বাড়তি ফিচার:**

```text
Add: audio downloads into app-specific storage with delete and offline playback; timestamped notes per lecture; user playlists; export and import of user data (favorites, progress, notes, playlists) as a JSON file through the system file picker.
```

## টেস্টিং চেকলিস্ট ও ঝুঁকি

প্রতিটি ফেজ শেষে নিচের চেকগুলো ফোনে হাতে-কলমে করে টিক দিন।

- [ ] প্রথম ইনস্টল নেট ছাড়া — seed ডেটা থেকে তালিকা দেখায়
- [ ] নেট চালু করলে সিঙ্ক হয়, নতুন লেকচার তালিকায় আসে
- [ ] রিপোতে একটি লেকচার যোগ করলে পরের সিঙ্কে অ্যাপে দেখা যায়
- [ ] ভুল JSON push করলে Action ব্যর্থ হয়, অ্যাপের ডেটা অপরিবর্তিত থাকে
- [ ] `schemaVersion` বাড়ালে পুরনো অ্যাপ "অ্যাপ আপডেট করুন" দেখায়, আগের ডেটা থাকে
- [ ] ইউটিউব প্লেয়ারের লোগো, শিরোনাম, কোণা, শেষের স্ক্রিন — যেখানেই ট্যাপ করুন, ইউটিউব অ্যাপ বা ব্রাউজার খোলে না
- [ ] ভিডিও শেষ বা পজ হলে নিজস্ব কভার কার্ড আসে, "More videos" দেখা যায় না
- [ ] ফুলস্ক্রিন ও ফোন ঘোরালে ভিডিও আবার শুরু থেকে চলে না
- [ ] অডিও স্ক্রিন বন্ধ অবস্থায় চলে; নোটিফিকেশন ও ব্লুটুথ হেডসেটের বাটন কাজ করে
- [ ] অ্যাপ বন্ধ করে আবার খুললে লেকচার ঠিক আগের জায়গা থেকে চলে
- [ ] বাংলা সার্চে "য়" দুই রকমভাবে টাইপ করলেও একই ফল আসে
- [ ] ডার্ক মোডে সব লেখা স্পষ্ট পড়া যায়
- [ ] রিপো থেকে লেকচার মুছে দিলে ফেভারিট বা ইতিহাস খুললে ক্র্যাশ হয় না

| ঝুঁকি | প্রভাব | সমাধান |
| --- | --- | --- |
| আপলোডার কোনো ভিডিওর এমবেড বন্ধ রেখেছেন, বা ভিডিও মুছে/প্রাইভেট করেছেন | সেই লেকচার চলবে না | `onError`-এ `VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER` ও `VIDEO_NOT_FOUND` ধরে বাংলায় বার্তা দেখান; কনটেন্ট রিপো থেকে সেই এন্ট্রি সরান বা অডিও লিংক দিন |
| ইউটিউব তাদের এমবেড প্লেয়ারের চেহারা বদলায় | নতুন বাটন/লিংক আসতে পারে | টাচ-শিল্ড সব টাচ আটকায়, তাই নতুন লিংকও অকার্যকর থাকে; লাইব্রেরি আপডেট রাখুন |
| raw.githubusercontent.com পুরনো ফাইল দেয় | হ্যাশ মেলে না | ফাইল বাতিল করে পরের সিঙ্কে আবার চেষ্টা; jsDelivr ব্যাকআপ |
| GitHub-এ পৌঁছানো যায় না | নতুন লেকচার আসে না | অফলাইন-ফার্স্ট, পুরনো ডেটা দিয়ে অ্যাপ পুরোপুরি চলে |
| ইউটিউব ব্যাকগ্রাউন্ড প্লে শর্তবিরোধী | Play Store-এ দেওয়া যাবে না | অ্যাপ ব্যক্তিগত রাখুন; কখনো প্রকাশ করলে ইউটিউবের "শুধু শুনুন" মোড বাদ দিন |
| অন্যের লেকচার archive.org-এ আপলোড | স্বত্ব ও অনুমতির প্রশ্ন | বক্তা বা প্রতিষ্ঠানের অনুমতি নিয়ে আপলোড করুন, অথবা তাদের নিজেদের প্রকাশিত অডিও লিংক ব্যবহার করুন |
| একই শব্দের নানা বানান (নামাজ / নামায / সালাত) | সার্চে লেকচার না পাওয়া | `tags`-এ বিকল্প বানান যোগ করুন; পরে একটি প্রতিশব্দ তালিকা |

## সূত্র

- [android-youtube-player — GitHub README](https://github.com/PierfrancescoSoffritti/android-youtube-player) (ভার্সন, IFramePlayerOptions, কাস্টম UI, ব্যাকগ্রাউন্ড প্লে নীতি)
- [YouTube Embedded Players and Player Parameters — Google for Developers](https://developers.google.com/youtube/player_parameters)
- [jsDelivr purge tool](https://www.jsdelivr.com/tools/purge)
- [jsDelivr ব্রাঞ্চ ক্যাশ ১২ ঘণ্টা — nuts-foundation/denylist README](https://www.github.com/nuts-foundation/denylist)
- [raw.githubusercontent.com ক্যাশ — GitHub Community discussion #46691](https://github.com/orgs/community/discussions/46691)
