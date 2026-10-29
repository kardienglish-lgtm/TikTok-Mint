# Build notes — TikTok Mint Custom 1.1.3

This is a source/build kit for the user-provided single-file TikTok APK **47.4.42**, not a compiled APK. Keep `LICENSE` and `ADDITIONAL-TERMS.md` with the source.

## Requirements

- Python 3
- JDK 17 or newer (`java` and `javac` on `PATH`)
- Internet access for the builder's pinned Android tools
- A single-file TikTok 47.4.42 APK (not an XAPK/APKS bundle)

## GitHub Codespaces

1. Put `tiktok-47-4-42.apk` in the workspace root and upload/extract the developer kit.
2. Run the commands in `README.md` from Terminal → New Terminal.
3. Use `--profile mint`; `full` is experimental and not recommended for this TikTok version.
4. Verify that the output exists, then install on a test device and check the requested screens and switch scroll retention.

The source-level and JVM harness checks do not compile the complete Android injection into an APK and do not substitute for a device test. A previous build attempt in this environment failed downloading Android platform dependencies due to DNS/network access; a successful APK build, signature check, installation, or launch is not claimed.
