"""youtube_api.py against a fake transport: key handling, retries, quota and paging."""
from __future__ import annotations

import io
import json
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlparse

import pytest

import youtube_api
from youtube_api import QuotaExceeded, YouTubeApiError, YouTubeClient, parse_duration, uploads_playlist

KEY = "AIza-test-key"


class FakeResponse(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


def http_error(status: int, reason: str = "", message: str = "nope") -> HTTPError:
    body = json.dumps({"error": {"code": status, "message": message, "errors": [{"reason": reason}]}}).encode()
    return HTTPError("https://example.invalid", status, message, {}, io.BytesIO(body))


class Transport:
    """Plays back a list of responses (dict -> JSON body, Exception -> raised)."""

    def __init__(self, *responses):
        self.responses = list(responses)
        self.requests = []

    def __call__(self, request, timeout):
        self.requests.append(request)
        item = self.responses.pop(0)
        if isinstance(item, Exception):
            raise item
        return FakeResponse(json.dumps(item).encode())


def client(*responses):
    transport = Transport(*responses)
    return YouTubeClient(KEY, opener=transport, sleep=lambda s: None), transport


@pytest.mark.parametrize(
    ("value", "seconds"),
    [("PT1H2M3S", 3723), ("PT15M33S", 933), ("PT45S", 45), ("P1DT1M", 86460), ("P0D", 0), ("", 0), (None, 0), ("bad", 0)],
)
def test_parse_duration(value, seconds):
    assert parse_duration(value) == seconds


def test_uploads_playlist():
    assert uploads_playlist("UCfx50GXZb6gDbxfTBScSGaw") == "UUfx50GXZb6gDbxfTBScSGaw"
    with pytest.raises(ValueError):
        uploads_playlist("PLabc")


def test_missing_key_is_refused():
    with pytest.raises(YouTubeApiError):
        YouTubeClient("")


def test_key_goes_in_header_never_in_url():
    api, transport = client({"items": []})
    api.videos(["AAAAAAAAAAA"])
    request = transport.requests[0]
    assert KEY not in request.full_url
    assert request.get_header("X-goog-api-key") == KEY


def test_playlist_pages_until_no_token_and_counts_units():
    api, _ = client(
        {"items": [{"contentDetails": {"videoId": "AAAAAAAAAAA"}}], "nextPageToken": "p2"},
        {"items": [{"contentDetails": {"videoId": "BBBBBBBBBBB"}}]},
    )
    assert api.playlist_video_ids("UUx") == ["AAAAAAAAAAA", "BBBBBBBBBBB"]
    assert api.usage.units == 2 and api.usage.searches == 0


def test_missing_playlist_gives_empty_list():
    api, _ = client(http_error(404, "playlistNotFound"))
    assert api.playlist_video_ids("UUgone") == []


def test_search_uses_search_bucket_and_stops_at_max_pages():
    page = {"items": [{"id": {"videoId": "AAAAAAAAAAA"}}], "nextPageToken": "more"}
    api, transport = client(page, page, page)
    assert api.search_video_ids("হারুন ইজহার", "date", max_pages=2) == ["AAAAAAAAAAA", "AAAAAAAAAAA"]
    assert api.usage.searches == 2 and api.usage.units == 0
    query = parse_qs(urlparse(transport.requests[0].full_url).query)
    assert query["videoEmbeddable"] == ["true"] and query["type"] == ["video"] and query["order"] == ["date"]


def test_videos_are_fetched_fifty_at_a_time():
    ids = [f"{i:011d}" for i in range(120)]
    api, transport = client({"items": []}, {"items": []}, {"items": []})
    api.videos(ids)
    assert len(transport.requests) == 3


def test_retries_server_errors_then_succeeds():
    api, transport = client(http_error(503), URLError("down"), {"items": [{"id": "AAAAAAAAAAA"}]})
    assert "AAAAAAAAAAA" in api.videos(["AAAAAAAAAAA"])
    assert len(transport.requests) == 3


def test_gives_up_after_retries():
    api, _ = client(http_error(500), http_error(500), http_error(500))
    with pytest.raises(YouTubeApiError, match="gave up"):
        api.videos(["AAAAAAAAAAA"])


def test_quota_exceeded_stops_at_once():
    api, transport = client(http_error(403, "quotaExceeded"))
    with pytest.raises(QuotaExceeded):
        api.search_video_ids("x", "date", 1)
    assert len(transport.requests) == 1


def test_bad_request_is_not_retried_and_hides_the_key():
    api, transport = client(http_error(400, "keyInvalid", "API key not valid"))
    with pytest.raises(YouTubeApiError) as info:
        api.videos(["AAAAAAAAAAA"])
    assert KEY not in str(info.value)
    assert len(transport.requests) == 1


def test_module_has_no_third_party_imports():
    source = open(youtube_api.__file__, encoding="utf-8").read()
    assert "import requests" not in source
