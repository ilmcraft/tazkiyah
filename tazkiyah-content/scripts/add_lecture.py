#!/usr/bin/env python3
"""Add a lecture to data/lectures/<speaker>.json.

YouTube (title fetched from YouTube oEmbed — no API key needed):
    python scripts/add_lecture.py --speaker speaker-001 --category tazkiyah "https://youtu.be/XXXXXXXXXXX"

Several categories / your own title (skips the oEmbed lookup):
    python scripts/add_lecture.py --speaker speaker-001 --category salah --category ibadah \
        --title "সালাতে খুশু" "https://www.youtube.com/watch?v=XXXXXXXXXXX"

Direct audio link instead of YouTube (--title is required):
    python scripts/add_lecture.py --speaker speaker-001 --category salah \
        --title "সালাতে খুশু" --audio "https://archive.org/download/ITEM/file.mp3"

The new lecture gets the next sequential id for the speaker (spk001-0009 after
spk001-0008) and addedAt = today. The same videoId / audio URL is never added twice.
Only the Python standard library is used.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import socket
import sys
import tempfile
from datetime import date
from pathlib import Path, PurePosixPath
from typing import Any, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urlparse
from urllib.request import Request, urlopen

REPO_ROOT = Path(__file__).resolve().parent.parent
SPEAKERS = "data/speakers.json"
CATEGORIES = "data/categories.json"
LECTURES_DIR = "data/lectures"

OEMBED_ENDPOINT = "https://www.youtube.com/oembed"
OEMBED_TIMEOUT_SEC = 15

VIDEO_ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")
YOUTUBE_HOSTS = {
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
    "music.youtube.com",
    "youtube-nocookie.com",
    "www.youtube-nocookie.com",
}
SHORT_HOSTS = {"youtu.be", "www.youtu.be"}
# youtube.com/<prefix>/<videoId>
PATH_PREFIXES = {"shorts", "live", "embed", "v", "e"}

AUDIO_MIME_TYPES = {
    ".mp3": "audio/mpeg",
    ".m4a": "audio/mp4",
    ".aac": "audio/aac",
    ".ogg": "audio/ogg",
    ".oga": "audio/ogg",
    ".opus": "audio/opus",
    ".wav": "audio/wav",
    ".flac": "audio/flac",
    ".webm": "audio/webm",
}


class AddLectureError(Exception):
    """A user-facing error; main() prints it and exits with status 1."""


# ---------------------------------------------------------------------------
# YouTube links
# ---------------------------------------------------------------------------

def parse_video_id(url: str) -> str:
    """Extract the 11-character video id from any common YouTube link (or a bare id)."""
    raw = url.strip()
    if VIDEO_ID_RE.match(raw):
        return raw
    if "://" not in raw:
        raw = "https://" + raw
    parsed = urlparse(raw)
    host = (parsed.hostname or "").lower()
    if parsed.scheme not in ("http", "https") or not (host in YOUTUBE_HOSTS or host in SHORT_HOSTS):
        raise AddLectureError(f"not a YouTube link: {url!r}")

    segments = [s for s in parsed.path.split("/") if s]
    candidate = None
    if host in SHORT_HOSTS:
        candidate = segments[0] if segments else None
    elif segments[:1] == ["watch"]:
        candidate = (parse_qs(parsed.query).get("v") or [None])[0]
    elif len(segments) >= 2 and segments[0] in PATH_PREFIXES:
        candidate = segments[1]

    if not candidate or not VIDEO_ID_RE.match(candidate):
        raise AddLectureError(
            f"could not find an 11-character video id in {url!r} "
            "(expected a watch?v=, youtu.be/, /shorts/, /live/ or /embed/ link)"
        )
    return candidate


def fetch_oembed(video_id: str, timeout: float = OEMBED_TIMEOUT_SEC) -> dict[str, Any]:
    """Look the video up via YouTube oEmbed. Fails if the video can't be embedded."""
    watch_url = f"https://www.youtube.com/watch?v={video_id}"
    query = urlencode({"url": watch_url, "format": "json"})
    request = Request(
        f"{OEMBED_ENDPOINT}?{query}", headers={"User-Agent": "tazkiyah-content/add_lecture"}
    )
    skip_hint = 'Pass --title "..." to add it without the lookup.'
    try:
        with urlopen(request, timeout=timeout) as response:
            body = response.read()
    except HTTPError as exc:
        if exc.code in (401, 403):
            reason = "embedding is disabled or the video is private — it will not play in the app"
        elif exc.code in (400, 404):
            reason = "video not found (deleted, private or a wrong id)"
        else:
            reason = f"HTTP {exc.code} {exc.reason}"
        raise AddLectureError(f"YouTube oEmbed lookup failed for {video_id}: {reason}. {skip_hint}") from exc
    except (URLError, socket.timeout, TimeoutError, OSError) as exc:
        reason = getattr(exc, "reason", exc)
        raise AddLectureError(
            f"could not reach YouTube oEmbed ({reason}). Check your internet connection. {skip_hint}"
        ) from exc

    try:
        payload = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AddLectureError(f"unexpected oEmbed response for {video_id}. {skip_hint}") from exc
    title = payload.get("title") if isinstance(payload, dict) else None
    if not isinstance(title, str) or not title.strip():
        raise AddLectureError(f"oEmbed response for {video_id} has no title. {skip_hint}")
    return payload


# ---------------------------------------------------------------------------
# Ids
# ---------------------------------------------------------------------------

def lecture_id_prefix(speaker_id: str) -> str:
    """speaker-001 -> spk001"""
    match = re.fullmatch(r"speaker-([0-9]+)", speaker_id)
    if not match:
        raise AddLectureError(f"speaker id must look like 'speaker-001', got {speaker_id!r}")
    return f"spk{match.group(1)}"


def next_lecture_id(speaker_id: str, existing_ids: Iterable[str]) -> str:
    """Next sequential id after the highest existing number for this speaker's prefix."""
    prefix = lecture_id_prefix(speaker_id)
    pattern = re.compile(rf"^{re.escape(prefix)}-([0-9]+)$")
    numbers = []
    for lecture_id in existing_ids:
        match = pattern.match(lecture_id) if isinstance(lecture_id, str) else None
        if match:
            numbers.append(int(match.group(1)))
    return f"{prefix}-{max(numbers, default=0) + 1:04d}"


# ---------------------------------------------------------------------------
# JSON files
# ---------------------------------------------------------------------------

def _read_json(path: Path, rel: str) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise AddLectureError(f"{rel} not found") from exc
    except json.JSONDecodeError as exc:
        raise AddLectureError(
            f"{rel} is not valid JSON (line {exc.lineno}, column {exc.colno}: {exc.msg}); fix it first"
        ) from exc


def _inline(value: Any) -> str:
    """One-line JSON in the repo's style: ["a", "b"] and { "k": "v", "n": 1 }."""
    if isinstance(value, dict):
        if not value:
            return "{}"
        inner = ", ".join(f"{_inline(k)}: {_inline(v)}" for k, v in value.items())
        return "{ " + inner + " }"
    if isinstance(value, list):
        return "[" + ", ".join(_inline(v) for v in value) + "]"
    return json.dumps(value, ensure_ascii=False)


def format_lecture_file(doc: dict[str, Any]) -> str:
    """Serialize a lecture file the way the repo's files are written by hand.

    2-space indent, UTF-8 text (ensure_ascii=False), key order preserved, one
    lecture field per line with arrays/objects kept on that line, trailing newline.
    """
    lines = ["{"]
    keys = list(doc)
    for i, key in enumerate(keys):
        comma = "," if i < len(keys) - 1 else ""
        value = doc[key]
        if key == "lectures" and isinstance(value, list) and value:
            lines.append(f"  {_inline(key)}: [")
            for j, lecture in enumerate(value):
                lecture_comma = "," if j < len(value) - 1 else ""
                if isinstance(lecture, dict) and lecture:
                    lines.append("    {")
                    fields = list(lecture.items())
                    for n, (field, field_value) in enumerate(fields):
                        field_comma = "," if n < len(fields) - 1 else ""
                        lines.append(f"      {_inline(field)}: {_inline(field_value)}{field_comma}")
                    lines.append("    }" + lecture_comma)
                else:
                    lines.append(f"    {_inline(lecture)}{lecture_comma}")
            lines.append("  ]" + comma)
        else:
            lines.append(f"  {_inline(key)}: {_inline(value)}{comma}")
    lines.append("}")
    return "\n".join(lines) + "\n"


def _write_atomic(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=f".{path.name}.", suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(text)
        os.replace(tmp, path)
    except BaseException:
        if os.path.exists(tmp):
            os.unlink(tmp)
        raise


def _ids_of(doc: Any, key: str) -> list[str]:
    items = doc.get(key) if isinstance(doc, dict) else None
    if not isinstance(items, list):
        return []
    return [item["id"] for item in items if isinstance(item, dict) and isinstance(item.get("id"), str)]


def _all_lectures(root: Path) -> list[tuple[str, dict[str, Any]]]:
    """(file, lecture) for every lecture in every lecture file."""
    found = []
    folder = root / LECTURES_DIR
    for path in sorted(folder.glob("*.json")) if folder.is_dir() else []:
        rel = f"{LECTURES_DIR}/{path.name}"
        doc = _read_json(path, rel)
        lectures = doc.get("lectures") if isinstance(doc, dict) else None
        for lecture in lectures if isinstance(lectures, list) else []:
            if isinstance(lecture, dict):
                found.append((rel, lecture))
    return found


def audio_mime_type(url: str) -> str | None:
    suffix = PurePosixPath(urlparse(url).path).suffix.lower()
    return AUDIO_MIME_TYPES.get(suffix)


# ---------------------------------------------------------------------------
# Main operation
# ---------------------------------------------------------------------------

def add_lecture(
    root: Path,
    speaker_id: str,
    category_ids: list[str],
    url: str | None = None,
    title: str | None = None,
    audio: str | None = None,
    today: date | None = None,
) -> tuple[str, dict[str, Any], dict[str, Any] | None]:
    """Add one lecture. Returns (lecture file, new entry, oEmbed payload or None)."""
    root = Path(root)
    if bool(url) == bool(audio):
        raise AddLectureError("give either a YouTube link or --audio URL (exactly one)")
    if title is not None and not title.strip():
        raise AddLectureError("--title must not be empty")

    speaker_ids = _ids_of(_read_json(root / SPEAKERS, SPEAKERS), "speakers")
    if speaker_id not in speaker_ids:
        raise AddLectureError(
            f"unknown speaker {speaker_id!r}; add it to {SPEAKERS} first "
            f"(existing: {', '.join(speaker_ids) or 'none'})"
        )
    known_categories = _ids_of(_read_json(root / CATEGORIES, CATEGORIES), "categories")
    categories = list(dict.fromkeys(category_ids))  # de-duplicate, keep order
    if not categories:
        raise AddLectureError("at least one --category is required")
    unknown = [c for c in categories if c not in known_categories]
    if unknown:
        raise AddLectureError(
            f"unknown category id(s): {', '.join(unknown)} (see {CATEGORIES})"
        )

    existing = _all_lectures(root)
    oembed = None
    media: dict[str, Any]
    if url:
        video_id = parse_video_id(url)
        for rel, lecture in existing:
            other = lecture.get("media")
            if isinstance(other, dict) and other.get("videoId") == video_id:
                raise AddLectureError(
                    f"video {video_id} is already in {rel} as {lecture.get('id')!r}"
                )
        if title is None:
            oembed = fetch_oembed(video_id)
            title = oembed["title"]
        media = {"type": "youtube", "videoId": video_id, "startSec": 0}
    else:
        audio = audio.strip()
        if not audio.startswith("https://"):
            raise AddLectureError(f"--audio URL must start with https:// (got {audio!r})")
        if title is None:
            raise AddLectureError("--title is required with --audio")
        for rel, lecture in existing:
            other = lecture.get("media")
            if isinstance(other, dict) and other.get("url") == audio:
                raise AddLectureError(f"this audio URL is already in {rel} as {lecture.get('id')!r}")
        media = {"type": "audio", "url": audio}
        mime = audio_mime_type(audio)
        if mime:
            media["mimeType"] = mime

    entry = {
        "id": next_lecture_id(speaker_id, (lecture.get("id") for _, lecture in existing)),
        "title": title.strip(),
        "categoryIds": categories,
        "media": media,
        "addedAt": (today or date.today()).isoformat(),
    }

    rel = f"{LECTURES_DIR}/{speaker_id}.json"
    path = root / rel
    if path.exists():
        doc = _read_json(path, rel)
        if not isinstance(doc, dict) or not isinstance(doc.get("lectures"), list):
            raise AddLectureError(f"{rel} must be an object with a \"lectures\" array")
    else:
        doc = {"speakerId": speaker_id, "lectures": []}
    doc["lectures"].append(entry)
    _write_atomic(path, format_lecture_file(doc))
    return rel, entry, oembed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Add a YouTube (or direct audio) lecture to the content repo."
    )
    parser.add_argument(
        "url", nargs="?", help="YouTube link (watch?v=, youtu.be/, shorts, live, embed) or video id"
    )
    parser.add_argument("--speaker", required=True, help="speaker id, e.g. speaker-001")
    parser.add_argument(
        "--category",
        dest="categories",
        action="append",
        required=True,
        help="category id (repeat for more than one)",
    )
    parser.add_argument("--title", help="lecture title (skips the YouTube oEmbed lookup)")
    parser.add_argument(
        "--audio", metavar="URL", help="direct https audio link instead of a YouTube video (needs --title)"
    )
    parser.add_argument("--root", type=Path, default=REPO_ROOT, help="repo root (default: this repo)")
    args = parser.parse_args(argv)

    try:
        rel, entry, oembed = add_lecture(
            args.root,
            args.speaker,
            args.categories,
            url=args.url,
            title=args.title,
            audio=args.audio,
        )
    except AddLectureError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    print(f"Added {entry['id']} to {rel}")
    print(f"  title:      {entry['title']}")
    if oembed and oembed.get("author_name"):
        print(f"  channel:    {oembed['author_name']}")
    print(f"  categories: {', '.join(entry['categoryIds'])}")
    print("Next: fix the title in Bengali if needed (add description/tags if you like), then commit and push.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
