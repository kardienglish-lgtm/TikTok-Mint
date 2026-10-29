"""Regression checks for the redesigned Mint settings text and controls."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"

class SettingsRedesignV2Tests(unittest.TestCase):
    def test_switch_rows_use_descriptive_copy_and_individual_glyphs(self):
        source = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn('TextView summary = detail(settingSummary(title, glyph, requiresRestart));', source)
        self.assertIn('private String settingIcon(String title, String fallback)', source)
        self.assertIn('private String settingSummary(String title, String iconName, boolean requiresRestart)', source)
        self.assertIn('return "Убирает рекламные публикации из рекомендаций.";', source)
        self.assertIn('return "Фильтрует уведомления о сообщениях друзей.";', source)
        self.assertIn('return "Округляет значки действий возле видео.";', source)

    def test_settings_copy_is_rewritten(self):
        source = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        texts = (JAVA / "Text.java").read_text(encoding="utf-8")
        for phrase in ('Разделы настроек', 'Выбери нужный раздел', 'Material 3 для профиля', 'Настройки модификации', 'Второй контрастный цвет'):
            self.assertIn(phrase, source)
        # Nostalgia/date cutoff was deliberately removed from the runtime settings.
        self.assertNotIn('Лента до 10.10.2022', source)
        self.assertIn('"Личные сообщения"', texts)
        self.assertIn('"Уведомления о репостах"', texts)
        self.assertIn('"Анимированные индикаторы"', texts)

    def test_required_upstream_notice_remains_visible_and_other_branding_is_not_promoted(self):
        source = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        # The inherited ADDITIONAL-TERMS.md requires a direct main-screen credit and intact support links.
        self.assertIn('section("Источник компонентов и лицензия")', source)
        self.assertIn('"Источник компонентов · TikTok Mint"', source)
        self.assertIn('"Форк TikTok Mint · сопровождение @kardinebi · канал @TikTokmintt"', source)
        self.assertIn('() -> open(GIT)', source)
        # Payment prompts and direct-message-to-channel rows were explicitly removed.
        self.assertNotIn('Text.YOOMONEY', source)
        self.assertNotIn('Text.DONATE_BANNER_WRITE', source)
        self.assertNotIn('"TikTok Mint 2.0.5 · сопровождение @kardinebi"', source)


    def test_app_author_details_live_under_about_and_redundant_thanks_panel_is_removed(self):
        source = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn('section("О приложении")', source)
        self.assertIn('"Разработчик модификации"', source)
        self.assertIn('\"TikTok \" + Version.TIKTOK + \" · независимая модификация\"', source)
        self.assertNotIn('private View thanksHead()', source)
        self.assertNotIn('private View thanks()', source)
        self.assertNotIn('Text.MADE_BY);', source)
        # The About section should name the current fork maintainer.
        about = source[source.index('column.addView(section("О приложении"))'):source.index('private void rebuild()')]
        self.assertIn('@kardinebi', about)
        self.assertIn('TikTok Mint', about)
        self.assertNotIn('Исходный проект и лицензия', about)
        self.assertNotIn('Исходный проект', source[source.index('column.addView(section(Text.LINKS))'):])
        # The source package's additional terms require the upstream notice to remain.
        self.assertIn('section("Источник компонентов и лицензия")', source)
        self.assertIn('Форк TikTok Mint · сопровождение @kardinebi', source)
        self.assertNotIn('Text.DONATE_BANNER_WRITE', source)

    def test_screen_restyling_waits_for_layout_quiet_and_avoids_nested_profile_pills(self):
        source = (JAVA / "MintScreenDesign.java").read_text(encoding="utf-8")
        self.assertIn('root.postDelayed(deferredScan, QUIET_MS)', source)
        self.assertIn('private static void cancelDeferred()', source)
        self.assertIn('pruneNested(desired, ACCOUNT_STAT, ACCOUNT_ACTION)', source)
        account = source[source.index('private static void styleAccount'):source.index('private static void pruneNested')]
        self.assertNotIn('else if (view instanceof TextView) desired.put(view, ACCOUNT_STAT)', account)
        self.assertIn('case ACCOUNT_STAT:', source)
        self.assertIn('radius = 18f; minHeight = 44; horizontal = 6; vertical = 3; break;', source)

    def test_settings_hero_is_compact_solid_and_keeps_logs_accessible(self):
        source = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertNotIn("root.addView(new MintBackdrop(this)", source)
        hero_start = source.index("private View homeHero()")
        hero_end = source.index("\n    private boolean hasRecentLogWarning()", hero_start)
        hero = source[hero_start:hero_end]
        self.assertIn('label("TikTok Mint")', hero)
        self.assertIn('detail("Настройки модификации")', hero)
        self.assertIn('SettingsGlyph.make("article", skin.text)', hero)
        self.assertIn("showQuickLogs()", hero)
        self.assertNotIn("setColors(", hero)
        self.assertNotIn("GradientDrawable.Orientation.TL_BR", hero)
        self.assertNotIn("MATERIAL 3 EXPRESSIVE", hero)
        self.assertNotIn("DUAL ACCENT", hero)

if __name__ == "__main__":
    unittest.main()
