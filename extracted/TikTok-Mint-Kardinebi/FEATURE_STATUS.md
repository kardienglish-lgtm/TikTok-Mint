
Source changes present
Settings home: compact Mint app bar and a single-column category list. Within categories, preference rows use a semantic Material icon, effect description and single trailing switch. Original preference keys and callbacks remain unchanged.
Settings background: the settings page itself has no animated or gradient backdrop. Slow, low-opacity rotating geometry is limited to profile content and the comments-list background; it never paints individual recycled comment rows and respects system animation settings.
Search, repost/share, comments and profile: previous ExpressiveScreens.java implementation deleted. A new MintScreenDesign.java contains independent Mint palette logic and bespoke treatment for each screen family while preserving native content and handlers.
Native view appearance is restored on leaving the relevant styled screen. Recycled views re-capture their native appearance when the native background changes. Profile stats/actions no longer receive stacked outlines, and repeated native hierarchy scans are coalesced until the layout quiets.
First-run automatic tutorial and crash-report consent prompt remain disabled from the preceding revision.
Supplied launcher PNG matches the existing 512 px master; no icon change was needed.
Verified here
Latest focused redesign/runtime suite: 22 tests passed (Ran 22 tests in 1.713s — OK).
Additional settings, account, sheet and comment regression suite: 13 tests passed (Ran 13 tests in 5.050s — OK).
python3 -m compileall -q . — passed.
The Java runtime harness compiles and exercises MintScreenDesign.java against Android test doubles. This is source/runtime logic verification only, not a device test.
Constraints / not verified
The full unittest discover run exceeded the 180-second tool limit before reporting a final summary. Do not treat that run as a pass. Relevant focused suites passed; a few existing ResourceWarning messages in test code concern unclosed fixture reads and did not fail those runs.
No original TikTok 47.4.42 APK is included, so no complete APK build, signature check, installation or physical-device screenshot is available.
The supplied ADDITIONAL-TERMS.md explains that third-party ownership, attribution and licence requirements remain in force after rebranding. The source/license area stays visible in the main settings screen.
Actual runtime detection and visual quality on the target TikTok APK remain unverified until tested on-device.
Customization and bugfix pass (2026-10-24)
All mod-owned sheets opened by offline comments, offline choices and settings use the independent Material 3 panel/dialog layer; obsolete square AlertDialog.Builder use was removed from these flows.
Custom like/comment/favorite/share artwork is copied to app-private storage at selection time, downsampled before decode and capped at 512 px on its longest edge before PNG encoding. This avoids revoked content:// grants and avoids retaining giant source images in memory. TikTok icon-wrappers are discovered through nested drawable/superclass inspection.
Publication-date hooks no longer depend on a debug-only log string. The selected-post hook remains scoped to the known descriptor class and exact a2(int, Aweme) signature and checks the native getAid sequence; author renderers also try the native holder payload when model getters are absent. This broadens compatibility, but cannot guarantee coverage for every encrypted/obfuscated screen without APK/device verification.
Added a self-contained Material 3 sketch pad with touch drawing, colour palette, three brush widths, undo, clear, and PNG export to Pictures/TikTok Mint.
Favourite-sticker selection now unwraps more repository-flow shapes and completes on valid unchanged/empty snapshots. When TikTok renames the private favourites repository, recently observed chat stickers are offered as a visible fallback rather than leaving an empty picker. Loading does not recursively rebuild during cooldown.
Auto-renew now requests Android 13+ notification permission on enable, prepares native-service discovery, triggers sticker loading and surfaces explicit diagnostics for missing exact-alarm permission, notifications, discovered streak service, observed chats or a resolvable selected sticker.
The old nostalgia filter and its settings strings are absent from runtime source.
Verification added: six source-level checks and an alternate native smali layout without the diagnostic string; see tests/test_customization_revision.py and tests/test_date_patch_compat.py.
Verification tools
Added a repeatable quick/full QA entry point with log capture and source preflight.
Added APK archive and optional signature audit.
Added ADB screenshot bursts, UI hierarchy/device metadata, contact-sheet generation and baseline pixel-difference reports, plus self-tests for image-diff calculations.
Real device/emulator screenshots remain an external check until a device is visible to adb; the tools do not claim to have rendered the actual TikTok UI by themselves.
Date-hook follow-up: fixed deeper native holder unwrapping so publication dates can bind to the actual author row when TikTok wraps Aweme inside getItem()/getAweme() layers; regression-tested with the selected-date/author-slot harness.
Verification follow-up: full 80-file test suite passed in four isolated batches after updating stale test doubles/assertions. Real Android APK/device rendering remains unverified in this environment.
Deep optimization update — 2026-10-25
Historical UI note: an earlier iteration used a two-column category grid and quick setting cards. Those elements were removed in the 2026-10-25 Material 3 settings rebuild.
PhotoSwipeOptimizer.java is wired into the target photo ViewPager lifecycle. It requests an offscreen-page limit of 2 only when system memory conditions permit, otherwise preserving the native default. This is a bounded page-retention optimization, not a rewrite of TikTok's private image loader.
The source/license card remains visible and identifies @kardinebi as maintainer while preserving applicable third-party notices.
Focused tests: 12 passed; quick QA: 39 tests passed; full matrix: 82/82 test files passed. Full Android DEX build, physical-device testing and before/after RAM/CPU profiling are not yet verified.