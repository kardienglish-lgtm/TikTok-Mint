"""Source guards for the colour-scheme menu: swatches come from Themes, the secret theme stays gated."""
import pathlib
import unittest

JAVA = pathlib.Path(__file__).resolve().parents[1] / "inject" / "java" / "cat" / "kardinebi"


class ThemePresetsSourceTest(unittest.TestCase):
    def test_presets_have_one_source_of_colours_for_apply_and_swatches(self):
        themes = (JAVA / "Themes.java").read_text(encoding="utf-8")
        self.assertIn("public static int presetBackground(int chosen, boolean dark)", themes)
        self.assertIn("public static int presetText(boolean dark)", themes)
        self.assertIn("int background = presetBackground(chosen, dark);", themes)
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn("Themes.presetBackground(which, dark)", settings)

    def test_selected_scheme_is_marked_and_menu_is_a_panel(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn("if (inUse) row.addView(new Check(this, Accent.colour()));", settings)
        self.assertIn("Panel panel = Panel.with(this, skin, Text.THEME_PRESETS);", settings)

    def test_secret_theme_is_only_offered_when_unlocked(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn("if (isKardinebiUnlocked()) {\n            list.addView(line());\n            list.addView(presetRow(\"Кардинеби", settings)


if __name__ == "__main__":
    unittest.main()
