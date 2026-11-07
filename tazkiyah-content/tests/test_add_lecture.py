from __future__ import annotations

import io
import json
import socket
from datetime import date
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlparse

import pytest

import add_lecture
import validate
from conftest import LECTURE_FILE, LECTURE_FILE_TEXT, read_json, write_json

VID = "dQw4w9WgXcQ"
TODAY = date(2026, 11, 7)


@pytest.fixture(autouse=True)
def no_network(monkeypatch):
    """Fail loudly if a test reaches for the network without mocking it."""

    def forbidden(*args, **kwargs):
        raise AssertionError("network access in tests: mock add_lecture.urlopen")

    monkeypatch.setattr(add_lecture, "urlopen", forbidden)


@pytest.fixture
def oembed(monkeypatch):
    """Mock a successful oEmbed response; returns the list of requested URLs."""
    calls: list[str] = []

    def fake_urlopen(request, timeout=None):
        calls.append(request.full_url)
        body = {"title": "Fetched Title — শিরোনাম", "author_name": "Some Channel"}
        return io.BytesIO(json.dumps(body).encode("utf-8"))

    monkeypatch.setattr(add_lecture, "urlopen", fake_urlopen)
    return calls


def mock_urlopen_error(monkeypatch, exc: Exception) -> None:
    def fake_urlopen(request, timeout=None):
        raise exc

    monkeypatch.setattr(add_lecture, "urlopen", fake_urlopen)


def run(repo: Path, *args: str) -> int:
    return add_lecture.main([*args, "--root", str(repo)])


# ---------------------------------------------------------------------------
# URL parsing
# ---------------------------------------------------------------------------

@pytest.mark.parametrize(
    "url",
    [
        f"https://www.youtube.com/watch?v={VID}",
        f"https://youtube.com/watch?v={VID}&t=42s",
        f"https://www.youtube.com/watch?feature=share&v={VID}",
        f"https://www.youtube.com/watch?v={VID}&list=PL1234567890&index=2",
        f"https://m.youtube.com/watch?v={VID}",
        f"https://music.youtube.com/watch?v={VID}&feature=share",
        f"http://www.youtube.com/watch?v={VID}",
        f"www.youtube.com/watch?v={VID}",
        f"https://WWW.YouTube.com/watch?v={VID}",
        f"https://youtu.be/{VID}",
        f"https://youtu.be/{VID}?si=AbCdEfGh&t=10",
        f"youtu.be/{VID}",
        f"https://www.youtube.com/shorts/{VID}",
        f"https://youtube.com/shorts/{VID}?feature=share",
        f"https://www.youtube.com/live/{VID}?si=xyz",
        f"https://www.youtube.com/embed/{VID}?start=30",
        f"https://www.youtube-nocookie.com/embed/{VID}",
        f"  https://youtu.be/{VID}  ",
        VID,
    ],
)
def test_parse_video_id_variants(url):
    assert add_lecture.parse_video_id(url) == VID


def test_parse_video_id_keeps_dash_and_underscore():
    assert add_lecture.parse_video_id("https://youtu.be/a-b_c-d_e-f") == "a-b_c-d_e-f"


@pytest.mark.parametrize(
    "url",
    [
        "",
        "https://vimeo.com/123456789",
        f"https://notyoutube.com/watch?v={VID}",
        f"https://youtube.com.evil.example/watch?v={VID}",
        f"ftp://youtube.com/watch?v={VID}",
        "https://www.youtube.com/watch?list=PL1234567890",
        "https://www.youtube.com/playlist?list=PL1234567890",
        "https://www.youtube.com/@jakariamasudofficial",
        "https://youtu.be/short",
        "https://www.youtube.com/watch?v=tooLongVideoId123",
        "https://www.youtube.com/shorts/",
    ],
)
def test_parse_video_id_rejects_bad_links(url):
    with pytest.raises(add_lecture.AddLectureError):
        add_lecture.parse_video_id(url)


# ---------------------------------------------------------------------------
# Id generation
# ---------------------------------------------------------------------------

@pytest.mark.parametrize(
    "speaker, existing, expected",
    [
        ("speaker-001", [], "spk001-0001"),
        ("speaker-001", ["spk001-0001", "spk001-0002"], "spk001-0003"),
        ("speaker-001", ["spk001-0001", "spk001-0007", "spk001-0003"], "spk001-0008"),
        ("speaker-001", ["spk002-0050", "spk001-0002"], "spk001-0003"),
        ("speaker-001", ["spk0010-0009"], "spk001-0001"),
        ("speaker-010", ["spk001-0005"], "spk010-0001"),
        ("speaker-001", ["spk001-9999"], "spk001-10000"),
    ],
)
def test_next_lecture_id(speaker, existing, expected):
    assert add_lecture.next_lecture_id(speaker, existing) == expected


def test_next_lecture_id_rejects_unexpected_speaker_id():
    with pytest.raises(add_lecture.AddLectureError):
        add_lecture.next_lecture_id("jakaria", [])


# ---------------------------------------------------------------------------
# File formatting
# ---------------------------------------------------------------------------

def test_formatter_round_trips_hand_written_style():
    assert add_lecture.format_lecture_file(json.loads(LECTURE_FILE_TEXT)) == LECTURE_FILE_TEXT


def test_formatter_handles_empty_and_nested_values():
    doc = {"speakerId": "speaker-003", "lectures": [], "extra": {"a": [], "b": {}}}
    assert add_lecture.format_lecture_file(doc) == (
        '{\n  "speakerId": "speaker-003",\n  "lectures": [],\n  "extra": { "a": [], "b": {} }\n}\n'
    )


# ---------------------------------------------------------------------------
# Adding YouTube lectures
# ---------------------------------------------------------------------------

def test_add_youtube_lecture_fetches_title_and_appends(repo, oembed):
    before = (repo / LECTURE_FILE).read_text(encoding="utf-8")

    rel, entry, payload = add_lecture.add_lecture(
        repo, "speaker-001", ["tazkiyah"], url=f"https://youtu.be/{VID}?si=x", today=TODAY
    )

    assert rel == LECTURE_FILE
    assert payload["author_name"] == "Some Channel"
    assert entry == {
        "id": "spk001-0003",
        "title": "Fetched Title — শিরোনাম",
        "categoryIds": ["tazkiyah"],
        "media": {"type": "youtube", "videoId": VID, "startSec": 0},
        "addedAt": "2026-11-07",
    }
    assert list(entry) == ["id", "title", "categoryIds", "media", "addedAt"]

    # oEmbed was asked about the canonical watch URL, as JSON.
    assert len(oembed) == 1
    requested = urlparse(oembed[0])
    assert f"{requested.scheme}://{requested.netloc}{requested.path}" == add_lecture.OEMBED_ENDPOINT
    query = parse_qs(requested.query)
    assert query == {"url": [f"https://www.youtube.com/watch?v={VID}"], "format": ["json"]}

    after = (repo / LECTURE_FILE).read_text(encoding="utf-8")
    doc = json.loads(after)
    assert doc["lectures"][-1] == entry
    assert [lec["id"] for lec in doc["lectures"]] == ["spk001-0001", "spk001-0002", "spk001-0003"]
    # Existing lines are untouched: the change is a pure append.
    assert after.startswith(before[: before.rindex("    }")])
    assert after.endswith("}\n")
    assert "শিরোনাম" in after and "\\u" not in after
    assert '      "media": { "type": "youtube", "videoId": "dQw4w9WgXcQ", "startSec": 0 },\n' in after


def test_add_uses_today_by_default(repo, oembed):
    _, entry, _ = add_lecture.add_lecture(repo, "speaker-001", ["salah"], url=VID)
    assert entry["addedAt"] == date.today().isoformat()


def test_title_flag_skips_oembed(repo):
    # no_network is active: any fetch would fail the test.
    _, entry, payload = add_lecture.add_lecture(
        repo, "speaker-001", ["salah"], url=VID, title="  নিজের শিরোনাম  ", today=TODAY
    )
    assert payload is None
    assert entry["title"] == "নিজের শিরোনাম"


def test_multiple_categories_are_deduplicated_in_order(repo):
    _, entry, _ = add_lecture.add_lecture(
        repo, "speaker-001", ["salah", "ibadah", "salah"], url=VID, title="t", today=TODAY
    )
    assert entry["categoryIds"] == ["salah", "ibadah"]


def test_cli_success(repo, oembed, capsys):
    code = run(repo, "--speaker", "speaker-001", "--category", "tazkiyah", "--category", "salah",
               f"https://www.youtube.com/shorts/{VID}")
    assert code == 0
    out = capsys.readouterr().out
    assert "Added spk001-0003" in out and "Some Channel" in out
    assert read_json(repo, LECTURE_FILE)["lectures"][-1]["categoryIds"] == ["tazkiyah", "salah"]


def test_creates_lecture_file_for_speaker_without_one(repo):
    _, entry, _ = add_lecture.add_lecture(
        repo, "speaker-002", ["tazkiyah"], url=VID, title="প্রথম লেকচার", today=TODAY
    )
    path = repo / "data/lectures/speaker-002.json"
    assert path.exists()
    doc = json.loads(path.read_text(encoding="utf-8"))
    assert doc == {"speakerId": "speaker-002", "lectures": [entry]}
    assert list(doc) == ["speakerId", "lectures"]
    assert entry["id"] == "spk002-0001"
    assert validate.validate_repo(repo)[0] == []


def test_id_accounts_for_lectures_moved_to_other_files(repo):
    # A spk002 lecture that lives in speaker-001's file (e.g. it was re-attributed).
    doc = read_json(repo, LECTURE_FILE)
    doc["lectures"].append({
        "id": "spk002-0005", "title": "moved", "categoryIds": ["salah"],
        "media": {"type": "youtube", "videoId": "BBBBBBBBBBB"}, "addedAt": "2026-11-07",
    })
    write_json(repo, LECTURE_FILE, doc)
    _, entry, _ = add_lecture.add_lecture(repo, "speaker-002", ["salah"], url=VID, title="t", today=TODAY)
    assert entry["id"] == "spk002-0006"


def test_result_passes_validation(repo, oembed):
    add_lecture.add_lecture(repo, "speaker-001", ["tazkiyah"], url=VID, today=TODAY)
    assert validate.validate_repo(repo)[0] == []


# ---------------------------------------------------------------------------
# Refusals and errors (file must stay unchanged)
# ---------------------------------------------------------------------------

def assert_refused(repo, capsys, args, message):
    before = (repo / LECTURE_FILE).read_bytes()
    assert run(repo, *args) == 1
    assert message in capsys.readouterr().err
    assert (repo / LECTURE_FILE).read_bytes() == before


def test_refuses_duplicate_video_same_speaker(repo, capsys):
    assert_refused(
        repo, capsys,
        ["--speaker", "speaker-001", "--category", "salah", "https://youtu.be/AAAAAAAAAAA"],
        "already in data/lectures/speaker-001.json as 'spk001-0001'",
    )


def test_refuses_duplicate_video_other_speaker(repo, capsys):
    assert_refused(
        repo, capsys,
        ["--speaker", "speaker-002", "--category", "salah", "--title", "x",
         "https://www.youtube.com/watch?v=AAAAAAAAAAA"],
        "already in data/lectures/speaker-001.json",
    )
    assert not (repo / "data/lectures/speaker-002.json").exists()


def test_refuses_unknown_speaker(repo, capsys):
    assert_refused(
        repo, capsys, ["--speaker", "speaker-009", "--category", "salah", "--title", "x", VID],
        "unknown speaker 'speaker-009'",
    )


def test_refuses_unknown_category(repo, capsys):
    assert_refused(
        repo, capsys, ["--speaker", "speaker-001", "--category", "nope", "--title", "x", VID],
        "unknown category id(s): nope",
    )


def test_refuses_bad_url(repo, capsys):
    assert_refused(
        repo, capsys, ["--speaker", "speaker-001", "--category", "salah", "https://vimeo.com/1"],
        "not a YouTube link",
    )


def test_refuses_empty_title(repo, capsys):
    assert_refused(
        repo, capsys, ["--speaker", "speaker-001", "--category", "salah", "--title", " ", VID],
        "--title must not be empty",
    )


def http_error(code: int, reason: str) -> HTTPError:
    return HTTPError("https://www.youtube.com/oembed", code, reason, None, io.BytesIO(b""))


@pytest.mark.parametrize(
    "make_exc, message",
    [
        (lambda: http_error(401, "Unauthorized"), "embedding is disabled"),
        (lambda: http_error(403, "Forbidden"), "embedding is disabled"),
        (lambda: http_error(404, "Not Found"), "video not found"),
        (lambda: http_error(500, "Server Error"), "HTTP 500"),
        (lambda: URLError("nodename nor servname provided"), "could not reach YouTube oEmbed"),
        (lambda: socket.timeout("timed out"), "could not reach YouTube oEmbed"),
    ],
    ids=["401", "403", "404", "500", "offline", "timeout"],
)
def test_oembed_failures_error_clearly(repo, capsys, monkeypatch, make_exc, message):
    mock_urlopen_error(monkeypatch, make_exc())
    assert_refused(repo, capsys, ["--speaker", "speaker-001", "--category", "salah", VID], message)


def test_oembed_response_without_title(repo, capsys, monkeypatch):
    monkeypatch.setattr(add_lecture, "urlopen", lambda req, timeout=None: io.BytesIO(b'{"author_name": "x"}'))
    assert_refused(repo, capsys, ["--speaker", "speaker-001", "--category", "salah", VID], "has no title")


def test_oembed_response_not_json(repo, capsys, monkeypatch):
    monkeypatch.setattr(add_lecture, "urlopen", lambda req, timeout=None: io.BytesIO(b"<html>"))
    assert_refused(repo, capsys, ["--speaker", "speaker-001", "--category", "salah", VID], "unexpected oEmbed response")


# ---------------------------------------------------------------------------
# Audio lectures
# ---------------------------------------------------------------------------

def test_add_audio_lecture(repo):
    url = "https://archive.org/download/ITEM/lecture-02.m4a"
    _, entry, payload = add_lecture.add_lecture(
        repo, "speaker-001", ["salah"], audio=url, title="অডিও লেকচার", today=TODAY
    )
    assert payload is None
    assert entry["media"] == {"type": "audio", "url": url, "mimeType": "audio/mp4"}
    assert entry["id"] == "spk001-0003"
    assert validate.validate_repo(repo)[0] == []


def test_audio_without_known_extension_has_no_mime_type(repo):
    url = "https://example.org/stream?id=42"
    _, entry, _ = add_lecture.add_lecture(repo, "speaker-001", ["salah"], audio=url, title="t", today=TODAY)
    assert entry["media"] == {"type": "audio", "url": url}


@pytest.mark.parametrize(
    "args, message",
    [
        (["--audio", "http://example.org/a.mp3", "--title", "t"], "must start with https://"),
        (["--audio", "https://example.org/a.mp3"], "--title is required with --audio"),
        (["--audio", "https://archive.org/download/ITEM/file.mp3", "--title", "t"], "already in"),
        (["--audio", "https://example.org/a.mp3", "--title", "t", VID], "exactly one"),
        (["--title", "t"], "exactly one"),
    ],
)
def test_audio_refusals(repo, capsys, args, message):
    assert_refused(repo, capsys, ["--speaker", "speaker-001", "--category", "salah", *args], message)
