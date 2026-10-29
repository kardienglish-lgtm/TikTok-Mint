> **Quality repair update (2026-10-24):** This report has historical notes that predate the latest rollback. Current behavior is: comment composer and profile/account views are native; feed recommendation/profile tabs and inbox/activity styling are disabled; settings backdrop/hero gradient are removed; the compact logs view lists recent entries. See `QUALITY-FIX-REPORT.md` for current patch details and verification boundaries.

# Verification report — TikTok Mint Custom 1.1.3 (Settings / scroll refinement)

Date: 2026-10-24

## Settings and scroll refinement (2026-10-24)

- `SettingsActivity.java`: switch rows now use a feature-specific glyph, a concise display caption, a unique explanatory summary and one trailing animated switch. Internal setting keys and callbacks remain the source of truth, so UI wording changes do not alter preference storage. The existing accessibility descriptions now use the displayed label.
- `MintBackdrop.java`: the animated settings backdrop now includes three additional geometric shapes and orbiting dots, layered over cached radial gradients. `ValueAnimator` is lifecycle-bound to attachment/visibility, and invalidation is capped at approximately 30 fps.
- `MintScreenDesign.java`: a first scan is performed for a new activity root, while subsequent same-root calls are debounced until 150 ms of layout quiet and respect a minimum 180 ms between expensive scans. The visible tree walk is capped at 1,500 nodes. Profile styling now targets containing stat/action cells and prunes nested same-role containers; the ordinary profile action/stat chips no longer stack several outlines.
- The source notice was rewritten to identify the current TikTok Mint maintainer while preserving third-party source/licence context. The About page and the source notice are kept separate.

### Verification in this environment

Focused redesign/runtime suite:

```text
python3 -m unittest tests.test_settings_redesign_v2 tests.test_settings_blank_sheet_source tests.test_mint_expressive_controls tests.test_expressive_screens_runtime tests.test_custom_redesign_source tests.test_profile_mint_runtime -v
Ran 22 tests in 1.713s — OK
```

Additional settings/account/sheet/comment suite:

```text
PYTHONPATH=tests python3 -m unittest tests.test_settings_row_hook tests.test_settings_safety_runtime tests.test_account_appearance_runtime tests.test_sheet_runtime tests.test_comment_selection_runtime tests.test_comment_like_runtime tests.test_unsaved_runtime -v
Ran 13 tests in 5.050s — OK
```

`python3 -m compileall -q .` passed. The full `python3 -m unittest discover -s tests -p 'test_*.py'` run exceeded the environment's 180-second execution limit before yielding a final summary, so the complete test suite is **not confirmed**. A few legacy tests emit `ResourceWarning` for unclosed fixture reads; that did not fail the targeted suites.

## This revision

- `inject/java/cat/kardinebi/M3Switch.java`: replaced the status-icon style control with a standalone animated Mint switch. It uses a 58×36 dp tonal track inside a 64×40 dp touch area, a ten-lobed expressive thumb, an animated checkmark, 360 ms easing, drag/swipe interaction, and an accessibility class/state description. Native setting callbacks are still called.
- `inject/java/cat/kardinebi/MintBackdrop.java`: added a non-interactive settings backdrop with low-alpha radial gradients, flower-like expressive shapes, outlined capsules, three additional polygons, orbiting dots and slow drift. It sits behind the settings scroll content and is excluded from accessibility traversal.
- `inject/java/cat/kardinebi/SettingsActivity.java`: carried forward the standalone Mint settings home from the previous revision, added a feature-specific leading icon, explanatory text and a single trailing switch with a 78 dp minimum row height, and placed the backdrop underneath the scroll view. Existing setting actions remain wired to their original callbacks.
- Deleted `inject/java/cat/kardinebi/ExpressiveScreens.java` and replaced it with a new `MintScreenDesign.java` visual layer. Its own Mint palette and separate treatments are used for search fields/results, share/repost sheets/actions, comment sheet/composer/bubbles/actions, and profile header/stats/actions/tabs. It does not replace native click listeners or the native view content. Native appearance snapshots are restored when the styling scope ends, including background, padding, minimum height, elevation and text color; snapshots are rebased when a recycled native row receives a new background.
- `inject/java/cat/kardinebi/Themes.java`: hooks the new `MintScreenDesign` layer where the old overlay ran.
- Carried forward the previous revision's disabled automatic first-run tutorial and crash-report consent prompt. The uploaded TikTok icon was already an exact match for the 512 px master and remains unchanged.

## Earlier-revision checks (2026-10-23)

These results are from the previous expressive redesign before the 2026-10-24 settings-label/background/debounce pass; the latest checks are listed near the top. Focused suite:

```text
PYTHONPATH=tests python -m unittest   tests.test_mint_expressive_controls   tests.test_expressive_screens_runtime   tests.test_m3_custom_overhaul   tests.test_custom_redesign_source   tests.test_settings_blank_sheet_source   tests.test_first_run_prompts_disabled   tests.test_kardinebi -v

Ran 124 tests in 2.491s — OK
```

Additional regression/runtime suite:

```text
PYTHONPATH=tests python -m unittest   tests.test_repost_runtime tests.test_profile_mint_runtime   tests.test_account_appearance_runtime tests.test_screen_runtime   tests.test_sheet_runtime tests.test_settings_safety_runtime   tests.test_comment_selection_runtime tests.test_comment_like_runtime   tests.test_crash_consent_runtime tests.test_tutorial_runtime   tests.test_offline_comments_runtime -v

Ran 16 tests in 11.083s — OK
```

The runtime visual harness compiles `MintScreenDesign.java` with small Android doubles and checks representative search, repost/share, comment and account/profile screens, including that a native click handler stays intact. These tests are not an Android app install or device rendering check. Some legacy tests emit Python `ResourceWarning` messages for unclosed test fixture reads; the tests themselves pass.

The complete unittest discovery suite was attempted in this revision and exceeded the environment's 180-second execution limit before a final summary, so it is not a confirmed pass. Relevant focused suites listed above passed.

## Material 3 / Expressive references

- Android Developers, “Material Design 3 in Compose”: https://developer.android.com/develop/ui/compose/designsystems/material3 — official guidance for color roles, typography, shapes and Material 3 Expressive motion/components.
- Material 3 Switch component: https://m3.material.io/components/switch/overview — official component anatomy and state reference.
- Material 3 bottom sheets: https://m3.material.io/components/bottom-sheets/overview
- Material 3 search: https://m3.material.io/components/search/overview

The switch is a custom Android View implementation with an M3 Expressive visual direction, not the stock Compose Switch component. The design uses tonal surface roles, distinguishable shapes and state/motion rather than simply re-coloring the old switch UI.

## Third-party attribution and branding

The project is branded TikTok Mint and maintained by @kardinebi. The included `ADDITIONAL-TERMS.md` clarifies that this rebranding does not transfer ownership of third-party components or replace their applicable licences, copyright notices or attribution requirements. The settings screen keeps a visible source/licence section as a convenient place to surface that information; it does not claim authorship of third-party work.

## Not verified

- No complete Android APK build, signing, install or launch was completed. The uploaded developer kit does not include the original TikTok 47.4.42 APK input required by the builder.
- No on-device screenshot or validation of actual TikTok 47.4.42 view detection, final pixel layout, animation smoothness, performance or scrolling was possible.
- Since the screens are native TikTok content with a new visual pass, actual rendering still needs a target APK/device check.


## About-section cleanup (2026-10-24)

- Moved the app's own developer identity and app/source summary into the “О приложении” section and removed the duplicate identity card from the top of the appearance-only settings page.
- Removed the separate app-specific “Спасибо”/contributor accordion and its stale external contributor link. Removed redundant maker text from the version footer.
- Kept source and licence information visible in settings while separating it from the app-specific About section.
- Added a source regression asserting these conditions. Device rendering and APK build remain unverified because the original TikTok APK is not present in this environment.

## Customization / bugfix pass (2026-10-24)

The following focused suite passed from `tests/` with `PYTHONPATH=.:..`:

```
python3 -m unittest   test_date_patch_compat test_customization_revision test_date_overlay_runtime   test_nostalgia_filter_runtime test_streak_repository_runtime   test_profile_mint_runtime test_expressive_runtime test_mint_expressive_controls   test_m3_custom_overhaul test_expressive_screens_runtime   test_offline_comments_runtime test_offline_native   test_streak_schedule_edges test_streak_schedule_runtime -v
```

Result for the focused command: **30 tests passed**. `python3 -m compileall -q .` passed. These tests cover source contracts and runtime harnesses with test doubles, not the obfuscated TikTok 47.4.42 app itself. The custom artwork normalizer, drawing view, broader date-hook matcher, fallback sticker list and auto-renew diagnostics still require a complete APK build and real-device checks. No successful APK assembly, signing, install or on-device verification is claimed because platform tools were not available in this environment.

A second focused suite (comment selection, native screen/sheet, patching, downloads, crash consent, accent, tutorial and date/texture compatibility) passed **42 tests** after updating its local test doubles for the independent Material 3 palette. The suites overlap in coverage; do not add the two counts as a unique-test total.

## Additional repeatable QA + screenshot capture tooling (2026-10-24)

Added `qa/run_qa.sh` with `preflight`, focused `quick`, and full-discovery `full` modes; `qa/static_audit.py` for syntax/path/version invariants; `qa/apk_audit.py` for APK ZIP integrity, required-entry and optional `apksigner` checks; `qa/capture_ui.sh` for screenshot bursts and best-effort UI hierarchy/device metadata collection; and `qa/visual_report.py` for contact sheets, consecutive-frame changed-pixel metrics, baseline comparisons and a review report. Added four self-tests for the visual metric/report code.

The toolchain in the current assistant container has Python 3.13, JDK 21 and Pillow, but **does not have `adb`, Android Emulator, `sdkmanager` or `apksigner` installed**. That means this session can exercise source/JVM tests and image-report logic, but cannot capture a live TikTok screen here. The user must run the capture script on a machine/device session visible to `adb` and upload the resulting ZIP/contact sheet for direct visual inspection. Capture output can include visible usernames/comments; review/redact before sharing.


## Full QA pass after runner and date-hook corrections (2026-10-24)

- Replaced the monolithic full-test command with `qa/run_test_matrix.py`, which runs each test file in an isolated process, applies a per-file timeout, and saves individual logs plus a JSON/Markdown summary. This avoids losing diagnostics when one test hangs or the overall session times out.
- The extra suite run initially found a stale runtime-test stub missing `Skin.material3(Context)` and a date binding regression for a nested holder `holder -> item -> Aweme` shape. The stub was updated, and `Dates.bindAuthor` now unwraps up to four nested model wrappers and chooses the model that exposes both aid and timestamp. The date routing regression test then passed.
- Updated the legacy settings redesign test that still expected the removed “Лента до 10.10.2022” Nostalgia setting and searched for a deleted profile helper. It now validates the intended current contract.
- The 80-file suite was completed in batches: each batch passed after those corrections (22 + 22 + 22 + 14 test files). The quick suite also passes. This is source/JVM-harness evidence only, not real-device UI validation.


## About-screen author-reference cleanup (2026-10-24)

- Removed duplicated upstream author/source wording from the app-information dialog and the secondary links screen. Removed duplicate source-project links from those screens.
- Kept the source/license notice on the main settings screen to preserve third-party context alongside the rebranded app identity.
- Added source regression assertions for the cleaned About/links screens and retained required attribution.
- The developer kit's APK build and real-device UI remain unverified until run in Codespaces against the supplied TikTok APK.


## Post-cleanup quick QA (2026-10-24)

- `bash qa/run_qa.sh quick`: **PASS**.
- Static audit: 123 Python files parsed; 12 critical paths checked.
- All 26 tests in the quick QA matrix passed.
- `adb` was unavailable, so no screenshot/device UI check was run. The APK build itself remains unverified.
