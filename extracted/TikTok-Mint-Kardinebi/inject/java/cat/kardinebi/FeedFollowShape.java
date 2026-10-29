package cat.kardinebi;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Material 3 expressive treatment for the small follow affordance under an
 * avatar in the recommendation feed. It is intentionally scoped to
 * FeedAvatarDefaultAssem, not all controls labelled Follow throughout TikTok.
 *
 * The original native click listener stays in place. A non-consuming touch
 * observer starts only the visual response; the shape itself never performs
 * a follow request.
 */
public final class FeedFollowShape {
    private static final int ROOT_WATCH = 0x7e0c04a1;
    private static final int STATE_TAG = 0x7e0c04a2;
    private static final int DRAWABLE_TAG = 0x7e0c04a3;
    private static final int[] SHAPES = {
            Expressive.CLOVER4, Expressive.COOKIE9, Expressive.FLOWER6,
            Expressive.BURST12, Expressive.PENTAGON, Expressive.ARCH3, Expressive.COOKIE7
    };
    private static final Map<View, State> STATES = new WeakHashMap<>();
    private static volatile boolean warnedMissingIcon;
    private static volatile boolean reportedAttachment;

    private FeedFollowShape() { }

    public static void bind(Object component) { bind(component, null); }

    /** The modern hook supplies the exact view passed to onViewCreated. */
    public static void bind(Object component, View createdView) {
        if (component == null || !Md3.followShapeEnabled()) return;
        String type = component.getClass().getName();
        if (!type.endsWith("FeedAvatarDefaultAssem")) return;

        // In TikTok 47.4.42 the actual icon is the TuxIconView stored in LLJLL;
        // the other icon views in this assembler are not the follow affordance.
        // Resolve that native field first, then use view traversal only as a
        // compatibility fallback. Public getContentView() is not exposed on all
        // assembler variants, which previously caused the hook to exit silently.
        View nativeIcon = viewField(component, "LLJLL");
        View nativeTarget = viewField(component, "LLJLILLLLZIIL");
        ViewGroup root = resolveRoot(component, nativeIcon != null ? nativeIcon : nativeTarget, createdView);
        if (root == null) return;

        String aid = "";
        try { aid = NativeRead.aid(NativeRead.get(NativeRead.item(component), "getAweme")); }
        catch (Throwable ignored) { }
        final String itemId = aid == null ? "" : aid;
        final View resolvedIcon = nativeIcon != null && isInside(root, nativeIcon) ? nativeIcon : null;
        final View resolvedTarget = nativeTarget != null && isInside(root, nativeTarget) ? nativeTarget : null;
        Runnable scan = () -> {
            if (!root.isAttachedToWindow() || root.getWidth() <= 0 || root.getHeight() <= 0) return;
            View currentIcon = viewField(component, "LLJLL");
            View currentTarget = viewField(component, "LLJLILLLLZIIL");
            if (currentIcon == null) currentIcon = resolvedIcon;
            if (currentTarget == null) currentTarget = resolvedTarget;
            styleIn(root, itemId,
                    currentIcon != null && isInside(root, currentIcon) ? currentIcon : null,
                    currentTarget != null && isInside(root, currentTarget) ? currentTarget : null);
        };
        root.post(scan);
        root.postDelayed(scan, 140L);
        root.postDelayed(scan, 360L);
        if (root.getTag(ROOT_WATCH) == null) {
            root.setTag(ROOT_WATCH, Boolean.TRUE);
            root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
                if (v.isAttachedToWindow()) v.postDelayed(() -> {
                    if (v instanceof ViewGroup) {
                        View currentIcon = viewField(component, "LLJLL");
                        View currentTarget = viewField(component, "LLJLILLLLZIIL");
                        styleIn((ViewGroup) v, "",
                                currentIcon != null && isInside((ViewGroup) v, currentIcon) ? currentIcon : resolvedIcon,
                                currentTarget != null && isInside((ViewGroup) v, currentTarget) ? currentTarget : resolvedTarget);
                    }
                }, 80L);
            });
        }
    }

    /** Read a known native view field even when TikTok marks it private. */
    private static View viewField(Object instance, String name) {
        if (instance == null) return null;
        for (Class<?> owner = instance.getClass(); owner != null; owner = owner.getSuperclass()) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value instanceof View) return (View) value;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static ViewGroup resolveRoot(Object component, View nativeIcon, View createdView) {
        View[] candidates = {
                viewField(component, "LLJJJJ"), viewField(component, "LLJJJ"), createdView,
                asView(NativeRead.get(component, "getContentView")),
                asView(NativeRead.get(component, "getView")),
                asView(NativeRead.get(component, "getRootView"))
        };
        if (nativeIcon != null) {
            for (View candidate : candidates) {
                if (candidate instanceof ViewGroup && isInside((ViewGroup) candidate, nativeIcon))
                    return (ViewGroup) candidate;
            }
            ViewParent parent = nativeIcon.getParent();
            ViewGroup nearest = parent instanceof ViewGroup ? (ViewGroup) parent : null;
            if (nearest != null) return nearest;
        }
        for (View candidate : candidates) if (candidate instanceof ViewGroup) return (ViewGroup) candidate;
        return null;
    }

    private static View asView(Object value) { return value instanceof View ? (View) value : null; }

    private static boolean isInside(ViewGroup root, View child) {
        View current = child;
        for (int depth = 0; current != null && depth < 32; depth++) {
            if (current == root) return true;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static void styleIn(ViewGroup root, String aid) { styleIn(root, aid, null, null); }
    private static void styleIn(ViewGroup root, String aid, View nativeIcon) { styleIn(root, aid, nativeIcon, null); }

    private static void styleIn(ViewGroup root, String aid, View nativeIcon, View nativeTarget) {
        View icon = nativeIcon != null && isInside(root, nativeIcon) ? nativeIcon : findFollowIcon(root);
        View button = isNativeHitTarget(nativeTarget, icon, root) ? nativeTarget
                : icon == null ? null : clickTarget(icon, root);
        if (button == null) {
            // Tux icon views can be unnamed and their native click target may be
            // a non-square wrapper. Fall back only within the verified avatar
            // assembler, rather than silently styling an unrelated screen control.
            button = findNativeClickTarget(root);
            if (button != null && icon == null) icon = button;
        }
        if (button == null) {
            if (!warnedMissingIcon) synchronized (FeedFollowShape.class) {
                if (!warnedMissingIcon) {
                    warnedMissingIcon = true;
                    Diary.note("feed follow shape: native LLJLL icon had no clickable target");
                }
            }
            return;
        }
        if (icon == null) icon = button;
        State old;
        synchronized (STATES) { old = STATES.get(button); }
        if (old != null) {
            if (old.aid.isEmpty() || aid.isEmpty() || old.aid.equals(aid)) {
                old.icon = new WeakReference<>(icon);
                return;
            }
            old.restoreForReuse();
            synchronized (STATES) { STATES.remove(button); }
        }

        State state = new State(button, icon, aid);
        synchronized (STATES) { STATES.put(button, state); }
        button.setTag(STATE_TAG, state);
        FollowDrawable drawable = new FollowDrawable(button.getContext());
        state.drawable = drawable;
        button.setTag(DRAWABLE_TAG, drawable);
        try {
            button.setBackground(ripple(button.getContext(), drawable));
            if (icon instanceof ImageView) {
                if (button != icon) icon.setAlpha(0f);
                else ((ImageView) icon).setImageDrawable(null);
            } else if (icon instanceof TextView) {
                ((TextView) icon).setText("");
            }
            if (button != icon) icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            installTouchObserver(state);
            if (!reportedAttachment) synchronized (FeedFollowShape.class) {
                if (!reportedAttachment) {
                    reportedAttachment = true;
                    Diary.note("recommendation follow shape attached to native LLJLL plus icon");
                }
            }
        } catch (Throwable error) {
            state.restoreForReuse();
            synchronized (STATES) { STATES.remove(button); }
            Diary.note("feed follow shape: " + error.getClass().getSimpleName());
        }
    }

    private static Drawable ripple(Context context, FollowDrawable drawable) {
        int maskKind = SHAPES[Math.floorMod((int) (System.nanoTime() ^ context.hashCode()), SHAPES.length)];
        return new RippleDrawable(ColorStateList.valueOf(0x38FFFFFF), drawable,
                new ShapeMask(context, maskKind));
    }

    private static View findFollowIcon(ViewGroup root) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        ArrayDeque<View> fallback = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 220) {
            View view = queue.removeFirst();
            if (view != root && isFollowGlyph(view)) return view;
            if (view != root && plausibleFollowGlyph(view, root)) fallback.addLast(view);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < Math.min(group.getChildCount(), 80); i++) {
                    View child = group.getChildAt(i);
                    if (child != null) queue.addLast(child);
                }
            }
        }
        // Some TikTok builds use generated drawable IDs and expose no
        // content-description/resource name. The hook is scoped to the avatar
        // assembler, so a small icon in the lower avatar area is a safe fallback.
        View best = null;
        int bestScore = Integer.MAX_VALUE;
        while (!fallback.isEmpty()) {
            View candidate = fallback.removeFirst();
            int score = fallbackScore(candidate, root);
            if (score < bestScore) { best = candidate; bestScore = score; }
        }
        return best;
    }

    private static boolean plausibleFollowGlyph(View view, ViewGroup root) {
        if (view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0.01f) return false;
        float d = Math.max(1f, view.getResources().getDisplayMetrics().density);
        int w = view.getWidth(), h = view.getHeight();
        if (w < 10 * d || h < 10 * d || w > 48 * d || h > 48 * d) return false;
        if (Math.max(w, h) > Math.min(w, h) * 1.8f) return false;
        String identity = (Names.of(view) + " " + Names.type(view) + " "
                + String.valueOf(view.getContentDescription())).toLowerCase(java.util.Locale.ROOT);
        if (contains(identity, "avatar", "profile", "portrait", "headshot", "user_photo", "user_avatar")) return false;
        boolean iconLike = view instanceof ImageView || view instanceof TextView
                || contains(identity, "iconview", "icon_view", "tuxicon", "follow", "plus", "add");
        return iconLike && fallbackScore(view, root) < Integer.MAX_VALUE;
    }

    private static int fallbackScore(View view, ViewGroup root) {
        float d = Math.max(1f, view.getResources().getDisplayMetrics().density);
        int[] a = new int[2], b = new int[2];
        view.getLocationOnScreen(a); root.getLocationOnScreen(b);
        float cx = a[0] + view.getWidth() / 2f - b[0];
        float cy = a[1] + view.getHeight() / 2f - b[1];
        float rw = Math.max(1, root.getWidth()), rh = Math.max(1, root.getHeight());
        if (cy < rh * 0.30f || cx < rw * 0.16f || cx > rw * 0.84f) return Integer.MAX_VALUE;
        return Math.round(Math.abs(cx - rw / 2f) / d * 2f + Math.abs(cy - rh * 0.76f) / d);
    }

    private static boolean isFollowGlyph(View view) {
        String name = Names.of(view);
        String type = Names.type(view);
        CharSequence desc = view.getContentDescription();
        String content = desc == null ? "" : desc.toString().toLowerCase(java.util.Locale.ROOT);
        boolean glyph = view instanceof ImageView || view instanceof TextView
                || contains((name + " " + type).toLowerCase(java.util.Locale.ROOT), "tuxicon", "iconview");
        if (!glyph) return false;
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && (text.toString().trim().equals("+") || text.toString().trim().equals("＋"))) return true;
        }
        String all = (name + " " + type + " " + content).toLowerCase(java.util.Locale.ROOT);
        boolean follow = contains(all, "follow", "subscribe", "подпис", "关注");
        boolean add = contains(all, "add", "plus", "follow_add", "add_follow", "button_follow",
                "follow_button", "follow_btn", "subscription_add", "plus_follow", "follow_action", "+");
        return (follow && add) || contains(all, "feed_avatar_follow", "avatar_follow_add", "followaddicon");
    }

    private static boolean contains(String source, String... values) {
        for (String value : values) if (source.contains(value)) return true;
        return false;
    }

    /**
     * The obfuscated LLJLILLLLZIIL field is TikTok's native surface for the
     * avatar follow affordance. Do not require it to be enabled/clickable:
     * TikTok disables this View in some bind states even though it still draws
     * the plus. We only style it while visible and geometrically tied to LLJLL.
     */
    private static boolean isNativeHitTarget(View target, View icon, ViewGroup root) {
        if (target == null || target.getVisibility() != View.VISIBLE
                || target.getAlpha() <= 0.01f || !isInside(root, target)) return false;
        float d = Math.max(1f, target.getResources().getDisplayMetrics().density);
        int w = target.getWidth(), h = target.getHeight();
        if (w < 6 * d || h < 6 * d || w > 220 * d || h > 220 * d) return false;
        if (icon == null || target == icon) return true;
        if (target instanceof ViewGroup && isInside((ViewGroup) target, icon)) return true;
        int[] a = new int[2], b = new int[2];
        target.getLocationOnScreen(a); icon.getLocationOnScreen(b);
        float ax = a[0] + w / 2f, ay = a[1] + h / 2f;
        float bx = b[0] + icon.getWidth() / 2f, by = b[1] + icon.getHeight() / 2f;
        return Math.hypot(ax - bx, ay - by) <= 120f * d;
    }

    private static View clickTarget(View icon, ViewGroup root) {
        float density = Math.max(1f, icon.getResources().getDisplayMetrics().density);
        View current = icon;
        View nearest = null;
        for (int depth = 0; depth < 6 && current != null; depth++) {
            int w = current.getWidth(), h = current.getHeight();
            boolean compact = w >= 12 * density && h >= 12 * density
                    && w <= 112 * density && h <= 112 * density
                    && Math.max(w, h) <= Math.min(w, h) * 2.25f;
            if (current != root && current.isClickable() && compact) {
                if (nearest == null) nearest = current;
                if (w <= 72 * density && h <= 72 * density) return current;
            }
            if (current == root) break;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return nearest != null ? nearest : findNativeClickTarget(root);
    }

    /** Find an unnamed native follow hit target by its position below the avatar. */
    private static View findNativeClickTarget(ViewGroup root) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        View best = null;
        double bestScore = Double.MAX_VALUE;
        float density = Math.max(1f, root.getResources().getDisplayMetrics().density);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 260) {
            View view = queue.removeFirst();
            if (view != root && view.isClickable() && view.getVisibility() == View.VISIBLE
                    && view.getAlpha() > 0.01f) {
                int w = view.getWidth(), h = view.getHeight();
                if (w >= 14 * density && h >= 14 * density && w <= 120 * density && h <= 120 * density) {
                    int[] a = new int[2], b = new int[2];
                    view.getLocationOnScreen(a); root.getLocationOnScreen(b);
                    double cx = a[0] + w / 2.0 - b[0], cy = a[1] + h / 2.0 - b[1];
                    double rw = Math.max(1, root.getWidth()), rh = Math.max(1, root.getHeight());
                    if (cx >= rw * 0.17 && cx <= rw * 0.83 && cy >= rh * 0.38 && cy <= rh * 0.98) {
                        double areaPenalty = (w * (double) h) / (density * density) * 0.018;
                        double score = Math.abs(cx - rw * 0.5) / density * 2.0
                                + Math.abs(cy - rh * 0.76) / density + areaPenalty;
                        if (score < bestScore) { bestScore = score; best = view; }
                    }
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < Math.min(group.getChildCount(), 80); i++) {
                    View child = group.getChildAt(i);
                    if (child != null) queue.addLast(child);
                }
            }
        }
        return best;
    }

    private static void installTouchObserver(State state) {
        View button = state.button.get();
        if (button == null || Boolean.TRUE.equals(button.getTag(STATE_TAG + 10))) return;
        button.setTag(STATE_TAG + 10, Boolean.TRUE);
        final float slop = ViewConfiguration.get(button.getContext()).getScaledTouchSlop() * 2f;
        final float[] down = new float[2];
        final boolean[] wasDown = new boolean[1];
        button.setOnTouchListener((view, event) -> {
            State current = view.getTag(STATE_TAG) instanceof State ? (State) view.getTag(STATE_TAG) : null;
            if (current == null || current.completed || !view.isEnabled()) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getX(); down[1] = event.getY(); wasDown[0] = true;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    wasDown[0] = false;
                    break;
                case MotionEvent.ACTION_UP:
                    boolean tap = wasDown[0] && Math.abs(event.getX() - down[0]) < slop
                            && Math.abs(event.getY() - down[1]) < slop;
                    wasDown[0] = false;
                    if (tap) {
                        // Recycler rows can be reused between ACTION_UP and this
                        // delayed visual response. Animate only the exact state
                        // that received the tap, never the next video's button.
                        final State observed = current;
                        view.postDelayed(() -> {
                            if (view.getTag(STATE_TAG) == observed) observed.complete();
                        }, 35L);
                    }
                    break;
            }
            // Preserve native dispatch: TikTok's click listener still receives the tap.
            return false;
        });
    }

    private static final class State {
        final WeakReference<View> button;
        WeakReference<View> icon;
        final String aid;
        final Drawable oldBackground;
        final Drawable oldIcon;
        final CharSequence oldIconText;
        final int oldIconAccessibility;
        final float oldIconAlpha;
        final float oldButtonAlpha;
        final float oldScaleX, oldScaleY, oldRotation;
        FollowDrawable drawable;
        boolean completed;

        State(View button, View icon, String aid) {
            this.button = new WeakReference<>(button);
            this.icon = new WeakReference<>(icon);
            this.aid = aid == null ? "" : aid;
            oldBackground = button.getBackground();
            oldIcon = icon instanceof ImageView ? ((ImageView) icon).getDrawable() : null;
            oldIconText = icon instanceof TextView ? ((TextView) icon).getText() : null;
            oldIconAccessibility = icon.getImportantForAccessibility();
            oldIconAlpha = icon.getAlpha();
            oldButtonAlpha = button.getAlpha();
            oldScaleX = button.getScaleX(); oldScaleY = button.getScaleY(); oldRotation = button.getRotation();
        }

        void complete() {
            final View target = button.get();
            if (target == null || completed || !target.isAttachedToWindow()) return;
            completed = true;
            if (drawable != null) drawable.morphToSuccess();
            target.animate().cancel();
            target.animate().rotationBy(18f).scaleX(1.08f).scaleY(1.08f)
                    .setDuration(180L).setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                    .withEndAction(() -> {
                        if (!target.isAttachedToWindow()) return;
                        target.animate().rotationBy(250f).scaleX(0.92f).scaleY(0.92f)
                                .setDuration(360L).setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .withEndAction(() -> target.animate().alpha(0f).scaleX(0.62f).scaleY(0.62f)
                                        .setDuration(420L).setInterpolator(new android.view.animation.DecelerateInterpolator()).start())
                                .start();
                    }).start();
            Diary.note("recommendation follow animation played");
        }

        void restoreForReuse() {
            View target = button.get();
            View originalIcon = icon.get();
            if (target == null) return;
            try {
                target.animate().cancel();
                if (drawable != null) drawable.cancel();
                target.setBackground(oldBackground);
                target.setAlpha(oldButtonAlpha);
                target.setScaleX(oldScaleX); target.setScaleY(oldScaleY); target.setRotation(oldRotation);
                if (originalIcon != null) {
                    originalIcon.setAlpha(oldIconAlpha);
                    originalIcon.setImportantForAccessibility(oldIconAccessibility);
                    if (originalIcon instanceof ImageView) ((ImageView) originalIcon).setImageDrawable(oldIcon);
                    else if (originalIcon instanceof TextView) ((TextView) originalIcon).setText(oldIconText);
                }
                target.setTag(DRAWABLE_TAG, null);
                target.setTag(STATE_TAG, null);
                target.setTag(STATE_TAG + 10, null);
                target.setOnTouchListener(null);
            } catch (Throwable ignored) { }
        }
    }

    private static final class FollowDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path();
        private final Path glyph = new Path();
        private final RectF rect = new RectF();
        private final float density;
        private float progress;
        private float press;
        private boolean pressed;
        private int finalKind = SHAPES[Math.floorMod((int) (System.nanoTime() >>> 8), SHAPES.length)];
        private ValueAnimator animator;

        FollowDrawable(Context context) {
            density = context.getResources().getDisplayMetrics().density;
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void morphToSuccess() {
            if (animator != null) animator.cancel();
            finalKind = SHAPES[Math.floorMod((int) (System.nanoTime() ^ android.os.SystemClock.uptimeMillis()), SHAPES.length)];
            animator = ValueAnimator.ofFloat(progress, 1f);
            animator.setDuration(820L);
            animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            animator.addUpdateListener(value -> { progress = (Float) value.getAnimatedValue(); invalidateSelf(); });
            animator.start();
        }

        void cancel() { if (animator != null) animator.cancel(); }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            float size = Math.min(bounds.width(), bounds.height());
            float cx = bounds.centerX(), cy = bounds.centerY();
            float radius = size * (0.44f - 0.035f * press);
            canvas.save();
            canvas.rotate(360f * progress, cx, cy);
            Expressive.shape(shape, finalKind, cx, cy, radius, progress, 0f);
            int fill = Expressive.mix(Accent.colour(), Color.WHITE, progress);
            paint.setColor(fill);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(shape, paint);

            float stroke = Math.max(1.8f * density, size * 0.095f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(Color.WHITE);
            float plusAlpha = 1f - Math.min(1f, progress * 2.2f);
            if (plusAlpha > 0.01f) {
                paint.setAlpha(Math.round(255f * plusAlpha));
                canvas.drawLine(cx - radius * 0.32f, cy, cx + radius * 0.32f, cy, paint);
                canvas.drawLine(cx, cy - radius * 0.32f, cx, cy + radius * 0.32f, paint);
            }
            float checkAlpha = Math.max(0f, Math.min(1f, (progress - 0.26f) / 0.40f));
            if (checkAlpha > 0.01f) {
                paint.setAlpha(Math.round(255f * checkAlpha));
                paint.setColor(SecondaryAccent.colour());
                glyph.reset();
                glyph.moveTo(cx - radius * 0.30f, cy + radius * 0.01f);
                glyph.lineTo(cx - radius * 0.07f, cy + radius * 0.23f);
                glyph.lineTo(cx + radius * 0.33f, cy - radius * 0.23f);
                canvas.drawPath(glyph, paint);
            }
            canvas.restore();
        }

        @Override protected boolean onStateChange(int[] state) {
            boolean now = false;
            for (int value : state) if (value == android.R.attr.state_pressed) now = true;
            if (now == pressed) return false;
            pressed = now;
            press = now ? 1f : 0f;
            invalidateSelf();
            return true;
        }
        @Override public boolean isStateful() { return true; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class ShapeMask extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Context context;
        private final int kind;
        ShapeMask(Context context, int kind) { this.context = context; this.kind = kind; paint.setColor(Color.WHITE); }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float radius = Math.min(b.width(), b.height()) * 0.44f;
            Expressive.shape(path, kind, b.centerX(), b.centerY(), radius, 1f, 0f);
            canvas.drawPath(path, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
