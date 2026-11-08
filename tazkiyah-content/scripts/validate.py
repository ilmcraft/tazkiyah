#!/usr/bin/env python3
"""Validate the Tazkiyah content repo.

Checks:
  * every file matches its JSON Schema in schema/ (manifest.json, data/speakers.json,
    data/categories.json, data/lectures/*.json) — this covers the YouTube videoId
    format, https:// media URLs and YYYY-MM-DD dates;
  * speaker and category ids are unique, lecture ids are unique across all files;
  * each lecture file's speakerId exists in speakers.json and matches the file name;
  * every categoryId exists; every category parentId exists (and forms no cycle);
  * a speaker's photo file exists;
  * no YouTube video or media URL is in the catalog twice (in any speaker's file);
  * a series id has one title everywhere and no part number twice;
  * the bot's discovery/sources.json and discovery/seen/*.json match their schemas and point at
    existing speakers and categories.

Exits 0 when everything is valid, 1 (with one line per problem) otherwise.

Usage:  python scripts/validate.py [--root PATH]
"""
from __future__ import annotations

import argparse
import difflib
import json
import sys
from pathlib import Path
from typing import Any, Iterable, Iterator

try:
    from jsonschema import Draft202012Validator, ValidationError
except ImportError:  # pragma: no cover - environment problem, not a data problem
    sys.stderr.write("validate.py needs the 'jsonschema' package: pip install jsonschema\n")
    raise SystemExit(2)

REPO_ROOT = Path(__file__).resolve().parent.parent

SPEAKERS = "data/speakers.json"
CATEGORIES = "data/categories.json"
LECTURES_DIR = "data/lectures"
MANIFEST = "manifest.json"

SCHEMA_FILES = {
    "manifest": "schema/manifest.schema.json",
    "speakers": "schema/speakers.schema.json",
    "categories": "schema/categories.schema.json",
    "lectures": "schema/lectures.schema.json",
    "discovery": "schema/discovery.schema.json",
    "seen": "schema/seen.schema.json",
}
DISCOVERY_SOURCES = "discovery/sources.json"
SEEN_DIR = "discovery/seen"


class Problems:
    """Collects human-readable error lines."""

    def __init__(self) -> None:
        self.lines: list[str] = []

    def add(self, file: str, message: str, where: str = "") -> None:
        location = f"{file}: {where}" if where else file
        self.lines.append(f"{location}: {message}")

    def __len__(self) -> int:
        return len(self.lines)


# ---------------------------------------------------------------------------
# Loading
# ---------------------------------------------------------------------------

def load_json(root: Path, rel: str, problems: Problems) -> Any:
    """Parse a JSON file; record a readable problem and return None on failure."""
    path = root / rel
    try:
        text = path.read_text(encoding="utf-8")
    except FileNotFoundError:
        problems.add(rel, "file not found")
        return None
    except UnicodeDecodeError as exc:
        problems.add(rel, f"not valid UTF-8 ({exc})")
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        problems.add(rel, f"invalid JSON: {exc.msg} at line {exc.lineno}, column {exc.colno}")
        return None


def load_validators(root: Path, problems: Problems) -> dict[str, Draft202012Validator]:
    validators = {}
    for name, rel in SCHEMA_FILES.items():
        schema = load_json(root, rel, problems)
        if schema is None:
            continue
        Draft202012Validator.check_schema(schema)
        validators[name] = Draft202012Validator(
            schema, format_checker=Draft202012Validator.FORMAT_CHECKER
        )
    return validators


# ---------------------------------------------------------------------------
# Schema errors -> readable messages
# ---------------------------------------------------------------------------

def describe_path(data: Any, path: Iterable[Any]) -> str:
    """Render a JSON path like  lectures[3] (spk001-0004).media.videoId."""
    out = ""
    node = data
    for part in path:
        if isinstance(part, int):
            out += f"[{part}]"
            try:
                node = node[part]
            except (IndexError, KeyError, TypeError):
                node = None
            if isinstance(node, dict) and isinstance(node.get("id"), str):
                out += f" ({node['id']})"
        else:
            out += f".{part}" if out else str(part)
            node = node.get(part) if isinstance(node, dict) else None
    return out


def _leaf_errors(error: ValidationError) -> Iterator[ValidationError]:
    """Expand a oneOf error (media) into the errors of the branch the user meant."""
    if error.validator != "oneOf" or not error.context:
        yield error
        return
    instance = error.instance
    branches: dict[Any, list[ValidationError]] = {}
    for sub in error.context:
        branches.setdefault(sub.relative_schema_path[0], []).append(sub)

    def is_type_mismatch(sub: ValidationError) -> bool:
        return list(sub.relative_path) == ["type"] and sub.validator == "const"

    if isinstance(instance, dict) and "type" in instance:
        chosen = [subs for subs in branches.values() if not any(map(is_type_mismatch, subs))]
        if chosen:
            for sub in chosen[0]:
                yield from _leaf_errors(sub)
            return
    yield error  # handled by friendly_message (unknown / missing type)


def _allowed_types(error: ValidationError) -> list[str]:
    """The media 'type' values of the oneOf branches, in schema order."""
    found: list[str] = []
    for sub in error.context:
        schema = sub.schema if isinstance(sub.schema, dict) else {}
        if sub.validator == "const" and list(sub.relative_path) == ["type"]:
            value = schema.get("const")
        else:
            value = schema.get("properties", {}).get("type", {}).get("const")
        if isinstance(value, str) and value not in found:
            found.append(value)
    return found


def friendly_message(error: ValidationError) -> str:
    field = error.absolute_path[-1] if error.absolute_path else None
    value = error.instance

    if error.validator == "oneOf":
        types = ", ".join(_allowed_types(error)) or "youtube, audio, video"
        if not isinstance(value, dict):
            return f"must be an object with a 'type' ({types})"
        if "type" not in value:
            return f"missing 'type' (must be one of: {types})"
        return f"unknown media type {value.get('type')!r} (must be one of: {types})"

    if error.validator == "additionalProperties" and isinstance(value, dict):
        allowed = list(error.schema.get("properties", {}))
        extras = [key for key in value if key not in allowed]
        parts = []
        for key in extras:
            guess = difflib.get_close_matches(key, allowed, n=1, cutoff=0.6)
            hint = f" (did you mean {guess[0]!r}?)" if guess else ""
            parts.append(f"{key!r}{hint}")
        return (
            f"unknown key {', '.join(parts)}; allowed keys: {', '.join(allowed)}. "
            "If this is a new field, add it to the schema in schema/ too."
        )

    if error.validator == "pattern":
        if field == "videoId":
            return (
                f"YouTube videoId must be exactly 11 characters of A-Z a-z 0-9 _ - (got {value!r})"
            )
        if field == "url":
            return f"media URL must start with https:// (got {value!r})"
        if field in ("addedAt", "publishedAt"):
            return f"date must be YYYY-MM-DD (got {value!r})"
        if field == "id":
            return f"id {value!r} does not match the required pattern {error.validator_value}"

    if error.validator == "format" and error.validator_value == "date":
        return f"{value!r} is not a real calendar date (YYYY-MM-DD)"

    if error.validator == "required":
        return error.message.replace("is a required property", "is required")

    return error.message


def schema_problems(
    validator: Draft202012Validator, data: Any, rel: str, problems: Problems
) -> None:
    def sort_key(err: ValidationError) -> tuple:
        return tuple((0, p, "") if isinstance(p, int) else (1, 0, str(p)) for p in err.absolute_path)

    leaves = [
        leaf
        for error in sorted(validator.iter_errors(data), key=sort_key)
        for leaf in _leaf_errors(error)
    ]
    # A malformed date fails both "pattern" and "format"; report it once.
    pattern_paths = {tuple(e.absolute_path) for e in leaves if e.validator == "pattern"}
    for leaf in leaves:
        if leaf.validator == "format" and tuple(leaf.absolute_path) in pattern_paths:
            continue
        problems.add(rel, friendly_message(leaf), describe_path(data, leaf.absolute_path))


# ---------------------------------------------------------------------------
# Cross-file checks
# ---------------------------------------------------------------------------

def _items(doc: Any, key: str) -> list[tuple[int, dict]]:
    if not isinstance(doc, dict) or not isinstance(doc.get(key), list):
        return []
    return [(i, item) for i, item in enumerate(doc[key]) if isinstance(item, dict)]


def check_speakers(root: Path, speakers: Any, problems: Problems) -> set[str]:
    ids: set[str] = set()
    for i, sp in _items(speakers, "speakers"):
        sid = sp.get("id")
        if not isinstance(sid, str):
            continue
        if sid in ids:
            problems.add(SPEAKERS, f"duplicate speaker id {sid!r}", f"speakers[{i}]")
        ids.add(sid)
        photo = sp.get("photo")
        if isinstance(photo, str) and not (root / photo).is_file():
            problems.add(SPEAKERS, f"photo file {photo!r} does not exist", f"speakers[{i}] ({sid})")
    return ids


def check_categories(categories: Any, problems: Problems) -> set[str]:
    items = _items(categories, "categories")
    ids: set[str] = set()
    parents: dict[str, Any] = {}
    for i, cat in items:
        cid = cat.get("id")
        if not isinstance(cid, str):
            continue
        if cid in ids:
            problems.add(CATEGORIES, f"duplicate category id {cid!r}", f"categories[{i}]")
        ids.add(cid)
        parents[cid] = cat.get("parentId")

    for i, cat in items:
        cid, parent = cat.get("id"), cat.get("parentId")
        if parent is None or not isinstance(cid, str):
            continue
        where = f"categories[{i}] ({cid})"
        if parent == cid:
            problems.add(CATEGORIES, "parentId points to itself", where)
        elif parent not in ids:
            problems.add(CATEGORIES, f"parentId {parent!r} does not exist", where)
        else:
            # Walk up the parents; report only categories that are part of a cycle.
            seen: set[str] = set()
            node = parent
            while isinstance(node, str) and node in parents and node not in seen:
                if node == cid:
                    problems.add(CATEGORIES, "parentId chain loops back to this category", where)
                    break
                seen.add(node)
                node = parents[node]
    return ids


def check_lecture_file(
    rel: str,
    doc: Any,
    speaker_ids: set[str] | None,
    category_ids: set[str] | None,
    seen_lecture_ids: dict[str, str],
    problems: Problems,
    seen_media: dict[str, str] | None = None,
    series: dict[str, tuple[str, dict[int, str]]] | None = None,
) -> int:
    if not isinstance(doc, dict):
        return 0
    stem = Path(rel).stem
    speaker_id = doc.get("speakerId")
    if isinstance(speaker_id, str):
        if speaker_id != stem:
            problems.add(
                rel, f"speakerId {speaker_id!r} does not match the file name (expected {stem!r})"
            )
        if speaker_ids is not None and speaker_id not in speaker_ids:
            problems.add(rel, f"speakerId {speaker_id!r} is not in {SPEAKERS}")

    count = 0
    for i, lec in _items(doc, "lectures"):
        count += 1
        lid = lec.get("id")
        where = f"lectures[{i}]" + (f" ({lid})" if isinstance(lid, str) else "")
        if isinstance(lid, str):
            if lid in seen_lecture_ids:
                problems.add(
                    rel, f"duplicate lecture id {lid!r} (already used in {seen_lecture_ids[lid]})", where
                )
            else:
                seen_lecture_ids[lid] = f"{rel} lectures[{i}]"
        cats = lec.get("categoryIds")
        if category_ids is not None and isinstance(cats, list):
            for cat in cats:
                if isinstance(cat, str) and cat not in category_ids:
                    problems.add(rel, f"unknown categoryId {cat!r}", where)
        if seen_media is not None:
            check_media_unique(rel, lec, where, seen_media, problems)
        if series is not None:
            check_series(rel, lec, where, series, problems)
    return count


def check_media_unique(rel: str, lec: dict, where: str, seen_media: dict[str, str], problems: Problems) -> None:
    """One lecture per recording: the same YouTube video or file URL may appear only once."""
    media = lec.get("media")
    if not isinstance(media, dict):
        return
    key = media.get("videoId") if media.get("type") == "youtube" else media.get("url")
    if not isinstance(key, str):
        return
    label = f"video {key!r}" if media.get("type") == "youtube" else f"media URL {key!r}"
    if key in seen_media:
        problems.add(rel, f"{label} is already in the catalog as {seen_media[key]}", where)
    else:
        seen_media[key] = f"{lec.get('id')} ({rel})"


def check_series(
    rel: str, lec: dict, where: str, series: dict[str, tuple[str, dict[int, str]]], problems: Problems
) -> None:
    """A series id has one title, and each part number is used once."""
    info = lec.get("series")
    if not isinstance(info, dict) or not isinstance(info.get("id"), str):
        return
    sid, title, part = info["id"], info.get("title"), info.get("part")
    if sid not in series:
        series[sid] = (title, {})
    known_title, parts = series[sid]
    if title != known_title:
        problems.add(rel, f"series {sid!r} has title {title!r} here but {known_title!r} elsewhere", where)
    if isinstance(part, int):
        if part in parts:
            problems.add(rel, f"series {sid!r} part {part} is also used by {parts[part]}", where)
        else:
            parts[part] = str(lec.get("id"))


def check_discovery(
    root: Path,
    validators: dict,
    speaker_ids: set[str] | None,
    category_ids: set[str] | None,
    problems: Problems,
) -> None:
    """The bot's config and its per-speaker lists of videos already decided on."""
    if (root / DISCOVERY_SOURCES).exists():
        doc = load_json(root, DISCOVERY_SOURCES, problems)
        if doc is not None and "discovery" in validators:
            schema_problems(validators["discovery"], doc, DISCOVERY_SOURCES, problems)
        listed: set[str] = set()
        for i, entry in _items(doc, "speakers"):
            sid = entry.get("speakerId")
            where = f"speakers[{i}]" + (f" ({sid})" if isinstance(sid, str) else "")
            if not isinstance(sid, str):
                continue
            if sid in listed:
                problems.add(DISCOVERY_SOURCES, f"speaker {sid!r} is listed twice", where)
            listed.add(sid)
            if speaker_ids is not None and sid not in speaker_ids:
                problems.add(DISCOVERY_SOURCES, f"speakerId {sid!r} is not in {SPEAKERS}", where)
            for cat in entry.get("defaultCategoryIds") or []:
                if category_ids is not None and isinstance(cat, str) and cat not in category_ids:
                    problems.add(DISCOVERY_SOURCES, f"unknown categoryId {cat!r}", where)
    folder = root / SEEN_DIR
    for path in sorted(folder.glob("*.json")) if folder.is_dir() else []:
        rel = f"{SEEN_DIR}/{path.name}"
        doc = load_json(root, rel, problems)
        if doc is None:
            continue
        if "seen" in validators:
            schema_problems(validators["seen"], doc, rel, problems)
        sid = doc.get("speakerId") if isinstance(doc, dict) else None
        if isinstance(sid, str):
            if sid != path.stem:
                problems.add(rel, f"speakerId {sid!r} does not match the file name (expected {path.stem!r})")
            if speaker_ids is not None and sid not in speaker_ids:
                problems.add(rel, f"speakerId {sid!r} is not in {SPEAKERS}")


# ---------------------------------------------------------------------------
# Entry points
# ---------------------------------------------------------------------------

def lecture_files(root: Path) -> list[str]:
    folder = root / LECTURES_DIR
    if not folder.is_dir():
        return []
    return [f"{LECTURES_DIR}/{p.name}" for p in sorted(folder.glob("*.json"))]


def validate_repo(root: Path) -> tuple[list[str], dict[str, int]]:
    """Return (problem lines, counts). An empty problem list means the repo is valid."""
    root = Path(root)
    problems = Problems()
    validators = load_validators(root, problems)

    def load_and_check(rel: str, schema_name: str) -> Any:
        doc = load_json(root, rel, problems)
        if doc is not None and schema_name in validators:
            schema_problems(validators[schema_name], doc, rel, problems)
        return doc

    if (root / MANIFEST).exists():
        load_and_check(MANIFEST, "manifest")

    speakers = load_and_check(SPEAKERS, "speakers")
    categories = load_and_check(CATEGORIES, "categories")
    speaker_ids = check_speakers(root, speakers, problems) if speakers is not None else None
    category_ids = check_categories(categories, problems) if categories is not None else None

    seen: dict[str, str] = {}
    seen_media: dict[str, str] = {}
    series: dict[str, tuple[str, dict[int, str]]] = {}
    lecture_count = 0
    files = lecture_files(root)
    for rel in files:
        doc = load_and_check(rel, "lectures")
        lecture_count += check_lecture_file(
            rel, doc, speaker_ids, category_ids, seen, problems, seen_media, series
        )

    check_discovery(root, validators, speaker_ids, category_ids, problems)

    counts = {
        "speakers": len(speaker_ids or ()),
        "categories": len(category_ids or ()),
        "lectureFiles": len(files),
        "lectures": lecture_count,
    }
    return problems.lines, counts


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate the Tazkiyah content repo.")
    parser.add_argument(
        "--root", type=Path, default=REPO_ROOT, help="repo root (default: this repo)"
    )
    args = parser.parse_args(argv)

    lines, counts = validate_repo(args.root)
    if lines:
        for line in lines:
            print(f"ERROR {line}", file=sys.stderr)
        print(f"\nValidation failed: {len(lines)} problem(s).", file=sys.stderr)
        return 1
    print(
        "OK: {speakers} speaker(s), {categories} categor(ies), "
        "{lectures} lecture(s) in {lectureFiles} file(s).".format(**counts)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
