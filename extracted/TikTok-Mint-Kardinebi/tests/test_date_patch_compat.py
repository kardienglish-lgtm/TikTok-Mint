"""Guard the publication-date hook against changed access flags and method layout."""
from pathlib import Path
import tempfile
import unittest
from kardinebi.dexpatch import rewrite_feed_dates

AUTHOR = "Lcom/ss/android/ugc/aweme/feed/assem/videoauthorinfo/VideoAuthorInfoRelationAssem;"
DESC = "Lcom/ss/android/ugc/aweme/feed/assem/desc/VideoDescAssem;"
AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"


AUTHOR_STUB = f""".class public {AUTHOR}
.super Ljava/lang/Object;
.method public onViewCreated(Landroid/view/View;)V
    .registers 2
    return-void
.end method
"""

DESC_STUB = f""".class public {DESC}
.super Ljava/lang/Object;
.method private a2(IL{AWEME[1:]})V
    .registers 5
    invoke-virtual {{v2}}, {AWEME}->getAid()Ljava/lang/String;
    if-eqz v2, :cond_0
    return-void
    :cond_0
    return-void
.end method
"""

class DatePatchCompatibilityTest(unittest.TestCase):
    def test_hooks_author_lifecycle_and_selected_post_in_47_4_42_style_smali(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            author = root / "VideoAuthorInfoRelationAssem.smali"
            author.write_text(f""".class public {AUTHOR}
.super Ljava/lang/Object;
.method public onViewCreated(Landroid/view/View;)V
    .registers 2
    if-eqz p1, :skip
    return-void
    :skip
    return-void
.end method
""", encoding="utf-8")
            desc = root / "VideoDescAssem.smali"
            desc.write_text(f""".class public {DESC}
.super Ljava/lang/Object;
.method public a2(IL{AWEME[1:]} )V
    .registers 5
    const-string v0, \"onHolderSelected: awemeId=\"
    invoke-virtual {{v2}}, {AWEME}->getAid()Ljava/lang/String;
    if-eqz v2, :cond_0
    return-void
    :cond_0
    return-void
.end method
""".replace(f"a2(IL{AWEME[1:]} )V", f"a2(IL{AWEME[1:]})V"), encoding="utf-8")

            counts = rewrite_feed_dates(temp)
            patched_author = author.read_text(encoding="utf-8")
            patched_desc = desc.read_text(encoding="utf-8")
            self.assertEqual(2, patched_author.count("Dates;->bindAuthor(Ljava/lang/Object;)V"))
            self.assertEqual(1, patched_desc.count("Dates;->bindPost(Ljava/lang/Object;Ljava/lang/Object;)V"))
            self.assertIn("visible publication date: author", counts)
            self.assertIn("visible publication date", counts)

    def test_hooks_selected_post_when_diagnostic_log_string_is_absent(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            desc = root / "VideoDescAssem.smali"
            (root / "VideoAuthorInfoRelationAssem.smali").write_text(AUTHOR_STUB, encoding="utf-8")
            desc.write_text(f""".class public {DESC}
.super Ljava/lang/Object;
.method private a2(IL{AWEME[1:]})V
    .registers 5
    invoke-virtual {{v2}}, {AWEME}->getAid()Ljava/lang/String;
    if-eqz v2, :cond_0
    return-void
    :cond_0
    return-void
.end method
""", encoding="utf-8")
            counts = rewrite_feed_dates(temp)
            patched = desc.read_text(encoding="utf-8")
            self.assertEqual(1, patched.count("Dates;->bindPost(Ljava/lang/Object;Ljava/lang/Object;)V"))
            self.assertIn("visible publication date", counts)


    def test_hooks_author_model_rebind_when_recycled_cell_receives_aweme(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            author = root / "VideoAuthorInfoRelationAssem.smali"
            (root / "VideoDescAssem.smali").write_text(DESC_STUB, encoding="utf-8")
            author.write_text(f""".class public {AUTHOR}
.super Ljava/lang/Object;
.method public pq(Lcom/ss/android/ugc/aweme/feed/model/Aweme;)V
    .registers 2
    return-void
.end method
""", encoding="utf-8")

            counts = rewrite_feed_dates(temp)
            patched = author.read_text(encoding="utf-8")
            self.assertEqual(1, patched.count("Dates;->bindAuthor(Ljava/lang/Object;Ljava/lang/Object;)V"))
            self.assertIn("visible publication date: author model", counts)

    def test_build_fails_when_post_date_method_is_renamed(self):
        """A TikTok build without the verified a2 method must not ship dateless."""
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "VideoAuthorInfoRelationAssem.smali").write_text(AUTHOR_STUB, encoding="utf-8")
            (root / "VideoDescAssem.smali").write_text(DESC_STUB.replace("a2(", "a9("), encoding="utf-8")
            with self.assertRaises(RuntimeError) as ctx:
                rewrite_feed_dates(temp)
            self.assertIn("post date", str(ctx.exception))

    def test_build_fails_when_owner_classes_are_absent(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(RuntimeError):
                rewrite_feed_dates(temp)

    def test_rerun_over_patched_tree_is_accepted(self):
        """Idempotence: a second pass must not fail on hooks it already placed."""
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "VideoAuthorInfoRelationAssem.smali").write_text(AUTHOR_STUB, encoding="utf-8")
            (root / "VideoDescAssem.smali").write_text(DESC_STUB, encoding="utf-8")
            rewrite_feed_dates(temp)
            counts = rewrite_feed_dates(temp)
            self.assertIn("date hooks placed", counts)

if __name__ == "__main__":
    unittest.main()
