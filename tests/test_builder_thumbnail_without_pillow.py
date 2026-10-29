import base64
from pathlib import Path

from kardinebi import png
from kardinebi.build import _thumbnail


def test_builder_thumbnail_uses_bundled_png_codec(tmp_path, monkeypatch):
    # A 400x200 opaque PNG should be reduced to 192x96 without distorting it.
    source = tmp_path / "wide.png"
    pixels = bytearray((34, 55, 89, 255) * (400 * 200))
    source.write_bytes(png.encode(png.Image(400, 200, pixels)))

    import builtins
    real_import = builtins.__import__

    def reject_pillow(name, *args, **kwargs):
        if name == "PIL" or name.startswith("PIL."):
            raise AssertionError("the builder must not require Pillow")
        return real_import(name, *args, **kwargs)

    monkeypatch.setattr(builtins, "__import__", reject_pillow)
    encoded = _thumbnail(str(source))
    result = png.decode(base64.b64decode(encoded))
    assert (result.width, result.height) == (192, 96)
