#!/usr/bin/env bash
# Repeatable local QA entry point. Run from anywhere.
set -Eeuo pipefail
MODE="${1:-quick}"
KIT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$KIT_ROOT"
# The outer developer kit contains the source ZIP but not loose Java/tests. If
# this copy is being run from the outer kit, extract the nested source once.
if [[ ! -d "$ROOT/tests" && -f "$KIT_ROOT/TikTok-Mint-Source-1.1.3-avatar-fix.zip" ]]; then
  QA_SOURCE="$KIT_ROOT/.qa-source"
  if [[ ! -d "$QA_SOURCE/kardinebi/tests" ]]; then
    command -v unzip >/dev/null 2>&1 || { echo "ERROR: unzip is required to prepare the source QA tree." >&2; exit 3; }
    mkdir -p "$QA_SOURCE"
    unzip -oq "$KIT_ROOT/TikTok-Mint-Source-1.1.3-avatar-fix.zip" -d "$QA_SOURCE"
  fi
  if [[ -d "$QA_SOURCE/kardinebi/tests" ]]; then ROOT="$QA_SOURCE/kardinebi"; fi
fi
if [[ ! -d "$ROOT/tests" ]]; then
  echo "ERROR: could not find the source tests. Run from the full source tree or keep TikTok-Mint-Source-1.1.3-avatar-fix.zip beside this script." >&2
  exit 3
fi
cd "$ROOT"
mkdir -p qa-artifacts/reports
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
LOG="qa-artifacts/reports/${STAMP}-${MODE}.log"

preflight() {
  python3 - <<'PY'
import sys, shutil, importlib.util
print(f"Python: {sys.version.split()[0]}")
if sys.version_info < (3, 10): raise SystemExit("ERROR: Python 3.10+ is required")
for cmd in ("javac", "java"):
    found = shutil.which(cmd)
    print(f"{cmd}: {found or 'MISSING'}")
    if not found: raise SystemExit(f"ERROR: {cmd} is required for Java runtime harnesses")
try:
    import PIL
    print(f"Pillow: {PIL.__version__}")
except ImportError:
    print("Pillow: MISSING (visual QA needs it; install with python3 -m pip install --user Pillow)")
if shutil.which("adb"):
    print(f"adb: {shutil.which('adb')}")
else:
    print("adb: missing (device screenshot capture is unavailable in this environment; source tests can still run)")
PY
  python3 -m compileall -q .
  python3 qa/static_audit.py
}

run_test_file() {
  local pattern="$1"
  printf '\n===== %s =====\n' "$pattern"
  python3 -m unittest discover -s tests -p "$pattern" -v
}

{
  echo "TikTok Mint QA"
  echo "UTC: $STAMP"
  echo "Mode: $MODE"
  echo "Source: $ROOT"
  echo
  case "$MODE" in
    preflight)
      preflight
      ;;
    quick)
      preflight
      run_test_file 'test_photo_swipe_patcher.py'
      run_test_file 'test_settings_blank_sheet_source.py'
      run_test_file 'test_settings_redesign_v2.py'
      run_test_file 'test_custom_redesign_source.py'
      run_test_file 'test_expressive_screens_runtime.py'
      run_test_file 'test_screen_runtime.py'
      run_test_file 'test_profile_mint_runtime.py'
      run_test_file 'test_feed_follow_shape_source.py'
      run_test_file 'test_avatar_shape_hook_runtime.py'
      run_test_file 'test_profile_shapes_source.py'
      run_test_file 'test_profile_layout_hook.py'
      run_test_file 'test_date_overlay_runtime.py'
      run_test_file 'test_date_patch_compat.py'
      run_test_file 'test_comment_like_runtime.py'
      run_test_file 'test_archive_media_runtime.py'
      run_test_file 'test_streak_repository_runtime.py'
      run_test_file 'test_streak_schedule_edges.py'
      run_test_file 'test_nostalgia_filter_runtime.py'
      run_test_file 'test_qa_tools.py'
      ;;
    full)
      preflight
      python3 qa/run_test_matrix.py --timeout "${QA_TEST_TIMEOUT_SECONDS:-60}"
      ;;
    *)
      echo "Usage: bash qa/run_qa.sh [preflight|quick|full]" >&2
      exit 2
      ;;
  esac
  echo
  echo "QA_MODE_RESULT=PASS"
} 2>&1 | tee "$LOG"
STATUS=${PIPESTATUS[0]}
if (( STATUS != 0 )); then
  echo "QA_MODE_RESULT=FAIL ($STATUS); see $ROOT/$LOG" >&2
  exit "$STATUS"
fi
printf '\nQA log saved to: %s\n' "$ROOT/$LOG"
