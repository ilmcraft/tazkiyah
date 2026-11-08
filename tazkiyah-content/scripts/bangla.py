"""Bengali text helpers shared by the content scripts.

`normalize` is a line-for-line port of the app's BanglaNormalizer.kt
(tazkiyah-android/.../core/util/BanglaNormalizer.kt), so the bot compares titles exactly the way
the app searches them. `clean_title` turns a YouTube upload title into an app title; a person
still reviews every title in the bot's pull request. Only the standard library is used.
"""
from __future__ import annotations

import re
import unicodedata
from difflib import SequenceMatcher
from typing import Iterable

_INVISIBLE = re.compile("[\u200B\u200C\u200D\u2060\uFEFF\u00AD]")
_WORD_SPLIT = re.compile(r"[^\w\u0980-\u09FF]+")
_WHITESPACE = re.compile(r"\s+")
_BANGLA_DIGITS = str.maketrans("০১২৩৪৫৬৭৮৯", "0123456789")
_BENGALI_LETTER = re.compile("[ঀ-৿]")


def fold(text: str) -> str:
    """Unify encodings without changing case: NFC, khanda-ta, joiners, য়/ড়/ঢ়, digits."""
    s = unicodedata.normalize("NFC", text)
    # Legacy khanda-ta (TA + HASANTA + ZWJ) becomes U+09CE; must run before joiners are stripped.
    s = s.replace("\u09A4\u09CD\u200D", "\u09CE")
    s = _INVISIBLE.sub("", s)
    # NFC decomposes these three (composition exclusions); fold both spellings to one code point.
    s = s.replace("\u09AF\u09BC", "\u09DF").replace("\u09A1\u09BC", "\u09DC").replace("\u09A2\u09BC", "\u09DD")
    return s.translate(_BANGLA_DIGITS)


def canonical(text: str) -> str:
    """Standard stored form: NFC (so য়/ড়/ঢ় stay letter + nukta), no joiners, single spaces.

    Unlike fold(), digits and case are kept — this is what goes into the catalog.
    """
    s = unicodedata.normalize("NFC", text).replace("ত্\u200D", "\u09CE")
    return _WHITESPACE.sub(" ", _INVISIBLE.sub("", s)).strip()


def normalize(text: str) -> str:
    """Same result as BanglaNormalizer.normalize() in the app."""
    return _WHITESPACE.sub(" ", fold(text).lower()).strip()


def contains_any(text: str, needles: Iterable[str]) -> bool:
    haystack = normalize(text)
    return any(normalize(n) in haystack for n in needles if n.strip())


_PART = re.compile(
    fold(r"(?:পর্ব|পার্ট|খণ্ড|part|episode|ep\.?)\s*[-:–#_]?\s*([0-9০-৯]+)|([0-9০-৯]+)\s*(?:ম|য়|য়|র্থ|ষ্ঠ|তম)?\s*(?:পর্ব|খণ্ড)"),
    re.IGNORECASE,
)


def part_number(title: str) -> int | None:
    """"পর্ব ২", "২য় পর্ব", "Part 2" -> 2; None when the title names no part."""
    match = _PART.search(fold(title))
    if not match:
        return None
    return int(match.group(1) or match.group(2))


_COMMON_WORDS = {
    normalize(w) for w in (
        "ও এর কি কী কেন কেমন কিভাবে কীভাবে যে যা এই সেই আমাদের আমরা তার তাদের জন্য থেকে নিয়ে সাথে এবং বা না "
        "নয় হবে হয় করে করা করতে আলোচনা লেকচার বক্তব্য ওয়াজ পর্ব জীবন কথা কিছু যেভাবে সবার আপনার আপনি "
        "গুরুত্বপূর্ণ সেরা নতুন মাস বিষয়ে সম্পর্কে the of and in a to lecture"
    ).split()
}


def shared_topic_words(a: str, b: str) -> set[str]:
    """Meaningful words (4+ letters, not filler) that both titles use."""
    def words(t: str) -> set[str]:
        return {w for w in _WORD_SPLIT.split(normalize(t)) if len(w) >= 4 and w not in _COMMON_WORDS}
    return words(a) & words(b)


def similarity(a: str, b: str) -> float:
    """0..1 similarity of two titles after normalisation."""
    na, nb = normalize(a), normalize(b)
    if not na or not nb:
        return 0.0
    return SequenceMatcher(None, na, nb).ratio()


# ---------------------------------------------------------------------------
# Title cleaning
# ---------------------------------------------------------------------------

_SEPARATORS = re.compile(r"\s*(?:\|\|?|｜|‖|::|॥|।\s*।|\s।\s|\s[-–—~]\s|\s[Il]\s[Il]\s)\s*")
_HASHTAG = re.compile(r"#\S+")
_BRACKETED_NOISE = re.compile(
    r"[\[(【](?:[^\])】]*?(?:official|full|hd|4k|new|waz|lecture|video|ভিডিও|ওয়াজ|লেকচার)[^\])】]*?)[\])】]",
    re.IGNORECASE,
)
# Generic words that are not a topic on their own.
_GENERIC = {
    "ওয়াজ", "নতুন ওয়াজ", "বাংলা ওয়াজ", "লেকচার", "বয়ান", "আলোচনা", "ওয়াজ মাহফিল", "মাহফিল",
    "waz", "new waz", "bangla waz", "full waz", "lecture", "full lecture", "bayan", "official",
    "full video", "ফুল ভিডিও", "podcast", "পডকাস্ট",
}
_YEAR = r"(?:19|20|১৯|২০)[0-9০-৯]{2}"
_GENERIC_NORMALIZED: set[str] = set()  # filled below, once normalize() exists
_NOISE_PATTERNS = [
    rf"\b(?:new|full|bangla|best)\s+(?:waz|lecture|bayan)(?:\s+{_YEAR})?\b",
    rf"(?:নতুন|বাংলা|সেরা|সম্পূর্ণ)\s+(?:ওয়াজ|লেকচার|বয়ান)(?:\s+{_YEAR})?",
    rf"ওয়াজ\s+{_YEAR}",
    r"\b(?:official|hd|4k|full\s+video|must\s+watch|viral)\b",
    r"(?:ফুল ভিডিও|অবশ্যই দেখুন|ভাইরাল)",
    r"\.(?:mp4|mkv|webm|avi)\b",  # file names used as titles
    r"@[\w.\-]+",  # @channel handles
    r"/{2,}",
]
# Sensational wording replaced by a neutral form (README: titles stay calm and factual).
_SENSATIONAL = [
    (r"কড়া\s+জবাব", "জবাব"),
    (r"দাঁতভাঙা\s+জবাব", "জবাব"),
    (r"উচিত\s+জবাব", "জবাব"),
    (r"হুংকার", ""),
    (r"তোলপাড়", ""),
    (r"ধুয়ে\s+দিলেন", ""),
    (r"মন\s+দিয়ে\s+শুনুন", ""),
    (r"(?:সবাই|অবশ্যই)\s+শুনুন", ""),
    (r"একবার\s+শুনে\s+দেখুন", ""),
    (r"অসাধারণ\s+(?:আলোচনা|বয়ান|লেকচার)", ""),
]
_HONORIFIC = r"(?:মুফতি|মুফতী|ডাঃ|ডা\s?\.?|ড\.|ডঃ|ডক্টর|শায়খুনা|শাইখুনা|শায়খ|শাইখ|উস্তাযুনা|উস্তাদ|উস্তায|মাওলানা|মাওঃ|mufti|dr\.?|shaykh|sheikh|ustadh)"
# An honorific left on its own ("[ডা . ]", "মাওঃ") once the name was removed.
_LONE_HONORIFIC = re.compile(r"[(\[]?\s*(?:ডাঃ|ডা\s?\.|ড\.|মাওঃ|ডঃ)\s*[)\]]?(?![\w\u0980-\u09FF])")
_NAME_SUFFIX = r"(?:\s*(?:চৌধুরী|চৌধুরি|chowdhury|ভাই|bhai))?(?:য়ের|এর|ের|র|'s)?"
_WORD_CHAR = "\\w\u0980-\u09FF"
# "হাফিঃ", "(হাফি.)", "হাফিযাহুল্লাহ" — the blessing after a name, left behind once the name is gone.
_BLESSING = re.compile(r"[(\[]?\s*হাফি(?:যাহুল্লাহ|জাহুল্লাহ|যাহুল্লা|জাহুল্লা|ঃ|\.|:)?\s*[)\]]?(?![\w\u0980-\u09FF])")
# "(ফাক্কাল্লাহু আসরাহ)" — "may Allah free him", said of an imprisoned scholar; also left behind with the name.
_RELEASE_PRAYER = re.compile(r"[(\[]?\s*ফা(?:ক্কাল্লাহু|ক্কাল্লাহ)\s+আস(?:রাহু|রাহ|রহু|রহ)\s*[)\]]?")
# A Latin tail after Bengali text is almost always the English duplicate of the title.
_LATIN_TAIL = re.compile(r"(?<=[\u0980-\u09FF?!।\])])\s+(?:[\[(]\s*[\])]\s*)*[A-Za-z][A-Za-z0-9 .:,'’&()\[\]_\-]*$")
_EDGE_PUNCT = " \t-–—|:;,.।·•*_~'\"“”‘’ঃ"


_HONORIFIC_WORDS = {"মুফতি", "মুফতী", "ডা.", "ডাঃ", "ডা", "ড.", "ডক্টর", "শায়খ", "শাইখ", "শায়খুনা", "শাইখুনা", "উস্তাদ", "মাওলানা", "mufti", "dr.", "dr", "shaykh", "sheikh"}


def name_variants(names: Iterable[str]) -> set[str]:
    """Each name plus the name without its last word ("শামসুল আরেফীন শক্তি" -> "শামসুল আরেফীন"),
    when at least two real name words remain — titles often drop the nickname."""
    out: set[str] = set()
    for name in names:
        name = canonical(name)
        if not name:
            continue
        out.add(name)
        words = name.split(" ")
        while words and words[0].lower() in _HONORIFIC_WORDS:  # the honorific is matched separately
            words = words[1:]
        for form in (words, words[:-1]):
            if len(form) >= 2:
                out.add(" ".join(form))
    return out


def _name_pattern(names: Iterable[str]) -> re.Pattern[str] | None:
    variants = sorted(name_variants(names), key=len, reverse=True)
    if not variants:
        return None
    alternatives = "|".join(re.escape(v).replace(r"\ ", r"\s+") for v in variants)
    # Whole words only: "শামসুল আরেফ" must not eat the start of "শামসুল আরেফীন".
    honorific, suffix = canonical(_HONORIFIC), canonical(_NAME_SUFFIX)
    return re.compile(
        rf"(?<![{_WORD_CHAR}])(?:{honorific}\s*)?(?:{alternatives}){suffix}(?![{_WORD_CHAR}])", re.IGNORECASE
    )


_GENERIC_NORMALIZED.update(normalize(g) for g in _GENERIC)


def _bengali_letters(text: str) -> int:
    return len(_BENGALI_LETTER.findall(text))


def _tidy(text: str) -> str:
    text = text.replace("\u09F7", "।")  # "৷" (a currency sign) typed as a danda
    text = re.sub(r"।(\s*।)+", "।", text)
    text = re.sub(r"[\[(【]\s*[\])】]", "", text)
    text = _WHITESPACE.sub(" ", text).strip()
    return text.strip(_EDGE_PUNCT).strip()


def _is_generic(segment: str) -> bool:
    plain = normalize(segment).strip(_EDGE_PUNCT + "!?")
    return not plain or plain in _GENERIC_NORMALIZED or len(plain) < 3


def clean_title(title: str, names: Iterable[str] = (), channel: str | None = None) -> str:
    """A calm app title from a YouTube upload title.

    Removes the speaker's name (with honorifics and possessive endings), the channel name,
    hashtags, emojis, "| channel |" segments, "new waz 2025"-style noise and sensational words,
    then keeps the segment with the most Bengali text. Falls back to the original title.
    """
    original = canonical(title)
    text = _HASHTAG.sub(" ", original)
    text = "".join(ch for ch in text if unicodedata.category(ch) not in {"So", "Sk", "Cs", "Co"} and ch != "️")
    text = _BRACKETED_NOISE.sub(" ", text)
    names_re = _name_pattern(names)
    if names_re is not None:
        text = names_re.sub(" ", text)
    if channel and channel.strip():
        text = re.sub(re.escape(canonical(channel)), " ", text, flags=re.IGNORECASE)
    for pattern in _NOISE_PATTERNS:
        text = re.sub(canonical(pattern), " ", text, flags=re.IGNORECASE)
    for pattern, replacement in _SENSATIONAL:
        text = re.sub(canonical(pattern), canonical(replacement), text)
    text = _RELEASE_PRAYER.sub(" ", text)
    text = _BLESSING.sub(" ", text)
    text = _LONE_HONORIFIC.sub(" ", text)
    text = re.sub(r"(?<=\S)_(?=\s|$)", "?", text).replace("_", " ")  # re-uploads turn "?" into "_"

    segments = [_tidy(s) for s in _SEPARATORS.split(text)]
    segments = [s for s in segments if not _is_generic(s)]
    if not segments:
        return _tidy(original) or title.strip()
    segments = [_tidy(_LATIN_TAIL.sub("", s)) if _bengali_letters(s) else s for s in segments]
    segments = [s for s in segments if not _is_generic(s)] or segments
    best = max(segments, key=lambda s: (_bengali_letters(s), len(s)))
    # Keep the first such segment when two tie (the title's lead is usually the topic).
    for s in segments:
        if (_bengali_letters(s), len(s)) == (_bengali_letters(best), len(best)):
            best = s
            break
    return best


# ---------------------------------------------------------------------------
# Categories
# ---------------------------------------------------------------------------

CATEGORY_KEYWORDS: list[tuple[str, tuple[str, ...]]] = [
    ("quran-tafsir", ("তাফসীর", "তাফসির", "tafsir", "কুরআন", "কোরআন", "quran", "সূরা", "সুরা")),
    ("hadith", ("হাদিস", "হাদীস", "hadith")),
    ("seerah", ("সীরাত", "সিরাত", "নবীজি", "রাসূল", "রাসুল", "seerah", "sirah")),
    ("aqidah", ("আকীদা", "আকিদা", "তাওহীদ", "তাওহিদ", "শিরক", "ঈমান", "aqidah", "tawheed")),
    ("salah", ("নামাজ", "নামায", "সালাত", "salah", "namaz")),
    ("sawm", ("রোজা", "রোযা", "সাওম", "রমজান", "রমাদান", "ramadan")),
    ("zakat", ("যাকাত", "জাকাত", "zakat")),
    ("hajj", ("হজ", "হজ্জ", "উমরা", "hajj")),
    ("family", ("বিয়ে", "বিবাহ", "দাম্পত্য", "স্বামী", "স্ত্রী", "পরিবার", "marriage", "family")),
    ("parenting", ("সন্তান", "প্যারেন্টিং", "parenting", "শিশু")),
    ("akhirah", ("মৃত্যু", "কবর", "আখিরাত", "আখেরাত", "জান্নাত", "জাহান্নাম", "কিয়ামত", "কেয়ামত")),
    ("tazkiyah", ("তাওবা", "তওবা", "আত্মশুদ্ধি", "তাযকিয়া", "তাজকিয়া", "গুনাহ", "গোনাহ")),
    ("akhlaq", ("আখলাক", "চরিত্র", "আদব")),
    ("dua-dhikr", ("দোয়া", "দুআ", "দোআ", "যিকির", "জিকির", "dua", "dhikr")),
    ("halal-rizq", ("হালাল", "রিযিক", "রিজিক", "সুদ", "ব্যবসা", "rizq")),
    ("history", ("ইতিহাস", "history", "খিলাফত", "সালতানাত")),
    ("dawah", ("দাওয়াহ", "দাওয়াত", "দাওয়া", "dawah")),
    ("youth", ("তরুণ", "তরুণদের", "যুবক", "যুবসমাজ", "youth", "gen z", "জেন-জি", "জেন জি", "ক্যাম্পাস")),
    ("contemporary", (
        "পশ্চিমা", "পশ্চিমের", "সেক্যুলার", "সেকুলার", "নারীবাদ", "ট্রান্সজেন্ডার", "ট্রান্স", "সমকাম", "সমকামিতা",
        "গণতন্ত্র", "গণতান্ত্রিক", "মিডিয়া", "জায়নবাদ", "শাহবাগ", "নাস্তিক", "নাস্তিকতা", "আধুনিকতা", "পুঁজিবাদ",
        "উপনিবেশ", "রাষ্ট্র", "রাজনীতি", "lgbt", "lgbtq", "feminism", "secular",
    )),
]

# A keyword matches a whole word, or the word plus one of these endings ("নামাজের", "রোজায়").
_SUFFIXES = ("", "ের", "এর", "র", "য়ের", "য়", "ে", "তে", "দের", "গুলো", "সমূহ", "s")


def _has_keyword(tokens: list[str], joined: str, keyword: str) -> bool:
    nk = normalize(keyword)
    if " " in nk or "-" in nk:
        return f" {nk} " in joined
    endings = {normalize(nk + s) for s in _SUFFIXES}
    return any(t in endings for t in tokens)


def guess_categories(text: str, allowed: Iterable[str], fallback: Iterable[str], limit: int = 2) -> list[str]:
    """Up to `limit` category ids whose keywords appear in `text`; else the speaker's defaults."""
    allowed_set = set(allowed)
    tokens = [t for t in _WORD_SPLIT.split(normalize(text)) if t]
    joined = f" {' '.join(tokens)} "
    found = [
        cat for cat, words in CATEGORY_KEYWORDS
        if cat in allowed_set and any(_has_keyword(tokens, joined, w) for w in words)
    ]
    if found:
        return found[:limit]
    return [c for c in fallback if c in allowed_set] or list(fallback)[:1]
