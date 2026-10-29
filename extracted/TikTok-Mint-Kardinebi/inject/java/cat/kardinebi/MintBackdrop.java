package cat.kardinebi;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Low-contrast, animated Material 3 Expressive geometry behind Mint settings. */
final class MintBackdrop extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF bounds = new RectF();
    private final Skin palette;
    private ValueAnimator drift;
    private float phase;
    private long lastInvalidate;
    private int shaderWidth = -1;
    private int shaderHeight = -1;
    private int shaderAccent;
    private Shader upperGlow;
    private Shader lowerGlow;

    MintBackdrop(Context context) {
        super(context);
        palette = Skin.material3(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startDrift();
    }

    @Override protected void onDetachedFromWindow() {
        stopDrift();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) startDrift(); else stopDrift();
    }

    private void startDrift() {
        if (drift != null || !Motion.enabled(getContext()) || getWindowVisibility() != VISIBLE) return;
        drift = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        drift.setDuration(26000L);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setInterpolator(new LinearInterpolator());
        drift.addUpdateListener(value -> {
            phase = (Float) value.getAnimatedValue();
            // Cap the decorative layer at about 30 fps. Scrolling and controls stay independent.
            long now = SystemClock.uptimeMillis();
            if (now - lastInvalidate >= 32L) {
                lastInvalidate = now;
                invalidate();
            }
        });
        drift.start();
    }

    private void stopDrift() {
        if (drift != null) {
            drift.cancel();
            drift = null;
        }
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShaders(w, h, Accent.colour());
    }

    private void updateShaders(float width, float height, int accent) {
        if ((int) width == shaderWidth && (int) height == shaderHeight && accent == shaderAccent) return;
        shaderWidth = (int) width;
        shaderHeight = (int) height;
        shaderAccent = accent;
        upperGlow = new RadialGradient(width * 0.88f, height * 0.11f,
                Math.max(width, height) * 0.36f,
                new int[]{alpha(accent, 36), alpha(accent, 13), alpha(accent, 0)},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP);
        lowerGlow = new RadialGradient(width * 0.06f, height * 0.73f,
                width * 0.42f,
                new int[]{alpha(mix(accent, palette.card, 0.34f), 24),
                        alpha(accent, 7), alpha(accent, 0)},
                null, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;

        int accent = Accent.colour();
        updateShaders(width, height, accent);
        float dx = (float) Math.cos(phase * 0.63f) * 11f * density;
        float dy = (float) Math.sin(phase) * 13f * density;

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(upperGlow);
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(lowerGlow);
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);

        // Main expressive bloom, cropped into the top-right corner.
        petalShape(width * 0.94f + dx, height * 0.11f + dy,
                112f * density, phase * 0.10f, 10, 96);
        paint.setColor(alpha(accent, 22));
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.1f * density);
        paint.setColor(alpha(accent, 36));
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.FILL);

        // Smaller moving flower gives the lower half its own visual anchor.
        petalShape(width * 0.10f - dx * 0.55f, height * 0.70f - dy * 0.55f,
                65f * density, -phase * 0.12f, 8, 72);
        paint.setColor(alpha(mix(accent, palette.card, 0.30f), 18));
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(alpha(accent, 27));
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.FILL);

        // Soft angular panels and orbital rings, each moving on a different slow path.
        canvas.save();
        canvas.rotate(-20f + (float) Math.sin(phase * 0.48f) * 2.5f,
                width * 0.05f, height * 0.40f);
        bounds.set(-72f * density, height * 0.29f, 78f * density, height * 0.49f);
        paint.setColor(alpha(mix(accent, palette.card, 0.48f), 18));
        canvas.drawRoundRect(bounds, 42f * density, 42f * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.25f * density);
        paint.setColor(alpha(accent, 32));
        canvas.drawRoundRect(bounds, 42f * density, 42f * density, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();

        canvas.save();
        canvas.rotate(18f + (float) Math.sin(phase * 0.8f) * 3f,
                width * 0.95f, height * 0.53f);
        bounds.set(width * 0.74f, height * 0.42f, width * 1.17f, height * 0.63f);
        paint.setColor(alpha(mix(accent, palette.card, 0.40f), 16));
        canvas.drawRoundRect(bounds, 32f * density, 32f * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.1f * density);
        paint.setColor(alpha(accent, 30));
        canvas.drawRoundRect(bounds, 32f * density, 32f * density, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();

        float orbitX = width * 0.82f - dx * 0.5f;
        float orbitY = height * 0.34f + dy * 0.25f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(alpha(accent, 25));
        canvas.drawCircle(orbitX, orbitY, 37f * density, paint);
        canvas.drawCircle(orbitX, orbitY, 49f * density, paint);
        canvas.drawCircle(orbitX, orbitY, 61f * density, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(orbitX + (float) Math.cos(phase) * 49f * density,
                orbitY + (float) Math.sin(phase) * 49f * density,
                3.5f * density, paint);

        // A pair of rounded diamonds and a small expressive sunburst.
        canvas.save();
        canvas.rotate(45f + (float) Math.sin(phase * 0.55f) * 7f,
                width * 0.82f, height * 0.76f);
        bounds.set(width * 0.77f, height * 0.71f,
                width * 0.87f, height * 0.81f);
        paint.setColor(alpha(accent, 22));
        canvas.drawRoundRect(bounds, 7f * density, 7f * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * density);
        paint.setColor(alpha(accent, 36));
        canvas.drawRoundRect(bounds, 7f * density, 7f * density, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();

        drawSunburst(canvas, width * 0.16f + dx * 0.25f, height * 0.19f + dy * 0.25f,
                14f * density, phase * 0.14f, density, accent);
        drawSunburst(canvas, width * 0.72f, height * 0.89f - dy * 0.25f,
                9f * density, -phase * 0.12f, density, accent);

        // A second family of floating geometry adds depth without introducing
        // nested borders on the actual settings controls.
        drawPolygon(canvas, width * 0.90f + dx * 0.65f, height * 0.65f + dy * 0.42f,
                14f * density, 6, phase * 0.12f, density, accent, 17, 31);
        drawPolygon(canvas, width * 0.18f - dx * 0.38f, height * 0.53f + dy * 0.30f,
                8f * density, 3, -phase * 0.18f, density, accent, 14, 28);
        drawPolygon(canvas, width * 0.53f + dx * 0.22f, height * 0.91f - dy * 0.30f,
                10f * density, 5, phase * 0.16f, density, accent, 12, 25);

        float bubbleX = width * 0.50f;
        float bubbleY = height * 0.30f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(alpha(accent, 25));
        for (int i = 0; i < 5; i++) {
            double orbit = phase * 0.20f + i * Math.PI * 2d / 5d;
            float bx = bubbleX + (float) Math.cos(orbit) * (20f + i * 4f) * density;
            float by = bubbleY + (float) Math.sin(orbit) * (12f + i * 3f) * density;
            float r = (2f + (i % 3) * 1.2f) * density;
            canvas.drawCircle(bx, by, r, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // A scattered dot field and four outlined pills add detail without fighting the text.
        paint.setColor(alpha(palette.text, 28));
        float dotStartX = 22f * density, dotStartY = height * 0.82f;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 6; col++) {
                float offset = (row % 2 == 0) ? 0f : 3.2f * density;
                float size = ((row + col) % 3 == 0) ? 1.7f : 1.1f;
                canvas.drawCircle(dotStartX + col * 10f * density + offset,
                        dotStartY + row * 10f * density, size * density, paint);
            }
        }
        paint.setColor(alpha(accent, 28));
        for (int i = 0; i < 4; i++) {
            float px = width * (0.22f + i * 0.08f);
            float py = height * (0.60f + (i % 2) * 0.05f) + dy * (i % 2 == 0 ? 0.2f : -0.2f);
            bounds.set(px, py, px + (17 + (i % 2) * 5) * density, py + 5f * density);
            canvas.save();
            canvas.rotate(i % 2 == 0 ? -28f : 24f, bounds.centerX(), bounds.centerY());
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f * density);
            canvas.drawRoundRect(bounds, 5f * density, 5f * density, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.restore();
        }

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawPolygon(Canvas canvas, float cx, float cy, float radius, int sides,
                             float rotation, float density, int accent,
                             int fillAlpha, int strokeAlpha) {
        if (sides < 3) return;
        shape.reset();
        for (int i = 0; i < sides; i++) {
            double angle = rotation + Math.PI * 2d * i / sides - Math.PI / 2d;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            if (i == 0) shape.moveTo(x, y); else shape.lineTo(x, y);
        }
        shape.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(alpha(accent, fillAlpha));
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(alpha(accent, strokeAlpha));
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void petalShape(float cx, float cy, float radius, float rotation,
                            int lobes, int steps) {
        double start = rotation - Math.PI / 2d;
        shape.reset();
        for (int i = 0; i < steps; i++) {
            double angle = Math.PI * 2d * i / steps;
            float reach = radius * (1f - 0.105f * (1f - (float) Math.cos(lobes * (angle - start))));
            float x = (float) (cx + reach * Math.cos(angle));
            float y = (float) (cy + reach * Math.sin(angle));
            if (i == 0) shape.moveTo(x, y); else shape.lineTo(x, y);
        }
        shape.close();
    }

    private void drawSunburst(Canvas canvas, float cx, float cy, float radius,
                              float rotation, float density, int accent) {
        paint.setColor(alpha(accent, 30));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.25f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 8; i++) {
            double angle = rotation + Math.PI * 2d * i / 8d;
            float inner = radius * 0.42f;
            float outer = radius * (i % 2 == 0 ? 1f : 0.78f);
            canvas.drawLine(cx + (float) Math.cos(angle) * inner,
                    cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer,
                    cy + (float) Math.sin(angle) * outer, paint);
        }
        canvas.drawCircle(cx, cy, radius * 0.24f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static int alpha(int colour, int value) {
        return (colour & 0x00FFFFFF) | (Math.max(0, Math.min(255, value)) << 24);
    }

    private static int mix(int from, int to, float amount) {
        float inverse = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(from) * inverse + Color.red(to) * amount),
                Math.round(Color.green(from) * inverse + Color.green(to) * amount),
                Math.round(Color.blue(from) * inverse + Color.blue(to) * amount));
    }
}
