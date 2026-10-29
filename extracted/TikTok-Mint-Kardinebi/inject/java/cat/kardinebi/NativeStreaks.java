package cat.kardinebi;

import com.ss.android.ugc.aweme.im.streak.api.IStreakService;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Finds TikTok's streak service using native providers verified in the supplied
 * 47.4.42 APK. The conversation list is accumulated only from native calls the
 * user actually causes; this class does not enumerate or message every contact.
 */
final class NativeStreaks {
    private static String account;
    private static long last;

    private NativeStreaks() {}

    static synchronized void prepare() {
        String uid = Account.liveId();
        if (uid == null || uid.length() == 0) uid = Account.id();
        if (uid == null || uid.length() == 0) return;

        long now = android.os.SystemClock.uptimeMillis();
        if (uid.equals(account) && now - last < 5000L) return;
        account = uid;
        last = now;

        IStreakService service = provider("X.0RuD", "LIZ");
        if (service == null) service = provider("com.ss.android.ugc.aweme.im.streak.impl.StreakService", "getInstance");
        // Older releases used a different provider; it is strictly a fallback.
        if (service == null) service = provider("X.06p9", "LIZ");
        if (service != null) {
            Streaks.discover(service, null);
            Diary.note("streak service discovered from native provider");
        } else {
            Diary.note("streak service provider not found; auto-streak remains idle");
        }
    }

    private static IStreakService provider(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className);
            Method method = null;
            try { method = type.getDeclaredMethod(methodName); }
            catch (NoSuchMethodException missing) { method = type.getMethod(methodName); }
            method.setAccessible(true);
            Object receiver = null;
            if (!Modifier.isStatic(method.getModifiers())) {
                java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                receiver = constructor.newInstance();
            }
            Object value = method.invoke(receiver);
            return value instanceof IStreakService ? (IStreakService) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
