# TikTok Mint settings rework v2

Implemented in source:
- Removed payment/donation prompts and the separate Material 3 drawing pad.
- The TikTok Mint row in settings routes to https://t.me/TikTokmintt. The separate GitHub source link remains under source/licence information.
- Settings with sub-options now open a dedicated Material 3 modal instead of expanding their controls inline: region/country search, accent palette, theme colour roles, launcher icon choices, badge ordering, streak-sticker selection, and the diary/log viewer.
- Search results for region, accent, theme and badges open the same modal as their main settings rows.
- Direct on/off switches remain direct switch rows; file picking and actions that already open a system picker/dialog retain that behaviour.
- Font picker is a modal dialog with the preview text “Я люблю TikTok Mint”; selected font/emoji/weight are staged in the preview and saved for application after app restart.
- Weight selection from 100 to 900 is included in the font dialog, with an animated expressive slider; the duplicate inline weight control is removed from the settings screen.
- Three emoji are randomized when the font dialog opens; the emoji theme refreshes visible UI after selecting a set.
- Region selection is redesigned with a compact summary row on the settings page and a searchable two-column country grid in its own modal.
- Fixed retry logic for user textures so a failed initial load does not permanently cache the fallback glyph.
- Added a streak text template that can be copied and pasted manually.

Verification:
- Focused settings regression suite: 31 passed.
- The full test suite did not complete within the available command limit.
- A Java syntax-oriented check found no parser errors in `SettingsActivity.java`, but a complete Java/Android compile is unavailable here because the Android SDK (`android.jar`) and builder toolchain are not present.
- The full Android APK build was not completed in this environment. No finished APK is included and no device test is claimed.
- Scheduled automatic text messaging is not implemented. The current `Streaks.sendText()` is a stub; only the template/copy workflow is provided. Existing sticker auto-renew behaviour is unchanged.
