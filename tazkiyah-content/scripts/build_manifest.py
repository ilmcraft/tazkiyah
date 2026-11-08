#!/usr/bin/env python3
"""Rebuild manifest.json from the data files.

Computes sha256 + size for data/speakers.json, data/categories.json and every
data/lectures/*.json (in that order, lecture files sorted by name).

Only when the file list or any hash changed does it bump dataVersion by 1 and set
updatedAt (UTC). Otherwise manifest.json is left untouched, byte for byte.
schemaVersion, minAppVersionCode and any other keys are kept. Lecture files also record
their lecture count, so when data changes without --changelog the changelog (the app's
"নতুন কনটেন্ট" banner) is written automatically: which speakers got how many new lectures.
--changelog replaces it (only applied when something changed).

Usage:  python scripts/build_manifest.py [--root PATH] [--changelog "..."]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
MANIFEST = "manifest.json"
CORE_FILES = ("data/speakers.json", "data/categories.json")
LECTURES_DIR = "data/lectures"

DEFAULTS = {"schemaVersion": 1, "minAppVersionCode": 1}


class ManifestError(Exception):
    pass


def data_files(root: Path) -> list[str]:
    """Manifest file order: speakers, categories, then lecture files sorted by name."""
    missing = [rel for rel in CORE_FILES if not (root / rel).is_file()]
    if missing:
        raise ManifestError(f"missing required data file(s): {', '.join(missing)}")
    lectures_dir = root / LECTURES_DIR
    lectures = sorted(p.name for p in lectures_dir.glob("*.json")) if lectures_dir.is_dir() else []
    return list(CORE_FILES) + [f"{LECTURES_DIR}/{name}" for name in lectures]


def file_entry(root: Path, rel: str) -> dict[str, Any]:
    data = (root / rel).read_bytes()
    entry: dict[str, Any] = {"path": rel, "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)}
    if rel.startswith(f"{LECTURES_DIR}/"):
        try:
            entry["lectures"] = len(json.loads(data.decode("utf-8")).get("lectures", []))
        except (ValueError, AttributeError):
            pass  # validate.py reports broken files
    return entry


_BN_DIGITS = str.maketrans("0123456789", "০১২৩৪৫৬৭৮৯")


def auto_changelog(root: Path, old_files: Any, new_files: list[dict[str, Any]]) -> str:
    """"নতুন লেকচার: <speaker> (৩৬৯টি), …" from the lecture counts; generic when unknown."""
    old_counts = {
        f.get("path"): f.get("lectures") for f in (old_files if isinstance(old_files, list) else []) if isinstance(f, dict)
    }
    try:
        speakers = {
            sp["id"]: sp["name"]
            for sp in json.loads((root / CORE_FILES[0]).read_text(encoding="utf-8")).get("speakers", [])
        }
    except (OSError, ValueError, KeyError, TypeError):
        speakers = {}
    added = []
    for entry in new_files:
        count = entry.get("lectures")
        if count is None:
            continue
        before = old_counts.get(entry["path"], 0) if entry["path"] in old_counts else 0
        if before is None:
            return "লেকচারের তালিকা হালনাগাদ হয়েছে"
        if count > before:
            speaker_id = Path(entry["path"]).stem
            name = speakers.get(speaker_id, speaker_id)
            added.append(f"{name} ({str(count - before).translate(_BN_DIGITS)}টি)")
    if added:
        return "নতুন লেকচার: " + ", ".join(added)
    return "লেকচারের তালিকা হালনাগাদ হয়েছে"


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def dumps(manifest: dict[str, Any]) -> str:
    return json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"


def _comparable(files: Any) -> list[tuple]:
    if not isinstance(files, list):
        return []
    return [
        (f.get("path"), f.get("sha256"), f.get("size")) if isinstance(f, dict) else (f,)
        for f in files
    ]


def build_manifest(
    root: Path, changelog: str | None = None, now: str | None = None
) -> tuple[bool, dict[str, Any]]:
    """Update manifest.json under root. Returns (changed, manifest)."""
    root = Path(root)
    entries = [file_entry(root, rel) for rel in data_files(root)]
    manifest_path = root / MANIFEST

    old_files: Any = None
    if manifest_path.exists():
        try:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise ManifestError(f"{MANIFEST} is not valid JSON: {exc}") from exc
        if not isinstance(manifest, dict):
            raise ManifestError(f"{MANIFEST} must contain a JSON object")
        if _comparable(manifest.get("files")) == _comparable(entries):
            return False, manifest
        old_files = manifest.get("files")
        old_version = manifest.get("dataVersion", 0)
        if not isinstance(old_version, int) or isinstance(old_version, bool):
            raise ManifestError(f"{MANIFEST}: dataVersion must be an integer, got {old_version!r}")
        new_version = old_version + 1
    else:
        manifest = dict(DEFAULTS)
        new_version = 1

    # Assigning to existing keys keeps their position; new keys go in the canonical order.
    for key, value in DEFAULTS.items():
        manifest.setdefault(key, value)
    manifest["dataVersion"] = new_version
    manifest["updatedAt"] = now or utc_now()
    if changelog is not None:
        manifest["changelog"] = changelog
    elif old_files is not None:
        manifest["changelog"] = auto_changelog(root, old_files, entries)
    else:
        manifest.setdefault("changelog", "")
    manifest["files"] = entries

    manifest_path.write_text(dumps(manifest), encoding="utf-8")
    return True, manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Rebuild manifest.json (hashes, sizes, dataVersion).")
    parser.add_argument("--root", type=Path, default=REPO_ROOT, help="repo root (default: this repo)")
    parser.add_argument(
        "--changelog", help="changelog text for the app's update banner (used only if data changed)"
    )
    args = parser.parse_args(argv)

    try:
        changed, manifest = build_manifest(args.root, changelog=args.changelog)
    except ManifestError as exc:
        print(f"ERROR {exc}", file=sys.stderr)
        return 1

    if changed:
        print(
            f"manifest.json updated: dataVersion {manifest['dataVersion']}, "
            f"{len(manifest['files'])} file(s), updatedAt {manifest['updatedAt']}"
        )
    else:
        print(f"manifest.json is up to date (dataVersion {manifest.get('dataVersion')}); not modified.")
        if args.changelog is not None:
            print("Note: nothing changed, so --changelog was not applied.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
