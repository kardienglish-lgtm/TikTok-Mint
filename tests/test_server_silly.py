"""The calculator's door on the server, against a database of its own."""

import os
import pathlib
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

# the server runs on a Python that still has `cgi`; nothing tested here reads a form
try:
    import cgi  # noqa: F401
except ImportError:
    import types
    sys.modules["cgi"] = types.ModuleType("cgi")

import badges
import dep


class SillyServerTests(unittest.TestCase):
    def setUp(self):
        self.room = tempfile.TemporaryDirectory()
        self.was = (badges.DB, badges.WHO)
        badges.DB = os.path.join(self.room.name, "badges.db")
        badges.WHO = os.path.join(self.room.name, "nobody.json")
        badges.prepare()
        db = badges.connect()
        for uid, proved in (("1111111", 1), ("2222222", 1), ("3333333", 0)):
            db.execute("INSERT INTO owner (uid, token, claimed, proved) VALUES (?,?,?,?)",
                       (uid, "key-" + uid, 1, proved))
        db.commit()
        db.close()

    def tearDown(self):
        badges.DB, badges.WHO = self.was
        self.room.cleanup()

    def test_legacy_easter_egg_is_retired_and_public_list_has_one_badge(self):
        body = {"uid": "1111111", "token": "key-1111111"}
        self.assertEqual(badges.easter_egg(body, "ip-one")[0], 410)
        public = badges.public()
        self.assertEqual([item["id"] for item in public["badges"]], ["oh_its_me"])
        self.assertEqual(public["badges"][0]["users"],
                         ["7321751563004052486", "7413409720789369874"])

    def test_the_calculator_is_for_proved_accounts_but_its_prize_is_not_a_profile_badge(self):
        self.assertEqual(badges.dep_play({"do": "state"}, "ip"), (403, {"error": "prove"}))
        self.assertEqual(badges.dep_play({"do": "state", "uid": "3333333", "token": "key-3333333"}, "ip")[0], 403)
        code, said = badges.dep_play({"do": "state", "uid": "1111111", "token": "key-1111111"}, "ip")
        self.assertEqual((code, said["balance"], said["price"]), (200, dep.START, dep.PRICE))
        db = badges.connect()
        self.assertIsNone(db.execute("SELECT title_ru FROM badge WHERE id = ?", (dep.BADGE,)).fetchone())
        db.close()
        self.assertNotIn(dep.BADGE, [item["id"] for item in badges.public()["badges"]])


if __name__ == "__main__":
    unittest.main()
