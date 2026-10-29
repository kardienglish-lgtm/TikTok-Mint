#!/usr/bin/env python3
"""Check an APK archive's integrity and optionally verify its Android signature."""
from __future__ import annotations
import argparse
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--require-signature-verifier", action="store_true", help="fail if apksigner is not installed")
    args = parser.parse_args()
    apk = args.apk.resolve()
    if not apk.is_file(): parser.error(f"APK not found: {apk}")
    failures = []
    notes = []
    print(f"APK: {apk}")
    print(f"Size: {apk.stat().st_size:,} bytes")
    if apk.stat().st_size < 1_000_000: failures.append("file is suspiciously small for the target TikTok APK")
    try:
        with zipfile.ZipFile(apk) as archive:
            bad = archive.testzip()
            if bad: failures.append(f"ZIP CRC failed for: {bad}")
            names = set(archive.namelist())
            checks = {
                "AndroidManifest.xml": "AndroidManifest.xml" in names,
                "classes.dex": any(name == "classes.dex" or (name.startswith("classes") and name.endswith(".dex")) for name in names),
                "resources.arsc": "resources.arsc" in names,
            }
            for key, ok in checks.items():
                print(f"{'PASS' if ok else 'FAIL'} required entry: {key}")
                if not ok: failures.append(f"missing APK entry: {key}")
            dex_count = sum(1 for name in names if name.startswith("classes") and name.endswith(".dex"))
            print(f"DEX files: {dex_count}")
            print(f"Archive entries: {len(names)}")
    except (OSError, zipfile.BadZipFile) as exc:
        print(f"FAIL: APK ZIP cannot be read: {exc}", file=sys.stderr)
        return 1

    verifier = shutil.which("apksigner")
    if verifier:
        result = subprocess.run([verifier, "verify", "--verbose", str(apk)], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        print(result.stdout.rstrip())
        if result.returncode: failures.append("apksigner verify failed")
        else: print("PASS: apksigner signature verification")
    else:
        message = "apksigner unavailable; cryptographic signature verification was skipped"
        print(f"WARN: {message}")
        notes.append(message)
        if args.require_signature_verifier: failures.append(message)
    for note in notes: print(f"NOTE: {note}")
    if failures:
        for item in failures: print(f"FAIL: {item}", file=sys.stderr)
        return 1
    print("APK structural audit passed; see WARN lines for checks that were skipped")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
