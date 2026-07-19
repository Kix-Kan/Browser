#!/usr/bin/env python3
"""Compare the pinned foundation with Mozilla's official product-details feed."""

from __future__ import annotations

import json
import re
import sys
import urllib.request
from pathlib import Path

FEED = "https://product-details.mozilla.org/1.0/firefox_versions.json"


def pinned_version(path: Path) -> str:
    match = re.search(r"^FIREFOX_ESR_VERSION=(.+)$", path.read_text(encoding="utf-8"), re.MULTILINE)
    if not match:
        raise ValueError("FIREFOX_ESR_VERSION is missing")
    return match.group(1)


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    pinned = pinned_version(root / "VERSION.env")
    with urllib.request.urlopen(FEED, timeout=20) as response:
        data = json.load(response)
    current = f"{data['FIREFOX_ESR']}esr"
    print(json.dumps({"pinned": pinned, "current": current}))
    return 0 if pinned == current else 10


if __name__ == "__main__":
    sys.exit(main())

