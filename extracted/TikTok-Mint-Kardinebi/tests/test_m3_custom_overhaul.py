"""Regression checks for the independent Material 3 redesign in Custom 1.1.3."""
from hashlib import sha256
from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"


class Material3CustomOverhaulTest(unittest.TestCase):
    def test_settings_palette_is_independent_of_tiktok_native_skin(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        skin = (JAVA / "Skin.java").read_text(encoding="utf-8")
        self.assertIn("skin = Skin.material3(this)", settings)
        self.assertIn("skin=Skin.material3(this)", settings)
        self.assertNotIn("Skin.remembered(this)", settings)
        palette = skin[skin.index("static Skin material3(Context context)"):
                       skin.index("static Skin remembered(Context context)")]
        self.assertIn("0xFF111610", palette)
        self.assertIn("0xFF1B231C", palette)
        self.assertIn("0xFFF7F9F4", palette)
        self.assertNotIn("measure(", palette)

    def test_switch_rebuild_restores_previous_scroll_position(self):
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        start = settings.index("private void rebuild()")
        end = settings.index("private void openDep()", start)
        rebuild = settings[start:end]
        self.assertIn("final int previousScroll = switchRebuildScrollY >= 0", rebuild)
        self.assertIn("restoreScroll(previousScroll)", rebuild)
        self.assertNotIn("restoreScroll(0)", rebuild)
        delayed = settings[settings.index("private void rebuildAfterSwitchAnimation()"):
                           settings.index("// ----------------------------------------------------------- the rows")]
        self.assertIn("switchRebuildScrollY = page.getScrollY()", delayed)
        restore = settings[settings.index("private void restoreScroll(final int scrollY)"):
                           settings.index("/**\n     * The bar that says a restart is due", settings.index("private void restoreScroll(final int scrollY)"))]
        self.assertIn("page.scrollTo(0, Math.min(requestedScrollY, max))", restore)

    def test_m3_native_surfaces_use_tonal_roles_and_restore_native_text(self):
        design = (JAVA / "MintScreenDesign.java").read_text(encoding="utf-8")
        for role in ("SEARCH_FIELD", "SHARE_SURFACE", "SHARE_ACTION", "COMMENT_SURFACE",
                     "COMMENT_INPUT", "COMMENT_ACTION", "ACCOUNT_HEADER", "ACCOUNT_STAT", "ACCOUNT_TAB"):
            self.assertIn(role, design)
        self.assertIn("surfaceHigh = mix(page, text, dark ? 0.135f : 0.095f)", design)
        self.assertIn("originalTextColor = hasTextColor ? ((TextView) view).getCurrentTextColor() : 0", design)
        self.assertIn("((TextView) view).setTextColor(originalTextColor)", design)
        self.assertNotIn("Themes.text()", design)
        self.assertNotIn("Themes.backgroundInUse()", design)
        self.assertNotIn("setOnClickListener(", design)

    def test_supplied_icon_assets_match_master_and_are_512px_pngs(self):
        assets = [ROOT / "icon_out/play/ic_launcher-512.png",
                  ROOT / "icon_out/play/ic_launcher-512-round.png",
                  ROOT / "icons/launcher/tiktok.png"]
        digests = []
        for asset in assets:
            raw = asset.read_bytes()
            self.assertEqual(b"\x89PNG\r\n\x1a\n", raw[:8])
            self.assertEqual((512, 512), struct.unpack(">II", raw[16:24]))
            digests.append(sha256(raw).hexdigest())
        self.assertEqual(1, len(set(digests)))
        settings = (JAVA / "SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn("Launcher.preview(Launcher.DEFAULT)", settings)


if __name__ == "__main__":
    unittest.main()
