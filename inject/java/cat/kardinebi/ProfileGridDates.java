package cat.kardinebi;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Guarded date badge for profile video tiles. It is deliberately fail-closed:
 * no reliable Aweme model means no guessed date. Current TikTok builds may keep
 * the grid model in a private adapter rather than the view tree, so this remains
 * an experimental fallback until a version-specific bind point is confirmed.
 */
public final class ProfileGridDates {
    private static final int BADGE_TAG = 0x7e0d0341;
    private static final int WATCH_TAG = 0x7e0d0343;
    private static final WeakHashMap<View, TextView> badges = new WeakHashMap<>();
    private static final WeakHashMap<View, Long> probed = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> profileRoots = new WeakHashMap<>();
    private ProfileGridDates() {}

    /** Mark a known profile-tab view from the profile component lifecycle hook. */
    public static void markProfile(View root) {
        if (root == null) return;
        synchronized (profileRoots) { profileRoots.put(root, Boolean.TRUE); }
    }

    public static void watch(View cell) {
        if (!(cell instanceof FrameLayout) || cell.getTag(WATCH_TAG) != null) return;
        cell.setTag(WATCH_TAG, Boolean.TRUE);
        cell.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> refresh(v));
        cell.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { probe(v); }
            @Override public void onViewDetachedFromWindow(View v) { remove(v); }
        });
        probe(cell);
    }

    private static void probe(View cell) {
        cell.postDelayed(() -> refresh(cell), 140L);
        cell.postDelayed(() -> refresh(cell), 480L);
    }

    private static void refresh(View cell) {
        if (cell == null) return;
        if (!Dates.isEnabled() || !Corners.holdsCover(cell) || !inProfile(cell)) {
            remove(cell);
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        synchronized (probed) {
            Long previous = probed.get(cell);
            if (previous != null && now - previous.longValue() < 180L) return;
            probed.put(cell, Long.valueOf(now));
        }
        Object post = findPost(cell);
        long created = NativeRead.number(NativeRead.get(post, "getCreateTime"));
        String date = Dates.label(created, System.currentTimeMillis() / 1000L);
        if (date == null || !(cell instanceof FrameLayout)) { remove(cell); return; }
        show((FrameLayout) cell, date);
    }

    private static boolean inProfile(View cell) {
        Object parent = cell;
        for (int i = 0; i < 10 && parent instanceof View; i++) {
            View ancestor = (View) parent;
            synchronized (profileRoots) {
                if (profileRoots.containsKey(ancestor)) return true;
            }
            String name = ancestor.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("profile") || name.contains("userwork") || name.contains("workgrid")) return true;
            try {
                int id = ancestor.getId();
                if (id != View.NO_ID) {
                    String resource = ancestor.getResources().getResourceEntryName(id).toLowerCase(java.util.Locale.ROOT);
                    if (resource.contains("profile_aweme") || resource.contains("profile_video")
                            || resource.contains("aweme_pager") || resource.contains("profile_tab")) return true;
                }
            } catch (Throwable ignored) { }
            parent = ancestor.getParent();
        }
        return false;
    }

    private static Object findPost(View cell) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        ArrayDeque<Object> queue = new ArrayDeque<>();
        queue.add(cell);
        Object current = cell;
        for (int level = 0; level < 7 && current instanceof View; level++) {
            View view = (View) current;
            queue.addLast(view);
            addCandidate(queue, view.getTag());
            addCandidate(queue, view.getTag(DateInfo.CELL_TAG));
            current = view.getParent();
        }
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 140) {
            Object object = queue.removeFirst();
            if (object == null || !seen.add(object)) continue;
            if (hasPostShape(object)) return object;
            if (object instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) object;
                for (int i = 0; i < Math.min(group.getChildCount(), 60); i++) {
                    View child = group.getChildAt(i);
                    addCandidate(queue, child == null ? null : child.getTag());
                    if (child instanceof ViewGroup) queue.addLast(child);
                }
            }
            Class<?> type = object.getClass();
            if (type.getName().startsWith("java.") || type.getName().startsWith("android.")) continue;
            for (int depth = 0; type != null && depth < 3; depth++, type = type.getSuperclass()) {
                Field[] fields;
                try { fields = type.getDeclaredFields(); } catch (Throwable ignored) { continue; }
                for (int i = 0; i < Math.min(fields.length, 24); i++) {
                    Field field = fields[i];
                    if (field.getType().isPrimitive() || field.getType().isEnum()) continue;
                    try { field.setAccessible(true); addCandidate(queue, field.get(object)); }
                    catch (Throwable ignored) { }
                }
            }
        }
        return null;
    }

    private static void addCandidate(ArrayDeque<Object> queue, Object candidate) {
        if (candidate == null || queue.size() >= 220) return;
        String name = candidate.getClass().getName();
        if (name.startsWith("java.lang.") || name.startsWith("java.util.")) return;
        queue.addLast(candidate);
    }

    private static boolean hasPostShape(Object object) {
        if (object == null) return false;
        String name = object.getClass().getName();
        if (!(name.endsWith(".Aweme") || name.endsWith("Aweme") || name.contains("VideoItemParams"))) return false;
        return NativeRead.get(object, "getCreateTime") != null && !NativeRead.aid(object).isEmpty();
    }

    private static void show(FrameLayout cell, String date) {
        TextView badge;
        synchronized (badges) { badge = badges.get(cell); }
        if (badge == null || badge.getParent() != cell) {
            badge = new TextView(cell.getContext());
            badge.setTag(BADGE_TAG, Boolean.TRUE);
            badge.setTextSize(10f);
            badge.setTextColor(0xFFFFFFFF);
            badge.setSingleLine(true);
            badge.setPadding(dp(cell, 4), dp(cell, 1), dp(cell, 4), dp(cell, 1));
            GradientDrawable background = new GradientDrawable();
            background.setColor(0x77000000);
            background.setCornerRadius(dp(cell, MintGeometry.BADGE_DP));
            badge.setBackground(background);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.LEFT);
            params.leftMargin = dp(cell, 4);
            params.bottomMargin = dp(cell, 19);
            cell.addView(badge, params);
            synchronized (badges) { badges.put(cell, badge); }
            Fonts.apply(badge);
        }
        if (!date.contentEquals(badge.getText())) badge.setText(date);
        badge.setVisibility(View.VISIBLE);
        badge.bringToFront();
    }

    private static int dp(View view, float value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void remove(View cell) {
        TextView badge;
        synchronized (badges) { badge = badges.remove(cell); }
        synchronized (probed) { probed.remove(cell); }
        if (badge != null && badge.getParent() instanceof ViewGroup) {
            try { ((ViewGroup) badge.getParent()).removeView(badge); } catch (Throwable ignored) { }
        }
    }
}
