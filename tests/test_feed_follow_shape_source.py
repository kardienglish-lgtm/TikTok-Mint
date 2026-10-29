"""Regression checks that recommendation cards retain TikTok native styling."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"


class FeedFollowShapeSourceTests(unittest.TestCase):
    def test_native_avatar_hook_only_targets_feed_avatar_component(self):
        patch = (ROOT / "kardinebi/dexpatch.py").read_text(encoding="utf-8")
        follow = (JAVA / "FeedFollowShape.java").read_text(encoding="utf-8")
        self.assertIn("FeedFollowShape;->bind(Ljava/lang/Object;)V", patch)
        self.assertIn('type.endsWith("FeedAvatarDefaultAssem")', follow)
        self.assertIn("return false;", follow)  # let TikTok's click continue
        self.assertIn("public static void bind(Object component)", follow)
        self.assertIn("public static void bind(Object component, View createdView)", follow)
        self.assertIn("FeedFollowShape;->bind(Ljava/lang/Object;Landroid/view/View;)V", patch)
        self.assertIn('viewField(component, "LLJLL")', follow)
        self.assertIn('viewField(component, "LLJLILLLLZIIL")', follow)
        self.assertIn('field.get(instance)', follow)
        self.assertIn('resolveRoot(component, nativeIcon != null ? nativeIcon : nativeTarget, createdView)', follow)
        self.assertIn('isNativeHitTarget(nativeTarget, icon, root)', follow)
        self.assertIn('findNativeClickTarget(root)', follow)
        self.assertIn('findNativeClickTarget(root)', follow)
        self.assertIn('iconview', follow)
        self.assertIn('native LLJLL icon had no clickable target', follow)
        self.assertNotIn('content instanceof ViewGroup) root', follow)
        target = follow[follow.index('private static boolean isNativeHitTarget('):follow.index('private static View clickTarget(')]
        self.assertIn('!isInside(root, target)', target)
        self.assertIn('target.getVisibility() != View.VISIBLE', target)
        self.assertNotIn('!target.isEnabled()', target)  # native view may draw while disabled

    def test_recommendation_feed_material_shape_is_disabled(self):
        md3 = (JAVA / "Md3.java").read_text(encoding="utf-8")
        self.assertIn("static boolean followShapeEnabled() {\n        return false;\n    }", md3)

    def test_settings_header_has_a_top_right_local_logs_action(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn('logs.setContentDescription("Открыть журнал событий TikTok Mint")', settings)
        self.assertIn("logs.setOnClickListener(v -> showQuickLogs())", settings)
        self.assertIn("private void showQuickLogs()", settings)
        self.assertIn("Diary.lines()", settings)
        self.assertIn("Diagnostics.fullReport()", settings)
        start = settings.index("private void showQuickLogs()")
        end = settings.index("private int groupOf(String section)", start)
        logs = settings[start:end]
        self.assertIn('"Событий: " + entries.size()', logs)
        self.assertIn('entries.size() - 8', logs)
        self.assertNotIn("friendlyLogSummary(entries)", logs)
        self.assertNotIn("Что это значит", logs)
        self.assertNotIn("Diagnostics.report()", logs)

    def test_avatar_material_switch_persists_last_expressive_shape(self):
        shape = (JAVA / "AvatarShape.java").read_text(encoding="utf-8")
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn('"squircle"', shape)
        self.assertIn('"diamond"', shape)
        self.assertIn('"flower"', shape)
        self.assertIn("setMaterialEnabled(Context context, boolean enabled)", shape)
        self.assertIn('"Форма аватара Material 3"', settings)
        self.assertIn("AvatarShape.setMaterialEnabled(this, on)", settings)


if __name__ == "__main__":
    unittest.main()
