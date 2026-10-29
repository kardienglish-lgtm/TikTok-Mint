# Material 3 settings and comments-background follow-up — 2026-10-25

This follow-up supersedes the older note that the settings still used the former gradient hero or quick-setting grid.

- Replaced the main settings dashboard's two-column grid and quick-toggle carousel with a single grouped, one-column category list.
- Rebuilt the header as a compact app bar, removed decorative gradients/pills and switched main surfaces to solid theme-derived tonal colours.
- Added vector/alias coverage for the icons referenced by settings, keeping icon names separate from the actual preference keys.
- Added slow, low-opacity rotating shape paths on the comments list's containing `RecyclerView`, not on recycled comment cells. The animation respects both the mod preference and system-wide reduced animations.
- Tightened the log indicator to avoid treating routine missing-hook messages as errors.
- Removed duplicated divider placeholders that had introduced blank visual gaps.

Focused test results are recorded in `MATERIAL3-REDESIGN-REPORT.md`. This is a source/kit change; no APK rebuild, install or device screenshot was available during this pass.

---

# Quality-fix pass — 1.1.3

This pass is a targeted rollback and source repair based on the reported in-app regressions. It does not claim successful APK/device validation; the original APK is not present in this working environment.

## Changes made

- Restored TikTok-native rendering for comment sheets/composer and profile/account screens. The custom scanner no longer paints those unstable hierarchies, avoiding the black oval and the late visual pop-in caused by GIF/media hierarchy rebuilds.
- Disabled broad Material 3 styling of feed tabs, profile tabs/statistics, and inbox/activity rows. These views now remain native; the separate custom-video-icon switch remains available.
- Decoupled the expressive follow affordance from the withdrawn feed-tab styling. Added a conservative structural fallback for builds where TikTok hides generated icon names.
- Removed the animated settings backdrop and replaced the settings hero gradient with a solid tonal Material 3 surface. The logs panel now shows only an event/error count and the last eight entries; detailed diagnostics are copied only when explicitly requested.
- Delayed setting-column rebuilds until after the switch animation can finish, reducing missing/intermittent checkmarks.
- Changed icon texture processing so an early reflection miss does not cache the image as handled forever. Failed texture loads retry after a short cooldown.
- Auto-renew will no longer silently stay enabled when sticker mode has no selected sticker; it shows a clear instruction instead. The user must select a sticker or explicitly choose text mode. Actual streak sending still depends on TikTok exposing the correct native service and Android background-alarm permissions.

## Verification boundary

The targeted source/runtime suite passed 37 tests (1.01 seconds). The full `python3 -m unittest discover -s tests -v` run exceeded the 300-second execution limit while running `test_profile_mint_runtime`, so the complete suite is not confirmed. Source regression tests can validate the deterministic parts of this patch. They cannot prove that TikTok 47.4.42 exposes the expected runtime view hierarchy or streak API. This environment has no target device/session, no attached original APK for rebuilding, and no on-device instrumentation. Do not describe the features as fully fixed until the built APK is installed and the listed flows are tested on-device.

## Deep optimization update — 2026-10-25

See `DEEP-OPTIMIZATION-REPORT.md`. The main settings home has a new layout and the source now contains a memory-aware photo carousel preloader. The targeted 12-test suite passed. The supplied APK has not been rebuilt in this environment and the black-frame symptom, real RAM/CPU delta and on-device rendering remain unverified.
