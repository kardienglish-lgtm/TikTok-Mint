import tempfile
import unittest
from pathlib import Path

from kardinebi import dexpatch


class NotificationHookMintProfileTests(unittest.TestCase):
    def test_public_notification_manager_overloads_are_routed_to_mint_filter(self):
        cases = {
            "A.smali": """.class public LA;
.super Ljava/lang/Object;
.method public post(Landroid/app/NotificationManager;ILandroid/app/Notification;)V
    .registers 4
    invoke-virtual {p1, p2, p3}, Landroid/app/NotificationManager;->notify(ILandroid/app/Notification;)V
    return-void
.end method
""",
            "B.smali": """.class public LB;
.super Ljava/lang/Object;
.method public post(Landroid/app/NotificationManager;Ljava/lang/String;ILandroid/app/Notification;)V
    .registers 5
    invoke-virtual/range {p1 .. p4}, Landroid/app/NotificationManager;->notify(Ljava/lang/String;ILandroid/app/Notification;)V
    return-void
.end method
""",
        }
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for name, content in cases.items():
                (root / name).write_text(content, encoding="utf-8")
            counts = dexpatch.rewrite_notification_calls(str(root))
            one = (root / "A.smali").read_text(encoding="utf-8")
            two = (root / "B.smali").read_text(encoding="utf-8")
            self.assertEqual(counts.get("NotificationManager->notify"), 2)
            self.assertIn("invoke-static {p1, p2, p3}, Lcat/kardinebi/ModNotifications;->notify(Landroid/app/NotificationManager;ILandroid/app/Notification;)V", one)
            self.assertIn("invoke-static/range {p1 .. p4}, Lcat/kardinebi/ModNotifications;->notify(Landroid/app/NotificationManager;Ljava/lang/String;ILandroid/app/Notification;)V", two)

    def test_notification_only_dex_is_selected_for_safe_rewriting(self):
        dex = b"Landroid/app/NotificationManager; notify"
        self.assertTrue(dexpatch.interesting_appearance(dex, {}))

    def test_compatibility_path_applies_notification_filter_without_full_profile(self):
        source = (Path(__file__).parents[1] / "kardinebi" / "dexpatch.py").read_text(encoding="utf-8")
        section = source[source.index("def rewrite_everything("):]
        start = section.index("if appearance_only:")
        end = section.index("\n    counts = rewrite_photo_swipe(root, dex)", start + 1)
        appearance = section[start:end]
        self.assertIn("rewrite_photo_swipe(root, dex)", appearance)
        self.assertIn("rewrite_smali(root)", appearance)
        self.assertIn("rewrite_notification_calls(root)", appearance)


if __name__ == "__main__":
    unittest.main()
