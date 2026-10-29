package cat.kardinebi;

import android.content.Context;
import android.content.SharedPreferences;

/** Independent expressive contrast colour used for decorative geometry and secondary emphasis. */
public final class SecondaryAccent {
    public static final String KEY = "accent_secondary";
    /** Off means every consumer uses the primary accent, not a stale second colour. */
    public static final String KEY_ON = "accent_secondary_on";
    private static volatile int cachedOn; // 0 unknown, 1 on, 2 off
    private static final int DEFAULT = 0xFF00A6A0;
    private static volatile int cached;
    private SecondaryAccent() {}

    /** Whether the second accent is in use at all. */
    public static boolean enabled() {
        int known = cachedOn;
        if (known != 0) return known == 1;
        Context context = Kardinebi.context();
        if (context == null) return true;
        try {
            boolean on = AccountAppearance.prefs(context).getBoolean(KEY_ON, true);
            cachedOn = on ? 1 : 2;
            return on;
        } catch (Throwable ignored) { return true; }
    }

    public static void setEnabled(boolean on) {
        Context context = Kardinebi.context();
        if (context != null) {
            try { AccountAppearance.prefs(context).edit().putBoolean(KEY_ON, on).apply(); }
            catch (Throwable ignored) { }
        }
        cachedOn = on ? 1 : 2;
        ProfileShapes.reload();
        Themes.repaintVisible();
    }

    /** The colour the user chose, whether or not it is currently in use. */
    public static int storedColour() {
        int known = cached;
        if (known != 0) return known;
        Context context = Kardinebi.context();
        if (context == null) return DEFAULT;
        try {
            int value = AccountAppearance.prefs(context).getInt(KEY, DEFAULT);
            if (value == 0) value = DEFAULT;
            cached = value;
            return value;
        } catch (Throwable ignored) { return DEFAULT; }
    }

    public static int colour() {
        if (!enabled()) return Accent.colour();
        int known = cached;
        if (known != 0) return known;
        Context context = Kardinebi.context();
        if (context == null) return DEFAULT;
        try {
            int value = AccountAppearance.prefs(context).getInt(KEY, DEFAULT);
            if (value == 0) value = DEFAULT;
            cached = value;
            return value;
        } catch (Throwable ignored) { return DEFAULT; }
    }

    public static void set(int colour) {
        Context context = Kardinebi.context();
        if (context == null) return;
        try { AccountAppearance.prefs(context).edit().putInt(KEY, colour | 0xFF000000).apply(); }
        catch (Throwable ignored) { }
        cached = colour | 0xFF000000;
        ProfileShapes.reload();
        Themes.repaintVisible();
    }

    public static void reload() { cached = 0; cachedOn = 0; ProfileShapes.reload(); }
}
