## Local diagnostics and feature self-test — 2026-10-29

- The Diagnostics section now has a direct **Open event log** action and a **Test mod functions** action. The test report is copyable and records its summary plus warnings/errors in the local diary.
- The local test checks the startup evidence and build metadata, confirms all 177 Java components in `inject/java/cat/kardinebi` can be loaded, and reads the current state for appearance, fonts/emoji, feed filters, media/downloads, comments, notifications, region data, launcher icons and plugin catalogue.
- It does not change user preferences or send network requests. Anything requiring a real TikTok screen, a video download, account state or a backend is explicitly reported as skipped; successful class loading is not proof that every hook works on every screen.
- This is a source change. A new APK still needs to be built and checked on a device.

## Material 3 Expressive settings rebuild — 2026-10-25

This is the latest settings UI revision and supersedes older notes below that mention a card grid, quick-control carousel, large gradient hero or a moving settings-page backdrop.

- Replaced the two-column category grid and standalone quick-toggle tiles with one vertical settings index. Each category is a compact list row with a semantic Material Symbols Rounded icon, supporting label and chevron, inside one calm tonal surface.
- Rebuilt the header as a compact app bar with a small Mint mark, title, subtitle, back action and a direct log action. Decorative gradient surfaces, the oversized hero and metadata pills are gone.
- Shared settings surfaces now use solid theme-derived tonal colours, a restrained 22 dp container shape, low-contrast separators and consistent text hierarchy. Existing preference keys, callbacks, search and scroll restoration remain intact.
- Added missing icon vectors and documented the aliases so no setting row silently loses its icon. The setting icon is decorative; the switch/action remains the only state control.
- Hooked slow, low-opacity rotating Material-style geometry to the enclosing comments list background as well as profile pages. Shapes are not attached to recycled comment rows and stop when system animations are disabled.
- Tightened log-warning detection so benign “not found”/missing-hook messages do not automatically produce an error dot.
- Added a focused regression test for each icon key, single-column navigation, solid surfaces, preserved callbacks and the comments-background hook.

The design decisions follow [Material 3 Lists](https://m3.material.io/components/lists/overview), [Android Settings guidance](https://developer.android.com/design/ui/mobile/guides/patterns/settings) and [Material Symbols](https://fonts.google.com/icons). See `MATERIAL3-REDESIGN-REPORT.md` for exact changes and test results. **This is a source/kit update, not a claim that a new APK has been built or installed.** The updated design report records the reference screenshots/articles reviewed and the 38 focused source/runtime tests that passed.

## Deep optimization pass — 2026-10-25

- Added a memory-aware adjacent-photo-page preloader around the TikTok 47.4.42 `PhotoSlideViewPagerComponent` lifecycle to reduce the chance of black/empty frames during fast carousel swipes. It is guarded on low-memory devices and does not maintain an extra bitmap cache. **Real-device behavior and RAM/CPU deltas remain unverified** until the rebuilt APK is installed and measured.
- See `DEEP-OPTIMIZATION-REPORT.md` for historical optimizations and the build/QA workflow.

# TikTok Mint Custom 1.1.3

**QUALITY REPAIR PASS — 2026-10-24 (supersedes earlier visual-pass notes below).** This revision deliberately rolls back custom Material styling on comment sheets/composer, profile/account, feed recommendation tabs and inbox/activity rows; those surfaces are native TikTok UI now. Material 3 stays on the mod settings, search/share surfaces and separately scoped custom video-action/follow features. The settings page uses solid tonal surfaces, not the animated gradient backdrop. Logs show a short summary and recent entries. See `QUALITY-FIX-REPORT.md` for changes and verification limits.

## About and author information (2026-10-24)

- The duplicate "О разработчике Mint" block was removed from the upper part of the appearance-only settings screen. Developer identity and app/source details are consolidated under "О приложении".
- The redundant in-app "Спасибо"/contributor accordion was removed, including its obsolete contributor link. The app's version footer no longer repeats the maker text.
- Redundant upstream author/source references and source-project links were removed from the About and secondary links screens. The source/license/support notice remains directly visible in settings, preserving third-party attribution alongside the new TikTok Mint branding.

## Settings and scroll refinement (2026-10-24)

- Reworked switch rows as a consistent 72 dp-minimum Material 3 pattern: a feature-specific icon surface, concise label, explanatory two-line summary and one trailing animated switch. Functional preference keys and callbacks are unchanged.
- Rewrote the most repetitive/ambiguous settings labels, descriptions and accessibility names so the row explains the effect rather than repeating “enabled/disabled”. Long labels use shorter on-screen captions while retaining their original internal preference key.
- The later quality-repair pass disables that animated background entirely and restores native account/profile rendering; the earlier decorative treatment is no longer active.
- Replaced arbitrary interval scanning of the native view hierarchy with a quiet-period debounce; repeated global-layout callbacks coalesce into one pass after the interface settles. The scan has a smaller traversal cap.
- Rephrased the source/license area as a compact section that credits the source components and identifies @kardinebi as the maintainer. Third-party ownership and licence obligations remain unchanged.

## Additional redesign pass (2026-10-23)

- Rebuilt the mod settings home around a branded Mint hero, quick switches and fresh groups for region, appearance, icon customisation, feed/video, notifications, maintenance and app info; existing setting callbacks remain wired.
- Search and repost/share retain their Material 3 treatment. Comment composer and profile/header/tab styling were rolled back in the quality-repair pass because they caused black-oval and late-pop-in regressions; TikTok now owns those native surfaces.
- Removed the first-launch crash-report prompt and automatic tutorial entry. Crash reporting remains off by default.
- The uploaded 2048 px icon was compared with the existing 512 px master after Lanczos downscaling and matched pixel-for-pixel, so the correct existing artwork was retained.

The source/license notice remains visible in the main settings screen as a project design choice. See `VERIFICATION_REPORT.md` for checks and unverified limits. No APK was built because this kit does not include the original TikTok 47.4.42 input APK.


## Media, dates, drawing and auto-renew bugfixes (2026-10-24)

- The four custom action textures (like, comment, favourite, share) are copied into the app's private files, decoded with sampling, reduced to at most 512 px on the longest edge and saved as PNG with transparency. This avoids temporary document-permission failures and prevents full-resolution wallpapers from being kept in memory. Drawable-wrapper detection now searches nested wrappers and compatible superclass fields.
- Publication-date injection no longer relies on a native debug log string. It uses the exact known selected-post class/method layout and `Aweme.getAid` guard, and author rows try the native holder payload if regular getters are missing. This is broader, not a guarantee that every future obfuscated TikTok screen will expose the same data.
- Added a local Material 3 sketch pad: colour palette, three brush sizes, undo, clear and PNG export to `Pictures/TikTok Mint`.
- Favourite-sticker loading handles more flow wrappers and finishes when a valid snapshot is unchanged or empty. When TikTok's private favourites repository cannot be found, stickers already observed in chats remain available as a fallback. A refresh cooldown cannot recursively rebuild the settings page.
- Enabling auto-renew prepares native service discovery, requests Android 13+ notification permission, starts loading stickers and reports missing exact-alarm permission, notifications, native service, observed conversations or selected sticker through a diagnostic row.
- The old Nostalgia date filter, switch and text strings are removed from runtime source. Offline comment and download-count dialogs now use the mod's Material 3 panel instead of square `AlertDialog` windows.

The tests below are source/runtime harnesses with doubles; the exact obfuscated classes and UI still need a build and device test against TikTok 47.4.42.

## What changed in 1.1.3

- Rebuilt the mod settings surface around an independent Material 3 palette. `SettingsActivity` uses `Skin.material3(this)` rather than TikTok settings colours. The current main navigation is a single-column grouped list; the earlier grid and quick-control tiles are historical and were removed in the 2026-10-25 redesign.
- Fixed the switch-triggered scroll reset: a rebuild captures the current scroll offset and restores it after measurement instead of explicitly resetting the home/appearance page to zero.
- Restyled recognized native search, repost/share, comments/composer and account/profile elements with M3 tonal roles, compact chips, solid surfaces and a controlled shape scale. Original text colors are restored when the view leaves the styled screen; native click handlers are not replaced.
- Replaced the launcher icon master and icon-choice thumbnail with the user-supplied mark (scaled to 512×512). The builder writes the master image into the target APK and regenerates `Shots.java` from the launcher assets.
- Kept the visual pass bounded: scans now coalesce after a quiet interval and cap the visible traversal at 1,500 nodes; drawables are cached by role/palette/state; native UI remains responsible for its data and interactions.

## Material 3 references used

The implementation follows the official Material 3 search-bar surface (`surfaceContainerHigh`), solid filled/elevated card roles and the Android Material shape scale: 4dp ExtraSmall, 8dp Small, 12dp Medium, 16dp Large, 20dp Large Increased and 28dp ExtraLarge.

- [Material 3 Search](https://m3.material.io/components/search/overview)
- [Material 3 Color](https://m3.material.io/styles/color/overview)
- [Material 3 Lists](https://m3.material.io/components/lists/overview)
- [Material Components Android: Search](https://github.com/material-components/material-components-android/blob/master/docs/components/Search.md)
- [Material Components Android: Cards](https://github.com/material-components/material-components-android/blob/master/docs/components/Card.md)
- [Material Components Android: Shape scale](https://github.com/material-components/material-components-android/blob/master/docs/theming/Shape.md)

## Verification status

The quality-repair target suite passed 37 source/runtime tests. A full `python3 -m unittest discover -s tests -v` run exceeded the execution limit while in `test_profile_mint_runtime`, so the complete suite is not confirmed. The Android dex/APK build and on-device UI test are not confirmed. A successful source test is not proof that the patched app installs or that every view is recognized on TikTok 47.4.42. The archive contains no installable APK.

## Build in GitHub Codespaces

Place `tiktok-47-4-42.apk` in `/workspaces/SuperOptimizer-26.4/`, upload this kit ZIP to that directory, then use Terminal → New Terminal:

```bash
unzip -o TikTok-Mint-Developer-Kit-1.1.3-material3-redesign.zip
cd TikTok-Mint-Developer-Kit-1.1.3-material3-redesign
python3 mint_builder.py \
  /workspaces/SuperOptimizer-26.4/tiktok-47-4-42.apk \
  --source-zip ./TikTok-Mint-Source-1.1.3-material3-redesign.zip \
  --profile mint \
  --accent 9B6DFF \
  --output ./build/TikTok-Mint-1.1.3-material3.apk
```

Use `--profile mint`, not the experimental `full` profile. The builder needs JDK 17+ and network access to download pinned Android tools. After building, install on a test device and verify the settings scroll position, search, reposts, comments and profile visuals.

## QA and visual inspection (added 2026-10-24)

This kit now has repeatable host checks and a device screenshot workflow. These are separate levels of evidence:

- **Quick regression suite:** run `bash qa/run_qa.sh quick`. It checks Python syntax and repository invariants, then executes targeted JVM/double-based tests for Material surfaces, profile styling, dates, textures/media, sticker loading, auto-renew logic and removed Nostalgia references. The log is written to `qa-artifacts/reports/`.
- **Full suite:** run `bash qa/run_qa.sh full`. This runs every `tests/test_*.py` file separately, with a per-file timeout and a JSON/Markdown summary plus per-test logs. It can take several minutes because many runtime tests compile Java harnesses. If you run it from the outer developer kit, it extracts the bundled source ZIP into a disposable `.qa-source/` folder first.
- **APK archive/signature preflight:** after building an APK, run `python3 qa/apk_audit.py ./build/TikTok-Mint-1.1.3.apk`. It checks ZIP CRCs and required APK entries, and uses `apksigner` for cryptographic signature verification when that tool is installed. Install `apksigner` via Android SDK Build-Tools to make the signature check available.
- **Real-screen visual report:** connect an Android device or emulator to a machine that has Android SDK Platform-Tools and navigate to a target screen. Run `bash qa/capture_ui.sh comments-gif --burst 12 --interval 0.4`, scrolling manually while the frames are captured. The script saves PNG frames, device resolution/density, a best-effort UI hierarchy, a contact sheet, frame-difference metrics and a ZIP under `qa-artifacts/review-packages/`. Run once per screen, e.g. `settings`, `search`, `profile`, `share-sheet`, `comments-gif`, `custom-textures`, `stickers` and `auto-renew`. The capture tools are included in the outer developer kit so they can be run on the local computer where USB-debugging access exists; the same scripts are also available inside the source ZIP.
- **Stable-screen regression:** take a clean reference capture with a stable screen state, then compare the next capture with `python3 qa/visual_report.py CURRENT_CAPTURE --compare REFERENCE_CAPTURE --fail-changed-ratio 0.02`. Use matching filenames and the same resolution, font scale, theme and state. Do not treat GIF/video playback or a scrolling capture as a stable baseline.

The screenshot tool requires `adb` and Pillow (`python3 -m pip install --user Pillow`). Android emulators are usually managed by Android Studio; a remote Codespace does not automatically see a phone connected by USB to your own computer. You can run the capture script on the local computer where `adb` can see the phone, or configure a reachable Android device/emulator. Set `ANDROID_SERIAL` if more than one device is connected.

**How the assistant can inspect the design:** upload the generated `*-visual-review.zip` here, or upload `report/contact-sheet.png` and any full-resolution screen capture. I cannot see a live Codespaces terminal or your device screen automatically; sharing the capture package is the hand-off that lets me inspect actual rendered pixels. Review and redact private text in screenshots/UI XML before sharing. Host-side Java test doubles are not a replacement for a real screenshot or install test.
