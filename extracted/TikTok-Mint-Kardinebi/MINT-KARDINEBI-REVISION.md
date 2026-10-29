# TikTok Mint 1.1.3 · Kardinebi revision
- Comment scanning now schedules one follow-up instead of restarting the debounce on every layout callback. It retains a previously detected comment surface briefly when the same attached view remains in the current hierarchy.
- Large media/GIF/sticker comment rows are left with their native backgrounds, and the comment sheet/bubbles use flatter tonal surfaces to prevent nested outlines.
- The Material treatment is removed from the For You / Following top strip. Profile tabs are painted by one system only, with the selected index cache retained across repeated layouts.
- Region settings are combined into one card with quick region choices and the existing full country list.
- Seven rapid taps on the TikTok Mint hero unlock a persistent green theme for `@kardineb`: all four Md3 switches, the bottom navigation, bold font, ad hiding, live hiding, repeated-repost notification filtering off, and US region. A toast reads `Привет от Кардинеби`.
- The unlocked profile receives a two-spark marker. Its tap text is `супер секретная пасхалочко`.

## Checks

The focused source/runtime suites and Python compileall passed; `MintScreenDesign.java` was compiled and exercised against Android test doubles. Full Java-to-dex build was blocked here because the Android platform download failed with a DNS/network error. The original TikTok APK was not present in this working environment, so this revision has not been built into a complete APK or tested on a device.

## Media, publication dates, sticker loading and drawing (2026-10-24)

- User-selected action textures are privately copied, downsampled before full decode and normalized to transparent PNG no larger than 512 px per longest edge.
- The icon hook now locates Tux icon drawables through nested wrappers and class hierarchies instead of requiring an exact runtime class name. Textures are stored by the app instead of relying on a long-lived external document URI.
- The selected-post date hook no longer requires a diagnostic log literal; it remains limited to the known descriptor class, exact `a2(int, Aweme)` prototype and native `getAid` guard. Author binding also tries the component's native payload.
- A Material 3 sketch pad is available from Data and Maintenance. It supports colour, brush size, undo, clear and PNG export to `Pictures/TikTok Mint`.
- Favourite stickers accept more repository-flow wrappers and use recently drawn chat stickers as fallback if TikTok's private favourites repository is unavailable. Load callbacks avoid refresh/rebuild loops.
- Auto-renew diagnostics now identify missing Android permissions, unknown native streak service, no observed conversations and an unavailable selected sticker. Enabling the feature prepares native discovery and requests Android 13+ notifications permission.
- Nostalgia filtering is gone from runtime source. Mint-owned offline comment and download-count dialogs use M3 panels.

## Verification boundary

`python3 -m compileall -q .` passed. A focused set of **67 tests passed**, including native-Java harnesses with doubles, date-hook compatibility, comments, profiles, icon texture contracts, sticker fallback, auto-renew schedules and nostalgia removal. This is not a device run. The environment has no `android.jar` and could not obtain Android SDK tools, so a full APK build/signature/install/rendering test remains unverified.



## Deep optimization and photo carousel — 2026-10-25

- Replaced the settings home category stack with a new two-column card grid, swipeable quick-control cards, a compact header and larger tonal setting surfaces. The old list composition was removed while category callbacks and preference keys remain intact.
- Added `PhotoSwipeOptimizer.java`, invoked only from the known `PhotoSlideViewPagerComponent.onViewCreated(View)` lifecycle. It sets the public offscreen limit to two only when the device is not marked low-RAM, the app heap class is at least 192 MB and at least 256 MB of system RAM is currently available. It doesn't allocate bitmaps or replace TikTok's loader/cache.
- The target photo-pager class was found in the supplied APK. Three specific photo-swipe tests and nine settings-source tests pass. APK build and real-device verification are still unverified due missing Android tools/network and no connected `adb` device.
- The source/license card is retained as required; `@kardinebi` is explicitly identified as the modifier/maintainer.
