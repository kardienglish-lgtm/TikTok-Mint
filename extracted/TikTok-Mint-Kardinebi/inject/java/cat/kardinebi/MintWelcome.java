package cat.kardinebi;

import android.app.Activity;
import android.content.Context;

/** First-run identity notice for the TikTok Mint fork. */
final class MintWelcome {
    private static final String PREFS = "tiktok_mint_identity";
    private static final String SHOWN = "welcome_shown";
    private MintWelcome() {}

    static void maybeShow(Activity activity) { /* First-run popup intentionally removed. */ }
}
