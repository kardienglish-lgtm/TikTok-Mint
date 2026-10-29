"""Regression tests for the narrow photo-carousel preloading hook."""
from pathlib import Path
import tempfile
import unittest

from kardinebi.dexpatch import rewrite_photo_swipe

TARGET = "Lcom/ss/android/ugc/aweme/ui/feed/photos/assem/PhotoSlideViewPagerComponent;"
HOOK = "PhotoSwipeOptimizer;->bind(Ljava/lang/Object;Landroid/view/View;)V"


class PhotoSwipePatcherTest(unittest.TestCase):
    def test_hooks_the_exact_photo_viewpager_lifecycle_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "PhotoSlideViewPagerComponent.smali"
            path.write_text(f""".class public {TARGET}
.super Ljava/lang/Object;
.method public onViewCreated(Landroid/view/View;)V
    .registers 2
    invoke-super {{p0, p1}}, Ljava/lang/Object;->toString()Ljava/lang/String;
    return-void
.end method
""", encoding="utf-8")
            result = rewrite_photo_swipe(temp, b"PhotoSlideViewPagerComponent")
            patched = path.read_text(encoding="utf-8")
            self.assertEqual(1, result["photo swipe adjacent-page preloader"])
            self.assertEqual(1, patched.count(HOOK))
            self.assertLess(patched.index(HOOK), patched.index("return-void"))
            self.assertEqual({}, rewrite_photo_swipe(temp, b"PhotoSlideViewPagerComponent"))
            self.assertEqual(1, path.read_text(encoding="utf-8").count(HOOK))

    def test_skips_unrelated_dex_and_does_not_patch_other_classes(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "SomeOtherClass.smali"
            path.write_text(""".class public Lx/y/SomeOtherClass;
.super Ljava/lang/Object;
.method public onViewCreated(Landroid/view/View;)V
    .registers 2
    return-void
.end method
""", encoding="utf-8")
            self.assertEqual({}, rewrite_photo_swipe(temp, b"not photo dex"))
            self.assertNotIn(HOOK, path.read_text(encoding="utf-8"))

    def test_runtime_helper_guards_offscreen_pages_by_memory(self):
        root = Path(__file__).resolve().parents[1]
        source = (root / "inject/java/cat/kardinebi/PhotoSwipeOptimizer.java").read_text(encoding="utf-8")
        self.assertIn("MAX_VIEW_NODES = 256", source)
        self.assertIn("manager.isLowRamDevice()", source)
        self.assertIn("manager.getMemoryClass() < 192", source)
        self.assertIn("info.availMem >= MIN_AVAILABLE_BYTES", source)
        self.assertIn("setOffscreenPageLimit", source)
        self.assertNotIn("BitmapFactory.decode", source)
        self.assertNotIn("new Bitmap", source)


if __name__ == "__main__":
    unittest.main()
