"""Regression checks for opt-in profile-only random geometry and profile backdrops."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"


class ProfileShapesSourceTests(unittest.TestCase):
    def test_random_avatar_shape_is_gated_to_visible_profile_pages(self):
        avatar = (JAVA / "AvatarShape.java").read_text(encoding="utf-8")
        layout = (JAVA / "ProfileLayout.java").read_text(encoding="utf-8")
        self.assertIn('if (!randomProfilesEnabled() || !profileVisible() || path == null) return index();', avatar)
        self.assertIn('static void onProfilePageBound()', avatar)
        self.assertIn('PROFILE_SHAPES.clear()', avatar)
        self.assertIn('AvatarShape.onProfilePageBound();ProfileShapes.install(view)', layout)
        self.assertIn('static boolean currentProfileVisible()', layout)

    def test_profile_backdrop_is_static_and_uses_both_accents(self):
        shapes = (JAVA / "ProfileShapes.java").read_text(encoding="utf-8")
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn('int secondary = SecondaryAccent.colour()', shapes)
        self.assertIn('int primary = Accent.colour()', shapes)
        # the composition is fixed: no rotation and no frame loop
        self.assertIn('float rotation = 0f;', shapes)
        self.assertNotIn('seconds * 4.0f', shapes)
        self.assertNotIn('scheduleSelf(this', shapes)
        self.assertIn('0.75f', shapes)  # contour is 0.75dp, not the old 1.25dp
        self.assertIn('installCommentBackground(View cell)', shapes)
        self.assertIn('type.contains("RecyclerView")', shapes)
        self.assertIn('ProfileShapes.installCommentBackground(cell)', (JAVA / "Comments.java").read_text(encoding="utf-8"))
        self.assertIn('AccountAppearance.prefs(context).edit().putBoolean(KEY, on).apply()', shapes)
        self.assertIn('"Случайные формы в профилях"', settings)
        self.assertIn('"Фоновые фигуры в профилях и комментариях"', settings)
        self.assertNotIn('Вращающиеся фигуры', settings)

    def test_secondary_accent_has_independent_persistence_and_picker(self):
        accent = (JAVA / "SecondaryAccent.java").read_text(encoding="utf-8")
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn('public static final String KEY = "accent_secondary"', accent)
        self.assertIn('AccountAppearance.prefs(context).edit().putInt(KEY, colour | 0xFF000000).apply()', accent)
        self.assertIn('private void chooseSecondaryAccentColour()', settings)
        self.assertIn('"Второй контрастный цвет"', settings)

    def test_date_overlay_uses_contrasting_colours_in_both_themes(self):
        overlay = (JAVA / "DateOverlay.java").read_text(encoding="utf-8")
        patch = (ROOT / "kardinebi/dexpatch.py").read_text(encoding="utf-8")
        self.assertIn('boolean dark = Themes.isDark()', overlay)
        self.assertIn('view.setTextColor(dark ? 0xFFF6F0FA : 0xFF211A26)', overlay)
        self.assertIn('bindAuthor(Ljava/lang/Object;Ljava/lang/Object;)V', patch)
        self.assertIn('bindPost(Ljava/lang/Object;Ljava/lang/Object;)V', patch)


if __name__ == "__main__":
    unittest.main()
