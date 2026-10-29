# Implementation report — Material 3 settings rebuild

Date: 2026-10-25

| Request | Status in source | Location |
|---|---|---|
| Rebuild settings dashboard instead of lightly editing the old grid | Done, source-checked | `inject/java/cat/kardinebi/SettingsActivity.java`: `rebuild`, `groupNavigation`, `categoryCard` |
| Remove decorative settings gradients and large hero | Done, source-checked | `homeHero`, `card`, main root in `SettingsActivity.java` |
| Use Material-style icons throughout settings | Done, source-checked | `SettingsGlyph.java`, `assets/ui/settings/SOURCES.json` |
| Preserve settings callbacks/search/navigation | Source assertions pass | `SettingsActivity.java`; focused tests below |
| Add slowly rotating shapes behind comments | Done in source; runtime layout unverified | `Comments.java`, `ProfileShapes.java` |
| Respect system animation preferences | Source check present | `ProfileShapes.java` |
| Reduce false-positive log warnings | Done in source; log behavior on device unverified | `SettingsActivity.java:hasRecentLogWarning` |
| Build and install final APK | Not done | Requires running the kit builder against the supplied TikTok APK, then device verification |

The updated source archive is the implementation deliverable. Passing source tests do not prove that every obfuscated native view is detected correctly in TikTok 47.4.42.
