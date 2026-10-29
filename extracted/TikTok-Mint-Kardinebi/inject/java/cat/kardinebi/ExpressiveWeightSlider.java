package cat.kardinebi;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Compact variable-font weight control with a small curling ribbon at its thumb. */
final class ExpressiveWeightSlider extends View {
    interface Listener { void changed(int value); default void finished(int value) {} }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ribbon = new Path();
    private final float density;
    private final int active, track;
    private final Listener listener;
    private int value;
    private float curl = 1f;
    private float press = 1f;
    private ValueAnimator motion;

    ExpressiveWeightSlider(Context context, int initial, int active, int track, Listener listener) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        this.value = clamp(initial);
        this.active = active;
        this.track = track;
        this.listener = listener;
        setMinimumHeight(dp(52));
        setFocusable(true);
        setClickable(true);
        setContentDescription("Жирность шрифта, " + value);
    }

    int value() { return value; }

    private int clamp(int v) { return Math.max(100, Math.min(900, Math.round(v / 50f) * 50)); }
    private float dp(float v) { return v * density; }
    private float start() { return dp(18); }
    private float end() { return Math.max(start() + dp(24), getWidth() - dp(18)); }
    private float thumbX() { return start() + (end() - start()) * ((value - 100f) / 800f); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float y = getHeight() / 2f;
        float left = start(), right = end(), x = thumbX();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(5));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(track);
        canvas.drawLine(left, y, right, y, paint);

        paint.setColor(active);
        ribbon.reset();
        float curlSize = dp(14) * curl * press;
        ribbon.moveTo(left, y);
        ribbon.lineTo(Math.max(left, x - dp(24)), y);
        ribbon.cubicTo(x - dp(8), y, x - dp(5), y - curlSize * 1.7f,
                x + curlSize * .45f, y - curlSize * .65f);
        ribbon.cubicTo(x + curlSize * 1.8f, y + curlSize * .55f,
                x + dp(3), y + curlSize * 1.65f, x - curlSize * .55f, y + curlSize * .65f);
        ribbon.cubicTo(x - curlSize * 1.1f, y - curlSize * .1f, x - dp(3), y, x, y);
        canvas.drawPath(ribbon, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active);
        float radius = dp(8) * press;
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(0x33000000);
        canvas.drawCircle(x, y + dp(1), radius + dp(2), paint);
        paint.setColor(active);
        canvas.drawCircle(x, y, radius, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animateCurl(1.35f, 180L);
                updateFrom(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFrom(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animateCurl(1f, 420L);
                if (listener != null) listener.finished(value);
                performClick();
                return true;
            default: return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void updateFrom(float x) {
        float span = Math.max(1f, end() - start());
        int next = clamp(100 + Math.round((x - start()) / span * 800f));
        if (next != value) {
            value = next;
            setContentDescription("Жирность шрифта, " + value);
            if (listener != null) listener.changed(value);
            invalidate();
        }
    }

    private void animateCurl(float target, long duration) {
        if (motion != null) motion.cancel();
        motion = ValueAnimator.ofFloat(curl, target);
        motion.setDuration(duration);
        motion.setInterpolator(new OvershootInterpolator(target > 1f ? .85f : .35f));
        motion.addUpdateListener(a -> { curl = (Float) a.getAnimatedValue(); invalidate(); });
        motion.start();
    }
}
