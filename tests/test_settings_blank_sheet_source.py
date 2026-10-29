"""Source checks for the uncluttered Material 3 Mint settings surface."""
from pathlib import Path
import unittest
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"
SOURCE = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")

class BlankSheetSettingsSourceTest(unittest.TestCase):
    def test_dashboard_is_a_single_column_material_settings_index(self):
        self.assertNotIn('private View quickSettings()', SOURCE)
        self.assertNotIn('quickSettingTile(', SOURCE)
        self.assertNotIn('quickChip("block"', SOURCE)
        self.assertNotIn('quickChip("calendar_month"', SOURCE)
        self.assertIn('heading = label("Разделы настроек")', SOURCE)
        self.assertIn('private View categoryCard(final int chosen', SOURCE)
        self.assertIn('item.setMinimumHeight(dp(72))', SOURCE)
        self.assertIn('for (int n = 0; n < shelf.length; n++)', SOURCE)
        self.assertNotIn('for (int n = 0; n < shelf.length; n += 2)', SOURCE)
        self.assertIn('SettingsGlyph.make("chevron_right", skin.muted())', SOURCE)

    def test_settings_surfaces_are_solid_tonal_and_do_not_reuse_old_gradient_tiles(self):
        card = SOURCE[SOURCE.index('private LinearLayout card()'):SOURCE.index('private View wrap(')]
        self.assertIn('shape.setColor(m3Surface(0.055f))', card)
        self.assertIn('setCornerRadius(dp(28))', card)  # M3 large shape (was 22dp)
        self.assertNotIn('setColors(', card)
        hero_start = SOURCE.index('private View homeHero()')
        hero_end = SOURCE.index('\n    private boolean hasRecentLogWarning()', hero_start)
        hero = SOURCE[hero_start:hero_end]
        self.assertNotIn('setColors(', hero)
        self.assertNotIn('GradientDrawable.Orientation', hero)
        self.assertIn('showQuickLogs()', hero)
        self.assertIn('SettingsGlyph.make("article", skin.text)', hero)
        self.assertIn('skin = Skin.material3(this)', SOURCE)
        self.assertIn('private View section(String text)', SOURCE)
        self.assertIn('view.setTextColor(skin.muted())', SOURCE)

    def test_setting_action_handlers_search_and_group_navigation_are_preserved(self):
        self.assertIn('setting.set(checked)', SOURCE)
        self.assertIn('runSearch(input.getText().toString())', SOURCE)
        self.assertIn('settingsGroup = chosen', SOURCE)
        self.assertIn('Motion.navigate(column, 1f', SOURCE)
        self.assertIn('private String settingIcon(String title, String fallback)', SOURCE)


    def test_each_visible_settings_icon_resolves_to_a_material_vector(self):
        glyph_source = (JAVA / "SettingsGlyph.java").read_text(encoding="utf-8")
        declared = set(re.findall(r'paths\.put\("([^"]+)"', glyph_source))
        used = set(re.findall(r'SettingsGlyph\.make\("([a-z_]+)"', SOURCE))
        used.update(re.findall(r'(?:toggleRow|liveToggleRow|actionRow|linkRow|icon)\(\s*"([a-z_]+)"', SOURCE))
        self.assertTrue(used, "settings must declare icon usage")
        self.assertEqual(set(), used - declared, "every settings icon needs a vector or documented alias")

    def test_log_indicator_ignores_benign_missing_hook_messages(self):
        warning = SOURCE[SOURCE.index('private boolean hasRecentLogWarning()'):SOURCE.index('private void showQuickLogs()', SOURCE.index('private boolean hasRecentLogWarning()'))]
        self.assertIn('line.contains("exception")', warning)
        self.assertIn('line.contains("crash")', warning)
        self.assertNotIn('line.contains("не найден")', warning)
        self.assertNotIn('line.contains("not recognized")', warning)

if __name__ == "__main__":
    unittest.main()
