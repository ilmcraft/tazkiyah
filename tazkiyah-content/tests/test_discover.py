"""discover.py end to end against a fake YouTube API."""
from __future__ import annotations

import json
import shutil
from datetime import date
from pathlib import Path

import pytest

import discover
import validate
from conftest import LECTURE_FILE, REPO_ROOT, read_json, write_json
from youtube_api import QuotaExceeded, Usage

TODAY = date(2026, 11, 8)
OFFICIAL = "UCofficial0000000000000a"
OTHER = "UCother000000000000000aa"  # a listed host channel: the speaker's name must be in the title
STRANGER = "UCstranger0000000000000a"  # not in sources.json

SOURCES = {
    "speakers": [
        {
            "speakerId": "speaker-001",
            "channels": [
                {"id": OFFICIAL, "handle": "@first", "name": "First Official", "official": True},
                {"id": OTHER, "name": "অন্য চ্যানেল", "official": False},
            ],
            "playlists": [{"id": "PLtrusted00000", "name": "বিশ্বস্ত প্লেলিস্ট", "trusted": True}],
            "queries": ["প্রথম বক্তা", "First Speaker", "প্রথম বক্তা লেকচার"],
            "mustMatch": ["প্রথম বক্তা", "first speaker"],
            "exclude": ["অন্য প্রথম"],
            "defaultCategoryIds": ["tazkiyah"],
        },
        {
            "speakerId": "speaker-002",
            "channels": [],
            "playlists": [],
            "queries": ["দ্বিতীয় বক্তা"],
            "mustMatch": ["দ্বিতীয় বক্তা"],
            "exclude": [],
            "defaultCategoryIds": ["ibadah"],
        },
    ]
}


def item(
    vid,
    title,
    *,
    channel="অন্য চ্যানেল",
    channel_id=OTHER,
    duration="PT30M",
    published="2026-10-01T10:00:00Z",
    description="",
    privacy="public",
    embeddable=True,
    live="none",
    region=None,
    age_restricted=False,
):
    details = {"duration": duration}
    if region:
        details["regionRestriction"] = region
    if age_restricted:
        details["contentRating"] = {"ytRating": "ytAgeRestricted"}
    return {
        "id": vid,
        "snippet": {
            "title": title,
            "channelTitle": channel,
            "channelId": channel_id,
            "description": description,
            "publishedAt": published,
            "liveBroadcastContent": live,
        },
        "contentDetails": details,
        "status": {"privacyStatus": privacy, "uploadStatus": "processed", "embeddable": embeddable},
    }


class FakeClient:
    def __init__(self, playlists=None, searches=None, videos=None, fail_search=False, fail_videos=False):
        self.usage = Usage()
        self.playlists = playlists or {}
        self.searches = searches or {}
        self.items = videos or {}
        self.search_calls = []
        self.fail_videos = fail_videos
        self.fail_search = fail_search

    def playlist_video_ids(self, playlist_id, max_pages=200):
        self.usage.units += 1
        return list(self.playlists.get(playlist_id, []))

    def search_video_ids(self, query, order, max_pages, published_after=None):
        if self.fail_search:
            raise QuotaExceeded("search: quotaExceeded")
        self.search_calls.append((query, order, max_pages))
        self.published_after = published_after
        found = []
        for page in self.searches.get(query, [[]])[:max_pages]:
            self.usage.searches += 1
            found += page
        return found

    def videos(self, ids):
        if self.fail_videos:
            raise QuotaExceeded("videos: quotaExceeded")
        ids = list(ids)
        self.usage.units += max(1, -(-len(ids) // 50))
        return {i: self.items[i] for i in ids if i in self.items}


@pytest.fixture
def content(repo: Path) -> Path:
    write_json(repo, "discovery/sources.json", SOURCES)
    shutil.copy(REPO_ROOT / "discovery" / "research-prompt.md", repo / "discovery" / "research-prompt.md")
    write_json(
        repo,
        "discovery/seen/speaker-001.json",
        {"speakerId": "speaker-001", "videos": {"SEENSEEN001": {"status": "rejected", "date": "2026-11-07", "note": "সংবাদ"}}},
    )
    return repo


def run(root, fake, **kwargs):
    return discover.discover(root, "speaker-001", fake, TODAY, **kwargs)


# ------------------------------------------------------------------ picking


def test_rotation_visits_every_speaker_in_order():
    sources = SOURCES["speakers"]
    picks = [discover.rotation_speaker(sources, date.fromordinal(TODAY.toordinal() + i)) for i in range(4)]
    assert sorted(set(picks)) == ["speaker-001", "speaker-002"]
    assert picks[0] != picks[1] and picks[0] == picks[2]


def test_search_order_alternates_weekly():
    orders = {discover.search_order(date.fromordinal(TODAY.toordinal() + 7 * i)) for i in range(2)}
    assert orders == {"date", "relevance"}


def test_pick_accepts_listed_speaker_and_rejects_bad_input(content, capsys):
    assert discover.main(["pick", "--root", str(content), "--speaker", "speaker-002"]) == 0
    assert capsys.readouterr().out.strip() == "speaker-002"
    assert discover.main(["pick", "--root", str(content), "--speaker", "speaker-002; rm -rf /"]) == 1
    assert discover.main(["pick", "--root", str(content), "--speaker", "speaker-009"]) == 1


def test_pick_writes_github_output(content, tmp_path):
    out = tmp_path / "out.txt"
    discover.main(["pick", "--root", str(content), "--date", "2026-11-08", "--github-output", str(out)])
    assert out.read_text().startswith("speaker=speaker-00")


# ------------------------------------------------------------------ sweep


def test_sweep_adds_new_lectures_and_skips_known(content):
    fake = FakeClient(
        playlists={
            "UUofficial0000000000000a": ["OFFICIAL001", "AAAAAAAAAAA"],  # second one is already in the catalog
            "PLtrusted00000": ["TRUSTED0001"],
        },
        searches={"প্রথম বক্তা": [["SEARCHHIT01", "SEENSEEN001"]]},
        videos={
            "OFFICIAL001": item(
                "OFFICIAL001", "নামাজের গুরুত্ব", channel="First Official", channel_id=OFFICIAL, published="2026-09-01T00:00:00Z"
            ),
            "TRUSTED0001": item("TRUSTED0001", "রমজানের প্রস্তুতি", duration="PT45M"),
            "SEARCHHIT01": item("SEARCHHIT01", "তাওবার দরজা | প্রথম বক্তা | অন্য চ্যানেল", duration="PT1H2M3S"),
        },
    )
    result = run(content, fake)

    assert [c.video_id for c in result.added] == ["OFFICIAL001", "SEARCHHIT01", "TRUSTED0001"]
    assert [c.lecture_id for c in result.added] == ["spk001-0003", "spk001-0004", "spk001-0005"]
    assert result.already_known == 2  # AAAAAAAAAAA (catalog) and SEENSEEN001 (seen)

    lectures = read_json(content, LECTURE_FILE)["lectures"]
    new = {lec["media"]["videoId"]: lec for lec in lectures[2:]}
    assert new["SEARCHHIT01"] == {
        "id": "spk001-0004",
        "title": "তাওবার দরজা",
        "categoryIds": ["tazkiyah"],
        "media": {"type": "youtube", "videoId": "SEARCHHIT01", "startSec": 0},
        "durationSec": 3723,
        "publishedAt": "2026-10-01",
        "addedAt": "2026-11-08",
    }
    assert new["OFFICIAL001"]["categoryIds"] == ["salah"]
    seen = read_json(content, "discovery/seen/speaker-001.json")["videos"]
    assert seen["SEARCHHIT01"] == {
        "status": "proposed", "date": "2026-11-08", "lectureId": "spk001-0004",
        "channelId": OTHER, "channel": "অন্য চ্যানেল",
    }
    assert seen["SEENSEEN001"]["status"] == "rejected"

    # What the bot wrote is a valid catalog, in the hand-written file style.
    assert validate.validate_repo(content)[0] == []
    text = (content / LECTURE_FILE).read_text(encoding="utf-8")
    assert '"media": { "type": "youtube", "videoId": "SEARCHHIT01", "startSec": 0 },' in text


def test_second_run_proposes_nothing_new(content):
    def fake():
        return FakeClient(
            searches={"প্রথম বক্তা": [["SEARCHHIT01"]]},
            videos={"SEARCHHIT01": item("SEARCHHIT01", "তাওবার দরজা — প্রথম বক্তা")},
        )

    assert len(run(content, fake()).added) == 1
    again = run(content, fake())
    assert again.added == [] and again.files_changed == []


@pytest.mark.parametrize(
    ("video", "reason"),
    [
        (item("CANDIDATE01", "প্রথম বক্তা", privacy="private"), "unavailable"),
        (item("CANDIDATE01", "প্রথম বক্তা", embeddable=False), "not_embeddable"),
        (item("CANDIDATE01", "প্রথম বক্তা", age_restricted=True), "not_embeddable"),
        (item("CANDIDATE01", "প্রথম বক্তা", region={"blocked": ["BD", "IN"]}), "region"),
        (item("CANDIDATE01", "প্রথম বক্তা", region={"allowed": ["US"]}), "region"),
        (item("CANDIDATE01", "প্রথম বক্তা", live="upcoming", duration="P0D"), "live"),
        (item("CANDIDATE01", "প্রথম বক্তা #shorts", duration="PT58S"), "short"),
        (item("CANDIDATE01", "অন্য প্রথম বক্তা — সংবাদ"), "namesake"),
        (item("CANDIDATE01", "সম্পূর্ণ অন্য কারো আলোচনা"), "no_name"),
        (None, "unavailable"),
    ],
)
def test_filters(content, video, reason):
    fake = FakeClient(searches={"প্রথম বক্তা": [["CANDIDATE01"]]}, videos={"CANDIDATE01": video} if video else {})
    result = run(content, fake)
    assert result.added == [] and result.dropped == {reason: 1}
    assert not (content / "discovery/seen/speaker-001.json").read_text().count("CANDIDATE01")


def test_reupload_titled_only_with_the_name_is_dropped(content):
    fake = FakeClient(
        searches={"প্রথম বক্তা": [["NAMEONLY002"]]},
        videos={"NAMEONLY002": item("NAMEONLY002", "প্রথম বক্তা")},
    )
    result = run(content, fake)
    assert result.added == [] and result.dropped == {"no_topic": 1}


def test_course_intro_is_not_a_lecture(content):
    fake = FakeClient(
        playlists={"UUofficial0000000000000a": ["INTROVIDEO1"]},
        videos={"INTROVIDEO1": item("INTROVIDEO1", "কোর্স কন্টেন্ট ইন্ট্রো ভিডিও", channel_id=OFFICIAL)},
    )
    assert run(content, fake).dropped == {"not_lecture": 1}


def test_title_that_is_only_the_name_is_flagged(content):
    fake = FakeClient(
        playlists={"UUofficial0000000000000a": ["NAMEONLY001"]},
        videos={"NAMEONLY001": item("NAMEONLY001", "প্রথম বক্তা", channel_id=OFFICIAL)},
    )
    assert "শিরোনাম দেখুন" in run(content, fake).added[0].flags


def test_name_only_in_description_is_listed_not_added(content):
    fake = FakeClient(
        searches={"প্রথম বক্তা": [["DESCONLY001"]]},
        videos={"DESCONLY001": item("DESCONLY001", "ঈমানের দাবি", description="আলোচক: প্রথম বক্তা")},
    )
    result = run(content, fake)
    assert result.added == [] and [c.video_id for c in result.low_confidence] == ["DESCONLY001"]
    assert read_json(content, "discovery/seen/speaker-001.json")["videos"]["DESCONLY001"]["status"] == "low-confidence"


def test_short_talks_from_the_official_channel_are_kept_and_flagged(content):
    fake = FakeClient(
        playlists={"UUofficial0000000000000a": ["SHORTTALK01"]},
        videos={"SHORTTALK01": item("SHORTTALK01", "দোয়ার আদব", channel_id=OFFICIAL, duration="PT8M")},
    )
    assert run(content, fake).added[0].flags == ["ক্লিপ হতে পারে (১৫ মিনিটের কম)"]


def test_short_videos_from_other_channels_are_clips(content):
    fake = FakeClient(
        searches={"প্রথম বক্তা": [["SHORTTALK01"]]},
        videos={"SHORTTALK01": item("SHORTTALK01", "প্রথম বক্তা: দোয়ার আদব", duration="PT8M")},
    )
    result = run(content, fake)
    assert result.added == [] and result.dropped == {"short": 1}


def test_same_length_reupload_with_a_new_title_is_a_duplicate(content):
    fake = FakeClient(
        searches={"প্রথম বক্তা": [["ORIGINAL001", "REUPLOAD002"]]},
        videos={
            "ORIGINAL001": item("ORIGINAL001", "গণতান্ত্রিক শাসনের চেয়ে শরিয়া শাসন কেন উত্তম | প্রথম বক্তা",
                                duration="PT56M9S", published="2025-01-01T00:00:00Z"),
            "REUPLOAD002": item("REUPLOAD002", "গনতান্ত্রিক শাষনের চেয়ে কেনো শরীয়াহ শাসন | প্রথম বক্তা",
                                duration="PT56M9S", published="2025-06-01T00:00:00Z"),
        },
    )
    result = run(content, fake)
    assert [c.video_id for c in result.added] == ["ORIGINAL001"]
    assert [(c.video_id, c.duplicate_of) for c in result.duplicates] == [("REUPLOAD002", "ORIGINAL001")]


def test_reupload_of_an_existing_lecture_is_a_duplicate(content):
    # spk001-0001 is "তাওবার শর্ত ও পদ্ধতি", durationSec 2730.
    fake = FakeClient(
        searches={"প্রথম বক্তা": [["REUPLOAD001"]]},
        videos={"REUPLOAD001": item("REUPLOAD001", "তাওবার শর্ত ও পদ্ধতি | প্রথম বক্তা", duration="PT45M31S")},
    )
    result = run(content, fake)
    assert result.added == []
    assert [(c.video_id, c.duplicate_of) for c in result.duplicates] == [("REUPLOAD001", "spk001-0001")]


def test_two_copies_keep_the_official_one(content):
    fake = FakeClient(
        playlists={"UUofficial0000000000000a": ["OFFICIALCPY"]},
        searches={"প্রথম বক্তা": [["MIRRORCOPY1"]]},
        videos={
            "MIRRORCOPY1": item("MIRRORCOPY1", "আখিরাতের প্রস্তুতি | প্রথম বক্তা", duration="PT40M", published="2026-01-01T00:00:00Z"),
            "OFFICIALCPY": item(
                "OFFICIALCPY", "আখিরাতের প্রস্তুতি", channel_id=OFFICIAL, duration="PT40M1S", published="2026-02-01T00:00:00Z"
            ),
        },
    )
    result = run(content, fake)
    assert [c.video_id for c in result.added] == ["OFFICIALCPY"]
    assert [(c.video_id, c.duplicate_of) for c in result.duplicates] == [("MIRRORCOPY1", "OFFICIALCPY")]


@pytest.mark.parametrize(
    ("a", "da", "b", "db", "same"),
    [
        ("জীবন নিয়ে এভাবে কখনো ভেবেছেন?", 2062, "জীবন নিয়ে এভাবে কখনো ভেবেছেন বগুড়া", 2062, True),
        ("ক্যারিয়ার ভাবনা", 3600, "ক্যারিয়ার ভাবনা", 3420, True),  # trimmed copy, same title
        ("সীরাত পর্ব ১", 3000, "সীরাত পর্ব ২", 3000, False),  # parts differ
        ("সালাতে খুশু", 1800, "রমজানের প্রস্তুতি", 1801, False),  # same length by chance
        ("ফিলিস্তিনের মুক্তি ও আরব শাসকদের ব্যর্থতা", 3206, "স্বাধীন ফিলিস্তিনের স্বপ্ন", 3208, True),  # shared topic word
        ("দুনিয়ার জীবন মুমিনের জন্য কারাগার", 1300, "প্রোডাক্টিভ জীবন গড়ার কৌশল", 1302, False),  # "জীবন" is filler
        ("তাওবা", 0, "তাওবা", 0, False),  # length unknown
    ],
)
def test_same_lecture(a, da, b, db, same):
    assert discover.same_lecture(a, da, b, db) is same


def test_parts_of_a_series_are_not_duplicates(content):
    fake = FakeClient(
        searches={"প্রথম বক্তা": [["SERIESPART1", "SERIESPART2"]]},
        videos={
            "SERIESPART1": item("SERIESPART1", "সীরাত পর্ব ১ | প্রথম বক্তা", duration="PT50M"),
            "SERIESPART2": item("SERIESPART2", "সীরাত পর্ব ২ | প্রথম বক্তা", duration="PT55M"),
        },
    )
    assert len(run(content, fake).added) == 2


def test_search_budget_is_shared_and_never_exceeded(content):
    pages = [["X%010d" % i] for i in range(20)]
    fake = FakeClient(searches={q: pages for q in SOURCES["speakers"][0]["queries"]})
    run(content, fake, search_budget=7)
    assert fake.usage.searches == 7
    assert [calls for _, _, calls in fake.search_calls] == [2, 2, 3]  # leftover moves to later queries


def test_dry_run_writes_nothing(content):
    before = (content / LECTURE_FILE).read_text()
    fake = FakeClient(searches={"প্রথম বক্তা": [["SEARCHHIT01"]]}, videos={"SEARCHHIT01": item("SEARCHHIT01", "প্রথম বক্তা: সবর")})
    result = run(content, fake, dry_run=True)
    assert len(result.added) == 1 and result.files_changed == []
    assert (content / LECTURE_FILE).read_text() == before


def test_report_lists_everything_for_review(content):
    fake = FakeClient(
        searches={"প্রথম বক্তা": [["SEARCHHIT01", "DESCONLY001", "CANDIDATE01"]]},
        videos={
            "SEARCHHIT01": item("SEARCHHIT01", "সবরের ফজিলত | প্রথম বক্তা"),
            "DESCONLY001": item("DESCONLY001", "ঈমানের দাবি", description="প্রথম বক্তা"),
            "CANDIDATE01": item("CANDIDATE01", "প্রথম বক্তা", duration="PT30S"),
        },
    )
    report = discover.render_report(run(content, fake), TODAY)
    assert "১টি যোগ, ১টি শুধু তালিকায়" in report
    assert "`spk001-0003` | সবরের ফজিলত | [সবরের ফজিলত \\| প্রথম বক্তা](https://youtu.be/SEARCHHIT01)" in report
    assert "কম নিশ্চিত" in report and "খুব ছোট (Shorts / ক্লিপ): ১টি" in report


# ------------------------------------------------------------------ CLI, import, prompt


def test_main_sweep_writes_report_and_outputs(content, tmp_path):
    fake = FakeClient(searches={"প্রথম বক্তা": [["SEARCHHIT01"]]}, videos={"SEARCHHIT01": item("SEARCHHIT01", "প্রথম বক্তা: সবর")})
    report, out = tmp_path / "report.md", tmp_path / "out.txt"
    code = discover.main(
        ["sweep", "--root", str(content), "--speaker", "speaker-001", "--date", "2026-11-08",
         "--report", str(report), "--github-output", str(out)],
        client=fake,
    )
    assert code == 0
    assert "সবর" in report.read_text()
    assert "added=1\n" in out.read_text() and "changed=true\n" in out.read_text()


def test_used_up_search_quota_keeps_the_channel_lectures(content, capsys):
    fake = FakeClient(
        playlists={"UUofficial0000000000000a": ["OFFICIALTK1"]},
        videos={"OFFICIALTK1": item("OFFICIALTK1", "তাওবার শর্ত", channel_id=OFFICIAL)},
        fail_search=True,
    )
    code = discover.main(["sweep", "--root", str(content), "--speaker", "speaker-001"], client=fake)
    assert code == 0 and "search quota ran out" in capsys.readouterr().out
    assert "OFFICIALTK1" in (content / LECTURE_FILE).read_text()
    result = run(content, FakeClient(fail_search=True))
    assert result.search_cut and "সার্চ কোটা শেষ" in discover.render_report(result, TODAY)


def test_quota_exceeded_on_lookup_stops_without_writing(content, capsys):
    before = (content / LECTURE_FILE).read_text()
    fake = FakeClient(playlists={"UU" + OFFICIAL[2:]: ["N0000000001"]}, fail_videos=True)
    code = discover.main(["sweep", "--root", str(content), "--speaker", "speaker-001"], client=fake)
    assert code == 3 and "quota" in capsys.readouterr().err
    assert (content / LECTURE_FILE).read_text() == before


def test_missing_api_key_is_a_clear_error(content, monkeypatch, capsys):
    monkeypatch.delenv("YOUTUBE_API_KEY", raising=False)
    assert discover.main(["sweep", "--root", str(content), "--speaker", "speaker-001"]) == 1
    assert "YOUTUBE_API_KEY" in capsys.readouterr().err


RESEARCH = """| # | url | original title |
|---|-----|----|
| 1 | https://www.youtube.com/watch?v=RESEARCH001 | প্রথম বক্তা: সবর |
| 2 | https://youtu.be/RESEARCH002?t=10 | আরেকটি |
| 3 | https://www.youtube.com/watch?v=RESEARCH001 | দুবার |

**Skipped:**
- https://www.youtube.com/shorts/SKIPPED0001 — Short
"""


def test_research_ids_ignore_the_skipped_list():
    assert discover.research_ids(RESEARCH) == ["RESEARCH001", "RESEARCH002"]


def test_import_runs_the_same_checks(content, tmp_path):
    research = tmp_path / "research.md"
    research.write_text(RESEARCH, encoding="utf-8")
    fake = FakeClient(
        videos={
            "RESEARCH001": item("RESEARCH001", "প্রথম বক্তা: সবর"),
            "RESEARCH002": item("RESEARCH002", "আরেকটি", duration="PT2M"),
        }
    )
    code = discover.main(["import", "--root", str(content), "--speaker", "speaker-001", "--file", str(research)], client=fake)
    assert code == 0
    assert fake.search_calls == []
    videos = [lec["media"].get("videoId") for lec in read_json(content, LECTURE_FILE)["lectures"]]
    assert "RESEARCH001" in videos and "RESEARCH002" not in videos


def test_prompt_names_the_speaker_and_known_ids(content):
    text = discover.render_prompt(content, "speaker-001")
    assert "প্রথম বক্তা" in text and "First Speaker" in text
    assert "AAAAAAAAAAA" in text and "SEENSEEN001" in text
    assert "  - প্রথম বক্তা লেকচার" not in text  # search combos are not name spellings
    assert "https://www.youtube.com/@first (the speaker's official channel)" in text
    assert "{{" not in text


def test_real_sources_cover_every_speaker():
    speakers = {s["id"] for s in json.loads((REPO_ROOT / "data/speakers.json").read_text())["speakers"]}
    sources = {s["speakerId"] for s in discover.load_sources(REPO_ROOT)}
    assert sources == speakers


def test_durations_are_filled_in_without_touching_other_fields(content):
    def strip(doc):
        doc["lectures"][0].pop("durationSec")
        doc["lectures"][0].pop("publishedAt")
    path = content / LECTURE_FILE
    doc = json.loads(path.read_text(encoding="utf-8"))
    strip(doc)
    from add_lecture import format_lecture_file
    path.write_text(format_lecture_file(doc), encoding="utf-8")
    fake = FakeClient(videos={"AAAAAAAAAAA": item("AAAAAAAAAAA", "x", duration="PT45M30S", published="2025-03-14T08:00:00Z")})
    assert discover.main(["durations", "--root", str(content)], client=fake) == 0
    lecture = read_json(content, LECTURE_FILE)["lectures"][0]
    assert lecture["durationSec"] == 2730 and lecture["publishedAt"] == "2025-03-14"
    assert list(lecture)[:7] == ["id", "title", "description", "categoryIds", "tags", "media", "durationSec"]
    assert validate.validate_repo(content)[0] == []


def test_durations_skip_unavailable_videos(content):
    path = content / LECTURE_FILE
    doc = json.loads(path.read_text(encoding="utf-8"))
    doc["lectures"][0].pop("durationSec")
    from add_lecture import format_lecture_file
    path.write_text(format_lecture_file(doc), encoding="utf-8")
    missing, filled, changed = discover.fill_durations(content, FakeClient())
    assert (missing, filled, changed) == (1, 0, [])


# ------------------------------------------------------------------ re-cuts and re-uploads


@pytest.mark.parametrize(
    "title, seconds, clickbait",
    [
        ("Indian Reaction | কথাগুলো হীরার চেয়েও দামি", 3000, True),
        ("১০০% আপনি চমকে যাবেন এভাবে কেউ বলেনি", 1100, True),
        ("কখনো হতাশ হবেন না🖤 আসিফ আদনান", 750, True),
        ("৭১ প্রশ্ন ? রাজশাহী ইসলামী সেমিনার ⚔গুরুত্বপূর্ণ", 4013, False),  # a whole seminar
        ("সভ্যতার উত্থান ও পতন", 3751, False),
        ("জীবন পরিবর্তনের জন্য এই কথাগুলোই যথেষ্ট", 1200, True),  # hype on a short upload: a cut clip
        ("এত সুন্দর ওয়াজ আর কোনোদিন শুনেননি", 4100, False),  # hype on a whole talk: only a bad title
    ],
)
def test_looks_clickbait(title, seconds, clickbait):
    assert discover.looks_clickbait(title, seconds) is clickbait


def test_clickbait_from_other_channels_is_dropped_but_not_from_official(content):
    fake = FakeClient(
        playlists={"UUofficial0000000000000a": ["OFFICIALEMO"]},
        searches={"প্রথম বক্তা": [["RECUTCLIP01"]]},
        videos={
            "RECUTCLIP01": item("RECUTCLIP01", "প্রথম বক্তা | জীবন বদলে যাবে😥", duration="PT18M"),
            "OFFICIALEMO": item("OFFICIALEMO", "রমজানের প্রস্তুতি 🌙", channel_id=OFFICIAL, duration="PT18M"),
        },
    )
    result = run(content, fake)
    assert [c.video_id for c in result.added] == ["OFFICIALEMO"]
    assert result.dropped == {"clickbait": 1}


def test_same_length_uploads_months_apart_are_not_merged_without_similar_titles():
    a, b = "জুলাই অভ্যুত্থান, অতঃপর", "The Political Thought of the Ummah"
    assert discover.same_lecture(a, 2945, b, 2945, "2024-08-26", "2024-09-11")
    assert not discover.same_lecture(a, 2945, b, 2946, "2024-08-26", "2025-09-11")
    assert not discover.same_lecture(a, 2945, b, 2945)  # no dates: titles must agree


def test_reuploads_of_one_talk_with_new_titles_keep_one(content):
    fake = FakeClient(
        searches={"প্রথম বক্তা": [["FIRSTUPLOAD", "SECONDCOPY1", "THIRDCOPY01"]]},
        videos={
            "FIRSTUPLOAD": item("FIRSTUPLOAD", "প্রথম বক্তা | ঢাকা বিশ্ববিদ্যালয়ে বক্তব্য", duration="PT39M16S", published="2026-08-17T08:00:00Z"),
            "SECONDCOPY1": item("SECONDCOPY1", "প্রথম বক্তা | ট্রান্সহিউম্যানিজম নিয়ে বিশ্লেষণ", duration="PT39M17S", published="2026-08-17T12:00:00Z"),
            "THIRDCOPY01": item("THIRDCOPY01", "প্রথম বক্তার লেকচার শুনে সবাই অবাক", duration="PT39M16S", published="2026-08-18T12:00:00Z"),
        },
    )
    result = run(content, fake)
    assert [c.video_id for c in result.added] == ["FIRSTUPLOAD"]
    assert {c.video_id for c in result.duplicates} == {"SECONDCOPY1", "THIRDCOPY01"}


def test_excluded_channels_are_skipped_for_every_speaker(content):
    sources = json.loads((content / "discovery/sources.json").read_text(encoding="utf-8"))
    sources["excludeChannels"] = ["Motivation Clips BD"]
    (content / "discovery/sources.json").write_text(json.dumps(sources, ensure_ascii=False), encoding="utf-8")
    fake = FakeClient(
        searches={"প্রথম বক্তা": [["RECUTCHAN01", "OTHERCHAN01"]]},
        videos={
            "RECUTCHAN01": item("RECUTCHAN01", "প্রথম বক্তা | তাওবার শর্ত", channel="Motivation Clips BD", duration="PT40M"),
            "OTHERCHAN01": item("OTHERCHAN01", "প্রথম বক্তা | সবরের ফজিলত", duration="PT40M"),
        },
    )
    result = run(content, fake)
    assert [c.video_id for c in result.added] == ["OTHERCHAN01"]
    assert result.dropped == {"clickbait": 1}


# ------------------------------------------------------------------ listed channels only, new uploads only


def stranger(vid, title="প্রথম বক্তা | সবরের ফজিলত", **kwargs):
    return item(vid, title, channel="Waz Mirror", channel_id=STRANGER, **kwargs)


def test_name_match_on_an_unlisted_channel_is_reported_not_added(content):
    before = (content / LECTURE_FILE).read_text()
    fake = FakeClient(
        searches={"প্রথম বক্তা": [["STRANGER001", "STRANGER002", "STRANGER003"]]},
        videos={
            "STRANGER001": stranger("STRANGER001"),
            "STRANGER002": stranger("STRANGER002", "প্রথম বক্তা | তাওবার দরজা", duration="PT50M"),
            "STRANGER003": stranger("STRANGER003", "ঈমানের দাবি", description="আলোচক: প্রথম বক্তা"),
        },
    )
    result = run(content, fake)
    assert result.added == [] and result.low_confidence == []
    assert [c.video_id for c in result.unlisted] == ["STRANGER001", "STRANGER002"]
    assert result.dropped == {"no_name": 1}
    assert (content / LECTURE_FILE).read_text() == before
    seen = read_json(content, "discovery/seen/speaker-001.json")
    assert seen["videos"]["STRANGER001"] == {
        "status": "unlisted-channel", "date": "2026-11-08", "channelId": STRANGER, "channel": "Waz Mirror",
    }
    assert validate.validate_repo(content)[0] == []
    report = discover.render_report(result, TODAY)
    assert "তালিকার বাইরের চ্যানেল" in report
    assert f"| [Waz Mirror](https://www.youtube.com/channel/{STRANGER}) | `{STRANGER}` | ২টি |" in report
    # Decided: the same search result does not come back.
    again = run(content, fake)
    assert again.unlisted == [] and again.files_changed == []


def test_listing_the_channel_brings_its_set_aside_lectures_in(content):
    fake = FakeClient(searches={"প্রথম বক্তা": [["STRANGER001"]]}, videos={"STRANGER001": stranger("STRANGER001")})
    assert run(content, fake).added == []
    sources = read_json(content, "discovery/sources.json")
    sources["speakers"][0]["channels"].append({"id": STRANGER, "name": "Waz Mirror", "official": False})
    write_json(content, "discovery/sources.json", sources)
    fake.playlists = {"UU" + STRANGER[2:]: ["STRANGER001"]}
    fake.searches = {}
    result = run(content, fake)
    assert [c.video_id for c in result.added] == ["STRANGER001"]
    assert read_json(content, "discovery/seen/speaker-001.json")["videos"]["STRANGER001"]["status"] == "proposed"


def test_import_does_not_add_from_unlisted_channels(content, tmp_path):
    research = tmp_path / "research.md"
    research.write_text("https://youtu.be/STRANGER001", encoding="utf-8")
    fake = FakeClient(videos={"STRANGER001": stranger("STRANGER001")})
    result = run(content, fake, imported_ids=discover.research_ids(research.read_text()))
    assert result.added == [] and [c.video_id for c in result.unlisted] == ["STRANGER001"]


def test_daily_search_only_looks_at_uploads_since_the_last_one(content):
    # The newest entry in `seen` is 2026-11-07: two weeks of overlap before it.
    fake = FakeClient(searches={"প্রথম বক্তা": [["SEARCHHIT01"]]}, videos={"SEARCHHIT01": item("SEARCHHIT01", "প্রথম বক্তা: সবর")})
    result = run(content, fake)
    assert fake.published_after == "2026-10-24" and result.since == "2026-10-24"
    assert {order for _, order, _ in fake.search_calls} == {"date"}
    seen = read_json(content, "discovery/seen/speaker-001.json")
    assert seen["lastSearch"] == "2026-11-08" and list(seen) == ["speakerId", "lastSearch", "videos"]
    assert discover.search_since(seen) == "2026-10-25"
    assert "২০২৬-১০-২৪ থেকে আপলোড হওয়া ভিডিও" in discover.render_report(result, TODAY)


def test_backfill_and_a_first_sweep_search_everything(content):
    fake = FakeClient()
    assert run(content, fake, backfill=True).since is None and fake.published_after is None
    assert discover.search_since({"speakerId": "speaker-002", "videos": {}}) is None
    code = discover.main(["sweep", "--root", str(content), "--speaker", "speaker-001", "--backfill"], client=fake)
    assert code == 0 and fake.published_after is None


def test_cut_short_search_does_not_move_the_date(content):
    fake = FakeClient(
        playlists={"UUofficial0000000000000a": ["OFFICIALTK1"]},
        videos={"OFFICIALTK1": item("OFFICIALTK1", "তাওবার শর্ত", channel_id=OFFICIAL)},
        fail_search=True,
    )
    assert len(run(content, fake).added) == 1
    assert "lastSearch" not in read_json(content, "discovery/seen/speaker-001.json")


def test_official_copy_of_an_existing_lecture_is_pointed_out(content):
    # spk001-0001 is "তাওবার শর্ত ও পদ্ধতি", durationSec 2730.
    fake = FakeClient(
        playlists={"UUofficial0000000000000a": ["OFFICIALCPY"]},
        videos={"OFFICIALCPY": item("OFFICIALCPY", "তাওবার শর্ত ও পদ্ধতি", channel_id=OFFICIAL, duration="PT45M30S")},
    )
    assert "`spk001-0001` — এটি অফিশিয়াল কপি" in discover.render_report(run(content, fake), TODAY)


def test_sweep_all_covers_every_speaker_in_one_report(content, tmp_path):
    fake = FakeClient(
        playlists={"UUofficial0000000000000a": ["OFFICIALTK1"]},
        searches={"প্রথম বক্তা": [["X0000000001"]] * 9, "দ্বিতীয় বক্তা": [["STRANGER001"]]},
        videos={
            "OFFICIALTK1": item("OFFICIALTK1", "তাওবার শর্ত", channel_id=OFFICIAL),
            "STRANGER001": stranger("STRANGER001", "দ্বিতীয় বক্তা | সবরের ফজিলত"),
        },
    )
    report, out = tmp_path / "report.md", tmp_path / "out.txt"
    code = discover.main(
        ["sweep", "--root", str(content), "--all", "--date", "2026-11-08", "--search-budget", "8",
         "--report", str(report), "--github-output", str(out)],
        client=fake,
    )
    assert code == 0
    assert "added=1\n" in out.read_text() and "name=২ জন বক্তা\n" in out.read_text()
    text = report.read_text()
    assert "১টি যোগ, ২ জন বক্তা" in text and "প্রথম বক্তা" in text and "দ্বিতীয় বক্তা" in text
    assert "Waz Mirror" in text and text.count("কীভাবে রিভিউ করবেন") == 1
    assert "সার্চ ৪/১০০" in text  # 3 queries of the first speaker (at most half of the 8) + 1 of the second
    assert read_json(content, "discovery/seen/speaker-002.json")["videos"]["STRANGER001"]["status"] == "unlisted-channel"
    assert validate.validate_repo(content)[0] == []


def test_sweep_all_keeps_finished_speakers_when_the_quota_runs_out(content):
    class Flaky(FakeClient):
        def videos(self, ids):
            ids = list(ids)
            if "STRANGER001" in ids:
                raise QuotaExceeded("videos: quotaExceeded")
            return super().videos(ids)

    fake = Flaky(
        playlists={"UUofficial0000000000000a": ["OFFICIALTK1"]},
        searches={"দ্বিতীয় বক্তা": [["STRANGER001"]]},
        videos={"OFFICIALTK1": item("OFFICIALTK1", "তাওবার শর্ত", channel_id=OFFICIAL)},
    )
    results, stopped_at = discover.sweep_all(content, fake, TODAY)
    assert [len(r.added) for r in results] == [1] and stopped_at == "speaker-002"
    assert "`speaker-002` থেকে বাকি বক্তাদের দেখা হয়নি" in discover.render_all_report(results, stopped_at, TODAY)


def test_sweep_needs_a_speaker_or_all(content, capsys):
    assert discover.main(["sweep", "--root", str(content)], client=FakeClient()) == 1
    assert "--all" in capsys.readouterr().err


def test_id_of_a_removed_lecture_is_not_reused(content):
    seen = read_json(content, "discovery/seen/speaker-001.json")
    seen["videos"]["REMOVED0001"] = {"status": "rejected", "date": "2026-11-07", "lectureId": "spk001-0009"}
    write_json(content, "discovery/seen/speaker-001.json", seen)
    fake = FakeClient(
        playlists={"UUofficial0000000000000a": ["OFFICIALTK1"]},
        videos={"OFFICIALTK1": item("OFFICIALTK1", "তাওবার শর্ত", channel_id=OFFICIAL)},
    )
    assert [c.lecture_id for c in run(content, fake).added] == ["spk001-0010"]
