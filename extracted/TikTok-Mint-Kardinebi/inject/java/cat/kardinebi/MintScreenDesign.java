package cat.kardinebi;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/**
 * Mint-owned visual treatment for search and share surfaces only. TikTok's
 * comments/composer, profile and account surfaces remain native because their
 * view hierarchies are rebuilt during media loading and are not stable paint targets.
 * It does not install, replace or intercept native actions.
 */
final class MintScreenDesign {
    // A hard minimum between expensive hierarchy scans plus a real quiet-period
    // debounce. The old throttle scanned at arbitrary points during a fling,
    // which made the theme visibly pop in halfway through a scroll.
    private static final long RESCAN_MS = 180L;
    // Keep one pending scan instead of restarting the timer on every layout callback.
    private static final long QUIET_MS = 60L;
    private static long lastScan;
    private static WeakReference<View> scannedRoot = new WeakReference<>(null);
    private static WeakReference<View> deferredRoot = new WeakReference<>(null);
    private static final Runnable deferredScan = new Runnable() {
        @Override public void run() {
            View target = deferredRoot.get();
            deferredRoot = new WeakReference<>(null);
            if (target == null || !target.isAttachedToWindow()) return;
            long now = android.os.SystemClock.uptimeMillis();
            long remaining = RESCAN_MS - (now - lastScan);
            if (remaining > 0L) {
                deferredRoot = new WeakReference<>(target);
                target.removeCallbacks(this);
                target.postDelayed(this, remaining);
                return;
            }
            scan(target);
        }
    };

    private static final int SEARCH_FIELD = 1;
    private static final int SEARCH_CARD = 2;
    private static final int SEARCH_CHIP = 3;
    private static final int SEARCH_RESULT = 4;
    private static final int SHARE_SURFACE = 5;
    private static final int SHARE_ACTION = 6;
    private static final int SHARE_ICON = 7;
    private static final int COMMENT_SURFACE = 8;
    private static final int COMMENT_INPUT = 9;
    private static final int COMMENT_BUBBLE = 10;
    private static final int COMMENT_ACTION = 11;
    private static final int ACCOUNT_SURFACE = 12;
    private static final int ACCOUNT_HEADER = 13;
    private static final int ACCOUNT_STAT = 14;
    private static final int ACCOUNT_ACTION = 15;
    // Retained as a paint kind for compatibility; profile tabs are now styled
    // only by Md3 to avoid competing background/selection animations.
    private static final int ACCOUNT_TAB = 16;

    private static final int ACT_REPOST = 1;
    private static final int ACT_SHARE = 1 << 1;
    private static final int ACT_COPY = 1 << 2;
    private static final int ACT_SAVE = 1 << 3;
    private static final int ACT_REPORT = 1 << 4;
    private static final int SIG_COMMENT = 1 << 5;
    private static final int SIG_COMMENT_INPUT = 1 << 6;
    private static final int SIG_COMMENT_ROW = 1 << 7;
    private static final int SIG_ACCOUNT_STAT = 1 << 8;
    private static final int SIG_ACCOUNT_ACTION = 1 << 9;
    private static final int SIG_ACCOUNT_PAGE = 1 << 10;
    private static final int SIG_ACCOUNT_MENU = 1 << 11;

    private static final Map<View, Snapshot> searchPaint = new WeakHashMap<>();
    private static final Map<View, Snapshot> sharePaint = new WeakHashMap<>();
    private static final Map<View, Snapshot> commentPaint = new WeakHashMap<>();
    private static final Map<View, Snapshot> accountPaint = new WeakHashMap<>();
    private static final Map<android.content.Context, Palette> palettes = new WeakHashMap<>();

    private MintScreenDesign() {}

    static void apply(View root) {
        if (root == null || !root.isAttachedToWindow()) return;
        View previouslyScanned = scannedRoot.get();
        if (previouslyScanned == null || previouslyScanned != root) {
            cancelDeferred();
            scan(root); // First pass on a new Activity, so its initial screen is styled promptly.
            scannedRoot = new WeakReference<>(root);
            return;
        }

        // Schedule once. Resetting this timer on every scroll/layout callback
        // meant the scan could happen only after the user stopped scrolling.
        View pending = deferredRoot.get();
        if (pending == root) return;
        if (pending != null) pending.removeCallbacks(deferredScan);
        deferredRoot = new WeakReference<>(root);
        root.removeCallbacks(deferredScan);
        root.postDelayed(deferredScan, QUIET_MS);
    }

    private static void cancelDeferred() {
        View pending = deferredRoot.get();
        if (pending != null) pending.removeCallbacks(deferredScan);
        deferredRoot = new WeakReference<>(null);
    }

    private static void scan(View root) {
        if (root == null || !root.isAttachedToWindow()) return;
        lastScan = android.os.SystemClock.uptimeMillis();
        try {
            Frame frame = new Frame(root);
            collect(root, null, 0, frame);

            boolean search = isSearch(frame);
            View share = search ? null : findShareSheet(frame);

            // Comments and profile were deliberately returned to TikTok's own
            // rendering. Their hierarchies are rebuilt while media/GIFs load,
            // so painting them produced a black composer and a visible late pop-in.
            // Restoring the snapshots is safer than guessing a transient tree.
            if (search) styleSearch(frame); else synchronize(searchPaint, new IdentityHashMap<>());
            if (share != null) styleShare(share, frame); else synchronize(sharePaint, new IdentityHashMap<>());
            synchronize(commentPaint, new IdentityHashMap<>());
            synchronize(accountPaint, new IdentityHashMap<>());
        } catch (Throwable error) {
            Diary.detail("Mint screen design: " + error);
        }
    }

    private static final class Node {
        final View view;
        final Node parent;
        final String resource;
        final String words;
        final String type;
        final int depth;
        final List<Node> children = new ArrayList<>();
        int actions;
        int signals;

        Node(View view, Node parent, int depth) {
            this.view = view;
            this.parent = parent;
            this.depth = depth;
            resource = resourceName(view);
            words = viewWords(view);
            type = view.getClass().getName().toLowerCase(Locale.ROOT);
            signals = localSignals(resource + " " + words, view);
            actions = localActions(words);
        }
    }

    private static final class Frame {
        final View root;
        final List<Node> nodes = new ArrayList<>();
        final IdentityHashMap<View, Node> byView = new IdentityHashMap<>();
        final int width;
        final int height;
        int searchFields;
        int searchHints;
        int accountStats;
        int accountActions;
        int accountPages;
        int accountMenus;

        Frame(View root) {
            this.root = root;
            width = Math.max(1, root.getWidth());
            height = Math.max(1, root.getHeight());
        }
    }

    private static void collect(View view, Node parent, int depth, Frame frame) {
        if (view == null || depth > 36 || frame.nodes.size() >= 1500
                || view.getVisibility() != View.VISIBLE) return;
        Node node = new Node(view, parent, depth);
        frame.nodes.add(node);
        frame.byView.put(view, node);
        if (parent != null) parent.children.add(node);

        if (view instanceof EditText && (containsAny(node.resource, "search", "query", "поиск")
                || containsAny(node.words, "search", "поиск", "найти"))) frame.searchFields++;
        if (containsAny(node.resource, "search", "query", "поиск")
                || containsAny(node.words, "search", "поиск", "история поиска", "тренды поиска")) frame.searchHints++;
        if ((node.signals & SIG_ACCOUNT_STAT) != 0) frame.accountStats++;
        if ((node.signals & SIG_ACCOUNT_ACTION) != 0) frame.accountActions++;
        if ((node.signals & SIG_ACCOUNT_PAGE) != 0) frame.accountPages++;
        if ((node.signals & SIG_ACCOUNT_MENU) != 0) frame.accountMenus++;

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = Math.min(group.getChildCount(), 128);
            for (int i = 0; i < count && frame.nodes.size() < 1500; i++)
                collect(group.getChildAt(i), node, depth + 1, frame);
            for (Node child : node.children) {
                node.actions |= child.actions;
                node.signals |= child.signals;
            }
        }
    }

    private static String resourceName(View view) {
        try {
            int id = view.getId();
            if (id == View.NO_ID || id == 0) return "";
            return view.getResources().getResourceEntryName(id).toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String viewWords(View view) {
        StringBuilder value = new StringBuilder();
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getText() != null) value.append(text.getText()).append(' ');
            if (text.getHint() != null) value.append(text.getHint()).append(' ');
        }
        try {
            if (view.getContentDescription() != null) value.append(view.getContentDescription()).append(' ');
        } catch (Throwable ignored) { }
        return value.toString().toLowerCase(Locale.ROOT).trim();
    }

    private static int localSignals(String value, View view) {
        int signals = 0;
        if (containsAny(value, "comment", "comments", "комментарии", "комментарий", "комментировать"))
            signals |= SIG_COMMENT;
        if (view instanceof EditText && (containsAny(value, "comment", "коммент", "ответ", "напишите")))
            signals |= SIG_COMMENT | SIG_COMMENT_INPUT;
        if (containsAny(value, "reply", "ответить", "нравится", "лайк", "like")) signals |= SIG_COMMENT_ROW;
        if (containsAny(value, "followers", "following", "likes", "подписчики", "подписки", "лайки"))
            signals |= SIG_ACCOUNT_STAT;
        if (containsAny(value, "edit profile", "share profile", "add friends", "follow", "message",
                "изменить профиль", "редактировать профиль", "поделиться профилем", "добавить друзей",
                "подписаться", "сообщение", "написать")) signals |= SIG_ACCOUNT_ACTION;
        if (containsAny(value, "profile_header", "profile_page", "user_profile", "my_profile", "профиль"))
            signals |= SIG_ACCOUNT_PAGE;
        if (containsAny(value, "account_menu", "account_sheet", "profile_menu", "account_popup",
                "управление аккаунтом", "сменить аккаунт")) signals |= SIG_ACCOUNT_MENU;
        return signals;
    }

    private static int localActions(String value) {
        int actions = 0;
        if (containsAny(value, "repost", "репост", "повторно опубликовать")) actions |= ACT_REPOST;
        if (containsAny(value, "share", "поделиться", "send to", "отправить")) actions |= ACT_SHARE;
        if (containsAny(value, "copy link", "copy", "копировать", "ссылка", "скопировать")) actions |= ACT_COPY;
        if (containsAny(value, "save video", "download", "save", "сохранить", "скачать")) actions |= ACT_SAVE;
        if (containsAny(value, "report", "жалоба", "пожаловаться")) actions |= ACT_REPORT;
        return actions;
    }

    private static boolean isSearch(Frame frame) {
        return frame.searchFields > 0 && frame.searchHints > 0;
    }

    private static View findShareSheet(Frame frame) {
        Node best = null;
        long bestArea = Long.MAX_VALUE;
        for (Node node : frame.nodes) {
            if (!(node.view instanceof ViewGroup) || node.view == frame.root) continue;
            if ((node.actions & ACT_REPOST) == 0
                    || (node.actions & (ACT_SHARE | ACT_COPY | ACT_SAVE)) == 0) continue;
            if (node.view.getWidth() < frame.width * 0.55f
                    || node.view.getHeight() < dp(node.view, 100)
                    || node.view.getHeight() > frame.height * 0.9f) continue;
            int[] location = location(node.view);
            int top = location[1] - location(frame.root)[1];
            if (top < frame.height * 0.04f || top + node.view.getHeight() < frame.height * 0.42f) continue;
            long area = (long) node.view.getWidth() * node.view.getHeight();
            if (area < bestArea) { bestArea = area; best = node; }
        }
        return best == null ? null : best.view;
    }

    private static View findCommentSheet(Frame frame) {
        Node best = null;
        long bestArea = Long.MAX_VALUE;
        for (Node node : frame.nodes) {
            if (!(node.view instanceof ViewGroup) || node.view == frame.root) continue;
            if ((node.signals & SIG_COMMENT_INPUT) == 0) continue;
            if (!containsAny(node.resource + " " + node.type, "comment", "sheet", "panel", "dialog")
                    && (node.signals & SIG_COMMENT) == 0) continue;
            if (node.view.getWidth() < frame.width * 0.48f
                    || node.view.getHeight() < dp(node.view, 150)
                    || node.view.getHeight() > frame.height * 0.96f) continue;
            long area = (long) node.view.getWidth() * node.view.getHeight();
            if (area < bestArea) { bestArea = area; best = node; }
        }
        return best == null ? null : best.view;
    }

    private static boolean isAccountPage(Frame frame) {
        return (frame.accountStats >= 2 && frame.accountActions >= 1)
                || (frame.accountPages >= 1 && frame.accountActions >= 1)
                || (frame.accountMenus >= 1 && frame.accountActions >= 2);
    }

    private static void styleSearch(Frame frame) {
        IdentityHashMap<View, Integer> desired = new IdentityHashMap<>();
        for (Node node : frame.nodes) {
            View view = node.view;
            String name = node.resource + " " + node.type;
            if (view instanceof EditText || (view instanceof TextView && containsAny(
                    name + " " + node.words, "search_input", "search_edit", "поиск", "search query"))) {
                if (containsAny(name + " " + node.words, "search", "поиск", "query", "найти"))
                    desired.put(view, SEARCH_FIELD);
            }
            if (!(view instanceof ViewGroup)) continue;
            boolean resultGroup = containsAny(name, "result", "suggest", "history", "trend", "hashtag");
            boolean categoryGroup = containsAny(name, "tab", "category", "search_filter", "search_categories");
            if (resultGroup && node.children.size() > 1) desired.put(view, SEARCH_CARD);
            if (containsAny(name, "result", "suggest", "trend", "hashtag") && node.children.size() > 1)
                desired.put(view, SEARCH_RESULT);
            if (categoryGroup && node.children.size() >= 2 && node.children.size() <= 18) {
                for (Node child : node.children)
                    if (child.view instanceof TextView) desired.put(child.view, SEARCH_CHIP);
            }
        }
        synchronize(searchPaint, desired);
    }

    private static void styleShare(View surface, Frame frame) {
        IdentityHashMap<View, Integer> desired = new IdentityHashMap<>();
        Set<View> rows = Collections.newSetFromMap(new IdentityHashMap<View, Boolean>());
        desired.put(surface, SHARE_SURFACE);
        for (Node node : frame.nodes) {
            View view = node.view;
            if (view == surface || !inside(view, surface)) continue;
            if ((node.actions & (ACT_REPOST | ACT_SHARE | ACT_COPY | ACT_SAVE | ACT_REPORT)) == 0) continue;
            View row = actionContainer(view, surface);
            if (row != null && row != surface) {
                desired.put(row, SHARE_ACTION);
                rows.add(row);
            }
        }
        for (Node node : frame.nodes) {
            View view = node.view;
            if (!(view instanceof ImageView) || view == surface || !inside(view, surface)) continue;
            if (view.getWidth() > dp(view, 88) || view.getHeight() > dp(view, 88)) continue;
            for (View row : rows) {
                if (inside(view, row)) { desired.put(view, SHARE_ICON); break; }
            }
        }
        synchronize(sharePaint, desired);
    }

    private static void styleComments(View surface, Frame frame) {
        IdentityHashMap<View, Integer> desired = new IdentityHashMap<>();
        desired.put(surface, COMMENT_SURFACE);
        List<Node> rowCandidates = new ArrayList<>();
        for (Node node : frame.nodes) {
            View view = node.view;
            if (view == surface || !inside(view, surface)) continue;
            String semantic = node.resource + " " + node.type + " " + node.words;
            if (view instanceof EditText || ((node.signals & SIG_COMMENT_INPUT) != 0
                    && containsAny(semantic, "comment_input", "comment_edit", "комментар", "ответ"))) {
                desired.put(view, COMMENT_INPUT);
                continue;
            }
            if (!(view instanceof ViewGroup) || node.children.size() < 2 || node.children.size() > 20) continue;
            int height = view.getHeight(), width = view.getWidth();
            boolean namedRow = containsAny(semantic, "comment_item", "comment_cell", "comment_row", "comment_bubble");
            boolean plausibleRow = width >= frame.width * 0.55f && height >= dp(view, 46)
                    && height <= dp(view, 180) && (node.signals & SIG_COMMENT_ROW) != 0;
            // Image/GIF comments often swap their inner subtree while decoding.
            // Leave those rows native so no background flashes over the media.
            if ((namedRow || plausibleRow) && !containsEmbeddedCommentMedia(node, frame))
                rowCandidates.add(node);
            if ((view.isClickable() || view.isLongClickable()) && containsAny(node.words,
                    "reply", "ответить", "like", "нравится", "лайк", "send", "отправить"))
                desired.put(view, COMMENT_ACTION);
        }
        // Paint only the deepest wide row carrying comment actions, avoiding a
        // stack of nested cards around one native comment.
        for (Node candidate : rowCandidates) {
            boolean hasRowChild = false;
            for (Node other : rowCandidates) {
                if (other != candidate && isDescendant(other, candidate)) { hasRowChild = true; break; }
            }
            if (!hasRowChild) desired.put(candidate.view, COMMENT_BUBBLE);
        }
        synchronize(commentPaint, desired);
    }

    private static boolean isDescendant(Node possibleChild, Node parent) {
        Node cursor = possibleChild.parent;
        while (cursor != null) {
            if (cursor == parent) return true;
            cursor = cursor.parent;
        }
        return false;
    }

    private static boolean isWithin(View child, View ancestor) {
        View cursor = child;
        while (cursor != null) {
            if (cursor == ancestor) return true;
            Object parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    /** Media comments should keep TikTok's native image/GIF tile, not a Mint bubble around it. */
    private static boolean containsEmbeddedCommentMedia(Node row, Frame frame) {
        for (Node child : frame.nodes) {
            if (child == row || !inside(child.view, row.view)) continue;
            View item = child.view;
            String semantic = child.resource + " " + child.type + " " + child.words;
            boolean mediaNamed = containsAny(semantic, "gif", "sticker", "comment_image",
                    "commentimage", "comment_media", "commentmedia", "animated_image", "photo_content");
            if (mediaNamed && (item instanceof ImageView || item instanceof ViewGroup)) return true;
            if (item instanceof ImageView && item.getWidth() >= dp(item, 64)
                    && item.getHeight() >= dp(item, 64)) return true;
        }
        return false;
    }

    private static void styleAccount(View root, Frame frame) {
        IdentityHashMap<View, Integer> desired = new IdentityHashMap<>();
        for (Node node : frame.nodes) {
            View view = node.view;
            if (view == root) continue;
            String semantic = node.resource + " " + node.type + " " + node.words;

            // Profile headers are tonal surfaces, not outlined cards stacked
            // over every child of the profile.
            if (view instanceof ViewGroup && containsAny(semantic,
                    "profile_header", "user_profile_header", "profile_top_container")
                    && view.getWidth() >= frame.width * 0.72f
                    && view.getHeight() >= dp(view, 84) && view.getHeight() <= frame.height * 0.58f) {
                desired.put(view, ACCOUNT_HEADER);
            }
            if ((node.signals & SIG_ACCOUNT_MENU) != 0 && view instanceof ViewGroup
                    && view.getWidth() >= frame.width * 0.60f
                    && view.getHeight() >= dp(view, 100) && view.getHeight() <= frame.height * 0.90f) {
                desired.put(view, ACCOUNT_SURFACE);
            }

            if ((node.signals & SIG_ACCOUNT_ACTION) != 0) {
                View row = actionContainer(view, root);
                if (row != null && row != root && row.getWidth() >= frame.width * 0.12f
                        && row.getHeight() >= dp(row, 28) && row.getHeight() <= dp(row, 112)) {
                    desired.put(row, ACCOUNT_ACTION);
                }
            }

            // Style the statistic's containing cell only. Styling its label,
            // count and outer row separately created the multi-ring pills seen
            // in the screenshot.
            if ((node.signals & SIG_ACCOUNT_STAT) != 0) {
                View row = actionContainer(view, root);
                if (row instanceof ViewGroup && row != root
                        && row.getWidth() >= frame.width * 0.12f
                        && row.getHeight() >= dp(row, 24) && row.getHeight() <= dp(row, 112)) {
                    desired.put(row, ACCOUNT_STAT);
                }
            }

        }
        pruneNested(desired, ACCOUNT_STAT, ACCOUNT_ACTION);
        synchronize(accountPaint, desired);
    }

    private static void pruneNested(IdentityHashMap<View, Integer> desired, int... kinds) {
        Set<Integer> protectedKinds = new java.util.HashSet<>();
        for (int kind : kinds) protectedKinds.add(kind);
        List<View> remove = new ArrayList<>();
        for (Map.Entry<View, Integer> candidate : desired.entrySet()) {
            if (!protectedKinds.contains(candidate.getValue())) continue;
            View child = candidate.getKey();
            Object parent = child == null ? null : child.getParent();
            while (parent instanceof View) {
                Integer parentKind = desired.get(parent);
                if (parentKind != null && parentKind.equals(candidate.getValue())) {
                    remove.add(child);
                    break;
                }
                parent = ((View) parent).getParent();
            }
        }
        for (View view : remove) desired.remove(view);
    }

    private static View actionContainer(View from, View surface) {
        View cursor = from;
        for (int depth = 0; depth < 5; depth++) {
            Object parent = cursor.getParent();
            if (!(parent instanceof View) || parent == surface) return cursor == from ? null : cursor;
            View row = (View) parent;
            if (row == surface) return cursor == from ? null : cursor;
            if ((row.isClickable() || row.isLongClickable()) && row.getHeight() <= dp(row, 160)) return row;
            if (row.getHeight() > dp(row, 160)) return cursor;
            if (row instanceof ViewGroup && ((ViewGroup) row).getChildCount() <= 6
                    && row.getHeight() >= dp(row, 28)) return row;
            cursor = row;
        }
        return cursor == surface ? null : cursor;
    }

    private static boolean inside(View child, View parent) {
        Object cursor = child;
        for (int depth = 0; depth < 36 && cursor instanceof View; depth++) {
            if (cursor == parent) return true;
            cursor = ((View) cursor).getParent();
        }
        return false;
    }

    private static int[] location(View view) {
        int[] result = new int[2];
        try { view.getLocationOnScreen(result); } catch (Throwable ignored) { }
        return result;
    }

    private static void synchronize(Map<View, Snapshot> saved, IdentityHashMap<View, Integer> desired) {
        for (View view : new ArrayList<>(saved.keySet())) {
            if (view == null || !desired.containsKey(view)) {
                if (view != null) saved.get(view).restore(view);
                saved.remove(view);
            }
        }
        for (Map.Entry<View, Integer> entry : desired.entrySet()) {
            View view = entry.getKey();
            if (view == null) continue;
            Snapshot snapshot = saved.get(view);
            if (snapshot == null) {
                snapshot = new Snapshot(view);
                saved.put(view, snapshot);
            }
            paint(view, entry.getValue(), snapshot);
        }
    }

    private static final class Snapshot {
        android.graphics.drawable.Drawable originalBackground;
        android.graphics.drawable.Drawable lastPaintedBackground;
        int left, top, right, bottom, minimumHeight;
        float elevation;
        int originalTextColor;
        boolean hasTextColor;
        int kind = -1;
        int paletteKey;
        boolean selected;

        Snapshot(View view) { rememberNativeState(view); }

        void rememberNativeState(View view) {
            originalBackground = view.getBackground();
            left = view.getPaddingLeft(); top = view.getPaddingTop();
            right = view.getPaddingRight(); bottom = view.getPaddingBottom();
            minimumHeight = view.getMinimumHeight();
            elevation = view.getElevation();
            hasTextColor = view instanceof TextView;
            originalTextColor = hasTextColor ? ((TextView) view).getCurrentTextColor() : 0;
        }

        void restore(View view) {
            view.setBackgroundDrawable(originalBackground);
            view.setPadding(left, top, right, bottom);
            view.setMinimumHeight(minimumHeight);
            view.setElevation(elevation);
            if (hasTextColor && view instanceof TextView)
                ((TextView) view).setTextColor(originalTextColor);
        }
    }

    /** Mint's role palette follows M3 surface hierarchy but is independent of TikTok's theme. */
    private static final class Palette {
        final int page, text, variant, accent, surfaceLow, surface, surfaceHigh;
        final int primaryContainer, outline, onAccent;
        final boolean dark;

        Palette(android.content.Context context, int accentColour) {
            Skin skin = Skin.material3(context);
            page = skin.page;
            text = skin.text;
            accent = accentColour;
            dark = skin.dark();
            variant = mix(text, page, 0.32f);
            surfaceLow = mix(page, text, dark ? 0.075f : 0.035f);
            surface = mix(page, text, dark ? 0.095f : 0.055f);
            surfaceHigh = mix(page, text, dark ? 0.135f : 0.095f);
            primaryContainer = mix(surfaceHigh, accent, dark ? 0.34f : 0.24f);
            outline = mix(page, text, dark ? 0.28f : 0.20f);
            int accentSurface = mix(accent, surfaceHigh, 0.10f);
            float luma = (0.299f * Color.red(accentSurface) + 0.587f * Color.green(accentSurface)
                    + 0.114f * Color.blue(accentSurface)) / 255f;
            onAccent = luma > 0.57f ? 0xFF172018 : 0xFFF8FFF5;
        }
    }

    private static Palette palette(View view) {
        android.content.Context context = view.getContext();
        int accent = Accent.colour();
        Palette value = palettes.get(context);
        if (value == null || value.accent != accent) {
            value = new Palette(context, accent);
            palettes.put(context, value);
        }
        return value;
    }

    private static void paint(View view, int kind, Snapshot snapshot) {
        Palette p = palette(view);
        boolean selected = view.isSelected() || view.isActivated();
        int paletteKey = (p.page * 31 + p.accent) * 31 + p.text;
        boolean stateChanged = snapshot.kind != kind || snapshot.paletteKey != paletteKey
                || snapshot.selected != selected;
        if (!stateChanged && snapshot.lastPaintedBackground != null
                && view.getBackground() == snapshot.lastPaintedBackground) return;
        if (snapshot.lastPaintedBackground != null && view.getBackground() != snapshot.lastPaintedBackground) {
            // Recycler rows can be reused by TikTok. Rebase every saved visual
            // property on the new native row before applying the Mint treatment.
            snapshot.rememberNativeState(view);
        }

        float density = view.getResources().getDisplayMetrics().density;
        GradientDrawable shape = new GradientDrawable();
        int fill;
        float radius;
        boolean outline = false;
        boolean sheet = false;
        int minHeight = 0;
        int horizontal = 0, vertical = 0;
        switch (kind) {
            case SEARCH_FIELD:
                fill = p.surfaceHigh; radius = 28f; outline = true; minHeight = 56;
                horizontal = 16; vertical = 4; break;
            case SEARCH_CARD:
                fill = p.surface; radius = 28f; outline = true; break;
            case SEARCH_CHIP:
                fill = selected ? p.primaryContainer : p.surfaceLow;
                radius = 24f; minHeight = 44; outline = !selected; horizontal = 8; vertical = 3; break;
            case SEARCH_RESULT:
                fill = p.surfaceLow; radius = 22f; minHeight = 56; horizontal = 10; vertical = 4; break;
            case SHARE_SURFACE:
                fill = p.surfaceHigh; radius = 36f; sheet = true; outline = true; break;
            case SHARE_ACTION:
                fill = selected ? p.primaryContainer : p.surfaceLow;
                radius = 22f; minHeight = 64; outline = true; horizontal = 10; vertical = 4; break;
            case SHARE_ICON:
                fill = p.primaryContainer; radius = 18f; minHeight = 48; horizontal = 4; vertical = 4; break;
            case COMMENT_SURFACE:
                fill = p.surface; radius = 28f; sheet = true; outline = false; break;
            case COMMENT_INPUT:
                fill = p.surfaceLow; radius = 28f; minHeight = 56; outline = true; horizontal = 12; vertical = 4; break;
            case COMMENT_BUBBLE:
                fill = p.surfaceLow; radius = 18f; minHeight = 52; outline = false; horizontal = 8; vertical = 4; break;
            case COMMENT_ACTION:
                fill = selected ? p.primaryContainer : p.surface;
                radius = 18f; minHeight = 44; horizontal = 5; vertical = 3; break;
            case ACCOUNT_SURFACE:
                fill = p.surfaceHigh; radius = 34f; sheet = true; outline = true; break;
            case ACCOUNT_HEADER:
                fill = p.surfaceLow; radius = 28f; horizontal = 12; vertical = 4; break;
            case ACCOUNT_STAT:
                fill = p.surfaceLow; radius = 18f; minHeight = 44; horizontal = 6; vertical = 3; break;
            case ACCOUNT_ACTION:
                fill = selected ? p.primaryContainer : p.surface;
                radius = 18f; minHeight = 48; horizontal = 8; vertical = 3; break;
            case ACCOUNT_TAB:
                fill = selected ? p.primaryContainer : 0x00000000;
                radius = 18f; minHeight = 44; horizontal = 8; vertical = 3; break;
            default:
                fill = p.surfaceLow; radius = 20f;
        }

        shape.setColor(fill);
        shape.setCornerRadius(radius * density);
        if (sheet) {
            float top = radius * density, bottom = 20f * density;
            shape.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        }
        if (outline) {
            int border = kind == SEARCH_FIELD && selected ? p.accent : p.outline;
            int alpha = kind == SEARCH_FIELD && selected ? 0xB8 : 0x72;
            shape.setStroke(Math.max(1, Math.round(density)), withAlpha(border, alpha));
        }
        view.setBackgroundDrawable(shape);
        snapshot.lastPaintedBackground = shape;
        if (minHeight > 0) view.setMinimumHeight(Math.max(snapshot.minimumHeight, dp(view, minHeight)));
        if (view instanceof TextView) {
            boolean primary = kind == SEARCH_FIELD || kind == SEARCH_RESULT || kind == SHARE_ACTION
                    || kind == COMMENT_INPUT || kind == COMMENT_BUBBLE || kind == COMMENT_ACTION
                    || kind == ACCOUNT_HEADER || kind == ACCOUNT_STAT || kind == ACCOUNT_ACTION
                    || kind == SHARE_SURFACE || kind == COMMENT_SURFACE
                    || kind == ACCOUNT_SURFACE;
            int color = primary ? p.text : p.variant;
            if ((kind == SEARCH_CHIP || kind == ACCOUNT_TAB) && selected) color = p.text;
            ((TextView) view).setTextColor(color);
        }
        view.setElevation(sheet ? Math.max(snapshot.elevation, dp(view, 3)) : snapshot.elevation);
        int h = Math.round(horizontal * density), v = Math.round(vertical * density);
        view.setPadding(snapshot.left + h, snapshot.top + v, snapshot.right + h, snapshot.bottom + v);

        boolean selectionChanged = snapshot.selected != selected;
        boolean firstPaint = snapshot.kind == -1;
        snapshot.kind = kind;
        snapshot.paletteKey = paletteKey;
        snapshot.selected = selected;
        if (view.isAttachedToWindow() && ((firstPaint && (kind == SHARE_SURFACE || kind == COMMENT_SURFACE
                || kind == ACCOUNT_SURFACE || kind == SEARCH_FIELD || kind == ACCOUNT_HEADER))
                || (selectionChanged && selected && (kind == SEARCH_CHIP || kind == ACCOUNT_TAB
                || kind == COMMENT_ACTION || kind == ACCOUNT_ACTION || kind == SHARE_ACTION)))) {
            Motion.change(view);
        }
    }

    private static int dp(View view, int value) {
        return Math.max(1, Math.round(value * view.getResources().getDisplayMetrics().density));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static int mix(int from, int to, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(((from >> 16) & 255) * (1f - a) + ((to >> 16) & 255) * a);
        int green = Math.round(((from >> 8) & 255) * (1f - a) + ((to >> 8) & 255) * a);
        int blue = Math.round((from & 255) * (1f - a) + (to & 255) * a);
        return (from & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static boolean containsAny(String text, String... patterns) {
        if (text == null) return false;
        for (String pattern : patterns) if (text.contains(pattern)) return true;
        return false;
    }
}
