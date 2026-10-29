"""Guarded TikTok native profile layout hook coverage."""
import pathlib
import tempfile
import unittest

from kardinebi import dexpatch

class ProfileLayoutHookTest(unittest.TestCase):
    def test_hook_is_idempotent_and_only_added_to_the_known_base_component(self):
        descriptor = dexpatch.PROFILE_CONTAINER
        with tempfile.TemporaryDirectory() as directory:
            target = pathlib.Path(directory).joinpath(*descriptor[1:-1].split("/" )).with_suffix(".smali")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(
                ".class public " + descriptor + "\n"
                ".super Ljava/lang/Object;\n\n"
                ".method public onViewCreated(Landroid/view/View;)V\n"
                "    .registers 2\n"
                "    return-void\n"
                ".end method\n", encoding="utf-8")
            first = dexpatch.rewrite_profile_layout(directory)
            contents = target.read_text(encoding="utf-8")
            self.assertEqual(1, first.get("native profile layout"))
            self.assertIn("ProfileLayout;->bind(Ljava/lang/Object;Landroid/view/View;)V", contents)
            self.assertEqual(1, contents.count("ProfileLayout;->bind"))
            second = dexpatch.rewrite_profile_layout(directory)
            self.assertEqual(1, second.get("native profile layout"))
            self.assertEqual(1, target.read_text(encoding="utf-8").count("ProfileLayout;->bind"))

    def test_known_profile_video_tabs_are_marked_for_date_badges(self):
        owner = dexpatch.PROFILE_GRID_ROOTS[0]
        with tempfile.TemporaryDirectory() as directory:
            target = pathlib.Path(directory).joinpath(*owner[1:-1].split("/" )).with_suffix(".smali")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(
                ".class public " + owner + "\n"
                ".super Ljava/lang/Object;\n\n"
                ".method public onViewCreated(Landroid/view/View;)V\n"
                "    .registers 2\n"
                "    return-void\n"
                ".end method\n", encoding="utf-8")
            first = dexpatch.rewrite_profile_grid_roots(directory)
            self.assertEqual(first.get("mint profile grid root"), 1)
            contents = target.read_text(encoding="utf-8")
            self.assertIn("ProfileLayout;->bind(Ljava/lang/Object;Landroid/view/View;)V", contents)
            self.assertEqual(1, contents.count("ProfileLayout;->bind"))
            second = dexpatch.rewrite_profile_grid_roots(directory)
            self.assertEqual(second.get("mint profile grid root"), 1)
            self.assertEqual(1, target.read_text(encoding="utf-8").count("ProfileLayout;->bind"))

if __name__ == "__main__":
    unittest.main()
