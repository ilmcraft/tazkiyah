"""A small YouTube Data API v3 client for the lecture bot (standard library only).

Quota (developers.google.com/youtube/v3/determine_quota_cost, checked 2026-11):
  * channels.list, playlistItems.list, videos.list: 1 unit per call from the daily 10,000-unit pool;
  * search.list: its own bucket since 2026-06-01 — 100 calls per day, 1 per call.
The client counts both, so a run can stop before the free quota is gone.

The API key travels in the X-Goog-Api-Key header, never in a URL, so it cannot leak into logs
or error messages.
"""
from __future__ import annotations

import json
import re
import socket
import time
from dataclasses import dataclass
from typing import Any, Callable, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

API_ROOT = "https://www.googleapis.com/youtube/v3"
PAGE_SIZE = 50
_RETRYABLE_STATUS = {429, 500, 502, 503, 504}
_QUOTA_REASONS = {"quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded", "userRateLimitExceeded"}
_DURATION = re.compile(r"^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$")


class YouTubeApiError(Exception):
    """The API refused a request (bad key, bad parameter, …)."""


class QuotaExceeded(YouTubeApiError):
    """The daily quota is used up; try again tomorrow."""


def parse_duration(value: str | None) -> int:
    """ISO 8601 duration ("PT1H2M3S", "P0D") to seconds; 0 when missing or unknown."""
    match = _DURATION.match(value or "")
    if not match:
        return 0
    days, hours, minutes, seconds = (int(g) if g else 0 for g in match.groups())
    return ((days * 24 + hours) * 60 + minutes) * 60 + seconds


def uploads_playlist(channel_id: str) -> str:
    """A channel's "all uploads" playlist: UCxxxx -> UUxxxx."""
    if not channel_id.startswith("UC"):
        raise ValueError(f"not a channel id: {channel_id!r}")
    return "UU" + channel_id[2:]


@dataclass
class Usage:
    units: int = 0
    searches: int = 0


class YouTubeClient:
    def __init__(
        self,
        api_key: str,
        opener: Callable[..., Any] = urlopen,
        sleep: Callable[[float], None] = time.sleep,
        timeout: float = 20,
        retries: int = 3,
    ) -> None:
        if not api_key:
            raise YouTubeApiError("YOUTUBE_API_KEY is not set")
        self._key = api_key
        self._open = opener
        self._sleep = sleep
        self._timeout = timeout
        self._retries = retries
        self.usage = Usage()

    # ------------------------------------------------------------------ transport

    def _get(self, method: str, params: dict[str, Any]) -> dict[str, Any]:
        url = f"{API_ROOT}/{method}?{urlencode(params)}"
        request = Request(url, headers={"X-Goog-Api-Key": self._key, "Accept": "application/json"})
        if method == "search":
            self.usage.searches += 1
        else:
            self.usage.units += 1
        last_error = ""
        for attempt in range(self._retries):
            try:
                with self._open(request, timeout=self._timeout) as response:
                    return json.loads(response.read().decode("utf-8"))
            except HTTPError as exc:
                status, reason, message = exc.code, *_error_details(exc)
                if reason in _QUOTA_REASONS and status in {403, 429}:
                    raise QuotaExceeded(f"{method}: {reason} — the daily quota is used up") from None
                if status not in _RETRYABLE_STATUS:
                    raise YouTubeApiError(f"{method}: HTTP {status} {reason}: {message}") from None
                last_error = f"HTTP {status}"
            except (URLError, socket.timeout, TimeoutError, ConnectionError) as exc:
                last_error = type(exc).__name__
            self._sleep(2 ** attempt)
        raise YouTubeApiError(f"{method}: gave up after {self._retries} tries ({last_error})")

    # ------------------------------------------------------------------ methods

    def playlist_video_ids(self, playlist_id: str, max_pages: int = 200) -> list[str]:
        """Every video in a playlist (newest first for uploads playlists). Missing playlist -> []."""
        ids: list[str] = []
        token: str | None = None
        for _ in range(max_pages):
            params = {"part": "contentDetails", "playlistId": playlist_id, "maxResults": PAGE_SIZE}
            if token:
                params["pageToken"] = token
            try:
                page = self._get("playlistItems", params)
            except YouTubeApiError as exc:
                if "playlistNotFound" in str(exc) or "HTTP 404" in str(exc):
                    return ids
                raise
            ids += [item["contentDetails"]["videoId"] for item in page.get("items", []) if item.get("contentDetails")]
            token = page.get("nextPageToken")
            if not token:
                break
        return ids

    def search_video_ids(
        self, query: str, order: str, max_pages: int, published_after: str | None = None
    ) -> list[str]:
        """Embeddable videos matching a query, up to max_pages × 50 (search bucket: 1 per page).

        `published_after` (YYYY-MM-DD) keeps only videos uploaded on or after that day."""
        ids: list[str] = []
        token: str | None = None
        for _ in range(max_pages):
            params = {
                "part": "id",
                "type": "video",
                "q": query,
                "order": order,
                "maxResults": PAGE_SIZE,
                "regionCode": "BD",
                "relevanceLanguage": "bn",
                "videoEmbeddable": "true",
            }
            if published_after:
                params["publishedAfter"] = f"{published_after}T00:00:00Z"
            if token:
                params["pageToken"] = token
            page = self._get("search", params)
            ids += [item["id"]["videoId"] for item in page.get("items", []) if item.get("id", {}).get("videoId")]
            token = page.get("nextPageToken")
            if not token:
                break
        return ids

    def videos(self, video_ids: Iterable[str]) -> dict[str, dict[str, Any]]:
        """Details for up to any number of ids (50 per call). Deleted/private ids are simply absent."""
        unique = list(dict.fromkeys(video_ids))
        found: dict[str, dict[str, Any]] = {}
        for start in range(0, len(unique), PAGE_SIZE):
            batch = unique[start:start + PAGE_SIZE]
            page = self._get("videos", {"part": "snippet,contentDetails,status", "id": ",".join(batch)})
            for item in page.get("items", []):
                found[item["id"]] = item
        return found


def _error_details(exc: HTTPError) -> tuple[str, str]:
    """(reason, message) from a Google API error body, without echoing the request URL."""
    try:
        body = json.loads(exc.read().decode("utf-8"))
        error = body.get("error", {})
        reasons = [e.get("reason", "") for e in error.get("errors", [])]
        return (reasons[0] if reasons else ""), str(error.get("message", ""))[:200]
    except Exception:  # noqa: BLE001 - any unreadable body is just "unknown"
        return "", ""
