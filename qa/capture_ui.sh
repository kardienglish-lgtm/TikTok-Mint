#!/usr/bin/env bash
# Capture the currently visible screen on an Android device/emulator reachable via adb.
set -Eeuo pipefail

usage() {
  cat <<'EOF'
Usage: bash qa/capture_ui.sh SCREEN_NAME [--burst COUNT] [--interval SECONDS] [--logs]

Examples:
  bash qa/capture_ui.sh settings
  bash qa/capture_ui.sh comments-gif --burst 12 --interval 0.4

Notes:
  - Navigate manually to the target screen before running.
  - --burst takes repeated frames; scroll manually during capture to diagnose flicker.
  - Set ANDROID_SERIAL when adb sees multiple devices.
  - By default only high-priority error logs are captured, and only with --logs.
EOF
}

if [[ $# -lt 1 || "$1" == "-h" || "$1" == "--help" ]]; then usage; exit 0; fi
NAME="$1"; shift
if [[ ! "$NAME" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]]; then echo "Invalid SCREEN_NAME. Use letters, digits, dash, underscore or dot." >&2; exit 2; fi
BURST=1
INTERVAL="0.5"
SAVE_LOGS=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --burst) BURST="${2:?missing count}"; shift 2 ;;
    --interval) INTERVAL="${2:?missing seconds}"; shift 2 ;;
    --logs) SAVE_LOGS=1; shift ;;
    *) echo "Unknown option: $1" >&2; usage; exit 2 ;;
  esac
done
if ! [[ "$BURST" =~ ^[0-9]+$ ]] || (( BURST < 1 || BURST > 50 )); then echo "--burst must be 1..50" >&2; exit 2; fi
if ! python3 - "$INTERVAL" <<'PY' >/dev/null 2>&1
import sys
v=float(sys.argv[1]); assert 0.05 <= v <= 10
PY
then echo "--interval must be between 0.05 and 10 seconds" >&2; exit 2; fi
if ! command -v adb >/dev/null 2>&1; then echo "ERROR: adb is not installed or not on PATH. This environment needs Android SDK Platform-Tools." >&2; exit 3; fi

ADB=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then ADB+=( -s "$ANDROID_SERIAL" ); fi
mapfile -t DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  if (( ${#DEVICES[@]} == 0 )); then echo "ERROR: no adb device is connected. Run 'adb devices -l'." >&2; exit 4; fi
  if (( ${#DEVICES[@]} > 1 )); then echo "ERROR: multiple devices detected. Set ANDROID_SERIAL to the target serial." >&2; printf '  %s\n' "${DEVICES[@]}" >&2; exit 4; fi
  ADB+=( -s "${DEVICES[0]}" )
fi
SERIAL="${ANDROID_SERIAL:-${DEVICES[0]}}"
SAFE_SERIAL="$(printf '%s' "$SERIAL" | tr -c 'A-Za-z0-9._-' '_')"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$ROOT/qa-artifacts/captures/$SAFE_SERIAL/$STAMP-$NAME"
mkdir -p "$OUT"

{
  echo "name=$NAME"
  echo "utc_timestamp=$STAMP"
  echo "serial=$SERIAL"
  echo "requested_frames=$BURST"
  echo "interval_seconds=$INTERVAL"
  echo "logs_included=$SAVE_LOGS"
  echo
  echo "=== SDK / OS ==="
  "${ADB[@]}" shell getprop ro.build.version.release 2>/dev/null | sed 's/^/android_release=/'
  "${ADB[@]}" shell getprop ro.build.version.sdk 2>/dev/null | sed 's/^/api_level=/'
  "${ADB[@]}" shell wm size 2>/dev/null | sed 's/^/screen_size=/'
  "${ADB[@]}" shell wm density 2>/dev/null | sed 's/^/screen_density=/'
  echo
  echo "=== Top activity ==="
  "${ADB[@]}" shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity|Resumed:' | tail -8 || true
} > "$OUT/device-info.txt"

for (( i=1; i<=BURST; i++ )); do
  file=$(printf '%s/frame_%03d.png' "$OUT" "$i")
  if ! "${ADB[@]}" exec-out screencap -p > "$file"; then echo "ERROR: screenshot failed at frame $i" >&2; exit 5; fi
  if [[ ! -s "$file" ]] || ! python3 - "$file" <<'PYIMG' >/dev/null 2>&1
from PIL import Image
import sys
with Image.open(sys.argv[1]) as image:
    if image.format != "PNG": raise SystemExit(1)
    image.verify()
PYIMG
  then echo "ERROR: screenshot $file is empty or invalid. Ensure Pillow is installed." >&2; exit 5; fi
  printf 'Captured frame %d/%d\n' "$i" "$BURST"
  if (( i < BURST )); then sleep "$INTERVAL"; fi
done

# UI hierarchy is best-effort: some Android builds deny uiautomator on protected windows.
if "${ADB[@]}" shell uiautomator dump /sdcard/tiktok-mint-window.xml >/dev/null 2>&1; then
  "${ADB[@]}" pull /sdcard/tiktok-mint-window.xml "$OUT/ui-hierarchy.xml" >/dev/null 2>&1 || true
  "${ADB[@]}" shell rm -f /sdcard/tiktok-mint-window.xml >/dev/null 2>&1 || true
else
  echo "UI hierarchy dump was unavailable on this screen." > "$OUT/ui-hierarchy-unavailable.txt"
fi
if (( SAVE_LOGS )); then
  "${ADB[@]}" logcat -d -t 800 '*:E' > "$OUT/logcat-errors.txt" 2>&1 || true
fi

python3 "$ROOT/qa/visual_report.py" "$OUT"
ZIP="$ROOT/qa-artifacts/review-packages/${STAMP}-${NAME}-visual-review.zip"
mkdir -p "$(dirname "$ZIP")"
python3 - "$OUT" "$ZIP" <<'PY'
from pathlib import Path
import sys, zipfile
src, dst = Path(sys.argv[1]), Path(sys.argv[2])
with zipfile.ZipFile(dst, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
    for item in sorted(src.rglob("*")):
        if item.is_file(): archive.write(item, item.relative_to(src))
with zipfile.ZipFile(dst) as archive:
    bad=archive.testzip()
    if bad: raise SystemExit(f"ZIP verification failed at {bad}")
print(f"Review package: {dst}")
PY
printf '\nDone. Capture folder: %s\n' "$OUT"
printf 'Upload this review ZIP in the chat to let the assistant inspect the screenshots. Review/redact visible personal data first.\n'
