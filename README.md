# তাযকিয়া (Tazkiyah)

আত্মশুদ্ধির পথে প্রতিদিন — বাংলা ইসলামিক বক্তাদের ভিডিও ও অডিও লেকচার, বক্তা ও বিষয় অনুযায়ী সাজানো একটি ব্যক্তিগত Android অ্যাপ।

| ফোল্ডার | কী আছে |
| --- | --- |
| [`tazkiyah-android/`](tazkiyah-android) | Android অ্যাপ (Kotlin, Jetpack Compose, Room, Media3, android-youtube-player) |
| [`tazkiyah-content/`](tazkiyah-content) | লেকচারের তালিকা (JSON), স্কিমা, স্ক্রিপ্ট — অ্যাপ এখান থেকেই নতুন লেকচার নামায় |
| [`.github/workflows/publish.yml`](.github/workflows/publish.yml) | কনটেন্ট বদলালে যাচাই করে `manifest.json` আপডেট করে |

বিস্তারিত পরিকল্পনা: [`tazkiyah-android/docs/PLAN.md`](tazkiyah-android/docs/PLAN.md)।

## নতুন লেকচার যোগ করা

[`tazkiyah-content/README.md`](tazkiyah-content/README.md) দেখুন। সংক্ষেপে:

```bash
cd tazkiyah-content
python scripts/add_lecture.py --speaker speaker-001 --category tazkiyah "https://youtu.be/XXXXXXXXXXX"
```

তারপর commit ও push — Action নিজে `manifest.json` আপডেট করবে, আর অ্যাপ পরের সিঙ্কে নতুন লেকচার পাবে।

## অ্যাপ বিল্ড করা

```bash
cd tazkiyah-android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew lint testDebugUnitTest
```

প্রতিটি বিল্ড `tazkiyah-content/`-এর একটি কপি অ্যাপের ভেতরে রাখে, তাই নেট ছাড়াও প্রথমবার তালিকা দেখা যায়। অ্যাপ কনটেন্ট নামায় `https://raw.githubusercontent.com/mdhelaluddin-ctg-bd/tazkiyah/main/tazkiyah-content/` থেকে (ব্যাকআপ: jsDelivr)।
