"""Regression coverage for verified avatar-mask hooks in TikTok 47.4.42."""
import pathlib
import tempfile
import unittest

from kardinebi import dexpatch


class AvatarShapeHookRuntimeTests(unittest.TestCase):
    def test_verified_tux_avatar_views_and_feed_dynamic_avatar_are_patched(self):
        owners = {
            "LX/0GyZ;": "LX/0Gye;",  # TuxAvatarView variant with image clipping
            "LX/0Gya;": "LX/0Gye;",  # TuxAvatarView variant with image clipping
            "LX/0Gx4;": "Lcom/bytedance/lighten/loader/SmartImageView;",  # feed dynamic avatar
        }
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for owner, superclass in owners.items():
                file = root / (owner.replace(";", "").replace("/", "_") + ".smali")
                file.write_text(
                    f".class public {owner}\n"
                    f".super {superclass}\n"
                    ".method public shape(Landroid/graphics/Path;)V\n"
                    "    .registers 6\n"
                    "    invoke-virtual {p1, v0, v1, v2, v3}, "
                    "Landroid/graphics/Path;->addCircle(FFFLandroid/graphics/Path$Direction;)V\n"
                    "    return-void\n"
                    ".end method\n", encoding="utf-8")
            counts = dexpatch.rewrite_anchored(str(root))
            rewritten = [p.read_text(encoding="utf-8") for p in root.glob("*.smali")]
            joined = "\n".join(rewritten)
            self.assertEqual(3, sum(text.count("AvatarShape;->addCircle") for text in rewritten))
            self.assertEqual(0, sum(text.count("Path;->addCircle(FFFLandroid/graphics/Path$Direction;)V") for text in rewritten))
            self.assertNotIn("adaptive avatar shape drawable", counts)
            # Re-running the patcher must not rewrite the same invocation again.
            self.assertFalse(dexpatch.rewrite_anchored(str(root)))

    def test_current_tiktok_avatar_owners_are_explicitly_hooked(self):
        self.assertTrue({"LX/0GyZ;", "LX/0Gya;", "LX/0Gx4;"}.issubset(set(dexpatch.ROUND_DRAWABLES)))

    def test_unrelated_bitmap_drawable_is_not_reshaped_by_heuristic(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            target = root / "LX" / "NotificationCircle.smali"
            target.parent.mkdir(parents=True)
            original = (
                ".class public LX/NotificationCircle;\n"
                ".super Landroid/graphics/drawable/Drawable;\n"
                ".field private shader:Landroid/graphics/BitmapShader;\n"
                ".method public draw(Landroid/graphics/Canvas;)V\n"
                "    .registers 6\n"
                "    invoke-virtual {v1, v2, v3, v4, v5}, "
                "Landroid/graphics/Path;->addCircle(FFFLandroid/graphics/Path$Direction;)V\n"
                "    return-void\n"
                ".end method\n")
            target.write_text(original, encoding="utf-8")
            dexpatch.rewrite_anchored(str(root))
            self.assertEqual(original, target.read_text(encoding="utf-8"))

    def test_avatar_lifecycle_hook_survives_changed_access_flags(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            target = root / "AvatarAssembler.smali"
            target.write_text(
                ".class public " + dexpatch.AVATAR_CLASS + "\n"
                ".super Ljava/lang/Object;\n"
                ".method protected onViewCreated(Landroid/view/View;)V\n"
                "    .registers 2\n"
                "    return-void\n"
                ".end method\n", encoding="utf-8")
            counts = dexpatch.rewrite_anchored(str(root))
            contents = target.read_text(encoding="utf-8")
            self.assertEqual(1, counts.get("native repost avatar"))
            self.assertIn("FeedFollowShape;->bind(Ljava/lang/Object;Landroid/view/View;)V", contents)
            self.assertEqual(1, contents.count("FeedFollowShape;->bind"))
            dexpatch.rewrite_anchored(str(root))
            self.assertEqual(1, target.read_text(encoding="utf-8").count("FeedFollowShape;->bind"))


if __name__ == "__main__":
    unittest.main()
