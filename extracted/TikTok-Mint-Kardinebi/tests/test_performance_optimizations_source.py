import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "inject/java/cat/kardinebi"


class PerformanceOptimizationsSource(unittest.TestCase):
    def test_recolour_cache_has_bounded_process_cache(self):
        source = (JAVA / "RecolourCache.java").read_text(encoding="utf-8")
        self.assertIn("MEMORY_LIMIT = 2 * 1024 * 1024", source)
        self.assertIn("MEMORY_ENTRY_LIMIT = 512 * 1024", source)
        self.assertIn("MAX_DISK_ENTRY_BYTES = 16L * 1024 * 1024", source)
        self.assertIn("private static final LinkedHashMap<String, MemoryEntry> memory", source)
        self.assertIn("forgetMemoryForShelf(dir, colours[i])", source)

    def test_pixel_hash_uses_bounded_buffer_not_full_pixel_copy(self):
        source = (JAVA / "RecolourCache.java").read_text(encoding="utf-8")
        hash_method = source.split("static String hash(int[] pixels", 1)[1].split(
            "static byte[] png(", 1)[0]
        self.assertIn("ByteBuffer.allocate(16 * 1024)", hash_method)
        self.assertIn("sha1.update(block.array(), 0, block.position())", hash_method)
        self.assertNotIn("ByteBuffer.allocate(pixels.length * 4 + 8)", hash_method)

    def test_cache_timestamp_io_is_only_in_writer_task(self):
        source = (JAVA / "RecolourCache.java").read_text(encoding="utf-8")
        helper = source.split("private static void touchDirectoryAsync", 1)[1].split(
            "private static long lastUsed", 1
        )[0]
        self.assertIn("writer.execute(new Runnable()", helper)
        self.assertIn("directory.setLastModified(usedAt)", helper)
        self.assertNotIn("file.getParentFile().setLastModified", source)

    def test_navbar_preferences_are_cached_and_setters_refresh_them(self):
        source = (JAVA / "NavBar.java").read_text(encoding="utf-8")
        self.assertIn("private static volatile Boolean enabledSetting", source)
        self.assertIn("private static volatile Boolean keptSetting", source)
        self.assertIn("enabledSetting = Boolean.valueOf(on)", source)
        self.assertIn("keptSetting = Boolean.valueOf(kept)", source)
        self.assertIn("if (known != null) return known.booleanValue()", source)


if __name__ == "__main__":
    unittest.main()
