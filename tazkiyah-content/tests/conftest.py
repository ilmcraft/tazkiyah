"""Shared fixtures: a small, valid content repo built in tmp_path.

The real schema/ folder is copied in, so the tests exercise the real schemas.
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "scripts"))

import build_manifest  # noqa: E402

SPEAKERS = {
    "speakers": [
        {"id": "speaker-001", "name": "প্রথম বক্তা", "nameEn": "First Speaker", "bio": "পরিচিতি", "order": 1},
        {"id": "speaker-002", "name": "দ্বিতীয় বক্তা", "nameEn": "Second Speaker", "bio": "", "order": 2},
    ]
}

CATEGORIES = {
    "categories": [
        {"id": "ibadah", "name": "ইবাদত", "icon": "mosque", "order": 1, "parentId": None},
        {"id": "salah", "name": "সালাত", "icon": "prayer", "order": 1, "parentId": "ibadah"},
        {"id": "tazkiyah", "name": "আত্মশুদ্ধি", "icon": "heart", "order": 2, "parentId": None},
    ]
}

# Written in the same hand-edited style as the real data/lectures files.
LECTURE_FILE_TEXT = r"""{
  "speakerId": "speaker-001",
  "lectures": [
    {
      "id": "spk001-0001",
      "title": "তাওবার শর্ত ও পদ্ধতি",
      "description": "ঐচ্ছিক \"সারাংশ\"",
      "categoryIds": ["tazkiyah"],
      "tags": ["তাওবা"],
      "media": { "type": "youtube", "videoId": "AAAAAAAAAAA", "startSec": 0 },
      "durationSec": 2730,
      "publishedAt": "2025-03-14",
      "addedAt": "2026-11-07",
      "series": { "id": "series-tawbah", "title": "তাওবা সিরিজ", "part": 1 }
    },
    {
      "id": "spk001-0002",
      "title": "সালাতে খুশু",
      "categoryIds": ["salah", "ibadah"],
      "media": { "type": "audio", "url": "https://archive.org/download/ITEM/file.mp3", "mimeType": "audio/mpeg" },
      "durationSec": 1980,
      "addedAt": "2026-11-07"
    }
  ]
}
"""

LECTURE_FILE = "data/lectures/speaker-001.json"
FIXED_NOW = "2026-11-07T00:00:00Z"


def read_json(root: Path, rel: str):
    return json.loads((root / rel).read_text(encoding="utf-8"))


def write_json(root: Path, rel: str, data) -> None:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


@pytest.fixture
def repo(tmp_path: Path) -> Path:
    root = tmp_path / "content"
    shutil.copytree(REPO_ROOT / "schema", root / "schema")
    write_json(root, "data/speakers.json", SPEAKERS)
    write_json(root, "data/categories.json", CATEGORIES)
    (root / LECTURE_FILE).parent.mkdir(parents=True)
    (root / LECTURE_FILE).write_text(LECTURE_FILE_TEXT, encoding="utf-8")
    (root / "images" / "speakers").mkdir(parents=True)
    build_manifest.build_manifest(root, changelog="প্রথম সংস্করণ", now=FIXED_NOW)
    return root
