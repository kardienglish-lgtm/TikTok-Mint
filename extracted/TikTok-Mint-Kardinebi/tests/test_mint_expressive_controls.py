import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"


class MintExpressiveControlsTest(unittest.TestCase):
    def test_switch_is_a_complete_custom_control_not_a_status_icon(self):
        source = (JAVA / "M3Switch.java").read_text(encoding="utf-8")
        for detail in (
            "TRACK_WIDTH = 58f", "TRACK_HEIGHT = 36f", "VIEW_WIDTH = 64f",
            "VIEW_HEIGHT = 40f", "makeBloom", "final int lobes = 10",
            "checkMeasure.getSegment", "MotionEvent.ACTION_MOVE",
            "MotionEvent.ACTION_UP", "onInitializeAccessibilityNodeInfo",
            "ANIMATION_DURATION_MS = 360L",
        ):
            self.assertIn(detail, source)
        self.assertNotIn("new android.widget.Switch", source)

    def test_settings_rows_have_new_copy_semantic_icons_and_preserved_callbacks(self):
        source = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn('settingSummary(title, glyph, requiresRestart)', source)
        self.assertIn('settingIcon(title, picture)', source)
        self.assertIn('item.setMinimumHeight(dp(72))', source)
        self.assertIn('new LinearLayout.LayoutParams(dp(64), dp(40))', source)
        self.assertIn('toggle.setContentDescription(displayTitle + ", " + (on ? "включено" : "выключено"))', source)
        self.assertIn('setting.set(checked)', source)
        self.assertIn('title.equals(Text.NOTIFY_REPOSTS)', source)
        self.assertIn('if (title.equals(Text.MD3_PROFILE)', source)

    def test_backdrop_uses_more_expressive_shapes_without_catching_taps(self):
        source = (JAVA / "MintBackdrop.java").read_text(encoding="utf-8")
        self.assertIn("new RadialGradient", source)
        self.assertIn("petalShape(width * 0.94f", source)
        self.assertIn("drawSunburst", source)
        self.assertIn("drawCircle(orbitX", source)
        self.assertIn("setClickable(false)", source)
        self.assertIn("setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO)", source)
        self.assertIn("drawPolygon(canvas", source)
        self.assertIn("drift.setDuration(26000L)", source)
        self.assertIn("now - lastInvalidate >= 32L", source)

    def test_search_and_share_keep_mint_layer_while_comments_and_profile_are_native(self):
        themes = (JAVA / "Themes.java").read_text(encoding="utf-8")
        design = (JAVA / "MintScreenDesign.java").read_text(encoding="utf-8")
        self.assertFalse((JAVA / "ExpressiveScreens.java").exists())
        self.assertIn("MintScreenDesign.apply(root)", themes)
        self.assertIn("Skin.material3(context)", design)
        self.assertNotIn("Themes.text()", design)
        self.assertNotIn("Themes.backgroundInUse()", design)
        self.assertIn("styleSearch", design)
        self.assertIn("styleShare", design)
        scan = design[design.index("private static void scan(View root)"):design.index("private static final class Node")]
        self.assertNotIn("styleComments(comments, frame)", scan)
        self.assertNotIn("styleAccount(root, frame)", scan)
        self.assertIn("synchronize(commentPaint, new IdentityHashMap<>())", scan)
        self.assertIn("synchronize(accountPaint, new IdentityHashMap<>())", scan)
        self.assertNotIn("setOnClickListener(", design)

    def test_settings_hero_uses_a_compact_solid_surface_and_keeps_logs(self):
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

    def test_feed_profile_and_inbox_styling_are_forced_native(self):
        source = (JAVA / "Md3.java").read_text(encoding="utf-8")
        self.assertIn("if (i != ICONS) value = false;", source)
        self.assertIn("if (which != ICONS)", source)
        self.assertIn('getBoolean("feed_follow_shape", true)', source)
        self.assertIn("static boolean followShapeEnabled()", source)

    def test_texture_failures_are_retryable_and_only_success_is_cached(self):
        source = (JAVA / "Md3.java").read_text(encoding="utf-8")
        self.assertIn("TEXTURE_RETRY_AT[which] = TEXTURE_TRIED[which] ? 0L : SystemClock.uptimeMillis() + 5000L;", source)
        self.assertIn("TEXTURE_BITMAPS[which] = null;", source)
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn('saved ? "Текстура сохранена" : "Не удалось загрузить изображение.', settings)


if __name__ == "__main__":
    unittest.main()
