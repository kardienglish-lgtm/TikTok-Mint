#!/usr/bin/env python3
"""Static source/package hygiene checks that do not require a target APK."""
from __future__ import annotations
import ast
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []
notes: list[str] = []

# Check tracked Python source syntax without relying only on compileall's exit code.
py_files = sorted(p for p in ROOT.rglob("*.py") if not any(part in {"__pycache__", ".venv", "venv", "qa-artifacts"} for part in p.parts))
for path in py_files:
    try:
        ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    except Exception as exc:
        failures.append(f"Python parse error: {path.relative_to(ROOT)}: {exc}")

# Check required custom injection hooks and tests exist. This catches accidental partial ZIPs.
required = [
    "inject/java/cat/kardinebi/MintScreenDesign.java",
    "inject/java/cat/kardinebi/Md3.java",
    "inject/java/cat/kardinebi/ProfileGridDates.java",
    "tests/test_expressive_screens_runtime.py",
    "tests/test_date_overlay_runtime.py",
    "tests/test_archive_media_runtime.py",
    "tests/test_streak_repository_runtime.py",
    "qa/run_qa.sh",
    "qa/run_test_matrix.py",
    "qa/capture_ui.sh",
    "qa/visual_report.py",
    "qa/apk_audit.py",
]
for rel in required:
    if not (ROOT / rel).is_file(): failures.append(f"Missing required source/test: {rel}")

# Version metadata must parse and have recognizable version fields.
version_path = ROOT / "version.json"
if version_path.is_file():
    try:
        data = json.loads(version_path.read_text(encoding="utf-8"))
        if not isinstance(data, dict): failures.append("version.json root is not an object")
        elif not any(key in data for key in ("version", "versionName", "name")): notes.append("version.json has no standard version key; check manually")
    except Exception as exc:
        failures.append(f"Invalid version.json: {exc}")
else:
    notes.append("version.json missing; source may be a reduced export")

# A raw Android layout is intentionally not expected here; actual app views are patched into a supplied APK.
if not (ROOT / "tests").is_dir(): failures.append("tests/ directory missing")
print(f"Static audit: {len(py_files)} Python files parsed")
print(f"Static audit: {len(required)} critical paths checked")
for note in notes: print(f"NOTE: {note}")
for failure in failures: print(f"FAIL: {failure}", file=sys.stderr)
if failures: raise SystemExit(1)
print("Static audit passed")
