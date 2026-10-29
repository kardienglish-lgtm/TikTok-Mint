package cat.kardinebi;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the adjacent photo page prepared on devices with enough headroom.
 * TikTok owns the photo pager and the image loader; this helper deliberately
 * changes only the public ViewPager offscreen-page limit, without replacing
 * their request/cache pipeline or holding image bitmaps itself.
 */
public final class PhotoSwipeOptimizer {
    private static final int DESIRED_OFFSCREEN_PAGES = 2;
    private static final int MAX_VIEW_NODES = 256;
    private static final long MIN_AVAILABLE_BYTES = 256L * 1024L * 1024L;
    private static final AtomicBoolean REPORTED = new AtomicBoolean(false);

    private PhotoSwipeOptimizer() { }

    /** Called once from the photo ViewPager component's onViewCreated hook. */
    public static void bind(Object component, View root) {
        if (root == null) return;
        try {
            root.postDelayed(new Runnable() {
                @Override public void run() {
                    try {
                        optimize(root);
                    } catch (Throwable ignored) {
                        // A TikTok update may change its private view hierarchy.
                        // A missed optimization must never crash photo playback.
                    }
                }
            }, 100L);
        } catch (Throwable ignored) {
            // The native screen remains untouched if scheduling is unavailable.
        }
    }

    private static void optimize(View root) {
        if (!root.isAttachedToWindow()) return;
        int wanted = desiredLimit(root.getContext());
        if (wanted <= 1) {
            reportOnce("photo pager: keeping default page limit on a low-memory device");
            return;
        }

        View pager = findPager(root);
        if (pager == null) return;
        Object adapter = invoke(pager, "getAdapter");
        if (adapter == null || adapterCount(adapter) < 2) return;

        Object currentValue = invoke(pager, "getOffscreenPageLimit");
        int current = currentValue instanceof Number ? ((Number) currentValue).intValue() : 1;
        if (current >= wanted) return;
        try {
            Method setter = pager.getClass().getMethod("setOffscreenPageLimit", int.class);
            setter.invoke(pager, wanted);
            reportOnce("photo pager: adjacent-page preloading enabled (limit=" + wanted + ")");
        } catch (Throwable ignored) {
            // Reflection is only used to remain compatible with TikTok's
            // obfuscated ViewPager subclass. Unsupported implementations no-op.
        }
    }

    /** Uses the app heap class and current system headroom to avoid pinning more pages on low RAM. */
    private static int desiredLimit(Context context) {
        try {
            Object service = context.getSystemService(Context.ACTIVITY_SERVICE);
            if (!(service instanceof ActivityManager)) return 1;
            ActivityManager manager = (ActivityManager) service;
            if (Build.VERSION.SDK_INT >= 19 && manager.isLowRamDevice()) return 1;
            if (manager.getMemoryClass() < 192) return 1;
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            return info.availMem >= MIN_AVAILABLE_BYTES ? DESIRED_OFFSCREEN_PAGES : 1;
        } catch (Throwable ignored) {
            return 1;
        }
    }

    private static View findPager(View root) {
        ArrayDeque<View> pending = new ArrayDeque<View>();
        pending.push(root);
        int visited = 0;
        while (!pending.isEmpty() && visited < MAX_VIEW_NODES) {
            View candidate = pending.pop();
            visited++;
            if (isPagerClass(candidate.getClass()) && hasPublicMethod(candidate.getClass(), "setOffscreenPageLimit")) {
                Object adapter = invoke(candidate, "getAdapter");
                if (adapter != null && adapterCount(adapter) > 1) return candidate;
            }
            if (candidate instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) candidate;
                for (int i = group.getChildCount() - 1; i >= 0; i--) {
                    View child = group.getChildAt(i);
                    if (child != null) pending.push(child);
                }
            }
        }
        return null;
    }

    private static boolean isPagerClass(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            String name = current.getName();
            if ("androidx.viewpager.widget.ViewPager".equals(name)
                    || "androidx.viewpager2.widget.ViewPager2".equals(name)) return true;
        }
        return false;
    }

    private static boolean hasPublicMethod(Class<?> type, String name) {
        try {
            type.getMethod(name, int.class);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int adapterCount(Object adapter) {
        Object count = invoke(adapter, "getCount");
        if (!(count instanceof Number)) count = invoke(adapter, "getItemCount");
        return count instanceof Number ? ((Number) count).intValue() : 0;
    }

    private static Object invoke(Object target, String name) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void reportOnce(String message) {
        if (REPORTED.compareAndSet(false, true)) Diary.detail(message);
    }
}
