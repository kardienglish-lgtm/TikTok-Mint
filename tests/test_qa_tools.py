"""Self-tests for image metrics/report tooling; no Android device is needed."""
from __future__ import annotations
import importlib.util
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # QA tools print a clear dependency message in this case.
    Image = None

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "qa" / "visual_report.py"
SPEC = importlib.util.spec_from_file_location("mint_visual_report", MODULE_PATH)
visual = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(visual)


@unittest.skipIf(Image is None, "Pillow is optional for non-visual source tests")
class VisualReportTests(unittest.TestCase):
    def test_identical_frames_have_no_changed_pixels(self):
        with tempfile.TemporaryDirectory() as temp:
            left = Path(temp) / "left.png"
            right = Path(temp) / "right.png"
            Image.new("RGB", (32, 32), (10, 20, 30)).save(left)
            Image.new("RGB", (32, 32), (10, 20, 30)).save(right)
            result = visual.compare_images(left, right)
            self.assertEqual(result["changed_ratio"], 0.0)
            self.assertEqual(result["mean_abs_diff"], 0.0)

    def test_one_pixel_change_is_detected(self):
        with tempfile.TemporaryDirectory() as temp:
            left = Path(temp) / "left.png"
            right = Path(temp) / "right.png"
            Image.new("RGB", (10, 10), (0, 0, 0)).save(left)
            image = Image.new("RGB", (10, 10), (0, 0, 0))
            image.putpixel((5, 5), (255, 255, 255))
            image.save(right)
            result = visual.compare_images(left, right)
            self.assertAlmostEqual(result["changed_ratio"], 0.01)
            self.assertGreater(result["mean_abs_diff"], 0.0)

    def test_dimension_mismatch_is_not_silently_resized(self):
        with tempfile.TemporaryDirectory() as temp:
            left = Path(temp) / "left.png"
            right = Path(temp) / "right.png"
            Image.new("RGB", (10, 10), (0, 0, 0)).save(left)
            Image.new("RGB", (20, 10), (0, 0, 0)).save(right)
            result = visual.compare_images(left, right)
            self.assertEqual(result["changed_ratio"], 1.0)
            self.assertIn("dimension mismatch", result["note"])

    def test_contact_sheet_is_created(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            image = root / "frame_001.png"
            Image.new("RGB", (80, 160), (30, 40, 50)).save(image)
            output = root / "report" / "contact-sheet.png"
            visual.save_contact_sheet([image], root, output)
            self.assertTrue(output.is_file())
            with Image.open(output) as result:
                self.assertGreater(result.width, 0)
                self.assertGreater(result.height, 0)


class ApkAuditTests(unittest.TestCase):
    def test_corrupt_apk_is_rejected_with_actionable_error(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "broken.apk"
            apk.write_bytes(b"this is not a ZIP/APK")
            result = subprocess.run(
                [sys.executable, str(ROOT / "qa" / "apk_audit.py"), str(apk)],
                capture_output=True, text=True, timeout=10,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("APK ZIP cannot be read", result.stderr)


if __name__ == "__main__":
    unittest.main()
