from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path

import pytest

import add_lecture
import build_manifest
from conftest import FIXED_NOW, LECTURE_FILE, REPO_ROOT, read_json, write_json

LATER = "2026-11-08T12:34:56Z"


@pytest.fixture
def later(monkeypatch):
    monkeypatch.setattr(build_manifest, "utc_now", lambda: LATER)


def run(repo: Path, *args: str) -> int:
    return build_manifest.main([*args, "--root", str(repo)])


def manifest_bytes(repo: Path) -> bytes:
    return (repo / "manifest.json").read_bytes()


def expected_entry(repo: Path, rel: str) -> dict:
    data = (repo / rel).read_bytes()
    return {"path": rel, "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)}


# ---------------------------------------------------------------------------
# No change -> byte-identical
# ---------------------------------------------------------------------------

def test_fixture_manifest_contents(repo):
    manifest = read_json(repo, "manifest.json")
    assert manifest == {
        "schemaVersion": 1,
        "minAppVersionCode": 1,
        "dataVersion": 1,
        "updatedAt": FIXED_NOW,
        "changelog": "প্রথম সংস্করণ",
        "files": [
            expected_entry(repo, "data/speakers.json"),
            expected_entry(repo, "data/categories.json"),
            expected_entry(repo, LECTURE_FILE),
        ],
    }
    assert list(manifest) == ["schemaVersion", "minAppVersionCode", "dataVersion", "updatedAt", "changelog", "files"]


def test_no_change_leaves_manifest_byte_identical(repo, later, capsys):
    before = manifest_bytes(repo)
    assert run(repo) == 0
    assert manifest_bytes(repo) == before
    assert "up to date" in capsys.readouterr().out


def test_changelog_flag_without_data_change_is_ignored(repo, later, capsys):
    before = manifest_bytes(repo)
    assert run(repo, "--changelog", "নতুন কিছু নেই") == 0
    assert manifest_bytes(repo) == before
    assert "--changelog was not applied" in capsys.readouterr().out


def test_non_data_files_do_not_count(repo, later):
    before = manifest_bytes(repo)
    (repo / "images/speakers/speaker-001.webp").write_bytes(b"img")
    (repo / "data/lectures/notes.txt").write_text("not json", encoding="utf-8")
    (repo / "data/.DS_Store").write_bytes(b"\0")
    assert run(repo) == 0
    assert manifest_bytes(repo) == before


def test_output_format_matches_existing_manifest_style():
    """build_manifest writes exactly the formatting the real manifest.json uses."""
    text = (REPO_ROOT / "manifest.json").read_text(encoding="utf-8")
    assert build_manifest.dumps(json.loads(text)) == text


# ---------------------------------------------------------------------------
# Changes -> bump
# ---------------------------------------------------------------------------

def test_changed_hash_bumps_version_and_keeps_other_fields(repo, later, capsys):
    text = (repo / LECTURE_FILE).read_text(encoding="utf-8")
    (repo / LECTURE_FILE).write_text(text.replace("সালাতে খুশু", "সালাতে খুশু ও খুযু"), encoding="utf-8")

    assert run(repo) == 0
    manifest = read_json(repo, "manifest.json")
    assert manifest["dataVersion"] == 2
    assert manifest["updatedAt"] == LATER
    assert manifest["schemaVersion"] == 1
    assert manifest["minAppVersionCode"] == 1
    assert manifest["changelog"] == "প্রথম সংস্করণ"
    assert manifest["files"][2] == expected_entry(repo, LECTURE_FILE)
    assert manifest["files"][0] == expected_entry(repo, "data/speakers.json")
    assert "dataVersion 2" in capsys.readouterr().out

    # Formatting: 2-space indent, real UTF-8 text, trailing newline.
    raw = (repo / "manifest.json").read_text(encoding="utf-8")
    assert raw == json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    assert "প্রথম সংস্করণ" in raw

    # Running again right away changes nothing.
    before = manifest_bytes(repo)
    assert run(repo) == 0
    assert manifest_bytes(repo) == before


def test_changelog_flag_overrides_on_change(repo, later):
    edit_target = repo / "data/speakers.json"
    edit_target.write_text(edit_target.read_text(encoding="utf-8").replace("পরিচিতি", "নতুন পরিচিতি"),
                           encoding="utf-8")
    assert run(repo, "--changelog", "বক্তার পরিচিতি হালনাগাদ") == 0
    manifest = read_json(repo, "manifest.json")
    assert manifest["changelog"] == "বক্তার পরিচিতি হালনাগাদ"
    assert manifest["dataVersion"] == 2


def test_new_lecture_files_are_listed_in_sorted_order(repo, later):
    for speaker in ("speaker-010", "speaker-002"):
        write_json(repo, f"data/lectures/{speaker}.json", {"speakerId": speaker, "lectures": []})
    assert run(repo) == 0
    manifest = read_json(repo, "manifest.json")
    assert [f["path"] for f in manifest["files"]] == [
        "data/speakers.json",
        "data/categories.json",
        "data/lectures/speaker-001.json",
        "data/lectures/speaker-002.json",
        "data/lectures/speaker-010.json",
    ]
    assert manifest["dataVersion"] == 2


def test_removed_lecture_file_bumps_version(repo, later):
    (repo / LECTURE_FILE).unlink()
    assert run(repo) == 0
    manifest = read_json(repo, "manifest.json")
    assert [f["path"] for f in manifest["files"]] == ["data/speakers.json", "data/categories.json"]
    assert manifest["dataVersion"] == 2


def test_add_lecture_then_build_bumps_once(repo, later):
    add_lecture.add_lecture(repo, "speaker-001", ["salah"], url="EEEEEEEEEEE", title="নতুন")
    assert run(repo) == 0
    assert read_json(repo, "manifest.json")["dataVersion"] == 2
    assert run(repo) == 0
    assert read_json(repo, "manifest.json")["dataVersion"] == 2


def test_custom_fields_are_preserved(repo, later):
    edit = read_json(repo, "manifest.json")
    edit["minAppVersionCode"] = 7
    edit["schemaVersion"] = 1
    edit["extra"] = {"keep": True}
    write_json(repo, "manifest.json", edit)
    (repo / "data/categories.json").write_text(
        (repo / "data/categories.json").read_text(encoding="utf-8") + " ", encoding="utf-8"
    )
    assert run(repo) == 0
    manifest = read_json(repo, "manifest.json")
    assert manifest["minAppVersionCode"] == 7
    assert manifest["extra"] == {"keep": True}
    assert manifest["dataVersion"] == 2


# ---------------------------------------------------------------------------
# Fresh repo / errors
# ---------------------------------------------------------------------------

def test_missing_manifest_is_created(repo, later):
    (repo / "manifest.json").unlink()
    assert run(repo, "--changelog", "শুরু") == 0
    manifest = read_json(repo, "manifest.json")
    assert list(manifest) == ["schemaVersion", "minAppVersionCode", "dataVersion", "updatedAt", "changelog", "files"]
    assert manifest["dataVersion"] == 1
    assert manifest["changelog"] == "শুরু"
    assert len(manifest["files"]) == 3


def test_real_clock_timestamp_format(repo):
    (repo / "manifest.json").unlink()
    assert run(repo) == 0
    stamp = read_json(repo, "manifest.json")["updatedAt"]
    assert re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z", stamp)


def test_missing_core_file_is_an_error(repo, capsys):
    before = manifest_bytes(repo)
    (repo / "data/categories.json").unlink()
    assert run(repo) == 1
    assert "missing required data file(s): data/categories.json" in capsys.readouterr().err
    assert manifest_bytes(repo) == before


def test_invalid_manifest_json_is_an_error(repo, capsys):
    (repo / "manifest.json").write_text("{", encoding="utf-8")
    assert run(repo) == 1
    assert "manifest.json is not valid JSON" in capsys.readouterr().err
