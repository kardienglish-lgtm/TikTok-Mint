#!/usr/bin/env python3
"""Build a contact sheet and frame-difference report from Android screenshots.

The report is diagnostic, not a verdict: video/GIF animation and scrolling naturally
change pixels. Use --compare only for stable screens captured under matching conditions.
"""
from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path
from statistics import mean

try:
    from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageStat
except ImportError as exc:  # friendly message for Codespaces
    print("ERROR: Pillow is required. Install with: python3 -m pip install --user Pillow", file=sys.stderr)
    raise SystemExit(2) from exc

IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".webp"}
EXCLUDED_NAMES = {"contact-sheet.png", "contact_sheet.png"}


def list_screens(root: Path) -> list[Path]:
    out = []
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in IMAGE_EXTS:
            continue
        rel = path.relative_to(root)
        if any(part.lower() in {"report", "reports", "diffs"} for part in rel.parts[:-1]):
            continue
        if path.name.lower() in EXCLUDED_NAMES or path.name.lower().startswith("diff_"):
            continue
        out.append(path)
    return sorted(out, key=lambda p: str(p.relative_to(root)).lower())


def image_signature(path: Path) -> tuple[int, int]:
    with Image.open(path) as image:
        return image.size


def compare_images(left_path: Path, right_path: Path, pixel_tolerance: int = 8) -> dict[str, float | int | str]:
    with Image.open(left_path) as left_image, Image.open(right_path) as right_image:
        left = left_image.convert("RGB")
        right = right_image.convert("RGB")
        if left.size != right.size:
            return {
                "left": str(left_path), "right": str(right_path),
                "width": left.width, "height": left.height,
                "changed_ratio": 1.0, "mean_abs_diff": 255.0,
                "note": f"dimension mismatch: {left.width}x{left.height} vs {right.width}x{right.height}",
            }
        diff = ImageChops.difference(left, right)
        # A pixel is considered changed if any RGB channel exceeds tolerance.
        changed = diff.convert("L").point(lambda value: 255 if value > pixel_tolerance else 0)
        changed_count = changed.histogram()[255]
        total = max(1, left.width * left.height)
        stats = ImageStat.Stat(diff)
        mean_abs = mean(stats.mean) if stats.mean else 0.0
        return {
            "left": str(left_path), "right": str(right_path),
            "width": left.width, "height": left.height,
            "changed_ratio": changed_count / total,
            "mean_abs_diff": mean_abs,
            "note": "",
        }


def save_contact_sheet(images: list[Path], root: Path, target: Path) -> None:
    columns = 4
    cell_w, cell_h = 270, 520
    label_h, padding = 42, 16
    rows = max(1, (len(images) + columns - 1) // columns)
    sheet = Image.new("RGB", (padding + columns * (cell_w + padding), padding + rows * (cell_h + label_h + padding)), "#17191d")
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("DejaVuSans.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    for index, path in enumerate(images):
        x = padding + (index % columns) * (cell_w + padding)
        y = padding + (index // columns) * (cell_h + label_h + padding)
        with Image.open(path) as raw:
            image = raw.convert("RGB")
            original_size = image.size
            image.thumbnail((cell_w, cell_h), Image.Resampling.LANCZOS)
            x_image = x + (cell_w - image.width) // 2
            y_image = y + (cell_h - image.height) // 2
            sheet.paste(image, (x_image, y_image))
        rel = str(path.relative_to(root))
        draw.text((x, y + cell_h + 3), rel[:42], fill="#f1f2f4", font=font)
        draw.text((x, y + cell_h + 20), f"{original_size[0]}×{original_size[1]}", fill="#aab1bc", font=font)
    target.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(target)


def write_markdown(path: Path, root: Path, count: int, pairs: list[dict], compare_rows: list[dict], contact_sheet: Path) -> None:
    lines = [
        "# TikTok Mint visual QA report", "",
        f"- Capture folder: `{root}`",
        f"- Images analysed: **{count}**",
        f"- Contact sheet: `{contact_sheet}`",
        "- Pixel-difference metrics are diagnostic only. GIF/video playback, scrolling, counters, clock and animated surfaces naturally change between frames.",
        "",
    ]
    if pairs:
        lines += ["## Consecutive-frame changes", "", "| Frame A | Frame B | Changed pixels | Mean RGB difference | Note |", "|---|---|---:|---:|---|"]
        for row in pairs:
            lines.append(f"| `{Path(row['left']).name}` | `{Path(row['right']).name}` | {row['changed_ratio']:.2%} | {row['mean_abs_diff']:.2f}/255 | {row['note'] or '—'} |")
        lines.append("")
    if compare_rows:
        lines += ["## Baseline comparison", "", "| Baseline | Current | Changed pixels | Mean RGB difference | Note |", "|---|---|---:|---:|---|"]
        for row in compare_rows:
            lines.append(f"| `{Path(row['left']).name}` | `{Path(row['right']).name}` | {row['changed_ratio']:.2%} | {row['mean_abs_diff']:.2f}/255 | {row['note'] or '—'} |")
        lines.append("")
    lines += [
        "## Interpretation", "",
        "Large changes between burst frames can be expected for animated GIFs, video, scroll movement or animated backgrounds. To inspect layout regressions, compare a stable screen with the same device resolution, density, font scale, theme and state.",
        "",
        "## Sharing note", "",
        "The accompanying UI hierarchy may contain visible usernames, comments or other on-screen text. Review it and redact anything private before sharing the package.",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture_dir", type=Path)
    parser.add_argument("--compare", type=Path, help="optional baseline directory; match files by relative path")
    parser.add_argument("--pixel-tolerance", type=int, default=8, help="per-channel absolute RGB noise tolerance (default: 8)")
    parser.add_argument("--fail-changed-ratio", type=float, default=None, help="exit 1 if any matched stable baseline image exceeds this changed-pixel ratio")
    args = parser.parse_args()
    root = args.capture_dir.resolve()
    if not root.is_dir():
        parser.error(f"capture directory does not exist: {root}")
    images = list_screens(root)
    if not images:
        parser.error(f"no screenshots found in {root}")
    report_dir = root / "report"
    report_dir.mkdir(exist_ok=True)
    contact = report_dir / "contact-sheet.png"
    save_contact_sheet(images, root, contact)

    frames = [p for p in images if p.name.startswith("frame_") or p.name.startswith("screen_")]
    consecutive = [compare_images(a, b, args.pixel_tolerance) for a, b in zip(frames, frames[1:])]
    if consecutive:
        with (report_dir / "frame-differences.csv").open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(consecutive[0].keys()))
            writer.writeheader()
            writer.writerows(consecutive)

    baseline_rows = []
    failed = False
    if args.compare:
        base = args.compare.resolve()
        if not base.is_dir():
            parser.error(f"baseline directory does not exist: {base}")
        current_by_relative = {p.relative_to(root).as_posix(): p for p in images}
        for base_image in list_screens(base):
            rel = base_image.relative_to(base).as_posix()
            current_image = current_by_relative.get(rel)
            if current_image is None:
                baseline_rows.append({"left": str(base_image), "right": "MISSING", "width": 0, "height": 0, "changed_ratio": 1.0, "mean_abs_diff": 255.0, "note": "missing current screenshot"})
                failed = True
                continue
            row = compare_images(base_image, current_image, args.pixel_tolerance)
            baseline_rows.append(row)
            if args.fail_changed_ratio is not None and row["changed_ratio"] > args.fail_changed_ratio:
                failed = True

    report_md = report_dir / "visual-report.md"
    write_markdown(report_md, root, len(images), consecutive, baseline_rows, contact)
    summary = report_dir / "visual-metrics.json"
    import json
    summary.write_text(json.dumps({"capture_dir": str(root), "image_count": len(images), "consecutive_frames": consecutive, "baseline_comparison": baseline_rows}, indent=2), encoding="utf-8")
    print(f"Images:        {len(images)}")
    print(f"Contact sheet: {contact}")
    print(f"Report:        {report_md}")
    if consecutive:
        high = max(row["changed_ratio"] for row in consecutive)
        print(f"Largest consecutive-frame change: {high:.2%} (interpret in context; motion naturally changes pixels)")
    if args.compare:
        print(f"Baseline pairs compared: {len(baseline_rows)}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
