package cat.kardinebi;

import android.content.Context;

/**
 * The plain look of TikTok Mint's settings: plain text and plain blocks, no
 * Material 3 surfaces. It is the default. Every function stays the same; only
 * the drawing changes. Turning it off in the preferences brings the old look back.
 */
final class PlainStyle {

    private PlainStyle() {}

    static final String KEY = "plain_settings_v1";

    static boolean on(Context context) {
        if (context == null) return true;
        return context.getSharedPreferences(Kardinebi.PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY, true);
    }
}
