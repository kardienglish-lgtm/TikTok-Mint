"""Source guards for the Material 3 settings screen.

The design is the only design: there is no switch, flag or fallback page.
These read the Java source and prove the wiring (first tile, shared navigation,
licence notice, row states), not how the screen looks on a device.
"""
import pathlib
import unittest

JAVA = pathlib.Path(__file__).resolve().parents[1] / "inject" / "java" / "cat" / "kardinebi"


class M3SettingsScreenSourceTest(unittest.TestCase):
    def setUp(self):
        self.flags = (JAVA / "Flags.java").read_text(encoding="utf-8")
        self.settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")

    def test_no_hidden_switch_for_the_design(self):
        self.assertNotIn("KEY_M3_SETTINGS", self.flags + self.settings)
        self.assertNotIn("flag_m3_settings", self.flags + self.settings)

    def test_main_page_puts_the_telegram_tile_first(self):
        header = self.settings.index("column.addView(m3Header());")
        tile = self.settings.index("column.addView(telegramTile());", header)
        grid = self.settings.index("column.addView(m3Groups());", header)
        self.assertLess(header, tile)
        self.assertLess(tile, grid)

    def test_section_pages_use_the_m3_header(self):
        self.assertIn("column.addView(m3SectionHeader(settingsGroup));", self.settings)

    def test_every_tile_and_row_opens_through_one_navigation(self):
        self.assertIn("private void openGroup(final int chosen)", self.settings)
        self.assertIn("item.setOnClickListener(v -> openGroup(chosen));", self.settings)
        self.assertIn("tile.setOnClickListener(v -> openGroup(group));", self.settings)

    def test_licence_notice_stays_on_the_main_page(self):
        self.assertIn("licenceSection();", self.settings)
        self.assertIn('column.addView(section("Источник компонентов и лицензия"));', self.settings)
        self.assertIn('"Источник компонентов · TikTok Mint"', self.settings)

    def test_rows_take_the_m3_selected_state(self):
        self.assertIn("applyM3Row(item, on);", self.settings)
        self.assertIn("applyM3Row(item, checked);", self.settings)
        self.assertIn("applyM3Row(row, selected);", self.settings)
        self.assertIn("applyM3Row(row, chosen);", self.settings)
        self.assertIn("private void applyM3Row(View item, boolean on)", self.settings)


if __name__ == "__main__":
    unittest.main()
