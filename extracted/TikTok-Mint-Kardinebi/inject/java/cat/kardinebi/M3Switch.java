package cat.kardinebi;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Mint's custom Material 3 Expressive switch.
 *
 * Unlike the previous floating status glyph, this is a complete control: a
 * tonal capsule, a travelling thumb, and a small ten-petal bloom that resolves
 * into a checkmark when enabled. The value remains owned by the setting row;
 * this view only presents and reports the user's interaction.
 */
public final class M3Switch extends View {
    public static final long ANIMATION_DURATION_MS = 360L;

    public interface OnChanged {
        void onChanged(boolean checked);
    }

    private static final float TRACK_WIDTH = 58f;
    private static final float TRACK_HEIGHT = 36f;
    private static final float VIEW_WIDTH = 64f;
    private static final float VIEW_HEIGHT = 40f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final RectF thumb = new RectF();
    private final Path bloom = new Path();
    private final Path check = new Path();
    private final Path checkPart = new Path();
    private final PathMeasure checkMeasure = new PathMeasure();

    private int accent = 0xFF83B987;
    private int surface = 0xFF1B231C;
    private int foreground = 0xFFE2E9DF;
    private int trackOff = 0xFF303A31;
    private int trackOn = 0xFF83B987;
    private int outlineOff = 0xFF7E897F;
    private int thumbOff = 0xFFC4CEC3;
    private int thumbOn = 0xFF142016;
    private boolean checked;
    private float position;
    private OnChanged listener;
    private ValueAnimator animator;
    private boolean interruptingAnimation;
    private float downX, downY, positionAtDown;
    private boolean dragging;

    public M3Switch(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setWillNotDraw(false);
        updatePalette();
    }

    /** Values are taken from Mint's independent Material 3 palette. */
    public void colours(int accentColour, int mutedColour, int surfaceColour) {
        accent = accentColour;
        surface = surfaceColour;
        foreground = mutedColour;
        updatePalette();
        invalidate();
    }

    private void updatePalette() {
        trackOff = mix(surface, foreground, 0.12f);
        outlineOff = mix(surface, foreground, 0.38f);
        thumbOff = mix(surface, foreground, 0.80f);
        trackOn = mix(accent, surface, 0.10f);
        thumbOn = contrast(trackOn);
    }

    public void setChecked(boolean value) {
        setChecked(value, false);
    }

    public void setChecked(boolean value, boolean animate) {
        if (checked == value && position == (value ? 1f : 0f)) return;
        cancelAnimatorKeepingPosition();
        checked = value;
        if (!animate || !Motion.enabled(getContext())) {
            position = value ? 1f : 0f;
            invalidate();
            return;
        }
        animateTo(value ? 1f : 0f);
    }

    private void animateTo(float target) {
        cancelAnimatorKeepingPosition();
        animator = ValueAnimator.ofFloat(position, target);
        animator.setDuration(ANIMATION_DURATION_MS);
        animator.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));
        animator.addUpdateListener(value -> {
            position = (Float) value.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (!interruptingAnimation && animation == animator) {
                    position = checked ? 1f : 0f;
                    animator = null;
                    invalidate();
                }
            }
        });
        animator.start();
    }

    private void cancelAnimatorKeepingPosition() {
        if (animator == null) return;
        interruptingAnimation = true;
        animator.cancel();
        animator = null;
        interruptingAnimation = false;
    }

    public boolean isChecked() { return checked; }

    public void setOnChanged(OnChanged value) { listener = value; }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelAnimatorKeepingPosition();
                downX = event.getX();
                downY = event.getY();
                positionAtDown = position;
                dragging = false;
                setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!dragging && Math.abs(dx) > dp(5) && Math.abs(dx) > Math.abs(dy)) dragging = true;
                if (dragging) {
                    float travel = Math.max(1f, dp(TRACK_WIDTH - TRACK_HEIGHT));
                    position = clamp(positionAtDown + dx / travel, 0f, 1f);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                dragging = false;
                setChecked(checked, true);
                return true;
            case MotionEvent.ACTION_UP:
                boolean inside = event.getX() >= 0 && event.getX() < getWidth()
                        && event.getY() >= 0 && event.getY() < getHeight();
                setPressed(false);
                if (inside) {
                    if (dragging) commit(event.getX() >= getWidth() / 2f);
                    else performClick();
                } else setChecked(checked, true);
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    private void commit(boolean value) {
        boolean changed = checked != value;
        setChecked(value, true);
        if (changed && listener != null) listener.onChanged(checked);
        super.performClick();
    }

    @Override public boolean performClick() {
        if (!isEnabled()) return false;
        boolean next = !checked;
        setChecked(next, true);
        if (listener != null) listener.onChanged(checked);
        super.performClick();
        return true;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(dp(VIEW_WIDTH), widthSpec), resolveSize(dp(VIEW_HEIGHT), heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float width = dp(TRACK_WIDTH), height = dp(TRACK_HEIGHT);
        float left = cx - width / 2f, top = cy - height / 2f;
        track.set(left, top, left + width, top + height);
        float eased = position * position * (3f - 2f * position);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(blend(trackOff, trackOn, eased));
        canvas.drawRoundRect(track, height / 2f, height / 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, density));
        paint.setColor(withAlpha(outlineOff, Math.round(205f * (1f - eased))));
        canvas.drawRoundRect(track, height / 2f, height / 2f, paint);
        paint.setStyle(Paint.Style.FILL);

        float radius = dp(8.5f + 3f * eased);
        float thumbLeft = left + height / 2f;
        float thumbRight = left + width - height / 2f;
        float thumbX = thumbLeft + (thumbRight - thumbLeft) * position;

        // A faint halo grows with the selected state and gives the movement a
        // little depth without turning the whole control into a glowing blob.
        paint.setColor(withAlpha(accent, Math.round(26f * eased)));
        canvas.drawCircle(thumbX, cy, radius + dp(4f), paint);

        float bloomAmount = clamp((position - 0.08f) / 0.92f, 0f, 1f);
        makeBloom(thumbX, cy, radius, bloomAmount, 36f * eased);
        paint.setColor(blend(thumbOff, thumbOn, eased));
        canvas.drawPath(bloom, paint);

        // The check is drawn progressively, in the accent ink, as the bloom
        // settles. Off is an honest unfilled thumb, not a decorative second icon.
        if (position > 0f) {
            check.reset();
            check.moveTo(thumbX - dp(4.6f), cy + dp(0.1f));
            check.lineTo(thumbX - dp(1.2f), cy + dp(3.4f));
            check.lineTo(thumbX + dp(4.5f), cy - dp(3.1f));
            checkMeasure.setPath(check, false);
            checkPart.reset();
            float checkProgress = clamp((position - 0.24f) / 0.76f, 0f, 1f);
            checkMeasure.getSegment(0f, checkMeasure.getLength() * checkProgress, checkPart, true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.9f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(accent);
            canvas.drawPath(checkPart, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void makeBloom(float cx, float cy, float radius, float amount, float rotation) {
        final int lobes = 10, steps = 100;
        double start = Math.toRadians(rotation) - Math.PI / 2.0;
        bloom.reset();
        for (int i = 0; i < steps; i++) {
            double angle = Math.PI * 2.0 * i / steps;
            float reach = radius * (1f - 0.12f * amount
                    * (1f - (float) Math.cos(lobes * (angle - start))));
            float x = (float) (cx + reach * Math.cos(angle));
            float y = (float) (cy + reach * Math.sin(angle));
            if (i == 0) bloom.moveTo(x, y); else bloom.lineTo(x, y);
        }
        bloom.close();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private static int contrast(int colour) {
        float luma = (0.299f * Color.red(colour) + 0.587f * Color.green(colour)
                + 0.114f * Color.blue(colour)) / 255f;
        return luma > 0.57f ? 0xFF172018 : 0xFFF8FFF5;
    }

    private static int mix(int from, int to, float amount) {
        return blend(from, to, clamp(amount, 0f, 1f));
    }

    private static int blend(int from, int to, float amount) {
        float t = clamp(amount, 0f, 1f);
        return Color.argb(
                Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    private static int withAlpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
