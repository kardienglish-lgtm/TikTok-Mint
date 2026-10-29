import base64
import re
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "assets" / "splash_mint_banner.png"
JAVA_DIR = ROOT / "inject" / "java" / "cat" / "kardinebi"


def png_dimensions(data: bytes) -> tuple[int, int]:
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    assert data[12:16] == b"IHDR"
    return struct.unpack(">II", data[16:24])


def test_mint_splash_asset_matches_native_resource_dimensions():
    data = ASSET.read_bytes()
    assert png_dimensions(data) == (476, 138)
    assert len(data) > 1000


def test_embedded_overlay_banner_is_exactly_the_asset():
    source = (JAVA_DIR / "SplashBanner.java").read_text(encoding="utf-8")
    encoded_part = source.split("static final String PNG =", 1)[1]
    chunks = re.findall(r'"([A-Za-z0-9+/=]+)"', encoded_part)
    assert chunks
    assert base64.b64decode("".join(chunks)) == ASSET.read_bytes()


def test_builder_guards_native_splash_resource_before_replacing_it():
    source = (ROOT / "kardinebi" / "build.py").read_text(encoding="utf-8")
    assert "def write_splash_banner(self, apk: Apk, arsc: Arsc)" in source
    assert "resource_id = 0x7F0413B8" in source
    assert 'target_path = "res/c/a92.png"' in source
    assert 'layout_path = "res/a/bi1.xml"' in source
    assert "references_banner" in source
    assert 'apk.replace(target_path, png)' in source
    call = source.index("self.write_splash_banner(apk, arsc)")
    first_compile = source.index('self.say("Building the mod\'s own dex")')
    assert call < first_compile, "banner class must be generated before Java compilation"


def test_overlay_only_covers_tiktok_main_activity_and_uses_same_banner():
    source = (JAVA_DIR / "SplashOverlay.java").read_text(encoding="utf-8")
    assert '"com.ss.android.ugc.aweme.main.MainActivity"' in source
    assert "Base64.decode(SplashBanner.PNG, Base64.DEFAULT)" in source
    assert 'Diary.note("launch picture shown: TikTok on black")' in source
    assert "postDelayed" in source
