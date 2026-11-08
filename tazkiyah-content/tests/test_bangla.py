"""bangla.py: the normaliser must match the app's BanglaNormalizer.kt; titles come out calm and clean."""
from __future__ import annotations

import pytest

import bangla


# Same cases as tazkiyah-android/app/src/test/.../BanglaNormalizerTest.kt
def test_ya_with_nukta_matches_precomposed():
    assert bangla.normalize("দোয়া") == bangla.normalize("দোয়া")


def test_rra_and_rha_both_encodings_are_unified():
    assert bangla.normalize("পড়া") == bangla.normalize("পড়া")
    assert bangla.normalize("আঢ়া") == bangla.normalize("আঢ়া")


def test_zero_width_joiners_are_removed():
    assert bangla.normalize("র্যাব") == bangla.normalize("র্‍যাব")
    assert bangla.normalize("কি‌তা‍ব") == "কিতাব"


def test_legacy_khanda_ta_becomes_khanda_ta():
    assert bangla.normalize("হঠাত্‍") == "হঠাৎ"


def test_case_whitespace_and_digits():
    assert bangla.normalize("  Jakaria   MASUD ২০২৫ ") == "jakaria masud 2025"


def test_contains_any_ignores_encoding_and_case():
    assert bangla.contains_any("নতুন লেকচার | MUFTI HARUN IZHAR", ["harun izhar"])
    assert bangla.contains_any("মুফতি হারুন ইযহার", ["হারুন ইযহার"])
    assert not bangla.contains_any("অন্য কেউ", ["হারুন ইযহার"])


HARUN = ["মুফতি হারুন ইজহার", "মুফতি হারুন ইযহার", "হারুন ইজহার", "Mufti Harun Izhar", "Harun Izhar", "Harun ijar"]


@pytest.mark.parametrize(
    ("title", "names", "channel", "expected"),
    [
        (
            "তারেক রহমানের বক্তব্যের কড়া জবাব দিলেন || মুফতি হারুন ইযহার || Mufti Harun Izhar || QH Tafsir.",
            HARUN,
            "QH Tafsir",
            "তারেক রহমানের বক্তব্যের জবাব দিলেন",
        ),
        (
            "তোমরা কাকে ভয় দেখাও? হারুন ইজহার চৌধুরী | Harun ijar | মুফতি হারুন ইজহার চৌধুরীর ওয়াজ",
            HARUN,
            None,
            "তোমরা কাকে ভয় দেখাও?",
        ),
        (
            "মুসলিমদের দলবদ্ধতা বনাম দলাদলি | আহমদ রফিক | JU Islamic Conference 2024",
            ["আহমদ রফিক", "আহমাদ রফিক"],
            "JU Society of Islamic Knowledge Seekers (JU SIKS)",
            "মুসলিমদের দলবদ্ধতা বনাম দলাদলি",
        ),
        (
            "🔥 সালাতে খুশু #waz #islamic | নতুন ওয়াজ ২০২৫",
            [],
            None,
            "সালাতে খুশু",
        ),
        ("আসিফ আদনানের সেরা একটি লেকচার", ["আসিফ আদনান"], "EBADOT TV", "সেরা একটি লেকচার"),
    ],
)
def test_clean_title(title, names, channel, expected):
    assert bangla.clean_title(title, names, channel) == expected


SHAKTI = ["ডা. শামসুল আরেফীন শক্তি", "Dr. Shamsul Arefin Shakti", "শামসুল আরেফিন শক্তি", "শামসুল আরেফ", "shamsul arefin"]


@pytest.mark.parametrize(
    ("title", "expected"),
    [
        ("মানুষের সংজ্ঞা: ২য় পর্ব [ডাঃ শামসুল আরেফীন] Definition of Man: Part 2 [Dr. Shamsul Arefin]", "মানুষের সংজ্ঞা: ২য় পর্ব"),
        ("আমাদের জীবনের প্রকৃত উদ্দেশ্য কি_ শামসুল আরেফীন শক্তি হাফিঃ", "আমাদের জীবনের প্রকৃত উদ্দেশ্য কি?"),
        ("বিয়ের প্রকৃত প্রস্তুতি কীভাবে নিবো ॥ ডা. শামসুল আরেফীন শক্তি", "বিয়ের প্রকৃত প্রস্তুতি কীভাবে নিবো"),
        ("কেনো আমরা সুখ খুঁজি মন দিয়ে শুনুন ডা শামসুল আরেফিন", "কেনো আমরা সুখ খুঁজি"),
        ("দ্বীনে ফেরার গল্প Samsul Arefin Shakti", "দ্বীনে ফেরার গল্প"),
        ("দাসপ্রথা সম্পর্কে (দ্বিতীয় বক্তব্য).mp4", "দাসপ্রথা সম্পর্কে (দ্বিতীয় বক্তব্য)"),
        ("ধর্ষণের মানসাঙ্ক @MessageOfDeen", "ধর্ষণের মানসাঙ্ক"),
        ("মুমিনের জীবনের লক্ষ্য কেমন হওয়া উচিত ?? //", "মুমিনের জীবনের লক্ষ্য কেমন হওয়া উচিত ??"),
        ("জীবন নিয়ে এভাবে কখনো ভেবেছেন? I I বগুড়া হালাকাহ", "জীবন নিয়ে এভাবে কখনো ভেবেছেন?"),
        ("করোনা ভাইরাস এর চেয়ে মুসলমান দের বড় ভাইরাস কি? মাওঃ", "করোনা ভাইরাস এর চেয়ে মুসলমান দের বড় ভাইরাস কি?"),
        ("ইসলাম কি ধর্ম? কেমন ধর্ম? [ডা . ]", "ইসলাম কি ধর্ম? কেমন ধর্ম?"),
    ],
)
def test_clean_title_reupload_noise(title, expected):
    assert bangla.clean_title(title, SHAKTI) == expected


def test_fake_danda_is_fixed():
    assert bangla.clean_title("জীবন ও আখিরাত নিয়ে আলোচনা৷ ৷") == "জীবন ও আখিরাত নিয়ে আলোচনা"


def test_partial_name_never_cuts_a_word():
    # "শামসুল আরেফ" is a search fragment; it must not leave "ীন" behind.
    assert bangla.clean_title("শামসুল আরেফীন উজ্জল স্যারের চিকিৎসা পরামর্শ", ["শামসুল আরেফ"]) == (
        "শামসুল আরেফীন উজ্জল স্যারের চিকিৎসা পরামর্শ"
    )


@pytest.mark.parametrize(
    ("title", "part"),
    [("মানুষের সংজ্ঞা: ২য় পর্ব", 2), ("Part 3", 3), ("মুমিনের ক্যারিয়ার ভাবনা পর্ব_২", 2), ("পর্ব-১০", 10), ("সাধারণ আলোচনা", None)],
)
def test_part_number(title, part):
    assert bangla.part_number(title) == part


def test_clean_title_keeps_original_when_nothing_is_left():
    assert bangla.clean_title("মুফতি হারুন ইজহার", HARUN) == "মুফতি হারুন ইজহার"


def test_similarity_of_reuploads():
    assert bangla.similarity("জীবনের লক্ষ্য ঠিক করো", "জীবনের লক্ষ্য ঠিক করো!") > 0.9
    assert bangla.similarity("সালাতে খুশু", "রমজানের প্রস্তুতি") < 0.5


CATEGORIES = ["salah", "sawm", "family", "history", "akhirah", "halal-rizq", "contemporary", "tazkiyah"]


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("নামাজের গুরুত্ব", ["salah"]),
        ("রোজায় যা করবেন", ["sawm"]),
        ("বিয়ে ও দাম্পত্য জীবন", ["family"]),
        ("মুসলমানদের ভারত বিজয়ের ইতিহাস", ["history"]),
        ("সুদের ভয়াবহতা", ["halal-rizq"]),
        ("সুদর্শন চেহারা", ["contemporary"]),  # "সুদ" must not match inside another word
        ("কবরের আযাব ও তাওবা", ["akhirah", "tazkiyah"]),
    ],
)
def test_guess_categories(text, expected):
    assert bangla.guess_categories(text, CATEGORIES, ["contemporary"]) == expected


def test_guess_categories_only_returns_existing_ids():
    assert bangla.guess_categories("হজের প্রস্তুতি", ["salah"], ["salah"]) == ["salah"]


HARUN = ["মুফতি হারুন ইজহার", "Mufti Harun Izhar", "হারুন ইজহার"]


@pytest.mark.parametrize(
    "title, expected",
    [
        ("অনলাইনকেন্দ্রিক দ্বীনি ভাইদের প্রতি বার্তা। উস্তায হারুন ইজহার (ফাক্কাল্লাহু আসরহ)", "অনলাইনকেন্দ্রিক দ্বীনি ভাইদের প্রতি বার্তা"),
        ("শবে মেরাজ ও প্রাসঙ্গিক আলাপ । শাইখ হারুন ইজহার ফাক্কাল্লাহু আসরহ।", "শবে মেরাজ ও প্রাসঙ্গিক আলাপ"),
        ("বাড়াবাড়ি, ছাড়াছাড়ি নিয়ে আলোচনা ~ শাইখুনা হারুন ইজহার হাফিযাহুল্লাহ", "বাড়াবাড়ি, ছাড়াছাড়ি নিয়ে আলোচনা"),
    ],
)
def test_clean_title_release_prayer_and_shaykhuna(title, expected):
    assert bangla.clean_title(title, HARUN) == expected


def test_clean_title_drops_a_leading_visarga_colon():
    title = "আসিফ আদনানের নতুন লেকচারঃ দ্বীন কায়িমের দ্বায়িত্ব কাদের?"
    assert bangla.clean_title(title, ["আসিফ আদনান"]) == "দ্বীন কায়িমের দ্বায়িত্ব কাদের?"
