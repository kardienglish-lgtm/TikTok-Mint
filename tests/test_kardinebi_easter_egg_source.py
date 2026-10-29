"""Regression checks for the comment stability and Kardinebi easter egg revision."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"


class KardinebiEasterEggSourceTests(unittest.TestCase):
    def test_comment_and_profile_hierarchies_are_not_painted_by_the_scanner(self):
        source = (JAVA / "MintScreenDesign.java").read_text(encoding="utf-8")
        self.assertIn("private static final long QUIET_MS = 60L", source)
        self.assertIn("if (pending == root) return;", source)
        scan = source[source.index("private static void scan(View root)"):source.index("private static final class Node")]
        self.assertNotIn("findCommentSheet(frame)", scan)
        self.assertNotIn("styleComments(comments, frame)", scan)
        self.assertNotIn("styleAccount(root, frame)", scan)
        self.assertIn("synchronize(commentPaint, new IdentityHashMap<>())", scan)
        self.assertIn("synchronize(accountPaint, new IdentityHashMap<>())", scan)

    def test_profile_tabs_have_only_one_styling_owner(self):
        design = (JAVA / "MintScreenDesign.java").read_text(encoding="utf-8")
        md3 = (JAVA / "Md3.java").read_text(encoding="utf-8")
        self.assertNotIn("desired.put(child.view, ACCOUNT_TAB)", design)
        self.assertIn("Preserve the selected-tab cache", md3)
        self.assertIn("For You / Following navigation is intentionally", md3)

    def test_legacy_secret_unlock_and_theme_are_removed(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertNotIn("Mine.unlockEasterEgg", settings)
        self.assertNotIn("kardinebiTapCount", settings)
        self.assertNotIn("Кардинеби · секретная тема", settings)
        self.assertNotIn("Секретный знак Кардинеби", settings)

    def test_compact_settings_dashboard_exposes_theme_presets(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        start = settings.index("private void appearanceProfileContent()")
        end = settings.index("private void rebuild()", start)
        compact_page = settings[start:end]
        self.assertIn('actionRow("palette", Text.THEME_PRESETS', compact_page)
        self.assertIn("this::chooseThemePreset", compact_page)

    def test_only_the_fixed_official_badge_is_public(self):
        import json
        badges = (JAVA / "Badges.java").read_text(encoding="utf-8")
        server = (ROOT / "server/badges.py").read_text(encoding="utf-8")
        config = json.loads((ROOT / "badges.json").read_text(encoding="utf-8"))
        self.assertIn('"oh_its_me"', badges)
        self.assertIn('"kardinebi-1024-glyph.png"', badges)
        self.assertIn('OFFICIAL_USERS = ("7321751563004052486", "7413409720789369874")', server)
        self.assertEqual(["oh_its_me"], [entry["id"] for entry in config["badges"]])
        self.assertEqual(["7321751563004052486", "7413409720789369874"], config["badges"][0]["users"])
        self.assertIn('return 410, {"error": "the only public badge is fixed"}', server)

    def test_required_upstream_notice_is_not_removed_or_moved(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        terms = (ROOT / "ADDITIONAL-TERMS.md").read_text(encoding="utf-8")
        self.assertIn('section("Источник компонентов и лицензия")', settings)
        self.assertIn('Источник компонентов · TikTok Mint', settings)
        self.assertIn("third-party work", terms)
        self.assertIn("Only original changes authored by kardinebi are claimed by kardinebi", terms)


if __name__ == "__main__":
    unittest.main()
