# Additional optimization status — Mint 1.1.3

## Source changes

- `inject/java/cat/kardinebi/RecolourCache.java`: bounded encoded-byte LRU (2 MiB total, 512 KiB/item), direct payload reads, 16 MiB disk-entry guard, async/throttled shelf timestamp updates, and streaming SHA-1 pixel hashing with a fixed 16 KiB working buffer.
- `inject/java/cat/kardinebi/NavBar.java`: cache the navigation settings booleans used in the pre-draw loop; setters update the cache immediately.
- `tests/test_recolour_cache_runtime.py`: verifies cached data, shelf eviction and new hash equality against the prior algorithm.
- `tests/test_performance_optimizations_source.py`: source regression checks for the bounded memory cache, no synchronous shelf timestamp update, settings cache and bounded hash buffer.

## Latest checks

- Quick QA: PASS; static audit parsed 125 Python files and checked 12 critical paths.
- `test_recolour_cache_runtime.py`: PASS.
- `test_performance_optimizations_source.py`: PASS, 4 tests.
- `test_accent_coverage_runtime.py`: PASS after the hash change.
- `test_download_morph_runtime.py`: PASS when invoked standalone.
- The earlier complete 83-file regression matrix passed in batches before the last streamed-hash micro-optimization. The exact final source has focused tests and quick QA passing, but a fresh complete matrix did not finish because the wrapper stalled while starting `test_download_morph_runtime.py`; therefore final full-matrix status is not claimed as confirmed.

## Measurements not performed

No real-device RAM/CPU/FPS benchmark was possible (`adb` missing), and no new APK was built because Android toolchain download failed on DNS. The cache may retain up to 2 MiB of encoded data; no actual RAM-savings percentage is claimed.
