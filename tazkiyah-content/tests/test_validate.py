from __future__ import annotations

import json
from pathlib import Path
from typing import Callable

import pytest
from jsonschema import Draft202012Validator

import validate
from conftest import LECTURE_FILE, REPO_ROOT, read_json, write_json


def problems(repo: Path) -> list[str]:
    return validate.validate_repo(repo)[0]


def edit(repo: Path, rel: str, change: Callable[[dict], None]) -> None:
    doc = read_json(repo, rel)
    change(doc)
    write_json(repo, rel, doc)


def lecture(doc: dict, index: int = 0) -> dict:
    return doc["lectures"][index]


# ---------------------------------------------------------------------------
# Valid repos
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("name", sorted(validate.SCHEMA_FILES))
def test_schemas_are_valid_draft_2020_12(name):
    schema = json.loads((REPO_ROOT / validate.SCHEMA_FILES[name]).read_text(encoding="utf-8"))
    assert schema["$schema"] == "https://json-schema.org/draft/2020-12/schema"
    Draft202012Validator.check_schema(schema)


def test_fixture_repo_is_valid(repo, capsys):
    assert validate.main(["--root", str(repo)]) == 0
    out = capsys.readouterr().out
    assert "OK: 2 speaker(s), 3 categor(ies), 2 lecture(s) in 1 file(s)." in out


def test_real_repo_is_valid():
    lines, counts = validate.validate_repo(REPO_ROOT)
    assert lines == []
    assert counts["lectures"] > 0


def test_optional_fields_may_be_missing(repo):
    def strip(doc):
        doc["lectures"] = [{
            "id": "spk001-0001", "title": "t", "categoryIds": ["salah"],
            "media": {"type": "youtube", "videoId": "AAAAAAAAAAA"}, "addedAt": "2026-11-07",
        }]
    edit(repo, LECTURE_FILE, strip)
    assert problems(repo) == []


def test_video_media_and_unknown_top_level_keys_are_allowed(repo):
    def change(doc):
        doc["note"] = "file-level keys are free-form"
        lecture(doc, 1)["media"] = {"type": "video", "url": "https://example.org/a.mp4", "mimeType": "video/mp4"}
    edit(repo, LECTURE_FILE, change)
    assert problems(repo) == []


def test_speaker_photo_that_exists_is_valid(repo):
    (repo / "images/speakers/speaker-001.webp").write_bytes(b"RIFF....WEBP")
    edit(repo, "data/speakers.json",
         lambda d: d["speakers"][0].update(photo="images/speakers/speaker-001.webp"))
    assert problems(repo) == []


def test_missing_manifest_is_not_an_error(repo):
    (repo / "manifest.json").unlink()
    assert problems(repo) == []


# ---------------------------------------------------------------------------
# Failure cases: (description, mutation, expected text in a problem line)
# ---------------------------------------------------------------------------

def _set_lecture(index: int, **fields):
    def change(repo):
        edit(repo, LECTURE_FILE, lambda d: lecture(d, index).update(fields))
    return change


def _del_lecture_field(field: str):
    def change(repo):
        edit(repo, LECTURE_FILE, lambda d: lecture(d).pop(field))
    return change


def _set_category(index: int, **fields):
    def change(repo):
        edit(repo, "data/categories.json", lambda d: d["categories"][index].update(fields))
    return change


def _broken_json(repo):
    (repo / LECTURE_FILE).write_text('{"speakerId": "speaker-001", "lectures": [', encoding="utf-8")


def _duplicate_id_across_files(repo):
    write_json(repo, "data/lectures/speaker-002.json", {
        "speakerId": "speaker-002",
        "lectures": [{
            "id": "spk001-0001", "title": "t", "categoryIds": ["salah"],
            "media": {"type": "youtube", "videoId": "CCCCCCCCCCC"}, "addedAt": "2026-11-07",
        }],
    })


def _unknown_speaker_file(repo):
    write_json(repo, "data/lectures/speaker-003.json", {"speakerId": "speaker-003", "lectures": []})


def _speaker_id_mismatch(repo):
    edit(repo, LECTURE_FILE, lambda d: d.update(speakerId="speaker-002"))


def _category_cycle(repo):
    def change(d):
        d["categories"][0]["parentId"] = "salah"  # ibadah -> salah -> ibadah
    edit(repo, "data/categories.json", change)


def _duplicate_category(repo):
    edit(repo, "data/categories.json", lambda d: d["categories"].append(dict(d["categories"][0])))


def _duplicate_speaker(repo):
    edit(repo, "data/speakers.json", lambda d: d["speakers"][1].update(id="speaker-001"))


def _missing_photo(repo):
    edit(repo, "data/speakers.json",
         lambda d: d["speakers"][0].update(photo="images/speakers/speaker-001.webp"))


def _speaker_without_name(repo):
    edit(repo, "data/speakers.json", lambda d: d["speakers"][0].pop("name"))


def _bad_manifest_hash(repo):
    edit(repo, "manifest.json", lambda d: d["files"][0].update(sha256="xyz"))


def _missing_speakers_file(repo):
    (repo / "data/speakers.json").unlink()


FAILURES = [
    ("invalid JSON", _broken_json, "invalid JSON"),
    ("missing addedAt", _del_lecture_field("addedAt"), "'addedAt' is required"),
    ("missing media", _del_lecture_field("media"), "'media' is required"),
    ("empty categoryIds", _set_lecture(0, categoryIds=[]), "lectures[0] (spk001-0001).categoryIds"),
    ("short videoId", _set_lecture(0, media={"type": "youtube", "videoId": "AAAAAAAAAA"}),
     "YouTube videoId must be exactly 11 characters"),
    ("bad videoId char", _set_lecture(0, media={"type": "youtube", "videoId": "AAAAA$AAAAA"}),
     "YouTube videoId must be exactly 11 characters"),
    ("negative startSec", _set_lecture(0, media={"type": "youtube", "videoId": "AAAAAAAAAAA", "startSec": -1}),
     "media.startSec"),
    ("http audio", _set_lecture(1, media={"type": "audio", "url": "http://archive.org/x.mp3"}),
     "media URL must start with https://"),
    ("http video", _set_lecture(1, media={"type": "video", "url": "http://example.org/x.mp4"}),
     "media URL must start with https://"),
    ("unknown media type", _set_lecture(1, media={"type": "podcast", "url": "https://x.org/a"}),
     "unknown media type 'podcast' (must be one of: youtube, audio, video)"),
    ("media without type", _set_lecture(1, media={"url": "https://x.org/a.mp3"}), "missing 'type'"),
    ("youtube key in audio", _set_lecture(1, media={"type": "audio", "url": "https://x.org/a.mp3", "videoId": "AAAAAAAAAAA"}),
     "unknown key 'videoId'"),
    ("typo in lecture key", _set_lecture(0, durationsec=10), "did you mean 'durationSec'?"),
    ("bad date format", _set_lecture(0, publishedAt="2025-3-14"), "date must be YYYY-MM-DD"),
    ("impossible date", _set_lecture(0, addedAt="2025-02-30"), "not a real calendar date"),
    ("zero duration", _set_lecture(0, durationSec=0), "durationSec"),
    ("series without part", _set_lecture(0, series={"id": "s", "title": "t"}), "'part' is required"),
    ("bad lecture id", _set_lecture(0, id="spk001-1"), "id 'spk001-1' does not match"),
    ("duplicate id in file", _set_lecture(1, id="spk001-0001"), "duplicate lecture id 'spk001-0001'"),
    ("duplicate id across files", _duplicate_id_across_files, "duplicate lecture id 'spk001-0001'"),
    ("speaker not in speakers.json", _unknown_speaker_file, "speakerId 'speaker-003' is not in data/speakers.json"),
    ("speakerId != file name", _speaker_id_mismatch, "does not match the file name (expected 'speaker-001')"),
    ("unknown categoryId", _set_lecture(0, categoryIds=["tazkiyah", "nope"]), "unknown categoryId 'nope'"),
    ("unknown parentId", _set_category(1, parentId="ghost"), "parentId 'ghost' does not exist"),
    ("self parent", _set_category(0, parentId="ibadah"), "parentId points to itself"),
    ("category cycle", _category_cycle, "loops back to this category"),
    ("typo in category key", _set_category(1, parentID="ibadah"), "did you mean 'parentId'?"),
    ("duplicate category id", _duplicate_category, "duplicate category id 'ibadah'"),
    ("duplicate speaker id", _duplicate_speaker, "duplicate speaker id 'speaker-001'"),
    ("missing photo file", _missing_photo, "photo file 'images/speakers/speaker-001.webp' does not exist"),
    ("speaker without name", _speaker_without_name, "'name' is required"),
    ("bad manifest hash", _bad_manifest_hash, "manifest.json: files[0].sha256"),
    ("missing speakers.json", _missing_speakers_file, "data/speakers.json: file not found"),
]


@pytest.mark.parametrize("mutate, expected", [f[1:] for f in FAILURES], ids=[f[0] for f in FAILURES])
def test_failure_cases(repo, capsys, mutate, expected):
    mutate(repo)
    assert validate.main(["--root", str(repo)]) == 1
    err = capsys.readouterr().err
    assert expected in err, err
    assert "Validation failed" in err


def test_every_problem_is_reported_not_just_the_first(repo):
    def change(doc):
        lecture(doc, 0)["categoryIds"] = ["nope"]
        lecture(doc, 1)["media"] = {"type": "audio", "url": "http://x.org/a.mp3"}
    edit(repo, LECTURE_FILE, change)
    lines = problems(repo)
    assert len(lines) == 2
    assert all(line.startswith(f"{LECTURE_FILE}: lectures[") for line in lines)


# ---------------------------------------------------------------------------
# Same recording twice, series, and the bot's files
# ---------------------------------------------------------------------------

def _second_speaker_file(repo: Path, media: dict, series: dict | None = None) -> None:
    lec = {"id": "spk002-0001", "title": "আরেকটি", "categoryIds": ["salah"], "media": media, "addedAt": "2026-11-08"}
    if series:
        lec["series"] = series
    write_json(repo, "data/lectures/speaker-002.json", {"speakerId": "speaker-002", "lectures": [lec]})


def test_same_youtube_video_in_two_speaker_files_is_an_error(repo):
    _second_speaker_file(repo, {"type": "youtube", "videoId": "AAAAAAAAAAA", "startSec": 0})
    assert problems(repo) == [
        "data/lectures/speaker-002.json: lectures[0] (spk002-0001): video 'AAAAAAAAAAA' is already in the "
        "catalog as spk001-0001 (data/lectures/speaker-001.json)"
    ]


def test_same_audio_url_twice_is_an_error(repo):
    _second_speaker_file(repo, {"type": "audio", "url": "https://archive.org/download/ITEM/file.mp3"})
    assert "media URL 'https://archive.org/download/ITEM/file.mp3' is already in the catalog" in problems(repo)[0]


def test_series_title_and_parts_must_agree(repo):
    _second_speaker_file(
        repo,
        {"type": "youtube", "videoId": "BBBBBBBBBBB"},
        {"id": "series-tawbah", "title": "অন্য নাম", "part": 1},
    )
    found = problems(repo)
    assert any("series 'series-tawbah' has title 'অন্য নাম' here but 'তাওবা সিরিজ' elsewhere" in p for p in found)
    assert any("series 'series-tawbah' part 1 is also used by spk001-0001" in p for p in found)


SOURCES_OK = {
    "speakers": [
        {
            "speakerId": "speaker-001",
            "channels": [{"id": "UCaaaaaaaaaaaaaaaaaaaaaa", "name": "অফিশিয়াল", "official": True}],
            "playlists": [],
            "queries": ["প্রথম বক্তা"],
            "mustMatch": ["প্রথম বক্তা"],
            "exclude": [],
            "defaultCategoryIds": ["salah"],
        }
    ]
}


def test_discovery_files_are_validated(repo):
    write_json(repo, "discovery/sources.json", SOURCES_OK)
    write_json(repo, "discovery/seen/speaker-001.json", {"speakerId": "speaker-001", "videos": {
        "AAAAAAAAAAA": {"status": "proposed", "date": "2026-11-08", "lectureId": "spk001-0001"}}})
    assert problems(repo) == []


def test_discovery_problems_are_reported(repo):
    bad = json.loads(json.dumps(SOURCES_OK))
    bad["speakers"][0]["speakerId"] = "speaker-009"
    bad["speakers"][0]["defaultCategoryIds"] = ["nope"]
    bad["speakers"][0]["channels"][0]["id"] = "not-a-channel"
    write_json(repo, "discovery/sources.json", bad)
    write_json(repo, "discovery/seen/speaker-002.json", {"speakerId": "speaker-001", "videos": {
        "AAAAAAAAAAA": {"status": "maybe", "date": "2026-11-08"}}})
    found = "\n".join(problems(repo))
    assert "speakerId 'speaker-009' is not in data/speakers.json" in found
    assert "unknown categoryId 'nope'" in found
    assert "discovery/sources.json: speakers[0].channels[0] (not-a-channel).id" in found
    assert "discovery/seen/speaker-002.json: speakerId 'speaker-001' does not match the file name" in found
    assert "discovery/seen/speaker-002.json: videos.AAAAAAAAAAA.status" in found
