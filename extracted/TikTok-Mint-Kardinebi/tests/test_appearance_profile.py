"""Safety tests for the experimental appearance-only port profile."""
import os
import tempfile
import unittest
from unittest import mock

from kardinebi import dexpatch


class AppearanceProfileTests(unittest.TestCase):
    def test_appearance_profile_uses_only_stable_or_guarded_hooks(self):
        with tempfile.TemporaryDirectory() as root, \
             mock.patch.object(dexpatch, "rewrite_accent", return_value={"accent": 2}) as accent, \
             mock.patch.object(dexpatch, "draws_the_loader", return_value=False), \
             mock.patch.object(dexpatch, "holds_a_card", return_value=False), \
             mock.patch.object(dexpatch, "rewrite_loading", return_value={"loading": 1}) as loading, \
             mock.patch.object(dexpatch, "rewrite_cards", return_value={"cards": 1}) as cards, \
             mock.patch.object(dexpatch, "rewrite_smali", return_value={"getSimCountryIso()Ljava/lang/String;": 1}) as region, \
             mock.patch.object(dexpatch, "rewrite_safe_appearance_calls", return_value={"TextView->setTypeface(Landroid/graphics/Typeface;)V": 1}) as framework, \
             mock.patch.object(dexpatch, "rewrite_feed_dates", return_value={}) as dates, \
             mock.patch.object(dexpatch, "rewrite_settings_row", return_value={}) as settings, \
             mock.patch.object(dexpatch, "rewrite_speed_touch", side_effect=AssertionError("version-specific speed hook ran")):
            counts = dexpatch.rewrite_everything(root, {}, b"test", True)
        self.assertEqual(counts, {"getSimCountryIso()Ljava/lang/String;": 1,
                                  "TextView->setTypeface(Landroid/graphics/Typeface;)V": 1, "accent": 2})
        region.assert_called_once_with(root)
        framework.assert_called_once_with(root)
        dates.assert_called_once_with(root)
        settings.assert_called_once_with(root, b"test")
        accent.assert_called_once_with(root)
        loading.assert_not_called()
        cards.assert_not_called()

    def test_appearance_profile_keeps_only_structural_loading_hook_when_present(self):
        with tempfile.TemporaryDirectory() as root, \
             mock.patch.object(dexpatch, "rewrite_accent", return_value={"accent": 2}), \
             mock.patch.object(dexpatch, "draws_the_loader", return_value=True), \
             mock.patch.object(dexpatch, "holds_a_card", return_value=True), \
             mock.patch.object(dexpatch, "rewrite_loading", return_value={"loading": 1}) as loading, \
             mock.patch.object(dexpatch, "rewrite_cards", return_value={"cards": 1}) as cards, \
             mock.patch.object(dexpatch, "rewrite_feed_dates", return_value={}):
            counts = dexpatch.rewrite_everything(root, {}, b"test", True)
        self.assertEqual(counts, {"accent": 2, "loading": 1, "cards": 1})
        loading.assert_called_once_with(root)
        cards.assert_called_once_with(root)


    def test_safe_framework_hooks_leave_framerate_native_and_rewrite_typeface(self):
        source = """.class public Ltest/Calls;
.super Ljava/lang/Object;
.method public call(Landroid/view/Surface;Landroid/widget/TextView;Landroid/graphics/Typeface;)V
    .registers 4
    invoke-virtual {p1, v0, v0}, Landroid/view/Surface;->setFrameRate(FI)V
    invoke-virtual {p2, p3}, Landroid/widget/TextView;->setTypeface(Landroid/graphics/Typeface;)V
    return-void
.end method
"""
        with tempfile.TemporaryDirectory() as root:
            path = os.path.join(root, "Calls.smali")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(source)
            counts = dexpatch.rewrite_safe_appearance_calls(root)
            with open(path, encoding="utf-8") as handle:
                changed = handle.read()
        self.assertIsNone(counts.get("Surface->setFrameRate(FI)V"))
        self.assertEqual(counts.get("TextView->setTypeface(Landroid/graphics/Typeface;)V"), 1)
        self.assertIn("Landroid/view/Surface;->setFrameRate(FI)V", changed)
        self.assertNotIn("Rate;->setFrameRate", changed)
        self.assertIn("Fonts;->setTypeface(Landroid/widget/TextView;Landroid/graphics/Typeface;)V", changed)

    def test_feed_lottie_hook_discovers_the_obfuscated_owner_by_asset_marker(self):
        source = '''.class public Ltest/FeedLikeAnimation;\n.super Ljava/lang/Object;\n\n.method public load(LX/1GYl;Ljava/lang/String;)V\n    .registers 3\n    const-string v0, "tt_feed_like_tap_tux_lottie.json"\n    invoke-virtual {p1, p2}, LX/1GYl;->setAnimation(Ljava/lang/String;)V\n    return-void\n.end method\n'''
        with tempfile.TemporaryDirectory() as root:
            path = os.path.join(root, "FeedLikeAnimation.smali")
            with open(path, "w", encoding="utf-8") as f:
                f.write(source)
            counts = dexpatch.rewrite_accent(root)
            with open(path, encoding="utf-8") as f:
                rewritten = f.read()
        self.assertEqual(counts.get("feed Lottie setAnimation"), 1)
        self.assertIn("Lcat/kardinebi/Accent;->setAnimation(Ljava/lang/Object;Ljava/lang/String;)V", rewritten)
        self.assertNotIn("LX/1GYl;->setAnimation(Ljava/lang/String;)V", rewritten)

    def test_feed_lottie_hook_does_not_rewrite_unrelated_animation_classes(self):
        source = '''.class public Ltest/OtherAnimation;\n.super Ljava/lang/Object;\n\n.method public load(LX/1GYl;Ljava/lang/String;)V\n    .registers 3\n    const-string v0, "checkout_success_lottie.json"\n    invoke-virtual {p1, p2}, LX/1GYl;->setAnimation(Ljava/lang/String;)V\n    return-void\n.end method\n'''
        with tempfile.TemporaryDirectory() as root:
            path = os.path.join(root, "OtherAnimation.smali")
            with open(path, "w", encoding="utf-8") as f:
                f.write(source)
            counts = dexpatch.rewrite_accent(root)
            with open(path, encoding="utf-8") as f:
                rewritten = f.read()
        self.assertNotIn("feed Lottie setAnimation", counts)
        self.assertEqual(source, rewritten)


if __name__ == "__main__":
    unittest.main()
