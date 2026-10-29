package cat.kardinebi;

import android.content.Context;
import java.io.File;

/** Clears only app-private temporary cache files; never preferences, databases or account data. */
final class CacheCleaner {
    private CacheCleaner() {}

    static final class Result {
        final int files;
        final long bytes;
        Result(int files, long bytes) { this.files = files; this.bytes = bytes; }
    }

    static Result clear(Context context) {
        if (context == null) return new Result(0, 0L);
        int[] files = new int[]{0};
        long[] bytes = new long[]{0L};
        clearChildren(context.getCacheDir(), files, bytes);
        if (android.os.Build.VERSION.SDK_INT >= 21) clearChildren(context.getCodeCacheDir(), files, bytes);
        return new Result(files[0], bytes[0]);
    }

    private static void clearChildren(File root, int[] count, long[] bytes) {
        if (root == null || !root.isDirectory()) return;
        File canonicalRoot;
        try { canonicalRoot = root.getCanonicalFile(); }
        catch (Throwable ignored) { return; }
        File[] children = root.listFiles();
        if (children == null) return;
        for (File child : children) removeInside(canonicalRoot, child, count, bytes);
    }

    private static void removeInside(File root, File item, int[] count, long[] bytes) {
        if (item == null) return;
        try {
            File canonical = item.getCanonicalFile();
            String parent = root.getPath() + File.separator;
            if (!canonical.getPath().startsWith(parent)) return; // refuses symlink escapes
            if (canonical.isDirectory()) {
                File[] nested = canonical.listFiles();
                if (nested != null) for (File child : nested) removeInside(root, child, count, bytes);
            }
            long size = canonical.isFile() ? canonical.length() : 0L;
            if (canonical.delete()) { count[0]++; bytes[0] += size; }
        } catch (Throwable error) {
            Diary.note("cache cleanup skipped: " + error.getClass().getSimpleName());
        }
    }
}
