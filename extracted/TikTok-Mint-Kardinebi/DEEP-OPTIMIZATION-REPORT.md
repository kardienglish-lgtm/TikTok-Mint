# Deep optimization and bug-fix report

Date: 2026-10-25
Target APK: TikTok 47.4.42 (`com.zhiliaoapp.musically`, ARM64)
Developer kit: 1.1.3 quality-fix (archive name and version preserved)

## Confirmed fixes

### Auto-streak sender selection
The target APK's sticker sender contract is `X.0o13`; its enum constant `AUTO_CONSECUTIVE_SA_STICKERS` is in `X.0np7`, which implements that contract. The previous resolver looked only for `X.13GH`, an unrelated type, so the reflective sender matcher rejected the actual send method. `Streaks.sourceFor()` now checks the contract first, then `X.0np7`, with `X.13GH` kept as a compatibility fallback. The regression test failed against the old behavior with `APK sticker source enum resolves through the sender interface`; after the fix, `test_streak_repository_runtime.py` passes. Actual message delivery remains unverified until a patched APK is built and exercised on-device.

### Publication dates on recycled recommendation cells
The target APK exposes `VideoAuthorInfoRelationAssem.pq(Aweme)`, called when a recycled author row receives a new post model. Previously the patcher only hooked initial view lifecycle, when the model can still be absent. It now adds `Dates.bindAuthor(Object,Object)` to the model-rebind method and keeps the lifecycle hook. The component exposes its view through `Xb()`, now included in accessor resolution. `Dates` uses one main-thread `Handler` instead of allocating a Handler for repeated feed binds.

A second stale-row bug was reproduced by a new test: after a row was rebound to a post with an invalid/missing timestamp, the prior post ID remained in the View tag and `authorRows` registry. The test failed on the old code with `recycled row clears its old post id...`. The fix removes old registry entries, clears the post-ID/date-data tags and unregisters the overlay on the main thread. A valid rebind also removes any former mappings for that recycled View before registering the new ID. The targeted test now passes. Actual layout appearance remains unverified on a physical device.

### Recolour cache resource use
- The 96 MiB disk limit now counts nested `like/<colour>/` shelves, not only files at the root. Oldest entries/shelves are removed until the cache drops to 75% of the cap.
- Disk writes use one low-priority worker with a four-entry bounded queue. A full queue discards the oldest optional cache write instead of retaining an unlimited number of encoded image arrays.
- Entries that cannot be read back under the 16 MiB cap are rejected before cloning or queueing.
- The process-local LRU is bounded to 2 MiB and 512 KiB per item. It stores encoded bytes, not decoded `Bitmap`s.
- Pixel hashing keeps the same SHA-1 key compatibility while using fixed 16 KiB blocks rather than allocating another `4 * pixels + 8` byte buffer.

The cache regression test first failed with a sparse 97 MiB file in `like/<colour>/`; after the fix, nested trimming, bounded queue, oversized-entry rejection and hash compatibility all pass.

## Full source and APK review

- The final developer-kit ZIP passes `unzip -t`; the final outer ZIP has 22 entries and no duplicate paths. The nested source ZIP has 681 entries, no duplicate paths, and excludes generated `__pycache__`, `.pyc`, and `qa-artifacts` files.
- APK structural audit passes for the supplied 347,127,290-byte APK: package `com.zhiliaoapp.musically`, version `47.4.42`, 53 DEX files and 26,323 ZIP entries. The archive contains roughly 299 native-library entries. Most DEX and native code belongs to TikTok itself; removing libraries without dependency/runtime evidence would risk breaking the app, not optimizing the mod.
- DEX inspection confirms the sender and author component signatures used above; the model has `getCreateTime(): long`.
- Static audit on source extracted from the final kit passed: 126 Python files parsed, 12 critical paths checked.
- Complete regression matrix after the final stale-row fix: **84/84 test files and 301 individual tests passed**, zero failed files/timeouts, across eight isolated batches with 30-second per-file limits (12, 12, 4, 8, 12, 12, 12, 12 files).
- Targeted tests run from the source extracted by the final kit also passed: `test_dates_author_runtime.py`, `test_date_patch_compat.py`, `test_date_overlay_runtime.py`, `test_streak_repository_runtime.py`, `test_recolour_cache_runtime.py`, and `test_photo_swipe_patcher.py`.
- Builder dry-run on the final outer ZIP passed and identified the correct APK package/version. Actual APK build is **not completed**: downloading Android `platform.zip` failed with `Temporary failure in name resolution` in this environment.

## Not verified / remaining limits

- `adb`, `apksigner`, `aapt2`, `apktool`, and `jadx` are unavailable here. APK signature validation was skipped. No device UI run, black-frame reproduction on a physical phone, or RAM/CPU/FPS profile was possible.
- The one-command quick wrapper was not used as the final result after the last source change: its fresh-package run once exceeded the outer terminal time limit at a test that passes individually and in the complete 84-file matrix. Use the complete matrix result above; don't treat that wrapper interruption as a test pass or a code failure.
- A patched APK has not been produced. Source-level tests do not prove the streak is delivered by TikTok, that date overlays render correctly on every feed variant, or that photo black frames are gone on-device.
- The original TikTok engine is present as compiled DEX and native libraries, not source code in this kit. A wholesale engine/API rewrite is not possible from these files alone.

## Further optimization candidates observed, not changed

- `FriendAvatars.java` and `Accent.java` still use background work queues that are not globally bounded. Adding back-pressure needs a deliberate failure/cleanup path; otherwise rejected work can leave recycled avatar rows or animations waiting forever. Not changed in this pass.
- Exact streak alarms can affect battery use and require permission on modern Android. Reducing their precision could make a streak renewal late; the existing exact-permission check and fallback are preserved.
- No performance improvement percentages are claimed because a device profiler was unavailable. Queue bounds and allocation reductions are source-level changes, not measured device RAM/FPS gains.
