# TikTok Mint QA tools

## Quick host checks

```bash
bash qa/run_qa.sh quick
```

Run the full test matrix (each file is isolated, has a timeout, and writes its own log):

```bash
bash qa/run_qa.sh full
```

Run the APK archive/signer audit after the builder completes:

```bash
python3 qa/apk_audit.py ./build/TikTok-Mint-1.1.3.apk
```

The signature check runs when Android SDK Build-Tools `apksigner` is installed. Missing `apksigner` is reported as skipped unless `--require-signature-verifier` is passed.

## See the actual rendered UI

Host tests cannot reveal all clipping, flicker or device-specific layout problems. For that, use a phone/emulator that `adb devices -l` can see. Install and open the test build first, then navigate to the screen you want to inspect.

One stable screenshot:

```bash
bash qa/capture_ui.sh settings
```

Capture a sequence while scrolling a GIF/image-heavy comment sheet:

```bash
bash qa/capture_ui.sh comments-gif --burst 12 --interval 0.4
```

The tool writes a folder under `qa-artifacts/captures/` containing frames, device size/density, current-activity details, and a best-effort Android UI hierarchy. It then creates a contact sheet, temporal pixel-difference metrics and a `*-visual-review.zip` under `qa-artifacts/review-packages/`.

To automate stable screenshot regression, first capture approved reference screenshots in a clean stable state. For subsequent captures, keep matching relative filenames and resolution and compare with:

```bash
python3 qa/visual_report.py CURRENT_CAPTURE --compare REFERENCE_CAPTURE --fail-changed-ratio 0.02
```

The 2% threshold is only a starting point. A failed comparison points out a change; it does not determine whether the change is good or bad. GIF/video playback, a running clock, scrolling, loading states and animated backgrounds should not be compared as stable golden images. Use the burst report to identify frames that change unexpectedly, then inspect the contact sheet / full-resolution images.

## Let the assistant inspect it

Upload the generated `*-visual-review.zip` or `report/contact-sheet.png` in the chat. The assistant can then inspect the image contents and the XML side-by-side with the reported dimensions and diff metrics. The assistant does not automatically have live access to your Codespace terminal or phone screen.

In GitHub Codespaces, a USB phone connected to your local PC usually is not automatically attached to the remote terminal. Use this script on the local machine where `adb` can see the phone, or configure a reachable emulator/device. On Windows, run in an environment with Bash, Python 3 and Android SDK Platform-Tools. Install Pillow with `python3 -m pip install --user Pillow` if needed.

Before sharing, review the UI XML and screenshots for usernames, private comments, notifications or other personal information. Only turn on the optional `--logs` switch when diagnosing an error, and review the log file before uploading.

Additional redesign checks: `tests/test_profile_shapes_source.py` covers profile-only shape randomization, animated dual-accent backgrounds, second-accent persistence, and date contrast; `tests/test_streak_account_refresh_source.py` checks favorite-sticker reload on account changes and guards against claiming the text sender works before its native API is verified.
