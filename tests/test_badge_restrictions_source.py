import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class BadgeRestrictionSourceTest(unittest.TestCase):
    def test_registry_has_only_the_official_badge_and_two_accounts(self):
        registry = json.loads((ROOT / "badges.json").read_text(encoding="utf-8"))
        badges = registry["badges"]
        self.assertEqual(len(badges), 1)
        self.assertEqual(badges[0]["id"], "oh_its_me")
        self.assertEqual(badges[0]["title_ru"], "о, это я")
        self.assertEqual(
            set(badges[0]["users"]),
            {"7321751563004052486", "7413409720789369874"},
        )
        self.assertEqual(badges[0]["image"], "kardinebi-1024-glyph.png")
        self.assertTrue((ROOT / "server" / "icons" / badges[0]["image"]).is_file())

    def test_server_migrates_old_grants_and_blocks_recreation(self):
        server = (ROOT / "server" / "badges.py").read_text(encoding="utf-8")
        self.assertIn('OFFICIAL_BADGE = "oh_its_me"', server)
        self.assertIn('OFFICIAL_USERS = ("7321751563004052486", "7413409720789369874")', server)
        self.assertIn("DELETE FROM badge WHERE id != ?", server)
        self.assertIn("DELETE FROM held WHERE badge != ?", server)
        self.assertIn("DELETE FROM held WHERE uid NOT IN (?, ?)", server)
        self.assertIn('WHERE id = ?', server)
        self.assertIn("доступен только закреплённый бейдж", server)
        self.assertIn("бейдж закреплён за двумя официальными аккаунтами", server)
        self.assertIn("снять закреплённый бейдж нельзя", server)
        self.assertIn('"kardinebi-1024-glyph.png"', server)

    def test_client_removes_legacy_local_badges_and_cached_grants(self):
        client = (ROOT / "inject" / "java" / "cat" / "kardinebi" / "Badges.java").read_text(encoding="utf-8")
        self.assertIn('"oh_its_me", "kardinebi-1024-glyph.png"', client)
        self.assertIn('only.put("7321751563004052486"', client)
        self.assertIn('only.put("7413409720789369874"', client)
        self.assertNotIn("DEVELOPER_BADGE =", client)
        self.assertNotIn("KARDINEBI_EASTER_EGG_BADGE =", client)
        mine = (ROOT / "inject" / "java" / "cat" / "kardinebi" / "Mine.java").read_text(encoding="utf-8")
        self.assertIn('"7321751563004052486".equals(uid)', mine)
        self.assertIn('"7413409720789369874".equals(uid)', mine)
        self.assertIn('"oh_its_me".equals(one.optString("id", ""))', mine)


if __name__ == "__main__":
    unittest.main()
