package cat.kardinebi;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
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
    private static final boolean COMMENT_BACKGROUNDS = false;
    private static final String KEY_TYPE = "profile_shapes_type";
    private static final String KEY_COLOUR = "profile_shapes_colour";
    private static final String KEY_SIZE = "profile_shapes_size";
    private static final String KEY_STROKE = "profile_shapes_stroke";
    private static final float DEFAULT_STROKE_DP = 0.75f;
    private static final Map<View, Drawable> BASES = new WeakHashMap<>();
    private static final Map<View, ShapeLayer> LAYERS = new WeakHashMap<>();
    private static final Map<View, Boolean> COMMENT_ROOTS = new WeakHashMap<>();
    private static final Map<View, Boolean> PREVIEWS = new WeakHashMap<>();
    private ProfileShapes() { }

    /**
     * Called from native comment-row binding. Find only the enclosing RecyclerView
     * that owns the list, then layer the low-alpha shapes behind its children.
     * Never decorate a recycled row, author avatar, composer or reaction control.
     */
    static void installCommentBackground(View cell) {
        if (cell == null || COMMENT_BACKGROUNDS || !enabled() || !Motion.enabled(cell.getContext())) return;
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

    /** -1 means the original mixed M3 composition; otherwise use one selected shape. */
    static int shapeType(Context context) {
        try { return AccountAppearance.prefs(context).getInt(KEY_TYPE, -1); }
        catch (Throwable ignored) { return -1; }
    }

    static String shapeName(Context context) {
        int type = shapeType(context);
        return type < 0 || type >= AvatarShape.ALL.length
                ? "Разные фигуры Material 3" : AvatarShape.nameOf(AvatarShape.ALL[type]);
    }

    static void setShapeType(Context context, int type) {
        int safe = type < -1 || type >= AvatarShape.ALL.length ? -1 : type;
        AccountAppearance.prefs(context).edit().putInt(KEY_TYPE, safe).apply();
        changed();
    }

    /** Zero follows the current Mint accent palette; any other value is a custom ARGB colour. */
    static int shapeColour(Context context) {
        try { return AccountAppearance.prefs(context).getInt(KEY_COLOUR, 0); }
        catch (Throwable ignored) { return 0; }
    }

    static void setShapeColour(Context context, int colour) {
        AccountAppearance.prefs(context).edit().putInt(KEY_COLOUR, colour).apply();
        changed();
    }

    static int shapeSize(Context context) {
        try { return Math.max(40, Math.min(180, AccountAppearance.prefs(context).getInt(KEY_SIZE, 100))); }
        catch (Throwable ignored) { return 100; }
    }

    static void setShapeSize(Context context, int percent) {
        AccountAppearance.prefs(context).edit().putInt(KEY_SIZE, Math.max(40, Math.min(180, percent))).apply();
        changed();
    }

    /** Stroke width in tenths of a dp, from 0 (off) to 4.0 dp. */
    static int shapeStroke(Context context) {
        try { return Math.max(0, Math.min(40, AccountAppearance.prefs(context).getInt(KEY_STROKE, 8))); }
        catch (Throwable ignored) { return 8; }
    }

    static void setShapeStroke(Context context, int tenthsDp) {
        AccountAppearance.prefs(context).edit().putInt(KEY_STROKE, Math.max(0, Math.min(40, tenthsDp))).apply();
        changed();
    }

    static void resetCustomisation(Context context) {
        AccountAppearance.prefs(context).edit().remove(KEY_TYPE).remove(KEY_COLOUR)
                .remove(KEY_SIZE).remove(KEY_STROKE).apply();
        changed();
    }

    private static void changed() {
        reload();
        ArrayList<View> previews;
        synchronized (PREVIEWS) { previews = new ArrayList<>(PREVIEWS.keySet()); }
        for (View view : previews) if (view != null) view.postInvalidate();
    }

    /** The settings page uses the same geometry as a profile background, with a surface behind it. */
    static View preview(Context context) {
        View view = new View(context) {
            private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                if (width <= 0) width = dp(getContext(), 300);
                setMeasuredDimension(width, dp(getContext(), 176));
            }

            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float d = getResources().getDisplayMetrics().density;
                surface.setColor(Skin.material3(getContext()).card);
                RectF panel = new RectF(dp(getContext(), 4), dp(getContext(), 4),
                        getWidth() - dp(getContext(), 4), getHeight() - dp(getContext(), 4));
                canvas.drawRoundRect(panel, dp(getContext(), 18), dp(getContext(), 18), surface);
                drawGeometry(canvas, new Rect(dp(getContext(), 8), dp(getContext(), 8),
                        getWidth() - dp(getContext(), 8), getHeight() - dp(getContext(), 8)),
                        getContext(), fill, outline);
                surface.setColor(Skin.material3(getContext()).muted());
                surface.setTextSize(dp(getContext(), 11));
                canvas.drawText("Предпросмотр фона профиля", dp(getContext(), 14),
                        getHeight() - dp(getContext(), 12), surface);
            }
        };
        synchronized (PREVIEWS) { PREVIEWS.put(view, Boolean.TRUE); }
        return view;
    }

    private static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void drawGeometry(Canvas canvas, Rect bounds, Context context,
                                     Paint fill, Paint outline) {
        if (bounds == null || bounds.isEmpty()) return;
        float d = context.getResources().getDisplayMetrics().density;
        float w = bounds.width(), h = bounds.height();
        float rotation = 0f;
        int[] kinds = {4, 6, 8, 11, 12};
        float[][] points = {{.13f,.18f,.12f},{.83f,.19f,.16f},{.73f,.80f,.14f},{.19f,.78f,.18f},{.52f,.50f,.10f}};
        int secondary = SecondaryAccent.colour();
        int primary = Accent.colour();
        int selected = shapeType(context);
        int custom = shapeColour(context);
        float sizeScale = shapeSize(context) / 100f;
        float strokeDp = shapeStroke(context) / 10f;
        fill.setStyle(Paint.Style.FILL);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeCap(Paint.Cap.ROUND);
        outline.setStrokeJoin(Paint.Join.ROUND);
        outline.setStrokeWidth(strokeDp * d);
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        for (int i=0; i<points.length; i++) {
            float x = points[i][0] * w, y = points[i][1] * h;
            float r = Math.max(8*d, points[i][2] * Math.min(w,h) * sizeScale);
            canvas.save();
            canvas.translate(x,y);
            canvas.rotate(rotation + i*31f);
            canvas.scale(r,r);
            int kind = selected >= 0 && selected < AvatarShape.ALL.length
                    ? selected : Math.min(AvatarShape.ALL.length - 1, kinds[i]);
            Path path = AvatarShape.unit(kind);
            int colour = custom != 0 ? custom : (i%2==0 ? secondary : primary);
            fill.setColor((colour & 0x00FFFFFF) | 0x17000000);
            outline.setColor((colour & 0x00FFFFFF) | 0x30000000);
            canvas.drawPath(path, fill);
            if (strokeDp > 0f) canvas.drawPath(path, outline);
            canvas.restore();
        }
        canvas.restore();
    }

    static void reload() {
        ArrayList<View> views;
        synchronized (BASES) { views = new ArrayList<>(BASES.keySet()); }
        for (View view : views) if (view != null) view.post(() -> install(view));
        ArrayList<View> previews;
        synchronized (PREVIEWS) { previews = new ArrayList<>(PREVIEWS.keySet()); }
        for (View view : previews) if (view != null) view.postInvalidate();
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
            Rect b = getBounds();
            drawGeometry(canvas, b, owner.getContext(), fill, outline);
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
