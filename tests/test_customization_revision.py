"""Source-level regression checks for the customization and maintenance pass."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"

class CustomizationRevisionTest(unittest.TestCase):
    def read(self, name):
        return (JAVA / name).read_text(encoding="utf-8")

    def test_private_texture_import_is_normalized_and_capped(self):
        source = self.read("Md3.java")
        self.assertIn("PRIVATE_TEXTURE", source)
        self.assertIn("setTexture(Context context, int which, Uri uri)", source)
        self.assertIn("512", source)
        self.assertIn("Bitmap.CompressFormat.PNG", source)
        self.assertIn("findTuxIcon", source)

    def test_material3_drawing_screen_is_removed(self):
        settings = self.read("SettingsActivity.java")
        self.assertFalse((JAVA / "SketchPad.java").exists())
        self.assertNotIn("SketchPad.open(this, skin)", settings)

    def test_nostalgia_filter_is_absent_from_runtime(self):
        combined = "\n".join(p.read_text(encoding="utf-8") for p in JAVA.glob("*.java"))
        self.assertNotIn("KEY_NOSTALGIA", combined)
        self.assertNotIn("NOSTALGIA_FEED", combined)
        self.assertNotIn("newerThanNostalgiaCutoff", combined)

    def test_streak_diagnostics_report_missing_native_prerequisites(self):
        alarm = self.read("StreakAlarm.java")
        streaks = self.read("Streaks.java")
        self.assertIn("nativeServiceSeen", alarm)
        self.assertIn("knownConversationCount", alarm)
        self.assertIn("selectedStickerReady", alarm)
        self.assertIn("static boolean nativeServiceSeen()", streaks)

    def test_mint_owned_dialogs_use_material3_panels(self):
        comments = self.read("OfflineComments.java")
        choices = self.read("OfflineChoices.java")
        self.assertIn("Panel.with(activity, Skin.material3(activity)", choices)
        self.assertNotIn("new AlertDialog.Builder", choices)
        self.assertNotIn("new AlertDialog.Builder", comments)

    def test_favourite_sticker_source_has_retry_and_repository_unwrap(self):
        source = self.read("FavouriteStickers.java")
        self.assertIn("getFavouriteStickersFlow", source)
        self.assertIn("getStickerList", source)
        self.assertIn("now-9000L", source)
        self.assertIn("replaceFavourites(normalized)", source)

if __name__ == "__main__":
    unittest.main()
