package cat.kardinebi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Recoloured pictures and animations kept on disk.
 *
 * Recolouring is done once per (source, colour). The answer is written here
 * and every later start, and every later load, reads it back instead of
 * repeating the work. The cache is dropped when TikTok is updated (the stamp
 * changes), when a texture pack changes, and old entries are trimmed once
 * the folder grows past {@link #LIMIT}.
 */
final class RecolourCache {
    private RecolourCache() {}

    /** Bump when the recolouring itself changes, so stale answers are not reused. */
    static final String ALGORITHM_VERSION = "2";
    private static final String VERSION = ALGORITHM_VERSION;
    private static final long LIMIT = 96L * 1024 * 1024;
    private static final int LIKE_COLOURS = 6;
    /** Small process-local cache for hot icon/animation lookups. Encoded bytes, not bitmaps. */
    private static final int MEMORY_LIMIT = 2 * 1024 * 1024;
    private static final int MEMORY_ENTRY_LIMIT = 512 * 1024;
    private static final long TOUCH_INTERVAL_MS = 30_000L;
    private static final long MAX_DISK_ENTRY_BYTES = 16L * 1024 * 1024;
    private static final int WRITE_QUEUE_LIMIT = 4;

    /** The answer "nothing to recolour here" - remembered too. */
    static final byte[] NONE = new byte[0];

    private static volatile File folder;
    private static volatile String stamp;
    private static volatile int writes;
    private static volatile long epoch;
    private static volatile boolean clearing;

    /**
     * Access-ordered encoded-data cache. A strict byte budget prevents this
     * optimization from turning into an unbounded bitmap/heap cache.
     */
    private static final LinkedHashMap<String, MemoryEntry> memory =
            new LinkedHashMap<String, MemoryEntry>(16, 0.75f, true);
    private static int memoryBytes;

    private static final class MemoryEntry {
        final byte[] data;
        final File directory;
        MemoryEntry(byte[] data, File directory) {
            this.data = data;
            this.directory = directory;
        }
    }
    private static final Object MEMORY_LOCK = new Object();

    /** Exact in-process recency for the color LRU, updated without filesystem I/O. */
    private static final Map<String, Long> LAST_TOUCH = new HashMap<String, Long>();
    /** Last time we persisted recency; metadata writes are throttled. */
    private static final Map<String, Long> LAST_STAMP_WRITE = new HashMap<String, Long>();
    private static final Object TOUCH_LOCK = new Object();

    // Cache writes are optional. Bound pending work so rapid scrolling cannot keep
    // an unlimited number of encoded images alive while the disk writer catches up.
    private static final ThreadPoolExecutor writer = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(WRITE_QUEUE_LIMIT),
            new ThreadFactory() {
                @Override public Thread newThread(Runnable job) {
                    Thread thread = new Thread(job, "kardinebi-recolour-write");
                    thread.setPriority(Thread.MIN_PRIORITY);
                    thread.setDaemon(true);
                    return thread;
                }
            }, new ThreadPoolExecutor.DiscardOldestPolicy());

    private static synchronized File folder() {
        File known = folder;
        if (known != null) return known;
        Context context = Kardinebi.context();
        if (context == null) return null;
        try {
            File dir = new File(context.getFilesDir(), "kardinebi/recolour-cache");
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            // A different TikTok build means different source art.
            long updated = 0;
            try {
                updated = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
            } catch (Throwable ignored) { }
            stamp = VERSION + "|" + updated;
            File mark = new File(dir, "stamp");
            String saved = readText(mark);
            if (!stamp.equals(saved)) {
                clearMemory();
                synchronized (TOUCH_LOCK) { LAST_TOUCH.clear(); LAST_STAMP_WRITE.clear(); }
                wipe(dir);
                writeText(mark, stamp);
            }
            folder = dir;
            return dir;
        } catch (Throwable error) {
            return null;
        }
    }

    private static String name(String key) {
        return hex(digest((stamp + "|" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    static String likeKey(String animation, int colour) {
        return animation + ":" + Integer.toHexString(colour & 0xFFFFFF)
                + ":" + ALGORITHM_VERSION;
    }

    /** Keep all frames of one colour together so the six-colour LRU is exact. */
    private static File entry(File dir, String key, boolean create) {
        if (key.startsWith("png|")) {
            String[] parts = key.split("\\|", 4);
            if (parts.length == 4 && parts[1].matches("[0-9a-fA-F]{1,6}") && parts[2].equals(ALGORITHM_VERSION)) {
                File shelf = new File(new File(dir, "like"), parts[1].toLowerCase(java.util.Locale.ROOT));
                if (create) shelf.mkdirs();
                return new File(shelf, name(key));
            }
        }
        if (!key.startsWith("lottie|")) return new File(dir, name(key));
        int version = key.lastIndexOf(':');
        int colour = key.lastIndexOf(':', version - 1);
        if (version < 0 || colour < 0 || !key.substring(version + 1).equals(ALGORITHM_VERSION))
            return new File(dir, name(key));
        String rgb = key.substring(colour + 1, version);
        if (!rgb.matches("[0-9a-fA-F]{1,6}")) return new File(dir, name(key));
        File shelf = new File(new File(dir, "like"), rgb.toLowerCase(java.util.Locale.ROOT));
        if (create) shelf.mkdirs();
        return new File(shelf, name(key));
    }

    /** The saved bytes, {@link #NONE} for "unchanged", or null if never seen. */
    static byte[] get(String key) {
        if (clearing) return null;
        final long generation = epoch;
        MemoryEntry hot = memoryGet(key);
        if (hot != null) {
            touchDirectoryAsync(hot.directory);
            return hot.data == NONE ? NONE : hot.data.clone();
        }
        try {
            File dir = folder();
            if (dir == null || clearing) return null;
            File file = entry(dir, key, false);
            if (!file.isFile()) return null;
            long length = file.length();
            // Cache entries are generated from small icons/animation resources. Refuse
            // corrupt or unexpectedly huge files before allocating their payload.
            if (length < 1 || length > MAX_DISK_ENTRY_BYTES) return null;
            byte[] data;
            boolean unchanged;
            FileInputStream in = new FileInputStream(file);
            try {
                int marker = in.read();
                if (marker < 0) return null;
                unchanged = marker == 0;
                if (unchanged) {
                    data = NONE;
                } else {
                    int payload = (int) length - 1;
                    data = new byte[payload];
                    int at = 0;
                    while (at < payload) {
                        int read = in.read(data, at, payload - at);
                        if (read < 0) return null;
                        if (read == 0) continue;
                        at += read;
                    }
                }
            } finally {
                try { in.close(); } catch (Throwable ignored) { }
            }
            touchDirectoryAsync(file.getParentFile());
            if (generation == epoch && !clearing) memoryPut(key, data, generation, file.getParentFile());
            return unchanged ? NONE : data;
        } catch (Throwable error) {
            return null;
        }
    }

    /** Save an answer; null means "nothing to recolour". Written off the caller's thread. */
    static void put(final String key, final byte[] data) {
        if (clearing) return;
        // Disk entries include one marker byte. Reject oversize data before clone()
        // and queueing; otherwise get() would refuse to read the file we just wrote.
        if (data != null && data.length >= MAX_DISK_ENTRY_BYTES) return;
        final long generation = epoch;
        final byte[] copy = data == null ? null : data.clone();
        File currentFolder = folder;
        File parent = currentFolder == null ? null : entry(currentFolder, key, false).getParentFile();
        memoryPutOwned(key, copy == null ? NONE : copy, generation, parent);
        try {
            writer.execute(new Runnable() {
                @Override public void run() { if (generation == epoch && !clearing) write(key, copy, generation); }
            });
        } catch (Throwable ignored) { }
    }

    /** Returns the retained value and its LRU shelf without exposing mutable bytes. */
    private static MemoryEntry memoryGet(String key) {
        synchronized (MEMORY_LOCK) {
            return memory.get(key);
        }
    }

    private static void memoryPut(String key, byte[] value, long generation, File directory) {
        memoryPut(key, value, generation, directory, true);
    }

    /** Store a private array whose owner will not mutate it, avoiding a second clone. */
    private static void memoryPutOwned(String key, byte[] value, long generation, File directory) {
        memoryPut(key, value, generation, directory, false);
    }

    private static void memoryPut(String key, byte[] value, long generation, File directory, boolean copy) {
        if (generation != epoch || clearing || value == null) return;
        synchronized (MEMORY_LOCK) {
            if (generation != epoch || clearing) return;
            MemoryEntry previous = memory.remove(key);
            if (previous != null) memoryBytes -= memorySize(previous.data);
            int size = memorySize(value);
            if (value != NONE && (value.length > MEMORY_ENTRY_LIMIT || size > MEMORY_LIMIT)) return;
            File shelf = directory != null ? directory : previous == null ? null : previous.directory;
            byte[] held = value == NONE ? NONE : copy ? value.clone() : value;
            memory.put(key, new MemoryEntry(held, shelf));
            memoryBytes += size;
            java.util.Iterator<Map.Entry<String, MemoryEntry>> entries = memory.entrySet().iterator();
            while (memoryBytes > MEMORY_LIMIT && entries.hasNext()) {
                Map.Entry<String, MemoryEntry> eldest = entries.next();
                memoryBytes -= memorySize(eldest.getValue().data);
                entries.remove();
            }
        }
    }

    private static int memorySize(byte[] value) {
        return Math.max(1, value == NONE ? 0 : value.length);
    }

    private static void clearMemory() {
        synchronized (MEMORY_LOCK) {
            memory.clear();
            memoryBytes = 0;
        }
    }

    /** Track every hit cheaply, but persist directory metadata only occasionally off-thread. */
    private static void touchDirectoryAsync(final File directory) {
        if (directory == null) return;
        final long now = System.currentTimeMillis();
        final String path = directory.getAbsolutePath();
        synchronized (TOUCH_LOCK) {
            if (LAST_TOUCH.size() >= 24 && !LAST_TOUCH.containsKey(path)) {
                LAST_TOUCH.clear();
                LAST_STAMP_WRITE.clear();
            }
            LAST_TOUCH.put(path, now);
            Long previousWrite = LAST_STAMP_WRITE.get(path);
            if (previousWrite != null && now - previousWrite < TOUCH_INTERVAL_MS) return;
            LAST_STAMP_WRITE.put(path, now);
        }
        try {
            writer.execute(new Runnable() {
                @Override public void run() {
                    if (clearing || !directory.isDirectory()) return;
                    long usedAt;
                    synchronized (TOUCH_LOCK) {
                        Long known = LAST_TOUCH.get(path);
                        usedAt = known == null ? now : known.longValue();
                    }
                    directory.setLastModified(usedAt);
                }
            });
        } catch (Throwable ignored) { }
    }

    private static long lastUsed(File directory) {
        synchronized (TOUCH_LOCK) {
            Long known = LAST_TOUCH.get(directory.getAbsolutePath());
            return known == null ? directory.lastModified() : known.longValue();
        }
    }

    private static void write(String key, byte[] data, long generation) {
        try {
            File dir = folder();
            if (dir == null) return;
            File file = entry(dir, key, true);
            File temp = new File(dir, file.getName() + ".tmp");
            FileOutputStream out = new FileOutputStream(temp);
            try {
                out.write(data == null ? 0 : 1);
                if (data != null) out.write(data);
            } finally {
                out.close();
            }
            if (!temp.renameTo(file)) {
                file.delete();
                if (!temp.renameTo(file)) temp.delete();
            }
            if (generation == epoch && !clearing) {
                memoryPutOwned(key, data == null ? NONE : data, generation, file.getParentFile());
                // Creating/replacing an entry also makes its color shelf recent for LRU.
                touchDirectoryAsync(file.getParentFile());
            }
            if (key.startsWith("lottie|") || key.startsWith("png|")) trimLikes(dir);
            if (++writes % 20 == 0) trim(dir);
        } catch (Throwable ignored) { }
    }

    /** Forget everything: the art it was made from has changed. */
    static void clear() {
        final long generation = ++epoch;
        final File dir = folder;
        clearing = true;
        clearMemory();
        synchronized (TOUCH_LOCK) { LAST_TOUCH.clear(); LAST_STAMP_WRITE.clear(); }
        if (dir == null) {
            clearing = false;
            return;
        }
        try {
            writer.execute(new Runnable() {
                @Override public void run() {
                    try { wipe(dir); writeText(new File(dir, "stamp"), stamp); }
                    finally { if (generation == epoch) clearing = false; }
                }
            });
        } catch (Throwable ignored) {
            if (generation == epoch) clearing = false;
        }
    }

    private static void trim(File dir) {
        long total = cacheBytes(dir, dir);
        if (total <= LIMIT) return;
        long target = LIMIT * 3 / 4;
        List<File> candidates = new ArrayList<File>();
        File[] root = dir.listFiles();
        if (root == null) return;
        for (File item : root) {
            if (item.isFile()) {
                if (!item.getName().equals("stamp")) candidates.add(item);
            } else if (item.isDirectory() && item.getName().equals("like")) {
                File[] shelves = item.listFiles(File::isDirectory);
                if (shelves != null) java.util.Collections.addAll(candidates, shelves);
            } else if (item.isDirectory()) {
                candidates.add(item);
            }
        }
        // Compare files and nested color shelves in the same LRU order. This avoids
        // evicting a recently used color merely because the bytes were in a subfolder.
        java.util.Collections.sort(candidates, new java.util.Comparator<File>() {
            @Override public int compare(File a, File b) {
                long aa = a.isDirectory() ? lastUsed(a) : a.lastModified();
                long bb = b.isDirectory() ? lastUsed(b) : b.lastModified();
                return Long.compare(aa, bb);
            }
        });
        for (File item : candidates) {
            if (total <= target) break;
            long size = item.isDirectory() ? treeBytes(item) : item.length();
            if (size == 0) continue;
            if (item.isDirectory()) {
                if (item.getParentFile() != null
                        && "like".equals(item.getParentFile().getName())) {
                    forgetMemoryForShelf(dir, item);
                }
                wipeTree(item);
            } else if (!item.delete()) {
                continue;
            }
            total -= size;
        }
    }

    private static long cacheBytes(File current, File root) {
        File[] children = current.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) {
            if (current.equals(root) && child.getName().equals("stamp")) continue;
            total += child.isFile() ? child.length() : child.isDirectory() ? cacheBytes(child, root) : 0L;
        }
        return total;
    }

    private static long treeBytes(File directory) {
        File[] children = directory.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) {
            total += child.isFile() ? child.length() : child.isDirectory() ? treeBytes(child) : 0L;
        }
        return total;
    }

    private static void trimLikes(File dir) {
        File[] colours = new File(dir, "like").listFiles(File::isDirectory);
        if (colours == null || colours.length <= LIKE_COLOURS) return;
        java.util.Arrays.sort(colours, (a, b) -> Long.compare(lastUsed(a), lastUsed(b)));
        for (int i = 0; i < colours.length - LIKE_COLOURS; i++) {
            forgetMemoryForShelf(dir, colours[i]);
            wipeTree(colours[i]);
        }
    }

    private static void forgetMemoryForShelf(File dir, File shelf) {
        String path = shelf.getAbsolutePath();
        synchronized (MEMORY_LOCK) {
            java.util.Iterator<Map.Entry<String, MemoryEntry>> entries = memory.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<String, MemoryEntry> item = entries.next();
                File heldShelf = item.getValue().directory;
                if (heldShelf != null && path.equals(heldShelf.getAbsolutePath())) {
                    memoryBytes -= memorySize(item.getValue().data);
                    entries.remove();
                }
            }
        }
        synchronized (TOUCH_LOCK) {
            LAST_TOUCH.remove(path);
            LAST_STAMP_WRITE.remove(path);
        }
    }

    private static void wipe(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) wipeTree(file);
    }

    private static void wipeTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) wipeTree(child);
        }
        file.delete();
    }

    private static String readText(File file) {
        try {
            if (!file.isFile()) return null;
            FileInputStream in = new FileInputStream(file);
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] block = new byte[256];
                for (int read; (read = in.read(block)) != -1;) bytes.write(block, 0, read);
                return new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } catch (Throwable error) {
            return null;
        }
    }

    private static void writeText(File file, String text) {
        try {
            if (text == null) return;
            FileOutputStream out = new FileOutputStream(file);
            try { out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            finally { out.close(); }
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------ helpers

    private static byte[] digest(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (Throwable error) {
            return new byte[] {(byte) java.util.Arrays.hashCode(data)};
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 15, 16))
                .append(Character.forDigit(b & 15, 16));
        return out.toString();
    }

    static String hash(byte[] data) {
        return hex(digest(data));
    }

    static String hash(int[] pixels, int width, int height) {
        // Hash the same big-endian byte sequence as the old contiguous buffer,
        // but keep temporary memory bounded instead of allocating 4 bytes per pixel.
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            ByteBuffer header = ByteBuffer.allocate(8);
            header.putInt(width).putInt(height);
            sha1.update(header.array());

            ByteBuffer block = ByteBuffer.allocate(16 * 1024);
            for (int pixel : pixels) {
                if (block.remaining() < 4) {
                    sha1.update(block.array(), 0, block.position());
                    block.clear();
                }
                block.putInt(pixel);
            }
            if (block.position() > 0) sha1.update(block.array(), 0, block.position());
            return hex(sha1.digest());
        } catch (Throwable error) {
            // Preserve the old digest() fallback if SHA-1 is unavailable, without
            // recreating the large byte array that this method is designed to avoid.
            int hash = 1;
            hash = hashByte(hash, width >>> 24);
            hash = hashByte(hash, width >>> 16);
            hash = hashByte(hash, width >>> 8);
            hash = hashByte(hash, width);
            hash = hashByte(hash, height >>> 24);
            hash = hashByte(hash, height >>> 16);
            hash = hashByte(hash, height >>> 8);
            hash = hashByte(hash, height);
            for (int pixel : pixels) {
                hash = hashByte(hash, pixel >>> 24);
                hash = hashByte(hash, pixel >>> 16);
                hash = hashByte(hash, pixel >>> 8);
                hash = hashByte(hash, pixel);
            }
            return hex(new byte[] {(byte) hash});
        }
    }

    private static int hashByte(int hash, int value) {
        return 31 * hash + (byte) value;
    }

    static byte[] png(Bitmap bitmap) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            return bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) ? out.toByteArray() : null;
        } catch (Throwable error) {
            return null;
        }
    }

    static Bitmap decode(byte[] png) {
        try {
            return BitmapFactory.decodeByteArray(png, 0, png.length);
        } catch (Throwable error) {
            return null;
        }
    }
}
