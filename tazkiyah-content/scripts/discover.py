#!/usr/bin/env python3
"""Find a speaker's new YouTube lectures and add them to the catalog for review.

Run weekly by .github/workflows/discover-lectures.yml for every speaker at once (`sweep --all`),
which then opens one pull request; nothing reaches the app until a person merges it.

    python scripts/discover.py pick [--speaker speaker-002] [--date 2026-11-08]
    python scripts/discover.py sweep --all [--backfill] [--search-budget 90] [--report r.md] [--dry-run]
    python scripts/discover.py sweep --speaker speaker-002 [--backfill] [--search-budget 90] [--report r.md] [--dry-run]
    python scripts/discover.py import --speaker speaker-002 --file research.md [--report r.md] [--dry-run]
    python scripts/discover.py prompt --speaker speaker-002 [--out prompt.md]

sweep   reads the speaker's channels and playlists (discovery/sources.json) completely and runs
        every search query, within the free daily quota. Searches only look at videos uploaded
        since the last search (two weeks of overlap); --backfill searches all of YouTube's history.
import  takes the YouTube links from a research result (see `prompt`) through the same checks.

Lectures are only ever added from the channels and playlists listed in discovery/sources.json.
A search or research hit on any other channel is never added: it is recorded as
"unlisted-channel" and the report lists those channels, so a person can approve a channel once
(add it to the speaker's `channels`) or refuse it (`excludeChannels`).
prompt  prints the deep-research prompt for one speaker, with the lectures already in the catalog.

Every candidate goes through the same checks: not already in the catalog or in
discovery/seen/<speaker>.json, public, embeddable, not blocked in Bangladesh, not live, at least
5 minutes long, not a namesake, and the speaker's name in the title (official channels and
trusted playlists excepted). Likely re-uploads of a lecture that is already there (similar title
and the same length) are listed but not added. Needs YOUTUBE_API_KEY except for `pick`/`prompt`.
Only the Python standard library is used.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata
from dataclasses import dataclass, field
from datetime import date, timedelta
from pathlib import Path
from typing import Any, Iterable

sys.path.insert(0, str(Path(__file__).resolve().parent))

from add_lecture import (  # noqa: E402
    AddLectureError,
    _all_lectures,
    _read_json,
    _write_atomic,
    format_lecture_file,
    next_lecture_id,
)
from bangla import (  # noqa: E402
    clean_title,
    contains_any,
    guess_categories,
    normalize,
    part_number,
    shared_topic_words,
    similarity,
)
from youtube_api import QuotaExceeded, YouTubeApiError, YouTubeClient, parse_duration, uploads_playlist  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parent.parent
SOURCES = "discovery/sources.json"
SEEN_DIR = "discovery/seen"
PROMPT_TEMPLATE = "discovery/research-prompt.md"
SPEAKERS = "data/speakers.json"
CATEGORIES = "data/categories.json"
LECTURES_DIR = "data/lectures"

DEFAULT_SEARCH_BUDGET = 90  # of the 100 free search calls a day; the rest is for manual runs
# Shorts are at most 3 minutes. Other channels mostly post clips cut from longer talks, so they need
# 10 minutes; the speaker's own channel and trusted playlists keep short talks from 5 minutes.
DEFAULT_MIN_DURATION = 900  # other channels: shorter uploads are nearly always clips cut from a talk
TRUSTED_MIN_DURATION = 300
CLIP_HINT_SEC = 900
DUPLICATE_SIMILARITY = 0.8
SAME_LENGTH_SIMILARITY = 0.45
# Videos about a course, a book or an event rather than a talk.
NOT_LECTURES = ("intro video", "ইন্ট্রো ভিডিও", "trailer", "ট্রেলার", "promo", "প্রোমো", "teaser", "টিজার")
# Re-cut "motivation" uploads from other channels: reactions and hype titles, never a whole talk.
CLICKBAIT = (
    "reaction", "রিঅ্যাকশন", "shocking", "চমকে যাবেন", "নাড়িয়ে দিবে", "নাড়িয়ে দেবে", "সেরা লেকচার",
    "viral", "ভাইরাল", "কেউ বলেনি",
)
CLICKBAIT_EMOJI_MAX_SEC = 1500  # an emoji-decorated title on an upload under 25 minutes is a cut clip
# Hype on a short upload from another channel means a cut-out "best part"; on a whole talk it is only a bad title.
HYPE = (
    "সেরা", "শুনেননি", "মূল্যবান", "হীরার", "হৃদয় ছুঁয়ে", "জীবন পরিবর্তন", "জীবন বদলে", "জীবন পাল্টে",
    "কলিজায়", "অগ্নিঝরা", "কঠিন বক্তব্য", "মুখ খুল", "একবার হলেও", "মন কাড়বে", "!!", "‼",
)
# Searches look this far back before the last search, so a late-indexed upload is not missed.
SEARCH_OVERLAP_DAYS = 14
SAME_LENGTH_DAYS = 120  # equal length (±2 s) uploaded this close together: the same recording
SPEAKER_ID_RE = re.compile(r"^speaker-[0-9]{3,}$")
VIDEO_URL_RE = re.compile(r"(?:[?&]v=|youtu\.be/|/shorts/|/live/|/embed/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])")

DROP_REASONS = {
    "unavailable": "মুছে ফেলা, প্রাইভেট বা প্রসেস হয়নি",
    "not_embeddable": "অ্যাপে চালানো যায় না (এমবেড বন্ধ / বয়সসীমা)",
    "region": "বাংলাদেশে ব্লক",
    "live": "লাইভ বা আসন্ন",
    "short": "খুব ছোট (Shorts / ক্লিপ)",
    "namesake": "অন্য ব্যক্তি (একই নাম) বা অডিওবুক",
    "not_lecture": "লেকচার নয় (ইন্ট্রো, ট্রেলার, প্রোমো)",
    "clickbait": "রি-কাট/হাইপ ক্লিপ বা বাদ দেওয়া চ্যানেল",
    "no_topic": "শিরোনামে শুধু বক্তার নাম, বিষয় নেই",
    "no_name": "শিরোনামে বক্তার নাম নেই",
}


class DiscoverError(Exception):
    """A user-facing error; main() prints it and exits with status 1."""


@dataclass
class Candidate:
    video_id: str
    sources: list[str] = field(default_factory=list)
    trusted: bool = False
    listed: bool = False  # found in a channel or playlist of sources.json, not only by a search
    title: str = ""
    channel: str = ""
    channel_id: str = ""
    description: str = ""
    published: str | None = None
    duration: int = 0
    app_title: str = ""
    categories: list[str] = field(default_factory=list)
    flags: list[str] = field(default_factory=list)
    decision: str = ""
    duplicate_of: str | None = None
    lecture_id: str | None = None


@dataclass
class Result:
    speaker_id: str
    speaker_name: str
    mode: str
    order: str | None
    added: list[Candidate]
    duplicates: list[Candidate]
    low_confidence: list[Candidate]
    dropped: dict[str, int]
    looked_at: int
    already_known: int
    units: int
    searches: int
    files_changed: list[str]
    search_cut: bool = False  # the daily search quota ran out part-way; channels and playlists were still read
    unlisted: list[Candidate] = field(default_factory=list)  # good matches on channels not in sources.json
    since: str | None = None  # searches were limited to uploads from this day on (None: no limit)

    @property
    def listed(self) -> int:
        return len(self.duplicates) + len(self.low_confidence) + len(self.unlisted)


# ---------------------------------------------------------------------------
# Loading
# ---------------------------------------------------------------------------

def _load(root: Path, rel: str) -> Any:
    try:
        return _read_json(root / rel, rel)
    except AddLectureError as exc:
        raise DiscoverError(str(exc)) from exc


def load_sources(root: Path) -> list[dict[str, Any]]:
    doc = _load(root, SOURCES)
    speakers = doc.get("speakers") if isinstance(doc, dict) else None
    if not isinstance(speakers, list) or not speakers:
        raise DiscoverError(f"{SOURCES} has no speakers")
    return speakers


def source_for(root: Path, speaker_id: str) -> dict[str, Any]:
    """The speaker's entry, with the file-wide `excludeChannels` added to its own."""
    doc = _load(root, SOURCES)
    shared = doc.get("excludeChannels", []) if isinstance(doc, dict) else []
    for entry in load_sources(root):
        if entry.get("speakerId") == speaker_id:
            return {**entry, "excludeChannels": [*shared, *entry.get("excludeChannels", [])]}
    raise DiscoverError(f"{speaker_id} is not in {SOURCES}")


def channel_excluded(c: Candidate, config: dict[str, Any]) -> bool:
    """A re-cut or compilation channel listed in excludeChannels (by name or channel id)."""
    names = {normalize(x) for x in config.get("excludeChannels", [])}
    return bool(names) and (normalize(c.channel) in names or normalize(c.channel_id) in names)


def speaker_info(root: Path, speaker_id: str) -> dict[str, Any]:
    for sp in _load(root, SPEAKERS).get("speakers", []):
        if sp.get("id") == speaker_id:
            return sp
    raise DiscoverError(f"{speaker_id} is not in {SPEAKERS}")


def seen_path(root: Path, speaker_id: str) -> Path:
    return root / SEEN_DIR / f"{speaker_id}.json"


def load_seen(root: Path, speaker_id: str) -> dict[str, Any]:
    path = seen_path(root, speaker_id)
    if not path.exists():
        return {"speakerId": speaker_id, "videos": {}}
    return _load(root, f"{SEEN_DIR}/{speaker_id}.json")


def save_seen(root: Path, doc: dict[str, Any]) -> None:
    ordered = {"speakerId": doc["speakerId"]}
    if doc.get("lastSearch"):
        ordered["lastSearch"] = doc["lastSearch"]
    ordered["videos"] = dict(sorted(doc["videos"].items()))
    doc = ordered
    _write_atomic(seen_path(root, doc["speakerId"]), json.dumps(doc, ensure_ascii=False, indent=2) + "\n")


# ---------------------------------------------------------------------------
# Picking today's speaker
# ---------------------------------------------------------------------------

def rotation_speaker(sources: list[dict[str, Any]], today: date) -> str:
    """One speaker per day, in sources.json order; needs no stored state."""
    return sources[today.toordinal() % len(sources)]["speakerId"]


def search_order(today: date) -> str:
    """Alternate weekly: newest uploads one week, YouTube's relevance ranking (older talks) the next."""
    return "date" if (today.toordinal() // 7) % 2 == 0 else "relevance"


def search_since(seen: dict[str, Any]) -> str | None:
    """The day searches start from: the last search (else the newest entry in `seen`) minus the
    overlap. None for a speaker that was never swept, whose first search has no limit."""
    last = seen.get("lastSearch") or max((v.get("date", "") for v in seen["videos"].values()), default="")
    try:
        return (date.fromisoformat(last) - timedelta(days=SEARCH_OVERLAP_DAYS)).isoformat()
    except ValueError:
        return None


def pick_speaker(root: Path, requested: str | None, today: date) -> str:
    sources = load_sources(root)
    if requested:
        requested = requested.strip()
        if not SPEAKER_ID_RE.match(requested):
            raise DiscoverError(f"speaker id must look like 'speaker-001', got {requested!r}")
        if requested not in {s["speakerId"] for s in sources}:
            raise DiscoverError(f"{requested} is not in {SOURCES}")
        return requested
    return rotation_speaker(sources, today)


# ---------------------------------------------------------------------------
# Checking candidates
# ---------------------------------------------------------------------------

def classify(c: Candidate, item: dict[str, Any] | None, config: dict[str, Any], official_ids: set[str]) -> None:
    if item is None:
        c.decision = "unavailable"
        return
    snippet, details, status = item.get("snippet", {}), item.get("contentDetails", {}), item.get("status", {})
    c.title = snippet.get("title", "")
    c.channel = snippet.get("channelTitle", "")
    c.channel_id = snippet.get("channelId", "")
    c.description = snippet.get("description", "")
    c.published = (snippet.get("publishedAt") or "")[:10] or None
    c.duration = parse_duration(details.get("duration"))
    if c.channel_id in official_ids:
        c.trusted = True

    region = details.get("regionRestriction") or {}
    min_duration = TRUSTED_MIN_DURATION if c.trusted else config.get("minDurationSec", DEFAULT_MIN_DURATION)
    if status.get("privacyStatus") != "public" or status.get("uploadStatus", "processed") != "processed":
        c.decision = "unavailable"
    elif not status.get("embeddable", True) or (details.get("contentRating") or {}).get("ytRating") == "ytAgeRestricted":
        c.decision = "not_embeddable"
    elif "BD" in region.get("blocked", []) or ("allowed" in region and "BD" not in region["allowed"]):
        c.decision = "region"
    elif snippet.get("liveBroadcastContent", "none") != "none":
        c.decision = "live"
    elif c.duration < min_duration:
        c.decision = "short"
    elif config.get("exclude") and contains_any(f"{c.title} {c.channel}", config["exclude"]):
        c.decision = "namesake"
    elif contains_any(c.title, NOT_LECTURES):
        c.decision = "not_lecture"
    elif not c.trusted and (channel_excluded(c, config) or looks_clickbait(c.title, c.duration)):
        c.decision = "clickbait"
    elif c.trusted or contains_any(c.title, config["mustMatch"]):
        c.decision = "add"
    elif contains_any(c.description, config["mustMatch"]):
        c.decision = "low-confidence"
    else:
        c.decision = "no_name"
    if c.decision == "add" and c.duration < CLIP_HINT_SEC:
        c.flags.append("ক্লিপ হতে পারে (১৫ মিনিটের কম)")


def looks_clickbait(title: str, duration: int) -> bool:
    """A hype or reaction re-cut: a clickbait phrase, or emojis/hype on an upload too short to be a talk."""
    if contains_any(title, CLICKBAIT):
        return True
    short = 0 < duration < CLICKBAIT_EMOJI_MAX_SEC
    has_emoji = any(unicodedata.category(ch) == "So" for ch in title)
    return short and (has_emoji or contains_any(title, HYPE))


def _days_apart(a: str | None, b: str | None) -> int | None:
    try:
        return abs((date.fromisoformat(a[:10]) - date.fromisoformat(b[:10])).days)  # type: ignore[index]
    except (TypeError, ValueError):
        return None


def same_lecture(
    title_a: str, duration_a: int, title_b: str, duration_b: int,
    published_a: str | None = None, published_b: str | None = None,
) -> bool:
    """Likely the same recording, re-uploaded.

    Re-uploads keep the exact length (re-encodes differ by a second or two) but often get a new
    title, so the same length needs only loosely similar titles — or none at all when both copies
    went up within a few months. A near-identical title allows a slightly trimmed copy. Different
    part numbers ("পর্ব ১" / "পর্ব ২") are never the same lecture.
    """
    if duration_a <= 0 or duration_b <= 0:
        return False
    part_a, part_b = part_number(title_a), part_number(title_b)
    if part_a is not None and part_b is not None and part_a != part_b:
        return False
    gap = abs(duration_a - duration_b)
    longest = max(duration_a, duration_b)
    sim = similarity(title_a, title_b)
    if gap <= 2 and sim >= SAME_LENGTH_SIMILARITY:
        return True
    days = _days_apart(published_a, published_b)
    if gap <= 2 and days is not None and days <= SAME_LENGTH_DAYS:
        return True
    if gap <= 3 and shared_topic_words(title_a, title_b):
        return True
    if gap <= max(3, round(0.01 * longest)) and sim >= DUPLICATE_SIMILARITY:
        return True
    return sim >= 0.92 and gap <= 0.1 * longest


def mark_duplicates(
    accepted: list[Candidate], existing: list[tuple[str, ...]]
) -> tuple[list[Candidate], list[Candidate]]:
    """(kept, duplicates). Official copies are preferred, then the oldest upload.

    `existing` holds (lecture id, title, length[, published date]) of the speaker's lectures."""
    kept: list[Candidate] = []
    duplicates: list[Candidate] = []
    for c in sorted(accepted, key=lambda x: (not x.trusted, x.published or "9999", x.video_id)):
        match = next(
            (e[0] for e in existing if same_lecture(c.app_title, c.duration, e[1], e[2], c.published, (e + (None,))[3])),
            None,
        )
        if match is None:
            other = next(
                (k for k in kept if same_lecture(c.app_title, c.duration, k.app_title, k.duration, c.published, k.published)),
                None,
            )
            match = other.video_id if other else None
        if match is None:
            kept.append(c)
        else:
            c.decision, c.duplicate_of = "duplicate", match
            duplicates.append(c)
    return kept, duplicates


# ---------------------------------------------------------------------------
# The run
# ---------------------------------------------------------------------------

def discover(
    root: Path,
    speaker_id: str,
    client: YouTubeClient,
    today: date,
    *,
    search_budget: int = DEFAULT_SEARCH_BUDGET,
    order: str | None = None,
    imported_ids: list[str] | None = None,
    dry_run: bool = False,
    backfill: bool = False,
) -> Result:
    config = source_for(root, speaker_id)
    speaker = speaker_info(root, speaker_id)
    category_ids = [c["id"] for c in _load(root, CATEGORIES).get("categories", [])]
    lectures = _all_lectures(root)
    seen = load_seen(root, speaker_id)
    official_ids = {ch["id"] for ch in config.get("channels", []) if ch.get("official")}
    listed_ids = {ch["id"] for ch in config.get("channels", [])}
    mode = "import" if imported_ids is not None else "sweep"
    # A scheduled sweep searches only what was uploaded since the last one, newest first; old talks are
    # searched for only on request (--backfill), in the weekly alternating order.
    since = None if backfill or mode == "import" else search_since(seen)
    order = order or ("date" if since else search_order(today))

    candidates: dict[str, Candidate] = {}
    search_cut = False
    searched_all = False

    def add(ids: Iterable[str], label: str, trusted: bool, listed: bool = False) -> None:
        for vid in ids:
            c = candidates.setdefault(vid, Candidate(vid))
            if label not in c.sources:
                c.sources.append(label)
            c.trusted = c.trusted or trusted
            c.listed = c.listed or listed

    if imported_ids is not None:
        add(imported_ids, "রিসার্চ ফাইল", trusted=False)
    else:
        for ch in config.get("channels", []):
            add(client.playlist_video_ids(uploads_playlist(ch["id"])), f"চ্যানেল: {ch['name']}", ch.get("official", False), listed=True)
        for pl in config.get("playlists", []):
            add(client.playlist_video_ids(pl["id"]), f"প্লেলিস্ট: {pl['name']}", pl.get("trusted", False), listed=True)
        queries = config.get("queries", [])
        for i, query in enumerate(queries):
            remaining = search_budget - client.usage.searches
            if remaining <= 0:
                break
            share = max(1, remaining // (len(queries) - i))
            try:
                add(client.search_video_ids(query, order, share, since), f"সার্চ: {query}", trusted=False)
            except QuotaExceeded:
                # The search bucket (100/day) is separate from the units pool: keep what the channels and
                # playlists found and finish the run; tomorrow's searches pick up the rest.
                search_cut = True
                break
        else:
            searched_all = True

    known = {
        lec.get("media", {}).get("videoId")
        for _, lec in lectures
        if isinstance(lec.get("media"), dict)
    }

    def undecided(c: Candidate) -> bool:
        """Not decided on before — or set aside for its channel, which has been listed since."""
        entry = seen["videos"].get(c.video_id)
        if entry is None:
            return True
        return entry.get("status") == "unlisted-channel" and (c.listed or entry.get("channelId") in listed_ids)

    fresh = [c for c in candidates.values() if c.video_id not in known and undecided(c)]
    details = client.videos(c.video_id for c in fresh) if fresh else {}

    names = [speaker["name"], speaker["nameEn"], *config.get("queries", []), *config.get("mustMatch", [])]
    dropped: dict[str, int] = {}
    accepted: list[Candidate] = []
    low: list[Candidate] = []
    unlisted: list[Candidate] = []
    for c in fresh:
        classify(c, details.get(c.video_id), config, official_ids)
        if c.decision in {"add", "low-confidence"} and not (c.listed or c.channel_id in listed_ids):
            # Only listed channels and playlists add lectures; a name match elsewhere is at most a
            # channel for a person to look at.
            c.decision = "unlisted-channel" if c.decision == "add" else "no_name"
        if c.decision in {"add", "low-confidence"}:
            c.app_title = clean_title(c.title, names, c.channel)
            name_only = contains_any(c.app_title, config["mustMatch"]) or (
                len(c.app_title.split()) < 2 and not re.search("[\u0980-\u09FF]", c.app_title)
            )
            if name_only and not c.trusted:
                c.decision = "no_topic"  # a re-upload titled only with the name says nothing about the talk
            elif name_only or len(c.app_title.split()) < 2:
                c.flags.append("শিরোনাম দেখুন")
            c.categories = guess_categories(
                f"{c.app_title} {c.title}", category_ids, config.get("defaultCategoryIds", [])
            )
        if c.decision == "add":
            accepted.append(c)
        elif c.decision == "low-confidence":
            low.append(c)
        elif c.decision == "unlisted-channel":
            unlisted.append(c)
        else:
            dropped[c.decision] = dropped.get(c.decision, 0) + 1

    # The speaker's existing lectures, with lengths, to catch re-uploads of the same talk.
    existing: list[tuple[str, str, int]] = []
    own = [lec for rel, lec in lectures if rel == f"{LECTURES_DIR}/{speaker_id}.json"]
    if accepted:
        yt = [lec for lec in own if lec.get("media", {}).get("type") == "youtube"]
        lengths = client.videos(lec["media"]["videoId"] for lec in yt) if yt else {}
        for lec in yt:
            item = lengths.get(lec["media"]["videoId"], {})
            duration = lec.get("durationSec") or parse_duration(item.get("contentDetails", {}).get("duration"))
            existing.append((lec["id"], lec["title"], duration, lec.get("publishedAt")))
    added, duplicates = mark_duplicates(accepted, existing)

    added.sort(key=lambda x: (x.published or "9999", x.video_id))
    all_ids = [lec.get("id") for _, lec in lectures]
    for c in added:
        c.lecture_id = next_lecture_id(speaker_id, all_ids)
        all_ids.append(c.lecture_id)

    files_changed: list[str] = []
    if not dry_run:
        stamp = today.isoformat()
        if added:
            rel = f"{LECTURES_DIR}/{speaker_id}.json"
            path = root / rel
            doc = _load(root, rel) if path.exists() else {"speakerId": speaker_id, "lectures": []}
            for c in added:
                entry: dict[str, Any] = {
                    "id": c.lecture_id,
                    "title": c.app_title,
                    "categoryIds": c.categories,
                    "media": {"type": "youtube", "videoId": c.video_id, "startSec": 0},
                    "durationSec": c.duration,
                }
                if c.published:
                    entry["publishedAt"] = c.published
                entry["addedAt"] = stamp
                doc["lectures"].append(entry)
            _write_atomic(path, format_lecture_file(doc))
            files_changed.append(rel)
        for c in added:
            seen["videos"][c.video_id] = {
                "status": "proposed", "date": stamp, "lectureId": c.lecture_id,
                "channelId": c.channel_id, "channel": c.channel,
            }
        for c in duplicates:
            seen["videos"][c.video_id] = {"status": "duplicate", "date": stamp, "note": f"একই লেকচার: {c.duplicate_of}"}
        for c in low:
            seen["videos"][c.video_id] = {"status": "low-confidence", "date": stamp, "note": "নাম শুধু বিবরণে"}
        for c in unlisted:
            seen["videos"][c.video_id] = {
                "status": "unlisted-channel", "date": stamp, "channelId": c.channel_id, "channel": c.channel,
            }
        if added or duplicates or low or unlisted:
            if mode == "sweep" and searched_all:
                seen["lastSearch"] = stamp
            save_seen(root, seen)
            files_changed.append(f"{SEEN_DIR}/{speaker_id}.json")

    return Result(
        speaker_id=speaker_id,
        speaker_name=speaker["name"],
        mode=mode,
        order=order if mode == "sweep" else None,
        added=added,
        duplicates=duplicates,
        low_confidence=low,
        dropped=dropped,
        looked_at=len(candidates),
        already_known=len(candidates) - len(fresh),
        units=client.usage.units,
        searches=client.usage.searches,
        files_changed=files_changed,
        search_cut=search_cut,
        unlisted=unlisted,
        since=since,
    )


def sweep_all(
    root: Path,
    client: YouTubeClient,
    today: date,
    *,
    search_budget: int = DEFAULT_SEARCH_BUDGET,
    order: str | None = None,
    dry_run: bool = False,
    backfill: bool = False,
) -> tuple[list[Result], str | None]:
    """Every speaker of sources.json in one run, the search budget shared equally between them.

    Returns the results and, when the quota ran out on a lookup, which speaker it stopped at
    (the speakers before it are complete and written)."""
    ids = [s["speakerId"] for s in load_sources(root)]
    results: list[Result] = []
    for i, speaker_id in enumerate(ids):
        units, searches = client.usage.units, client.usage.searches
        share = max(0, (search_budget - searches) // (len(ids) - i))
        try:
            result = discover(
                root, speaker_id, client, today,
                search_budget=searches + share, order=order, dry_run=dry_run, backfill=backfill,
            )
        except QuotaExceeded:
            return results, speaker_id
        result.units, result.searches = client.usage.units - units, client.usage.searches - searches
        results.append(result)
    return results, None


# ---------------------------------------------------------------------------
# Filling in lengths
# ---------------------------------------------------------------------------

def fill_durations(root: Path, client: YouTubeClient, dry_run: bool = False) -> tuple[int, int, list[str]]:
    """Adds durationSec (and publishedAt when missing) to every YouTube lecture that lacks it.

    Returns (missing, filled, changed files). One videos.list call per 50 lectures.
    """
    docs: dict[str, dict[str, Any]] = {}
    missing: dict[str, tuple[str, int]] = {}
    folder = root / LECTURES_DIR
    for path in sorted(folder.glob("*.json")):
        rel = f"{LECTURES_DIR}/{path.name}"
        docs[rel] = _load(root, rel)
        for i, lec in enumerate(docs[rel].get("lectures", [])):
            media = lec.get("media") or {}
            if media.get("type") == "youtube" and not lec.get("durationSec"):
                missing[media["videoId"]] = (rel, i)
    details = client.videos(missing) if missing else {}
    changed: set[str] = set()
    filled = 0
    for vid, (rel, i) in missing.items():
        item = details.get(vid)
        seconds = parse_duration((item or {}).get("contentDetails", {}).get("duration"))
        if seconds <= 0:
            continue  # gone, private or live: validate/the app handle it
        published = ((item or {}).get("snippet", {}).get("publishedAt") or "")[:10]
        lecture = docs[rel]["lectures"][i]
        updated: dict[str, Any] = {}
        for key, value in lecture.items():
            if key in {"durationSec"}:
                continue
            updated[key] = value
            if key == "media":
                updated["durationSec"] = seconds
                if published and "publishedAt" not in lecture:
                    updated["publishedAt"] = published
        docs[rel]["lectures"][i] = updated
        changed.add(rel)
        filled += 1
    if not dry_run:
        for rel in changed:
            _write_atomic(root / rel, format_lecture_file(docs[rel]))
    return len(missing), filled, sorted(changed)


# ---------------------------------------------------------------------------
# Report (pull request body)
# ---------------------------------------------------------------------------

BANGLA_DIGITS = str.maketrans("0123456789", "০১২৩৪৫৬৭৮৯")


def bn(value: Any) -> str:
    return str(value).translate(BANGLA_DIGITS)


def clock(seconds: int) -> str:
    h, rest = divmod(seconds, 3600)
    m, s = divmod(rest, 60)
    return bn(f"{h}:{m:02d}:{s:02d}" if h else f"{m}:{s:02d}")


def _cell(text: str) -> str:
    return text.replace("|", "\\|").replace("\n", " ").strip() or "—"


def render_all_report(results: list[Result], stopped_at: str | None, today: date) -> str:
    """One pull request text for a run over every speaker."""
    busy = [r for r in results if r.added or r.listed or r.dropped]
    quiet = [r for r in results if r not in busy]
    lines = [
        f"# নতুন লেকচার — {bn(sum(len(r.added) for r in results))}টি যোগ, {bn(len(results))} জন বক্তা",
        "",
        f"{bn(today.isoformat())} · কোটা: সার্চ {bn(sum(r.searches for r in results))}/১০০, "
        f"অন্যান্য {bn(sum(r.units for r in results))}/১০,০০০ ইউনিট",
        "",
    ]
    if stopped_at:
        lines += [f"> কোটা শেষ হয়ে যাওয়ায় `{stopped_at}` থেকে বাকি বক্তাদের দেখা হয়নি; পরের রানে দেখা হবে।", ""]
    for r in busy:
        lines += [render_report(r, today, footer=False), ""]
    if quiet:
        lines += ["**নতুন কিছু পাওয়া যায়নি:** " + ", ".join(r.speaker_name for r in quiet), ""]
    lines += _review_steps("speaker-00N", "এই PR খোলা থাকা অবস্থায় বট নতুন PR খুলবে না।")
    return "\n".join(lines) + "\n"


def _review_steps(speaker_id: str, closing: str) -> list[str]:
    return [
        "### কীভাবে রিভিউ করবেন",
        "",
        "1. **Files changed**-এ গিয়ে প্রতিটা লেকচারের শিরোনাম আর ক্যাটাগরি দেখে ঠিক করুন (✏️ Edit file)।",
        f"2. কোনো লেকচার না চাইলে `data/lectures/{speaker_id}.json` থেকে সেটা মুছে দিন, কিন্তু "
        f"`discovery/seen/{speaker_id}.json`-এর এন্ট্রি রেখে দিন — তাহলে বট আর কখনো সেটা আনবে না।",
        "3. **Merge** করলে publish workflow চলবে আর অ্যাপে আপডেট চলে যাবে।",
        "",
        f"_{closing}_",
    ]


def render_report(result: Result, today: date, footer: bool = True) -> str:
    r = result
    lines = [
        f"## নতুন লেকচার: {r.speaker_name} — {bn(len(r.added))}টি যোগ, {bn(r.listed)}টি শুধু তালিকায়",
        "",
        f"`{r.speaker_id}` · {bn(today.isoformat())} · "
        + (
            "রিসার্চ ফাইল থেকে আমদানি" if r.mode == "import"
            else "চ্যানেল, প্লেলিস্ট ও সার্চ ("
            + (f"{bn(r.since)} থেকে আপলোড হওয়া ভিডিও" if r.since else "সব সময়ের ভিডিও") + f", order: {r.order})"
        )
        + f" · দেখা হয়েছে {bn(r.looked_at)}টি ভিডিও, আগে থেকেই জানা {bn(r.already_known)}টি"
        + f" · কোটা: সার্চ {bn(r.searches)}/১০০, অন্যান্য {bn(r.units)}/১০,০০০ ইউনিট",
        "",
    ]
    if r.search_cut:
        lines += ["> আজকের সার্চ কোটা শেষ হয়ে যাওয়ায় সার্চ মাঝপথে থেমেছে; চ্যানেল ও প্লেলিস্ট পুরো দেখা হয়েছে।", ""]
    if r.added:
        lines += [
            "### যোগ করা হয়েছে — রিভিউ করুন",
            "",
            "| id | অ্যাপের শিরোনাম | ইউটিউবের শিরোনাম | চ্যানেল | দৈর্ঘ্য | প্রকাশ | ক্যাটাগরি | মন্তব্য |",
            "|---|---|---|---|---|---|---|---|",
        ]
        for c in r.added:
            note = ", ".join(c.flags + (["অফিশিয়াল/বিশ্বস্ত সোর্স"] if c.trusted else []))
            lines.append(
                f"| `{c.lecture_id}` | {_cell(c.app_title)} | [{_cell(c.title)}](https://youtu.be/{c.video_id}) "
                f"| {_cell(c.channel)} | {clock(c.duration)} | {bn(c.published or '—')} | {', '.join(c.categories)} | {_cell(note)} |"
            )
        lines.append("")
    if r.duplicates:
        lines += ["### সম্ভাব্য ডুপ্লিকেট — যোগ হয়নি", "", "| ভিডিও | চ্যানেল | দৈর্ঘ্য | একই লেকচার |", "|---|---|---|---|"]
        for c in r.duplicates:
            official = " — এটি অফিশিয়াল কপি, চাইলে পুরোনোটির জায়গায় বসান" if c.trusted else ""
            lines.append(
                f"| [{_cell(c.title)}](https://youtu.be/{c.video_id}) | {_cell(c.channel)} | {clock(c.duration)} "
                f"| `{c.duplicate_of}`{official} |"
            )
        lines.append("")
    if r.unlisted:
        by_channel: dict[str, list[Candidate]] = {}
        for c in r.unlisted:
            by_channel.setdefault(c.channel_id, []).append(c)
        lines += [
            "### তালিকার বাইরের চ্যানেল — যোগ হয়নি, আপনার সিদ্ধান্ত দরকার",
            "",
            "বট শুধু `discovery/sources.json`-এ থাকা চ্যানেল ও প্লেলিস্ট থেকে লেকচার যোগ করে। নিচের চ্যানেলগুলোতে "
            "শিরোনামে বক্তার নামসহ ভিডিও পাওয়া গেছে:",
            "",
            f"- **চ্যানেলটি রাখতে চাইলে** `sources.json`-এ `{r.speaker_id}`-এর `channels`-এ যোগ করুন: "
            '`{ "id": "<চ্যানেল আইডি>", "name": "<নাম>", "official": false }` — পরের রানে ওই চ্যানেলের লেকচারগুলো প্রস্তাব হবে।',
            "- **বাদ দিতে চাইলে** চ্যানেল আইডিটি `excludeChannels`-এ দিন, অথবা কিছুই করবেন না — এই ভিডিওগুলো আর আসবে না।",
            f"- **শুধু একটি ভিডিও চাইলে:** `python scripts/add_lecture.py --speaker {r.speaker_id} --category <id> <link>`",
            "",
            "| চ্যানেল | চ্যানেল আইডি | ভিডিও | উদাহরণ |",
            "|---|---|---|---|",
        ]
        for channel_id, group in sorted(by_channel.items(), key=lambda kv: (-len(kv[1]), kv[1][0].channel)):
            examples = " · ".join(f"[{_cell(c.title)}](https://youtu.be/{c.video_id})" for c in group[:2])
            lines.append(
                f"| [{_cell(group[0].channel)}](https://www.youtube.com/channel/{channel_id}) | `{channel_id}` "
                f"| {bn(len(group))}টি | {examples} |"
            )
        lines.append("")
    if r.low_confidence:
        lines += [
            "### কম নিশ্চিত — নাম শুধু বিবরণে, যোগ হয়নি",
            "",
            "দরকার হলে হাতে যোগ করুন: `python scripts/add_lecture.py --speaker "
            f"{r.speaker_id} --category <id> <link>`",
            "",
            "| ভিডিও | চ্যানেল | দৈর্ঘ্য |",
            "|---|---|---|",
        ]
        for c in r.low_confidence:
            lines.append(f"| [{_cell(c.title)}](https://youtu.be/{c.video_id}) | {_cell(c.channel)} | {clock(c.duration)} |")
        lines.append("")
    if r.dropped:
        lines += ["### বাদ পড়েছে", ""]
        lines += [f"- {DROP_REASONS.get(k, k)}: {bn(v)}টি" for k, v in sorted(r.dropped.items(), key=lambda kv: -kv[1])]
        lines.append("")
    if footer:
        lines += _review_steps(r.speaker_id, "এই PR খোলা থাকা অবস্থায় বট নতুন PR খুলবে না।")
    return "\n".join(lines) + "\n"


def summary(result: Result) -> str:
    r = result
    return (
        f"{r.speaker_id}: added {len(r.added)}, duplicates {len(r.duplicates)}, "
        f"low-confidence {len(r.low_confidence)}, on unlisted channels {len(r.unlisted)}, "
        f"dropped {sum(r.dropped.values())}, "
        f"looked at {r.looked_at} ({r.already_known} already known); "
        f"quota used: search {r.searches}/100, units {r.units}/10000"
        + ("; search quota ran out, searches stopped early" if r.search_cut else "")
    )


# ---------------------------------------------------------------------------
# Research prompt
# ---------------------------------------------------------------------------

def research_ids(text: str) -> list[str]:
    """Video ids from a research result, ignoring everything after its "Skipped" list."""
    cut = re.search(r"^\W*skipped\b", text, flags=re.IGNORECASE | re.MULTILINE)
    body = text[: cut.start()] if cut else text
    return list(dict.fromkeys(VIDEO_URL_RE.findall(body)))


_COMBO_WORDS = {"লেকচার", "পডকাস্ট", "ওয়াজ", "বয়ান", "সিয়ান", "ইসলামিক", "lecture", "podcast", "sean", "waz"}


def _is_query_combo(query: str) -> bool:
    """A search query that is a name plus a topic word, not a spelling of the name."""
    words = {w.lower() for w in query.split()}
    return bool(words & {w.lower() for w in _COMBO_WORDS})


def render_prompt(root: Path, speaker_id: str) -> str:
    config = source_for(root, speaker_id)
    speaker = speaker_info(root, speaker_id)
    template = (root / PROMPT_TEMPLATE).read_text(encoding="utf-8")
    known = sorted(
        {
            lec["media"]["videoId"]
            for rel, lec in _all_lectures(root)
            if rel == f"{LECTURES_DIR}/{speaker_id}.json" and lec.get("media", {}).get("type") == "youtube"
        }
        | set(load_seen(root, speaker_id)["videos"])
    )
    sources = [
        f"  - Channel: {ch['name']} — https://www.youtube.com/{ch.get('handle') or 'channel/' + ch['id']}"
        + (" (the speaker's official channel)" if ch.get("official") else " (also has other speakers)")
        for ch in config.get("channels", [])
    ] + [
        f"  - Playlist: {pl['name']} — https://www.youtube.com/playlist?list={pl['id']}"
        for pl in config.get("playlists", [])
    ]
    values = {
        "NAME_BN": speaker["name"],
        "NAME_EN": speaker["nameEn"],
        "BIO": speaker.get("bio") or "—",
        "NAME_VARIANTS": "\n".join(f"  - {q}" for q in config.get("queries", []) if not _is_query_combo(q)),
        "SOURCES": "\n".join(sources) or "  - (none known yet — finding the official channel is part of the task)",
        "EXCLUDE": "\n".join(f"  - {x}" for x in config.get("exclude", [])) or "  - (none known)",
        "KNOWN_IDS": ", ".join(known) or "(none yet)",
    }
    for key, value in values.items():
        template = template.replace("{{" + key + "}}", value)
    return template


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _write_outputs(path: str | None, values: dict[str, Any]) -> None:
    if not path:
        return
    with open(path, "a", encoding="utf-8") as handle:
        for key, value in values.items():
            handle.write(f"{key}={value}\n")


def _client() -> YouTubeClient:
    key = os.environ.get("YOUTUBE_API_KEY", "").strip()
    if not key:
        raise DiscoverError("YOUTUBE_API_KEY is not set (GitHub: Settings → Secrets → Actions)")
    return YouTubeClient(key)


def main(argv: list[str] | None = None, client: YouTubeClient | None = None) -> int:
    parser = argparse.ArgumentParser(description="Find a speaker's new YouTube lectures.")
    sub = parser.add_subparsers(dest="command", required=True)

    def common(p: argparse.ArgumentParser) -> None:
        p.add_argument("--root", type=Path, default=REPO_ROOT, help="repo root (default: this repo)")
        p.add_argument("--date", type=date.fromisoformat, default=None, help="pretend today is YYYY-MM-DD")
        p.add_argument("--github-output", default=None, help="append key=value results to this file")

    p_pick = sub.add_parser("pick", help="print today's speaker (or check --speaker)")
    common(p_pick)
    p_pick.add_argument("--speaker", default="")

    for name in ("sweep", "import"):
        p = sub.add_parser(name)
        common(p)
        p.add_argument("--speaker", required=name == "import")
        p.add_argument("--report", type=Path, default=None, help="write the pull request text here")
        p.add_argument("--dry-run", action="store_true", help="check everything but write no files")
        if name == "sweep":
            p.add_argument("--search-budget", type=int, default=DEFAULT_SEARCH_BUDGET)
            p.add_argument("--all", action="store_true", help="every speaker in sources.json, in one run")
            p.add_argument("--order", choices=["date", "relevance"], default=None)
            p.add_argument(
                "--backfill", action="store_true",
                help="search all of YouTube's history, not only uploads since the last search",
            )
        else:
            p.add_argument("--file", type=Path, required=True, help="research result with YouTube links")

    p_dur = sub.add_parser("durations", help="add missing lecture lengths from YouTube")
    common(p_dur)
    p_dur.add_argument("--report", type=Path, default=None)
    p_dur.add_argument("--dry-run", action="store_true")

    p_prompt = sub.add_parser("prompt", help="print the research prompt for a speaker")
    common(p_prompt)
    p_prompt.add_argument("--speaker", required=True)
    p_prompt.add_argument("--out", type=Path, default=None)

    args = parser.parse_args(argv)
    today = args.date or date.today()
    try:
        if args.command == "pick":
            speaker_id = pick_speaker(args.root, args.speaker, today)
            print(speaker_id)
            _write_outputs(args.github_output, {"speaker": speaker_id})
            return 0
        if args.command == "durations":
            api = client or _client()
            missing, filled, changed = fill_durations(args.root, api, dry_run=args.dry_run)
            print(f"durations: {filled} of {missing} missing filled in {len(changed)} file(s); units {api.usage.units}")
            if args.report:
                args.report.write_text(
                    f"## লেকচারের দৈর্ঘ্য যোগ — {bn(filled)}টি\n\n"
                    f"যেসব লেকচারে দৈর্ঘ্য (`durationSec`) ছিল না, YouTube থেকে তা যোগ করা হয়েছে "
                    f"({bn(missing)}টির মধ্যে {bn(filled)}টি; বাকিগুলো মুছে ফেলা/প্রাইভেট/লাইভ হতে পারে)। "
                    "অ্যাপে প্রতিটি লেকচারের পাশে কত মিনিটের তা দেখাবে।\n\n"
                    + "".join(f"- `{rel}`\n" for rel in changed),
                    encoding="utf-8",
                )
            _write_outputs(args.github_output, {"changed": "true" if changed else "false", "filled": filled})
            return 0
        if args.command == "prompt":
            speaker_id = pick_speaker(args.root, args.speaker, today)
            text = render_prompt(args.root, speaker_id)
            if args.out:
                args.out.write_text(text, encoding="utf-8")
                print(f"Wrote {args.out}")
            else:
                print(text)
            return 0

        if args.command == "sweep" and args.all:
            if args.speaker:
                raise DiscoverError("give --speaker or --all, not both")
            api = client or _client()
            results, stopped_at = sweep_all(
                args.root, api, today,
                search_budget=args.search_budget, order=args.order, dry_run=args.dry_run, backfill=args.backfill,
            )
            for r in results:
                print(summary(r))
            if stopped_at:
                print(f"STOPPED at {stopped_at}: the daily quota is used up; the next run continues.", file=sys.stderr)
            if args.report:
                args.report.write_text(render_all_report(results, stopped_at, today), encoding="utf-8")
            _write_outputs(
                args.github_output,
                {
                    "speaker": "all",
                    "name": f"{bn(len(results))} জন বক্তা",
                    "added": sum(len(r.added) for r in results),
                    "listed": sum(r.listed for r in results),
                    "changed": "true" if any(r.files_changed for r in results) else "false",
                },
            )
            return 0 if results or not stopped_at else 3
        if args.command == "sweep" and not args.speaker:
            raise DiscoverError("give --speaker speaker-00N or --all")
        speaker_id = pick_speaker(args.root, args.speaker, today)
        imported = None
        if args.command == "import":
            imported = research_ids(args.file.read_text(encoding="utf-8"))
            if not imported:
                raise DiscoverError(f"no YouTube links found in {args.file}")
        api = client or _client()
        result = discover(
            args.root,
            speaker_id,
            api,
            today,
            search_budget=getattr(args, "search_budget", DEFAULT_SEARCH_BUDGET),
            order=getattr(args, "order", None),
            imported_ids=imported,
            dry_run=args.dry_run,
            backfill=getattr(args, "backfill", False),
        )
    except DiscoverError as exc:
        print(f"ERROR {exc}", file=sys.stderr)
        return 1
    except QuotaExceeded as exc:
        print(f"STOPPED {exc}. Nothing was written; the next run continues.", file=sys.stderr)
        return 3
    except YouTubeApiError as exc:
        print(f"ERROR {exc}", file=sys.stderr)
        return 2

    print(summary(result))
    for c in result.added:
        print(f"  + {c.lecture_id} {c.video_id} {c.app_title} [{', '.join(c.categories)}]")
    if args.report:
        args.report.write_text(render_report(result, today), encoding="utf-8")
    _write_outputs(
        args.github_output,
        {
            "speaker": result.speaker_id,
            "name": result.speaker_name,
            "added": len(result.added),
            "listed": result.listed,
            "changed": "true" if result.files_changed else "false",
        },
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
