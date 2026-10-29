package cat.kardinebi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewParent;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Static Material 3 Expressive geometry behind profile content. Drawn once; never animated. */
public final class ProfileShapes {
    private static final String KEY = "profile_shapes_background";
    private static final Map<View, Drawable> BASES = new WeakHashMap<>();
    private static final Map<View, ShapeLayer> LAYERS = new WeakHashMap<>();
    private static final Map<View, Boolean> COMMENT_ROOTS = new WeakHashMap<>();
    private ProfileShapes() { }

    /**
     * Called from native comment-row binding. Find only the enclosing RecyclerView
     * that owns the list, then layer the low-alpha shapes behind its children.
     * Never decorate a recycled row, author avatar, composer or reaction control.
     */
    static void installCommentBackground(View cell) {
        if (cell == null || !enabled() || !Motion.enabled(cell.getContext())) return;
        android.util.DisplayMetrics metrics = cell.getResources().getDisplayMetrics();
        int minWidth = Math.max(240, (int) (metrics.widthPixels * 0.68f));
        int minHeight = Math.max(220, (int) (220f * metrics.density));
        ViewParent parent = cell.getParent();
        for (int depth = 0; depth < 12 && parent instanceof View; depth++) {
            View candidate = (View) parent;
            String type = candidate.getClass().getName();
            if (type.contains("RecyclerView") && candidate.getWidth() >= minWidth
                    && candidate.getHeight() >= minHeight) {
                synchronized (COMMENT_ROOTS) {
                    if (COMMENT_ROOTS.containsKey(candidate)) return;
                    COMMENT_ROOTS.put(candidate, Boolean.TRUE);
                }
                candidate.post(() -> {
                    if (candidate.isAttachedToWindow() && enabled() && Motion.enabled(candidate.getContext())) {
                        install(candidate);
                    } else {
                        synchronized (COMMENT_ROOTS) { COMMENT_ROOTS.remove(candidate); }
                    }
                });
                return;
            }
            parent = candidate.getParent();
        }
    }

    public static boolean enabled() {
        Context c = Kardinebi.context();
        if (c == null) return true;
        try { return AccountAppearance.prefs(c).getBoolean(KEY, true); }
        catch (Throwable ignored) { return true; }
    }

    public static void setEnabled(Context context, boolean on) {
        try { AccountAppearance.prefs(context).edit().putBoolean(KEY, on).apply(); }
        catch (Throwable ignored) { }
        reload();
    }

    static void install(View view) {
        if (view == null) return;
        synchronized (BASES) {
            Drawable base = BASES.get(view);
            ShapeLayer layer = LAYERS.get(view);
            if (base == null && layer == null) {
                base = view.getBackground();
                BASES.put(view, base == null ? new ColorDrawable(Color.TRANSPARENT) : base);
            }
            if (!enabled()) {
                view.setBackground(BASES.get(view));
                return;
            }
            if (layer == null) { layer = new ShapeLayer(view); LAYERS.put(view, layer); }
            Drawable original = BASES.get(view);
            view.setBackground(new LayerDrawable(new Drawable[]{original, layer}));
            layer.start();
        }
    }

    static void reload() {
        ArrayList<View> views;
        synchronized (BASES) { views = new ArrayList<>(BASES.keySet()); }
        for (View view : views) if (view != null) view.post(() -> install(view));
    }

    private static final class ShapeLayer extends Drawable implements Runnable {
        private final View owner;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long started = android.os.SystemClock.uptimeMillis();
        private boolean running;
        ShapeLayer(View owner) {
            this.owner = owner;
            fill.setStyle(Paint.Style.FILL);
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeCap(Paint.Cap.ROUND);
            outline.setStrokeJoin(Paint.Join.ROUND);
            outline.setStrokeWidth(Math.max(0.5f, owner.getResources().getDisplayMetrics().density * 0.75f));
        }
        /** Static by design: no frame loop, so nothing redraws while the profile is open. */
        void start() { running = false; }
        @Override public void run() { running = false; }
        @Override public void draw(Canvas canvas) {
            if (!enabled() || !Motion.enabled(owner.getContext())) return;
            Rect b = getBounds(); if (b.isEmpty()) return;
            float d = owner.getResources().getDisplayMetrics().density;
            float w = b.width(), h = b.height();
            float rotation = 0f; // no rotation: the composition is fixed
            int[] kinds = {4, 6, 8, 11, 12};
            float[][] points = {{.13f,.18f,.12f},{.83f,.19f,.16f},{.73f,.80f,.14f},{.19f,.78f,.18f},{.52f,.50f,.10f}};
            int secondary = SecondaryAccent.colour();
            int primary = Accent.colour();
            canvas.save(); canvas.translate(b.left, b.top);
            for (int i=0; i<points.length; i++) {
                float x = points[i][0] * w, y = points[i][1] * h;
                float r = Math.max(8*d, points[i][2] * Math.min(w,h));
                canvas.save(); canvas.translate(x,y); canvas.rotate(rotation + i*31f);
                canvas.scale(r,r);
                Path path = AvatarShape.unit(Math.min(AvatarShape.ALL.length-1, kinds[i]));
                int colour = (i%2==0 ? secondary : primary);
                fill.setColor((colour & 0x00FFFFFF) | 0x17000000);
                outline.setColor((colour & 0x00FFFFFF) | 0x30000000);
                canvas.drawPath(path, fill); canvas.drawPath(path, outline);
                canvas.restore();
            }
            canvas.restore();
        }
        @Override protected void onBoundsChange(Rect bounds) { super.onBoundsChange(bounds); }
        @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); outline.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { fill.setColorFilter(filter); outline.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public boolean setVisible(boolean visible, boolean restart) {
            boolean changed = super.setVisible(visible, restart);
            running = false; // never schedules a frame loop
            return changed;
        }
    }
}
