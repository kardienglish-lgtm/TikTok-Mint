package cat.kardinebi;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.widget.TextView;

import java.io.File;

/**
 * One typeface, everywhere TikTok writes.
 *
 * There is no single place an app's font is decided, so this works from two
 * ends. Anywhere TikTok sets a typeface itself the call is rewritten to come
 * through here and is answered with the chosen one. And every piece of text on
 * its way into a view already passes the mod -- that is how a badge becomes a
 * picture -- so the font is set there too, which reaches the great many views
 * that never ask for a typeface at all and simply inherit one.
 *
 * The choices are the ones the phone already has, plus any `.ttf` or `.otf`
 * file you point at: it is copied into the mod's own folder, because a file
 * picked out of Downloads is a borrowed handle that will not be readable on
 * the next start.
 */
public final class Fonts {

    private Fonts() {}

    public static final String KEY = "font";
    public static final String KEY_EMOJI = "font_emoji";
    public static final String KEY_BOLD = "font_bold";
    public static final String KEY_WEIGHT = "font_weight";

    /**
     * The emoji packs on offer, each fetched the first time it is chosen.
     *
     * All of them are open: Twemoji is CC-BY, Noto is under the Open Font
     * Licence, Blobmoji is Noto's older round faces kept going by somebody
     * else under the same licence. Apple's is not here and will not be -- it
     * is theirs and not redistributable -- but any font file will do, so
     * nothing stops you pointing at one you own.
     */
    public static final String TWEMOJI = "twemoji";
    public static final String NOTO = "noto";
    public static final String BLOBMOJI = "blobmoji";
    public static final String EMOJI_FILE = "emoji_file";

    /**
     * Every pack rides inside the apk. Nothing is fetched, ever.
     *
     * They came to twenty-four megabytes between them, on an apk that is three
     * hundred and sixty -- which is a better trade than a setting that says it
     * is downloading something and gives no sign of when it will be done.
     */
    public static final String TWEMOJI_ASSET = "kardinebi/twemoji.ttf";

    private static String packAsset(Context context, String which) {
        return EmojiPackManager.asset(context, which);
    }

    /** The names of the ones that need no file. */
    public static final String SYSTEM = "";
    public static final String SANS = "sans-serif";
    public static final String SANS_LIGHT = "sans-serif-light";
    public static final String SANS_CONDENSED = "sans-serif-condensed";
    public static final String SERIF = "serif";
    public static final String MONOSPACE = "monospace";
    public static final String CURSIVE = "cursive";
    public static final String FILE = "file";

    public static final String[] EMOJI_PACKS = {SYSTEM, TWEMOJI, NOTO, BLOBMOJI};

    /** TikTok's own text face. Read from the host app's assets at run time; never copied into the mod. */
    public static final String TIKTOK = "tiktok";
    public static final String TIKTOK_ASSET = "font/TikTok-Text-Regular.otf";

    /** The choices offered on the font page: the phone's own, Roboto, and TikTok when the app carries it. */
    public static final String[] PRESETS = {
            SYSTEM, SANS, TIKTOK,
    };

    /** Whether the host app actually ships the TikTok face; the picker hides the choice when it does not. */
    public static boolean tiktokPresent(Context context) {
        if (context == null) return false;
        try {
            context.getAssets().open(TIKTOK_ASSET).close();
            return true;
        } catch (Throwable missing) {
            return false;
        }
    }

    private static volatile String chosen;
    private static volatile String chosenEmoji;
    private static volatile Integer activeWeight;
    private static volatile Typeface face;
    private static volatile boolean looked;

    // ---------------------------------------------------------- the choice

    public static String name() {
        String known = chosen;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        // before the app has a context there is nothing to read, and writing
        // "the phone's own" down as the answer meant the chosen font never
        // arrived for the rest of the run
        if (prefs == null) return SYSTEM;
        String value = prefs.getString(KEY, SANS);
        chosen = value;
        return value;
    }

    public static int weight() {
        Integer known = activeWeight;
        if (known != null) return known.intValue();
        int read = savedWeight(Kardinebi.context());
        activeWeight = Integer.valueOf(read);
        return read;
    }

    public static int savedWeight(Context context) {
        if (context == null) context = Kardinebi.context();
        SharedPreferences p = context == null ? null : AccountAppearance.prefs(context);
        if (p == null) return 400;
        int fallback = p.getBoolean(KEY_BOLD, false) ? 700 : 400;
        return Math.max(100, Math.min(900, p.getInt(KEY_WEIGHT, fallback)));
    }

    public static String savedName(Context context) {
        if (context == null) context = Kardinebi.context();
        SharedPreferences p = context == null ? null : AccountAppearance.prefs(context);
        return p == null ? SANS : p.getString(KEY, SANS);
    }

    public static String savedEmoji(Context context) {
        if (context == null) context = Kardinebi.context();
        SharedPreferences p = context == null ? null : AccountAppearance.prefs(context);
        return p == null ? SYSTEM : p.getString(KEY_EMOJI, SYSTEM);
    }

    public static void saveWeightForRestart(Context context, int weight) {
        if (context == null) context = Kardinebi.context();
        if (context == null) return;
        int safe = Math.max(100, Math.min(900, Math.round(weight / 50f) * 50));
        AccountAppearance.prefs(context).edit().putInt(KEY_WEIGHT, safe)
                .putBoolean(KEY_BOLD, safe >= 600).apply();
    }

    public static boolean isBold() { return weight() >= 600; }

    public static void setWeight(int weight) {
        int safe = Math.max(100, Math.min(900, Math.round(weight / 50f) * 50));
        SharedPreferences p = prefs();
        if (p != null) p.edit().putInt(KEY_WEIGHT, safe).putBoolean(KEY_BOLD, safe >= 600).apply();
        activeWeight = Integer.valueOf(safe);
        forget();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(Themes::repaintVisible);
    }

    public static void setBold(boolean bold) { setWeight(bold ? 700 : 400); }

    /** Saves a dialog's draft; the running app keeps the old face until restart. */
    public static void saveForRestart(Context context, String letters, String emoji, int weight) {
        if (context == null) context = Kardinebi.context();
        if (context == null) return;
        int safe = Math.max(100, Math.min(900, Math.round(weight / 50f) * 50));
        AccountAppearance.prefs(context).edit()
                .putString(KEY, letters == null ? SYSTEM : letters)
                .putString(KEY_EMOJI, emoji == null ? SYSTEM : emoji)
                .putInt(KEY_WEIGHT, safe)
                .putBoolean(KEY_BOLD, safe >= 600)
                .apply();
    }

    public static void choose(String value) {
        if (value == null) value = SYSTEM;
        chosen = value;
        forget();
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY, value).apply();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(Themes::repaintVisible);
    }

    public static String emoji() {
        String known = chosenEmoji;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return SYSTEM;
        String value = prefs.getString(KEY_EMOJI, SYSTEM);
        chosenEmoji = value;
        return value;
    }

    public static void chooseEmoji(Context context, String value) {
        if (value == null) value = SYSTEM;
        if (context == null) context = Kardinebi.context();
        if (EmojiPackManager.find(context, value) == null) value = SYSTEM;
        chosenEmoji = value;
        forget();
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY_EMOJI, value).apply();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(Themes::repaintVisible);
    }

    private static void forget() {
        face = null;
        looked = false;
        synchronized (Fonts.class) { weightedFaces.clear(); }
    }

    static void reload() {
        chosen = null;
        chosenEmoji = null;
        activeWeight = null;
        forget();
    }

    /** Every pack is in the apk, so one is ready the moment it is chosen. */
    public static boolean emojiReady(Context context) {
        return EmojiPackManager.ready(context, emoji());
    }

    /**
     * Twemoji as a typeface, straight out of the apk.
     *
     * Used for the flags beside the countries whatever else is chosen: a flag
     * is the one emoji where the phone's own is often a pair of letters in a
     * box, and this one is always a flag.
     */
    public static Typeface twemoji(Context context) {
        Typeface known = bundled;
        if (known != null) return known;
        try {
            String asset = packAsset(context, TWEMOJI);
            if (asset == null) return null;
            known = Typeface.createFromAsset(context.getAssets(), asset);
            bundled = known;
            return known;
        } catch (Throwable error) {
            Diary.note("twemoji: " + error);
            return null;
        }
    }

    private static volatile Typeface bundled;

    /** Where a font picked out of the phone's storage is kept. */
    public static File file(Context context) {
        return AccountAppearance.file(context,"font");
    }

    /**
     * Take a font the person picked.
     *
     * Copied rather than remembered: the uri a picker hands over is readable
     * now and not after a restart, and a font that vanishes overnight would be
     * a mystery rather than a setting.
     */
    public static boolean take(Context context, android.net.Uri uri) {
        return take(context, uri, false);
    }

    public static boolean take(Context context, android.net.Uri uri, boolean forEmoji) {
        File temporary = null;
        try {
            File out = forEmoji ? emojiFile(context, EMOJI_FILE) : file(context);
            File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            temporary = new File(parent, out.getName() + ".tmp");
            java.io.InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) return false;
            java.io.FileOutputStream sink = new java.io.FileOutputStream(temporary);
            try {
                byte[] buffer = new byte[16384];
                int read;
                long total = 0;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > 24L * 1024 * 1024) return false;
                    sink.write(buffer, 0, read);
                }
            } finally {
                sink.close();
                in.close();
            }

            // refuse it here rather than have every screen fall back silently
            Typeface test = Typeface.createFromFile(temporary);
            if (test == null) return false;
            if (!temporary.renameTo(out)) return false;
            // Uploading only stages the file. The modal picker keeps the file
            // as a draft until the user presses Save, so cancellation is safe.
            return true;
        } catch (Throwable error) {
            Diary.note("font: " + error);
            return false;
        } finally {
            if (temporary != null && temporary.isFile()) temporary.delete();
        }
    }

    static File emojiFile(Context context, String which) {
        if(EMOJI_FILE.equals(which))return AccountAppearance.file(context,"emoji-custom");
        return new File(context.getFilesDir(), "kardinebi/emoji-" + which);
    }

    // ----------------------------------------------------------- using it

    /** The chosen typeface, or null to leave whatever was there alone. */
    public static Typeface chosenFace() {
        // asked for every label the app writes, so the answer once known is
        // read without waiting for anybody
        if (looked) return face;
        return lookedUp();
    }

    private static synchronized Typeface lookedUp() {
        if (looked) return face;
        // built under the lock and only then called built: the flag used to
        // go up first, so whatever asked next was handed nothing
        face = build();
        looked = true;
        return face;
    }

    /**
     * The letters and the emoji, as one typeface.
     *
     * Android will not let an app replace only the emoji, because a typeface
     * covers whatever it covers and an emoji font has no letters in it. What
     * it will do, from Android 10, is take a family and a list of families to
     * fall back to -- so the answer is one typeface built out of two: the
     * letters from the chosen font, and anything the letters do not cover from
     * the emoji font. Which is exactly what the phone's own font does, with
     * its own emoji font at the end.
     *
     * Below Android 10 there is no such thing, so the letters are changed and
     * the emoji stay the phone's own.
     */
    private static Typeface build() {
        Context context = Kardinebi.context();
        return build(context, name(), emoji());
    }

    /** A non-persistent settings preview; neither draft is written to preferences. */
    public static Typeface preview(Context context, String emojiPack) {
        return build(context, name(), emojiPack);
    }

    public static Typeface preview(Context context, String letters, String emojiPack) {
        return build(context, letters == null ? SYSTEM : letters,
                emojiPack == null ? SYSTEM : emojiPack);
    }

    private static Typeface build(Context context, String letters, String emoji) {

        Typeface plain = null;
        try {
            if (FILE.equals(letters)) {
                File font = context == null ? null : file(context);
                if (font != null && font.isFile()) plain = Typeface.createFromFile(font);
            } else if (TIKTOK.equals(letters)) {
                try {
                    plain = context == null ? null
                            : Typeface.createFromAsset(context.getAssets(), TIKTOK_ASSET);
                } catch (Throwable missing) {
                    plain = null;
                }
                if (plain == null) {
                    // said plainly in the log, then Roboto: never a silent wrong face
                    Diary.note("font: TikTok face not in this app, using Roboto");
                    plain = Typeface.create(SANS, Typeface.NORMAL);
                }
            } else if (!SYSTEM.equals(letters)) {
                // by file first. A phone that does not register "monospace"
                // or "cursive" hands back plain Roboto for them without
                // saying so, and the choice looks broken rather than missing
                File font = systemFont(letters);
                if (font != null) plain = Typeface.createFromFile(font);
                if (plain == null) plain = Typeface.create(letters, Typeface.NORMAL);
            }
        } catch (Throwable error) {
            Diary.note("font: " + error);
        }

        if (context == null || SYSTEM.equals(emoji)
                || android.os.Build.VERSION.SDK_INT < 29) {
            if (context == null) looked = false;
            return plain;
        }

        String asset = packAsset(context, emoji);
        if (asset != null) return hybrid(context, letters, null, asset);

        File pack = emojiFile(context, emoji);
        if (!pack.isFile()) return plain;
        return hybrid(context, letters, pack, null);
    }

    /**
     * The letters and the emoji as one typeface.
     *
     * `pack` is a file on disk, or null for the one inside the apk -- which is
     * read through the asset manager rather than as a file, because an asset
     * is not one.
     */
    private static Typeface hybrid(Context context, String letters, File pack,
                                   String asset) {
        try {
            android.graphics.fonts.FontFamily letterFamily;
            if (TIKTOK.equals(letters)) {
                android.graphics.fonts.Font face = new android.graphics.fonts.Font.Builder(
                        context.getAssets(), TIKTOK_ASSET).build();
                letterFamily = new android.graphics.fonts.FontFamily.Builder(face).build();
            } else {
                File base = FILE.equals(letters) ? file(context) : systemFont(letters);
                if (base == null || !base.isFile()) return null;
                letterFamily = new android.graphics.fonts.FontFamily.Builder(
                        new android.graphics.fonts.Font.Builder(base).build()).build();
            }

            android.graphics.fonts.Font.Builder emojiFont = asset != null
                    ? new android.graphics.fonts.Font.Builder(context.getAssets(), asset)
                    : new android.graphics.fonts.Font.Builder(pack);
            android.graphics.fonts.FontFamily emojiFamily =
                    new android.graphics.fonts.FontFamily.Builder(emojiFont.build()).build();

            return new Typeface.CustomFallbackBuilder(letterFamily)
                    .addCustomFallback(emojiFamily)
                    .build();
        } catch (Throwable error) {
            Diary.note("emoji: " + error);
            return null;
        }
    }

    /**
     * The file behind one of the phone's own font families.
     *
     * Needed twice over. Building letters and emoji into one typeface wants a
     * file, not a family name. And a phone that never registered "monospace"
     * or "cursive" answers Typeface.create with plain Roboto and says nothing.
     */
    private static File systemFont(String family) {
        for (String path : filesFor(family)) {
            File file = new File(path);
            if (file.isFile()) return file;
        }
        for (String path : filesFor(SANS)) {
            File file = new File(path);
            if (file.isFile()) return file;
        }
        return null;
    }

    private static String[] filesFor(String family) {
        if (MONOSPACE.equals(family)) {
            return new String[]{
                    "/system/fonts/DroidSansMono.ttf",
                    "/system/fonts/RobotoMono-Regular.ttf",
                    "/system/fonts/CutiveMono.ttf",
            };
        }
        if (SERIF.equals(family)) {
            return new String[]{
                    "/system/fonts/NotoSerif-Regular.ttf",
                    "/system/fonts/Tinos-Regular.ttf",
                    "/system/fonts/DroidSerif-Regular.ttf",
            };
        }
        if (CURSIVE.equals(family)) {
            return new String[]{
                    "/system/fonts/DancingScript-Regular.ttf",
                    "/system/fonts/NotoSerif-Italic.ttf",
                    "/system/fonts/Roboto-Italic.ttf",
            };
        }
        if (SANS_LIGHT.equals(family)) {
            return new String[]{
                    "/system/fonts/Roboto-Light.ttf",
                    "/system/fonts/NotoSans-Light.ttf",
            };
        }
        if (SANS_CONDENSED.equals(family)) {
            return new String[]{
                    "/system/fonts/RobotoCondensed-Regular.ttf",
                    "/system/fonts/NotoSansCondensed-Regular.ttf",
            };
        }
        return new String[]{
                "/system/fonts/Roboto-Regular.ttf",
                "/system/fonts/NotoSans-Regular.ttf",
                "/system/fonts/DroidSans.ttf",
        };
    }

    /**
     * Set the font on a view that is about to show text.
     *
     * Keeps the weight the view already had: a bold name stays bold, because
     * what is being changed is the shape of the letters and not the emphasis.
     */
    private static final class OwnedFont {
        Typeface original,applied;OwnedFont(Typeface original){this.original=original;}
    }
    private static final java.util.Map<TextView,OwnedFont> ownedFonts=
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<TextView,OwnedFont>());
    private static Typeface styleBase;
    private static Typeface[] styled=new Typeface[4];
    public static void applyTree(android.view.View root) {
        // no font chosen and none to give back: nothing on the screen to visit
        if(chosenFace()==null && weight()==400 && ownedFonts.isEmpty())return;
        long probe=Probe.start();applyTree(root,0,new int[]{4000});Probe.stop(Probe.FONTS,probe);
    }

    /** Whether this kind of view draws pictures with its letters, and must keep its own font. */
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>,Boolean> glyphs=
            new java.util.concurrent.ConcurrentHashMap<Class<?>,Boolean>();
    private static boolean drawsGlyphs(android.view.View view){
        Class<?> type=view.getClass();Boolean known=glyphs.get(type);
        if(known==null){
            // worked out once for each class: this used to make a lowercase
            // copy of the class name for every label the app wrote
            String kind=type.getName().toLowerCase(java.util.Locale.ROOT);
            known=Boolean.valueOf(kind.contains("icon") || kind.contains("emoji"));glyphs.put(type,known);
        }
        return known.booleanValue();
    }
    private static void applyTree(android.view.View root,int depth,int[] budget) {
        if(root==null || depth>40 || --budget[0]<0)return;
        if(root instanceof TextView)apply((TextView)root);
        if(root instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)root;
            for(int i=0;i<group.getChildCount() && budget[0]>0;i++)applyTree(group.getChildAt(i),depth+1,budget);
        }
    }
    public static void apply(TextView view) {
        if(view==null)return;
        Typeface wanted=chosenFace();
        boolean weightOnly = wanted == null && weight() != 400;
        if(wanted==null && !weightOnly && ownedFonts.isEmpty())return;
        if(drawsGlyphs(view))return;
        try{
            Typeface had=view.getTypeface();OwnedFont mine=ownedFonts.get(view);
            if(wanted==null && !weightOnly){
                if(mine!=null){if(had==mine.applied)view.setTypeface(mine.original);ownedFonts.remove(view);}return;
            }
            if(mine==null){mine=new OwnedFont(had);ownedFonts.put(view,mine);}
            else if(had!=mine.applied)mine.original=had;
            Typeface family = wanted != null ? wanted : mine.original;
            Typeface out=keep(family,mine.original);
            if(out!=had)view.setTypeface(out);mine.applied=out;
        }catch(Throwable ignored){}
    }

    public static void setText(TextView view,int resource){view.setText(resource);apply(view);}
    public static void setHint(TextView view,int resource){view.setHint(resource);apply(view);}
    public static void setHint(TextView view,CharSequence text){view.setHint(text);apply(view);}
    public static void setTextAppearance(TextView view,int resource){view.setTextAppearance(resource);apply(view);}
    public static void setTextAppearance(TextView view,Context context,int resource){view.setTextAppearance(context,resource);apply(view);}

    // --------------------------------------------- where TikTok sets one

    public static void setTypeface(TextView view, Typeface face) {
        Typeface wanted=chosenFace();
        boolean weightOnly = wanted == null && weight() != 400;
        if(wanted==null && !weightOnly && ownedFonts.isEmpty()){view.setTypeface(face);return;}
        boolean replace=(wanted!=null || weightOnly) && !drawsGlyphs(view);
        Typeface base = wanted != null ? wanted : face;
        view.setTypeface(replace?keep(base,face):face);
        if(replace){OwnedFont mine=new OwnedFont(face);mine.applied=view.getTypeface();ownedFonts.put(view,mine);}
        else ownedFonts.remove(view);
    }
    public static void setTypeface(TextView view, Typeface face, int style) {
        Typeface wanted=chosenFace();
        boolean weightOnly = wanted == null && weight() != 400;
        if(wanted==null && !weightOnly && ownedFonts.isEmpty()){view.setTypeface(face,style);return;}
        Typeface nativeStyle = Typeface.create(face, style);
        boolean replace=(wanted!=null || weightOnly) && !drawsGlyphs(view);
        Typeface base = wanted != null ? wanted : nativeStyle;
        view.setTypeface(replace ? keep(base, nativeStyle) : nativeStyle);
        if(replace){OwnedFont mine=new OwnedFont(nativeStyle);mine.applied=view.getTypeface();ownedFonts.put(view,mine);}
        else ownedFonts.remove(view);
    }

    public static Typeface setTypeface(android.graphics.Paint paint, Typeface face) {
        Typeface wanted = chosenFace();
        if (wanted == null && weight() == 400) return paint.setTypeface(face);
        return paint.setTypeface(keep(wanted == null ? face : wanted, face));
    }

    /** Cache styled faces by family, weight and italic; system-font mode has many families. */
    private static final java.util.Map<Typeface, java.util.Map<Integer, Typeface>> weightedFaces =
            new java.util.IdentityHashMap<Typeface, java.util.Map<Integer, Typeface>>();

    private static Typeface keep(Typeface wanted, Typeface theirs) {
        Typeface family = wanted != null ? wanted : theirs;
        if (family == null) family = Typeface.DEFAULT;
        try {
            int style = theirs == null ? family.getStyle() : theirs.getStyle();
            boolean italic = (style & Typeface.ITALIC) != 0;
            int requested = weight();
            int key = requested * 2 + (italic ? 1 : 0);
            synchronized (Fonts.class) {
                java.util.Map<Integer, Typeface> perFamily = weightedFaces.get(family);
                if (perFamily == null) {
                    perFamily = new java.util.HashMap<Integer, Typeface>();
                    weightedFaces.put(family, perFamily);
                }
                Typeface cached = perFamily.get(Integer.valueOf(key));
                if (cached == null) {
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        cached = Typeface.create(family, requested, italic);
                    } else {
                        int legacy = (requested >= 600 ? Typeface.BOLD : Typeface.NORMAL)
                                | (italic ? Typeface.ITALIC : Typeface.NORMAL);
                        cached = Typeface.create(family, legacy);
                    }
                    perFamily.put(Integer.valueOf(key), cached);
                }
                return cached;
            }
        } catch (Throwable ignored) { return family; }
    }

    private static SharedPreferences prefs() {
        Context context = Kardinebi.context();
        if (context == null) return null;
        return AccountAppearance.prefs(context);
    }
}
