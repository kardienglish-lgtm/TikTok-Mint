package cat.kardinebi;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Keeps the TikTok wordmark visible on a black background while TikTok moves
 * from its window splash into MainActivity. The PNG is also patched into splash_bg_normal.
 */
final class SplashOverlay implements Application.ActivityLifecycleCallbacks {
    private static final String LAUNCH_ACTIVITY = "com.ss.android.ugc.aweme.main.MainActivity";
    private static final long HOLD_MS = 1500L;
    private static boolean shown;

    static boolean wasShown() {
        return shown;
    }

    static void install(Application application) {
        application.registerActivityLifecycleCallbacks(new SplashOverlay());
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (shown || !LAUNCH_ACTIVITY.equals(activity.getClass().getName())) return;

        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            Diary.note("launch picture skipped: no activity content");
            return;
        }

        FrameLayout overlay = null;
        try {
            byte[] bytes = Base64.decode(SplashBanner.PNG, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                Diary.note("launch picture failed: TikTok wordmark could not be decoded");
                return;
            }

            overlay = new FrameLayout(activity);
            overlay.setBackgroundColor(Color.BLACK);
            overlay.setClickable(true);
            overlay.setFocusable(true);

            ImageView banner = new ImageView(activity);
            banner.setImageBitmap(bitmap);
            banner.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                    dp(activity, 158), dp(activity, 46), Gravity.CENTER);
            overlay.addView(banner, imageParams);
            content.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            shown = true;
            Diary.note("launch picture shown: TikTok on black");
            final ViewGroup host = content;
            final FrameLayout shownOverlay = overlay;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (shownOverlay.getParent() == host) host.removeView(shownOverlay);
                } catch (Throwable error) {
                    Diary.note("launch picture cleanup failed: " + error);
                }
            }, HOLD_MS);
        } catch (Throwable error) {
            if (overlay != null) {
                try {
                    if (overlay.getParent() == content) content.removeView(overlay);
                } catch (Throwable ignored) { }
            }
            Diary.note("launch picture failed: " + error);
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityResumed(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
