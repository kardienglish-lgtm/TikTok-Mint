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

    def test_secret_tap_unlock_applies_requested_features_and_green_theme(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        for token in (
            '++kardinebiTapCount < 7',
            '"Пасхалка открыта. Сохраняю значок профиля…"',
            'Mine.unlockEasterEgg',
            'Md3.set(Md3.KEY_FEED, true)',
            'Md3.set(Md3.KEY_PROFILE, true)',
            'Md3.set(Md3.KEY_INBOX, true)',
            'Md3.set(Md3.KEY_ICONS, true)',
            'Fonts.setBold(true)',
            'Feed.setEnabled(true)',
            'Feed.setHides(Feed.KEY_LIVE, true)',
            'ModNotifications.set(ModNotifications.REPEATED_REPOSTS, false)',
            'Kardinebi.setIso("us")',
            '0xFF38C875',
            '"Кардинеби · секретная тема"' if '"Кардинеби · секретная тема"' in settings else 'applyKardinebiTheme()',
        ):
            self.assertIn(token, settings)

    def test_compact_settings_dashboard_exposes_theme_presets(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        start = settings.index("private void appearanceProfileContent()")
        end = settings.index("private void rebuild()", start)
        compact_page = settings[start:end]
        self.assertIn('actionRow("palette", Text.THEME_PRESETS', compact_page)
        self.assertIn("this::chooseThemePreset", compact_page)

    def test_custom_sparkle_badge_is_not_a_checkmark_and_shows_exact_message(self):
        badges = (JAVA / "Badges.java").read_text(encoding="utf-8")
        badge = (JAVA / "Badge.java").read_text(encoding="utf-8")
        self.assertIn("KARDINEBI_EASTER_EGG_MARK = '\\uF8FE'", badges)
        self.assertIn('"супер секретная пасхалочко"', badges)
        self.assertIn("private static Drawable easterEggIcon", badge)
        self.assertIn('Badges.localEasterEggMark(uid)', badge)
        self.assertIn('Badges.marksFor(uid)', badge)
        self.assertIn('easter_egg', (ROOT / 'server/badges.py').read_text(encoding='utf-8'))

    def test_required_upstream_notice_is_not_removed_or_moved(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        terms = (ROOT / "ADDITIONAL-TERMS.md").read_text(encoding="utf-8")
        self.assertIn('section("Источник компонентов и лицензия")', settings)
        self.assertIn('Источник компонентов · TikTok Mint', settings)
        self.assertIn("third-party work", terms)
        self.assertIn("Only original changes authored by kardinebi are claimed by kardinebi", terms)


if __name__ == "__main__":
    unittest.main()
