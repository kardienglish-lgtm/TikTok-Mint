#!/usr/bin/env python3
"""Fail-fast validation for the TikTok APK this TikTok Mint source targets."""
from __future__ import annotations

import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ROOT)

from kardinebi.apkzip import Apk  # noqa: E402
from kardinebi.axml import Axml  # noqa: E402
from kardinebi.manifest import (  # noqa: E402
    application_class,
    min_sdk,
    package_name,
    version_name,
)

EXPECTED_PACKAGE = "com.zhiliaoapp.musically"
EXPECTED_VERSION = "47.4.42"


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("Usage: python check_apk.py path\\to\\tiktok.apk", file=sys.stderr)
        return 2

    path = os.path.abspath(argv[1])
    if not os.path.isfile(path):
        print(f"ERROR: file not found: {path}", file=sys.stderr)
        return 2

    try:
        apk = Apk(path)
        names = apk.names()
        required = {"AndroidManifest.xml", "resources.arsc", "classes.dex"}
        missing = sorted(required.difference(names))
        if missing:
            print("ERROR: not a standalone APK; missing " + ", ".join(missing), file=sys.stderr)
            return 2

        manifest = Axml.parse(apk.read("AndroidManifest.xml"))
        package = package_name(manifest)
        version = version_name(manifest)
        api = min_sdk(manifest)
        app = application_class(manifest)
        dex_count = sum(bool(re.fullmatch(r"classes(?:\d+)?\.dex", n)) for n in names)
        abis = sorted({n.split("/")[1] for n in names
                       if n.startswith("lib/") and len(n.split("/")) >= 3})
    except Exception as exc:
        print(f"ERROR: cannot inspect APK: {exc}", file=sys.stderr)
        return 2

    print(f"File:        {path}")
    print(f"Size:        {os.path.getsize(path):,} bytes")
    print(f"Package:     {package}")
    print(f"Version:     {version}")
    print(f"minSdk:      {api}")
    print(f"Application: {app}")
    print(f"DEX files:   {dex_count}")
    print(f"ABIs:        {', '.join(abis) if abis else '(not listed)'}")

    if package != EXPECTED_PACKAGE:
        print(f"ERROR: expected package {EXPECTED_PACKAGE}", file=sys.stderr)
        return 2
    if version != EXPECTED_VERSION:
        print(
            f"ERROR: this TikTok Mint source targets TikTok {EXPECTED_VERSION}, "
            f"but this file is {version}. Stop here; don't waste time on a full build.",
            file=sys.stderr,
        )
        return 2

    print("OK: package and appearance-profile version match this builder.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
