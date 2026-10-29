package cat.kardinebi;

import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

/** Makes the profile's Follow and Message controls squarer, while keeping soft corners. */
public final class ProfileButtons {
    private static final int WATCH_TAG = 0x7e0d0342;
    private static final float RADIUS_DP = MintGeometry.PROFILE_ACTION_DP;
    private static final Map<View, String> styled = new WeakHashMap<>();
    private static final ViewOutlineProvider ROUNDED = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                    RADIUS_DP * view.getResources().getDisplayMetrics().density);
        }
    };

    private ProfileButtons() {}

    public static void bind(Object component, View view) {
        if (component == null || view == null) return;
        String name = component.getClass().getName();
        if (!name.contains("profile") || !name.contains("ProfileHeaderCTA")) return;
        // Stay within this profile component, not the app decor root, so a
        // similarly labelled button elsewhere (for example in a suggested-user
        // row) is never restyled accidentally.
        if (view.getTag(WATCH_TAG) == null) {
            view.setTag(WATCH_TAG, Boolean.TRUE);
            view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> styleTree(v));
        }
        view.post(() -> styleTree(view));
    }

    private static void styleTree(View root) {
        if (root == null) return;
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 700) {
            View view = queue.removeFirst();
            if (view instanceof TextView && isActionLabel((TextView) view)) {
                View button = buttonFor(view, root);
                if (button != null) style(button, (TextView) view);
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                int count = Math.min(group.getChildCount(), 160);
                for (int i = 0; i < count; i++) {
                    View child = group.getChildAt(i);
                    if (child != null) queue.addLast(child);
                }
            }
        }
    }

    private static boolean isActionLabel(TextView text) {
        CharSequence chars = text.getText();
        String value = chars == null ? "" : chars.toString().trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty() || value.length() > 40) return false;
        return value.equals("подписаться") || value.equals("подписки")
                || value.equals("сообщение") || value.equals("сообщения")
                || value.equals("follow") || value.equals("following")
                || value.equals("message") || value.equals("messages");
    }

    private static View buttonFor(View label, View root) {
        View current = label;
        for (int depth = 0; depth < 4 && current != null && current != root; depth++) {
            if (current.isClickable() && current.getWidth() > 0 && current.getHeight() > 0) return current;
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return label.isClickable() ? label : null;
    }

    private static void style(View view, TextView label) {
        if (view == null) return;
        boolean first;
        boolean changed;
        String state = (label == null || label.getText() == null ? "" : label.getText().toString())
                + "\u0000" + view.isSelected() + "\u0000" + view.isEnabled();
        synchronized (styled) {
            String previous = styled.put(view, state);
            first = previous == null;
            changed = previous != null && !previous.equals(state);
        }
        try {
            // Preserve the native fill and ripple when the background uses a
            // GradientDrawable inside a Ripple/Layer/InsetDrawable. Adjusting
            // the shape's actual radius makes a long pill more rectangular;
            // clipping alone would only cut corners without filling them.
            Drawable background = view.getBackground();
            if (background != null) {
                Drawable mutable = background.mutate();
                boolean rounded = setRadius(mutable,
                        RADIUS_DP * view.getResources().getDisplayMetrics().density);
                if (rounded && mutable != background) view.setBackground(mutable);
            }
            if (first || view.getOutlineProvider() == null) {
                view.setOutlineProvider(ROUNDED);
                view.setClipToOutline(true);
            }
            view.invalidateOutline();
            if (changed) Motion.bump(view);
        } catch (Throwable error) {
            Diary.note("profile button shape: " + error);
        }
    }

    private static boolean setRadius(Drawable drawable, float radius) {
        if (drawable == null) return false;
        try {
            if (drawable instanceof GradientDrawable) {
                ((GradientDrawable) drawable).setCornerRadius(radius);
                return true;
            }
            if (drawable instanceof InsetDrawable) {
                return setRadius(((InsetDrawable) drawable).getDrawable(), radius);
            }
            if (drawable instanceof LayerDrawable) {
                LayerDrawable layers = (LayerDrawable) drawable;
                boolean changed = false;
                for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                    changed |= setRadius(layers.getDrawable(i), radius);
                }
                return changed;
            }
        } catch (Throwable ignored) {
            // Non-standard Tux drawables are still rounded by the outline clip.
        }
        return false;
    }
}
