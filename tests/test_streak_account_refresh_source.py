"""The selected account's favorite sticker repository must be refreshed immediately."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"


class StreakAccountRefreshSourceTests(unittest.TestCase):
    def test_account_switch_invalidates_and_refreshes_the_sticker_repository(self):
        favourite = (JAVA / "FavouriteStickers.java").read_text(encoding="utf-8")
        streaks = (JAVA / "Streaks.java").read_text(encoding="utf-8")
        self.assertIn('static void accountChanged()', favourite)
        self.assertIn('handler.post(() -> { if (Streaks.isEnabled()) load(null); })', favourite)
        self.assertIn('handler.postDelayed(() -> { if (Streaks.isEnabled()) load(null); }, 1000L)', favourite)
        self.assertIn('FavouriteStickers.accountChanged();', streaks)

    def test_text_sender_is_not_misreported_as_success_when_native_api_is_unknown(self):
        streaks = (JAVA / "Streaks.java").read_text(encoding="utf-8")
        self.assertIn('Diary.note("streak text: not available in this build")', streaks)
        self.assertIn('return false;', streaks[streaks.index('private static boolean sendText('):streaks.index('private static List<String> conversations()')])


if __name__ == "__main__":
    unittest.main()
