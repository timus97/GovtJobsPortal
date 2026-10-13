#!/usr/bin/env python3
"""Fail the build when the committed catalog or the git tree breaks a product lock.

Official links on the live catalog must be https. Student files, operator
records, and the removed Node trees must not be tracked. jobs.prev.json is a
historical snapshot and is not part of this check.
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

LIVE_CATALOG = (
    "data/processed/jobs.json",
    "data/processed/opportunities.json",
    "data/processed/exam_series.json",
)

ALLOWED_EXACT = {
    "data/students/.gitkeep",
    "data/student-files/.gitkeep",
    "data/ops/.gitkeep",
    "data/logs/.gitkeep",
    "data/cache/.gitkeep",
    "data/staging/.gitkeep",
    "data/raw/.gitkeep",
}

FORBIDDEN_PREFIXES = (
    "client/",
    "server/",
    "scripts/collect/",
    "data/students/",
    "data/student-files/",
    "data/ops/",
    "data/logs/",
    "data/cache/",
    "data/raw/",
)


def fail(message: str) -> None:
    print(f"ci-guard: {message}", file=sys.stderr)


def load_array(relative: str) -> list:
    path = ROOT / relative
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as ex:
        fail(f"{relative} is not readable JSON ({ex})")
        return []
    if not isinstance(data, list) or not data:
        fail(f"{relative} must be a non-empty JSON array")
        return []
    return data


def check_https(relative: str) -> int:
    errors = 0
    for row in load_array(relative):
        if not isinstance(row, dict):
            fail(f"{relative} has a row that is not an object")
            errors += 1
            continue
        url = str(row.get("officialUrl") or "").strip()
        if not url.lower().startswith("https://"):
            fail(f"{relative} id={row.get('id')} officialUrl must start with https://")
            errors += 1
    return errors


def tracked_paths() -> list[str]:
    output = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [path for path in output.decode("utf-8").split("\0") if path]


def check_tree(paths: list[str]) -> int:
    errors = 0
    for path in paths:
        if path in ALLOWED_EXACT:
            continue
        lowered = path.lower()
        if lowered.endswith(".sqlite") or (
            (lowered == ".env" or lowered.startswith(".env.")) and lowered != ".env.example"
        ):
            fail(f"tracked secret or database file: {path}")
            errors += 1
            continue
        if path.startswith("data/staging/") and not path.startswith("data/staging/ops_paste/"):
            fail(f"tracked staging file outside ops_paste: {path}")
            errors += 1
            continue
        for prefix in FORBIDDEN_PREFIXES:
            if path.startswith(prefix):
                fail(f"tracked path is private or is not part of the Java host: {path}")
                errors += 1
                break
    return errors


def main() -> int:
    errors = check_tree(tracked_paths())
    for relative in LIVE_CATALOG:
        errors += check_https(relative)
    if errors:
        fail(f"{errors} problem(s)")
        return 1
    print("ci-guard: catalog links are https and private paths are not tracked")
    return 0


if __name__ == "__main__":
    sys.exit(main())
