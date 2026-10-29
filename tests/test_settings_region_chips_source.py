import unittest
import zipfile
from pathlib import Path

class RegionChipSourceTest(unittest.TestCase):
    def test_compact_choice_chip_is_implemented_and_used(self):
        root = Path(__file__).resolve().parents[1]
        java = (root / "inject/java/cat/kardinebi/SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn("private View quickChip(String title, boolean selected, final Runnable action)", java)
        self.assertIn("shortcuts.addView(quickChip(\"🇺🇸 США\"", java)
        self.assertIn("shortcuts.addView(quickChip(\"🇬🇧 Британия\"", java)
        self.assertIn("shortcuts.addView(quickChip(\"🇩🇪 Германия\"", java)
        self.assertIn("shortcuts.addView(quickChip(\"🇳🇱 Нидерланды\"", java)
        self.assertIn("shape.setColor(fill);", java)
        self.assertIn("shape.setStroke(Math.max(1, dp(1)), outline);", java)

if __name__ == "__main__":
    unittest.main()
