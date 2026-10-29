#!/usr/bin/env python3
"""Run unittest files independently and retain a per-file result even on failures/timeouts."""
from __future__ import annotations
import argparse
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TESTS = ROOT / "tests"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--start", type=int, default=0, help="zero-based index in sorted test file list")
    parser.add_argument("--limit", type=int, default=0, help="maximum test files to run; 0 means all remaining")
    parser.add_argument("--timeout", type=int, default=60, help="per-test-file timeout in seconds")
    parser.add_argument("--report-dir", type=Path, default=ROOT / "qa-artifacts" / "reports" / "test-matrix")
    args = parser.parse_args()
    files = sorted(TESTS.glob("test_*.py"))
    if args.start < 0 or args.start > len(files): parser.error(f"--start must be 0..{len(files)}")
    if args.limit < 0: parser.error("--limit cannot be negative")
    if not 1 <= args.timeout <= 900: parser.error("--timeout must be between 1 and 900 seconds")
    selected = files[args.start:args.start + args.limit if args.limit else None]
    if not selected:
        print("No tests selected.")
        return 0

    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    report_dir = args.report_dir.resolve() / stamp
    log_dir = report_dir / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    results = []
    print(f"Test matrix: files {args.start}..{args.start + len(selected) - 1} of {len(files)}; timeout {args.timeout}s per file")
    for offset, test_file in enumerate(selected, start=args.start):
        print(f"[{offset + 1}/{len(files)}] {test_file.name} ...", flush=True)
        started = datetime.now(timezone.utc)
        status = "PASS"
        output = ""
        count = None
        try:
            process = subprocess.run(
                [sys.executable, "-m", "unittest", "discover", "-s", "tests", "-p", test_file.name, "-v"],
                cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, timeout=args.timeout,
                env={**os.environ, "PYTHONUNBUFFERED": "1"},
            )
            output = process.stdout
            if process.returncode != 0: status = "FAIL"
        except subprocess.TimeoutExpired as exc:
            status = "TIMEOUT"
            raw = exc.stdout or ""
            output = raw.decode("utf-8", errors="replace") if isinstance(raw, bytes) else raw
            output += f"\nQA RUNNER: exceeded {args.timeout} seconds and was stopped.\n"
        matches = re.findall(r"Ran\s+(\d+)\s+tests?\s+in", output)
        if matches: count = int(matches[-1])
        log_path = log_dir / f"{test_file.stem}.log"
        log_path.write_text(output, encoding="utf-8", errors="replace")
        duration = (datetime.now(timezone.utc) - started).total_seconds()
        row = {"index": offset, "file": test_file.name, "status": status, "tests": count, "duration_seconds": round(duration, 3), "log": str(log_path.relative_to(ROOT))}
        results.append(row)
        print(f"    {status}: {count if count is not None else '?'} test(s), {duration:.1f}s; log={row['log']}", flush=True)

    passed = sum(row["status"] == "PASS" for row in results)
    failed = [row for row in results if row["status"] != "PASS"]
    report = {
        "started_utc": stamp, "source_root": str(ROOT), "test_file_count_total": len(files),
        "start_index": args.start, "selected_file_count": len(selected), "timeout_seconds_per_file": args.timeout,
        "passed_files": passed, "failed_or_timed_out_files": len(failed), "results": results,
    }
    report_json = report_dir / "summary.json"
    report_json.write_text(json.dumps(report, indent=2), encoding="utf-8")
    lines = ["# Test matrix results", "", f"- Files selected: {len(selected)}", f"- Passed: {passed}", f"- Failed/timed out: {len(failed)}", "", "| File | Status | Tests | Seconds | Log |", "|---|---|---:|---:|---|"]
    for row in results:
        lines.append(f"| `{row['file']}` | **{row['status']}** | {row['tests'] if row['tests'] is not None else '—'} | {row['duration_seconds']:.1f} | `{row['log']}` |")
    lines += ["", "Individual test logs are adjacent in `logs/`. A timeout is reported separately from an assertion failure."]
    report_md = report_dir / "summary.md"
    report_md.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"\nSummary: {passed}/{len(selected)} test files passed; {len(failed)} failed/timed out.")
    print(f"JSON: {report_json}")
    print(f"Markdown: {report_md}")
    if failed:
        for row in failed: print(f"{row['status']}: {row['file']} (see {row['log']})", file=sys.stderr)
        return 1
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
