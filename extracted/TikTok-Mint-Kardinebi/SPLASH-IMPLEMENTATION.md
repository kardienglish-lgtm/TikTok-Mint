# TikTok Mint launch screen — implementation and verification

The launch screen uses two matching paths for TikTok 47.4.42:

1. The builder replaces only `res/c/a92.png`, drawable ID `0x7f0413b8` (`splash_activity_banner`). Before replacing it, it verifies that `resources.arsc` maps that ID to the expected path and that `res/a/bi1.xml` references the same ID. This avoids replacing a shared in-app logo or silently patching a different resource layout.
2. `SplashOverlay` displays the exact same `splash_mint_banner.png` for up to 1.5 seconds on `com.ss.android.ugc.aweme.main.MainActivity`, then removes the overlay. The image used by the overlay is generated into `SplashBanner.java` by the builder from the PNG asset.

The image is the exact `res/c/a92.png` TikTok wordmark extracted from the supplied TikTok 47.4.42 APK. It is transparent around the original TikTok note and wordmark; the overlay fills the screen in black. The PNG is 476 × 138, matching the native resource, and the same bytes are embedded into the overlay class.

Checks completed in the editing environment:

- Verified the APK resource table and binary XML reference chain for the native splash image.
- Invoked the builder's splash patch method on the supplied APK in memory; it changed the expected ZIP entry to the new asset and generated `SplashBanner.java`.
- Compiled `SplashOverlay.java` plus `SplashBanner.java` with a small Android API mock harness; it verified activity filtering, image decoding, insertion and timer cleanup.
- Added regression tests for the asset, embedded image, guarded resource replacement and overlay behavior.

Not verified: a complete signed APK or a real-device startup. The full builder stops when it tries to download the Android platform/toolchain because this environment has no network/DNS access. A real device test is still needed to confirm the timing and appearance in TikTok itself.

The rebranding pass updated stale assertions that expected the former project name. The current static audit passes (135 Python files parsed, 12 critical paths checked), and the focused branding/splash/settings suite passes (141 tests and 10 subtests). A full APK build and real-device startup remain unverified because the Android toolchain download is unavailable in the editing environment; unrelated pre-existing failures remain in the account-appearance runtime and date-hook compatibility tests.
