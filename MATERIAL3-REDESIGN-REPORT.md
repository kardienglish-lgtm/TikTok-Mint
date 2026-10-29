# Material 3 Expressive settings redesign

**Revision date:** 2026-10-25  
**Target:** TikTok Mint 1.1.3 source ZIP  
**Scope:** settings home and category rows, icon coverage, profile/comments geometry background

## Design references consulted

- [Android Developers: Settings design](https://developer.android.com/design/ui/mobile/guides/patterns/settings). It recommends clear labels, grouping related preferences, using supporting text only when useful, and moving large sets of options into subscreens.
- [Material 3 lists](https://m3.material.io/components/lists/overview). Used as the layout reference for a vertical, scannable category index with one primary label, optional supporting text, a semantic leading icon, and a navigation affordance.
- [Google Material Symbols](https://fonts.google.com/icons) and the [official Material Design Icons repository](https://github.com/google/material-design-icons). The bundled SVG set declares Material Symbols Rounded 24 px and Apache-2.0 in `assets/ui/settings/SOURCES.json`.
- Visual examples reviewed: [Android 16 settings redesign](https://www.techradar.com/computing/software/google-just-showed-us-the-new-look-of-android-16-and-its-aimed-directly-at-the-iphone-s-biggest-fans), [Android 16 settings screen examples](https://www.xatakandroid.com/sistema-operativo/gran-rediseno-android-16-profundo-que-creiamos-material-3-ha-cambiado-que-parece-otro), and the [Material 3 Expressive component showcase](https://ux-design-awards.com/winners/2026-1-material-3-expressive). These were used as visual references, not as assets copied into the APK.

## What changed

### Settings dashboard

- Replaced the old two-column card grid with a single-column list of categories inside one shared surface.
- Removed the horizontal quick-toggle carousel, oversized decorative hero, gradient-filled cards, gradient hero treatment, extra metadata pills, and repeated mini-card treatment behind each leading icon.
- Rebuilt the header as a compact app bar with back, a small Mint mark, title/subtitle, and a direct local-log action.
- Category rows are 72 dp minimum height with semantic icon, name, one supporting line, and chevron. Related destinations remain grouped; existing category IDs, callbacks, search and scroll restoration are retained.
- Settings groups use solid theme-derived tonal surfaces and low-contrast dividers. The main settings page does not attach the animated `MintBackdrop`.
- Preference rows use smaller icon-only leaders, a 72 dp minimum, concise supporting text, and the existing switch/action on the trailing edge. The switch remains the single state control.
- Removed redundant divider placeholders that had added blank gaps to the appearance screen.
- Added missing Material-style icon names/aliases so every literal settings icon request resolves through `SettingsGlyph` rather than silently returning `null`.
- Narrowed the log status dot to actual errors/crashes/exceptions and failures; routine messages such as a missing optional hook do not trigger it.

### Shapes in comments

- Comment-row binding now discovers the enclosing native `RecyclerView` and layers low-alpha geometric shapes on that container, never on individual recycled rows.
- The layer uses both theme accent colours, rotates slowly, and sits behind comment text and controls.
- The existing preference `profile_shapes_background` controls the profile and comments geometry together.
- Animation pauses when system animations are disabled. This is source-verified; real native-view detection still requires an on-device test against TikTok 47.4.42.

## Validation

The following focused source/runtime tests passed after the redesign:

```text
PYTHONPATH=tests python3 -m unittest \
  tests.test_settings_blank_sheet_source \
  tests.test_settings_redesign_v2 \
  tests.test_mint_expressive_controls \
  tests.test_profile_shapes_source \
  tests.test_custom_redesign_source \
  tests.test_first_run_prompts_disabled \
  tests.test_settings_row_hook \
  tests.test_settings_safety_runtime \
  tests.test_plexus_runtime -v

Ran 38 tests — OK
```

These tests inspect source invariants and run selected Java/Python test-double harnesses. They do **not** prove the rendered Android screen looks correct on a real device.

## Not verified

- The APK has not been rebuilt or signed in this environment.
- No real phone/emulator screenshot was captured for this revision.
- The native comments `RecyclerView` may differ between TikTok builds; the hook uses class name and bounds checks and needs real-device confirmation.
- The uploaded APK in the user's Codespace is not part of these source archives. Build the included kit with that APK, then run the APK audit and test dark/light themes, scroll restoration, settings toggles, and comments on device.
