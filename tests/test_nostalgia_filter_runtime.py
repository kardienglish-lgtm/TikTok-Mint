"""The obsolete nostalgia date filter must no longer exist anywhere in runtime code."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]
class NostalgiaRemovalTest(unittest.TestCase):
 def test_filter_and_settings_are_removed(self):
  files = list((ROOT / "inject/java/cat/kardinebi").glob("*.java"))
  source = "\n".join(p.read_text(encoding="utf-8") for p in files)
  self.assertNotIn("KEY_NOSTALGIA", source)
  self.assertNotIn("NOSTALGIA_FEED", source)
  self.assertNotIn("newerThanNostalgiaCutoff", source)
