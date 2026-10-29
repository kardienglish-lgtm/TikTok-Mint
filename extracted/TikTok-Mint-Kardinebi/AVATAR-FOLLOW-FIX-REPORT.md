# Avatar and follow-button fix report

Date: 2026-10-25
Target APK: TikTok 47.4.42 (`tiktok-47-4-42.apk` supplied with the project)

## Root cause confirmed against the supplied DEX metadata

The earlier hook searched for three legacy avatar-mask owners that are not the relevant clipping implementations in this APK. The actual `Path.addCircle(FFFLandroid/graphics/Path$Direction;)V` call sites were inspected in the target DEX:

- `LX/0GyZ;` (`classes7.dex`), in `onSizeChanged`.
- `LX/0Gya;` (`classes7.dex`), in `onSizeChanged`.
- `LX/0Gx4;` (`classes7.dex`), in `LJIIIIZZ(Canvas)`; this is the feed's `SmartImageView` avatar component.

`FeedAvatarDefaultAssem` in `classes13.dex` contains the fields `LLJLL` (`TuxIconView`), `LLJLILLLLZIIL` (`View`), `LLJJJ` (`ViewGroup`) and `LLJJJJ` (`RelativeLayout`). The previous runtime hook could silently return before finding the view tree and did not recognise `TuxIconView` as an icon candidate.

## Changes

- `kardinebi/dexpatch.py`: add explicit avatar-only call-site hooks for the verified avatar clipping classes. No heuristic patch is applied to arbitrary `Drawable`/`Bitmap` classes, because that would reshape unrelated circular UI.
- `FeedFollowShape.java`: hook the actual `FeedAvatarDefaultAssem.onViewCreated(View)` lifecycle with the created view, resolve the exact native icon and surrounding tree using the verified fields, recognise `TuxIconView`, and keep a tightly scoped geometric fallback within the feed-avatar component. The known native target can still be styled when TikTok has disabled its interaction state, as long as it is visible; its original click handling is not replaced by a new click action.
- Tests now cover the actual owner names, lifecycle overload and fallback requirements, and retain a regression check that unrelated circular Drawables are not reshaped.

## Verification

- DEX metadata and call sites inspected in the supplied APK: passed for the owners above.
- Focused source/runtime tests: passed (see the build's QA run).
- Full Android APK build and runtime/device test: not performed in this environment. The toolchain requires pinned Android build artifacts which are not cached here and the environment could not resolve the download host. Build with the included `mint_builder.py` in Codespaces, then check the rendered button and avatars on device.
