# Splash, builder dependency and offline retry fix

## Changes

- Replaced the previous Mint-only banner with the exact 476×138 `res/c/a92.png` TikTok wordmark from the supplied TikTok 47.4.42 APK. The existing overlay fills the screen in black and now records `launch picture shown: TikTok on black`.
- Kept the guarded resource check before modifying the native splash bitmap and regenerated the embedded Java PNG from the asset.
- Removed the builder's Pillow import from `_thumbnail`; the project uses its bundled PNG decoder/encoder and alpha-aware resize routine, so `python3 -m pip install Pillow` is no longer a build prerequisite.
- Added a 15-minute retry backoff after a failed remote badge-feed request. The latest error is still recorded once, but a permanently unresolved `kardinebi.narez.xyz` host is not retried every two minutes. Local cached badges remain available.

## Verification status

The source asset dimensions and embedded PNG are checked by `tests/test_splash_banner_source.py`. The Python source parses without syntax errors. A complete signed APK build and a physical-device boot test are not claimed here; those must be run in the user's Codespace and on the target Android device. The DNS error is an unavailable external endpoint; the retry backoff only limits repeated attempts and does not repair DNS or restore remote badges.
