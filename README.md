# তাযকিয়া (Tazkiyah)

আত্মশুদ্ধির পথে প্রতিদিন — বাংলা ইসলামিক বক্তাদের ভিডিও ও অডিও লেকচার নিয়ে একটি Android অ্যাপ ও ওয়েবসাইট, যেখানে লেকচারগুলো বক্তা ও বিষয় অনুযায়ী সাজানো।

## ডাউনলোড ও ইনস্টল

**ওয়েবসাইট ও ডাউনলোড পাতা: https://ilmcraft.github.io/tazkiyah/** — ব্রাউজারেই সব লেকচার শোনা যায়, আর এখান থেকেই অ্যাপ ডাউনলোড করা যায়। অন্যদের এই লিংকটিই দিন (অ্যাপের "অ্যাপ শেয়ার করুন"-ও এটিই পাঠায়)। সরাসরি সর্বশেষ APK: https://github.com/ilmcraft/tazkiyah/releases/latest/download/tazkiyah.apk (Android 8.0 বা তার পরের ভার্সন লাগবে)।

1. APK-টি ফোনে খুলুন। প্রথমবার ফোন "অজানা অ্যাপ ইনস্টল" (Install unknown apps) করার অনুমতি চাইবে — যে অ্যাপ দিয়ে ফাইলটি খুলছেন (যেমন Chrome বা Files), সেটিকে অনুমতি দিন।
2. **Install** চাপুন। Play Protect সতর্ক করলে "More details → Install anyway" চাপুন।
3. নতুন ভার্সন এলে অ্যাপের হোমে আর সেটিংস → "আপডেট, লাইসেন্স ও যোগাযোগ"-এ জানানো হবে; একইভাবে নতুন APK ইনস্টল করলে আপনার ফেভারিট, ইতিহাস ও সেভ করা লেকচার মুছে যাবে না।

ফাইলটি ঠিক আছে কি না যাচাই করতে চাইলে রিলিজ পাতার `.sha256` ফাইলের সঙ্গে `shasum -a 256 tazkiyah-<ভার্সন>.apk` মিলিয়ে নিন।

## রিপোতে কী আছে

| ফোল্ডার | কী আছে |
| --- | --- |
| [`tazkiyah-android/`](tazkiyah-android) | Android অ্যাপ (Kotlin, Jetpack Compose, Room, Media3, android-youtube-player, NewPipeExtractor) |
| [`tazkiyah-content/`](tazkiyah-content) | লেকচারের তালিকা (JSON), স্কিমা, স্ক্রিপ্ট ও নতুন লেকচার খোঁজার বট — অ্যাপ এখান থেকেই নতুন লেকচার নামায় |
| [`.github/workflows/`](.github/workflows) | `publish.yml` (কনটেন্ট যাচাই ও manifest), `discover-lectures.yml` (সাপ্তাহিক বট), `fill-durations.yml` (সাপ্তাহিক দৈর্ঘ্য), `content-ci.yml`, `android-ci.yml` |
| [`docs/`](docs) | ওয়েব ভার্সন ও ডাউনলোড পাতা (GitHub Pages, বিল্ড লাগে না) — `tazkiyah-content/` থেকে সরাসরি তালিকা পড়ে, তাই বক্তা আর লেকচারের সংখ্যা সবসময় আপডেট থাকে। লোকালি দেখতে: `python3 -m http.server 8765 --directory docs` |

বিস্তারিত পরিকল্পনা: [`tazkiyah-android/docs/PLAN.md`](tazkiyah-android/docs/PLAN.md)।

## নতুন লেকচার

- **নিজে থেকে:** বট সপ্তাহে একবার সব বক্তার নতুন লেকচার খুঁজে একটি Pull Request খোলে; রিভিউ করে Merge করলেই অ্যাপে চলে যায়।
- **হাতে বা AI রিসার্চ দিয়ে:** [`tazkiyah-content/README.md`](tazkiyah-content/README.md) দেখুন। সংক্ষেপে:

```bash
cd tazkiyah-content
python scripts/add_lecture.py --speaker speaker-001 --category tazkiyah "https://youtu.be/XXXXXXXXXXX"
```

তারপর commit ও push — Action নিজে `manifest.json` আপডেট করবে, আর অ্যাপ পরের সিঙ্কে নতুন লেকচার পাবে।

## অ্যাপ বিল্ড করা

```bash
cd tazkiyah-android
./gradlew lint testDebugUnitTest
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

প্রতিটি বিল্ড `tazkiyah-content/`-এর একটি কপি অ্যাপের ভেতরে রাখে, তাই নেট ছাড়াও প্রথমবার তালিকা দেখা যায়। অ্যাপ কনটেন্ট নামায় `https://raw.githubusercontent.com/ilmcraft/tazkiyah/main/tazkiyah-content/` থেকে (ব্যাকআপ: jsDelivr)।

### রিলিজ বানানো (রক্ষণাবেক্ষণকারীর জন্য)

1. `tazkiyah-android/app/build.gradle.kts`-এ `versionCode` ১ বাড়ান, `versionName` নতুন দিন (যেমন `1.2.0`)।
2. `./gradlew lint testDebugUnitTest assembleRelease` — R8 দিয়ে ছোট করা, release key-তে সাইন করা APK পাবেন `app/build/outputs/apk/release/app-release.apk`-এ। ফোনে চালিয়ে দেখুন (সেভ, প্লে, সিঙ্ক)।
3. commit-এ ট্যাগ দিন `app-v1.2.1` আর push করুন; GitHub-এ ওই ট্যাগে Release বানিয়ে `tazkiyah-1.2.1.apk`, তার `.sha256`, **আর একই ফাইল `tazkiyah.apk` নামে** যুক্ত করুন (ডাউনলোড পাতার বোতাম `releases/latest/download/tazkiyah.apk`-এ যায়)। রিলিজ নোট লিখুন `tazkiyah-android/release-notes/<ভার্সন>.md`-এ। অ্যাপ শুধু `app-v` দিয়ে শুরু হওয়া ট্যাগকেই নতুন ভার্সন হিসেবে চেনে।

**সাইনিং key:** release key রিপোর বাইরে থাকে — `~/.android/keystores/tazkiyah-release.jks`, আর তার পাসওয়ার্ড `~/.gradle/gradle.properties`-এ (`TAZKIYAH_RELEASE_STORE_FILE`, `TAZKIYAH_RELEASE_STORE_PASSWORD`, `TAZKIYAH_RELEASE_KEY_ALIAS`, `TAZKIYAH_RELEASE_KEY_PASSWORD`)। দুটোরই ব্যাকআপ রাখুন (যেমন পাসওয়ার্ড ম্যানেজার + এনক্রিপ্টেড ড্রাইভ): key হারালে ব্যবহারকারীদের ফোনে আর আপডেট দেওয়া যাবে না, নতুন key-র APK বসাতে আগেরটা আনইনস্টল করতে হবে। key না থাকলে release বিল্ড পরিষ্কার error দেয়, ডিবাগ key দিয়ে চুপচাপ সাইন করে না।

## লাইসেন্স

অ্যাপটি [GNU GPL v3.0](LICENSE)-তে প্রকাশিত (অফলাইন সেভের জন্য ব্যবহৃত NewPipeExtractor GPL-3.0, তাই পুরো অ্যাপও তাই)। ফন্টগুলো SIL OFL 1.1। লেকচারগুলোর স্বত্ব সংশ্লিষ্ট বক্তা ও আপলোডকারীদের; অ্যাপ সেগুলো ইউটিউবের প্লেয়ারেই চালায়।

> ব্যক্তিগত ব্যবহারের অ্যাপ: ব্যাকগ্রাউন্ডে ইউটিউব চালানো ও ইউটিউব থেকে অফলাইন সেভ ইউটিউবের শর্তের বাইরে, তাই এটি Play Store-এ দেওয়া হয়নি।

যোগাযোগ: [GitHub Issues](https://github.com/ilmcraft/tazkiyah/issues)
