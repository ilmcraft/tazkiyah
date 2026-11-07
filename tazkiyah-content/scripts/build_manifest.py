#!/usr/bin/env python3
"""Rebuild manifest.json from the data files.

Computes sha256 + size for data/speakers.json, data/categories.json and every
data/lectures/*.json (in that order, lecture files sorted by name).

Only when the file list or any hash changed does it bump dataVersion by 1 and set
updatedAt (UTC). Otherwise manifest.json is left untouched, byte for byte.
schemaVersion, minAppVersionCode, changelog and any other keys are kept;
--changelog replaces the changelog (only applied when something changed).

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
    return {"path": rel, "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)}


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

    if manifest_path.exists():
        try:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise ManifestError(f"{MANIFEST} is not valid JSON: {exc}") from exc
        if not isinstance(manifest, dict):
            raise ManifestError(f"{MANIFEST} must contain a JSON object")
        if _comparable(manifest.get("files")) == _comparable(entries):
            return False, manifest
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
