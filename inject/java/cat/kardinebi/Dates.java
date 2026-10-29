package cat.kardinebi;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;

/** Publication date for TikTok 47.4.42, scoped to native video renderers and author rows. */
public final class Dates {
    static void reloadSettings(){on=null;DateOverlay.update();}

    private Dates() {}

    public static final String KEY = "always_date";

    private static volatile Boolean on;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private static String selectedAid;
    private static String selectedDate;
    /** Full publication data of the post that is on screen, for the date sheet. */
    static volatile DateInfo.Data selectedInfo;
    private static final java.util.LinkedHashMap<String,java.lang.ref.WeakReference<android.view.View>> authorRows=new java.util.LinkedHashMap<>();
    /** Bind at view creation; adapters may not have delivered the post model yet. */
    public static void bindAuthor(Object holder) {
        if (holder == null) return;
        bindAuthor(holder, resolvePost(holder));
    }

    /** Bind again when a recycled author cell receives its new Aweme model. */
    public static void bindAuthor(Object holder, Object post) {
        if (holder == null) return;
        if (post == null) post = resolvePost(holder);
        String aid = NativeRead.aid(post);
        if (aid == null || aid.isEmpty()) aid = NativeRead.aid(holder);
        Object content = first(holder, "getContentView", "getView", "itemView", "getItemView", "Xb");
        if (!(content instanceof android.view.View) && holder instanceof android.view.View) content = holder;
        if (!(content instanceof android.view.View)) return;
        final android.view.View view = (android.view.View) content;
        long timestamp = createdAt(post);
        final String publication = label(timestamp, System.currentTimeMillis() / 1000L);
        if (publication == null || aid == null || aid.isEmpty()) {
            MAIN.post(() -> {
                forgetAuthorRow(view);
                view.setTag(0x7e0d0334, null);
                view.setTag(DateInfo.CELL_TAG, null);
                DateOverlay.unregister(view);
            });
            return;
        }
        final String postId = aid;
        final Object visiblePost = post;
        MAIN.post(() -> {
            if (!view.isAttachedToWindow()) return;
            forgetAuthorRow(view);
            view.setTag(0x7e0d0334, postId);
            view.setTag(DateInfo.CELL_TAG, DateInfo.Data.of(visiblePost));
            authorRows.put(postId, new java.lang.ref.WeakReference<>(view));
            while (authorRows.size() > 48) authorRows.remove(authorRows.keySet().iterator().next());
            DateOverlay.register(view, publication);
            if (postId.equals(selectedAid) && view.isShown()) DateOverlay.bind(view, publication);
        });
    }

    private static void forgetAuthorRow(android.view.View view) {
        java.util.Iterator<java.util.Map.Entry<String,java.lang.ref.WeakReference<android.view.View>>> rows = authorRows.entrySet().iterator();
        while (rows.hasNext()) {
            android.view.View row = rows.next().getValue().get();
            if (row == null || row == view) rows.remove();
        }
    }

    private static Object resolvePost(Object holder) {
        Object current = holder;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            String aid = NativeRead.aid(current);
            if (aid != null && !aid.isEmpty() && createdAt(current) > 0L) return current;
            Object next = first(current, "getAweme", "getItem", "getAwemeModel",
                    "getCurrentAweme", "getModel", "getVideoItem", "getContent");
            if (next == null || next == current) {
                Object fallback = NativeRead.item(current);
                if (fallback == null || fallback == current) break;
                next = fallback;
            }
            current = next;
        }
        return current == null ? holder : current;
    }

    private static Object first(Object target, String... accessors) {
        if (target == null) return null;
        for (String name : accessors) {
            try {
                java.lang.reflect.Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                Object value = method.invoke(target);
                if (value != null) return value;
            } catch (Throwable ignored) { }
            try {
                java.lang.reflect.Method method = target.getClass().getDeclaredMethod(name);
                method.setAccessible(true);
                Object value = method.invoke(target);
                if (value != null) return value;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static long createdAt(Object post) {
        Object value = first(post, "getCreateTime", "getCreateTimeInSeconds", "getPublishTime", "getCreateTimestamp", "getAwemeCreateTime");
        if (value instanceof Number) return ((Number)value).longValue();
        if (post != null) {
            for (String fieldName : new String[]{"createTime", "create_time", "publishTime", "createTimestamp"}) {
                try {
                    java.lang.reflect.Field field = post.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true); value = field.get(post);
                    if (value instanceof Number) return ((Number)value).longValue();
                } catch (Throwable ignored) { }
            }
        }
        return 0L;
    }

    public static boolean isEnabled() {
        Boolean known = on;
        if (known != null) return known.booleanValue();
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        boolean value = prefs.getBoolean(KEY, true);
        on = Boolean.valueOf(value);
        return value;
    }

    public static void setEnabled(boolean enabled) {
        on = Boolean.valueOf(enabled);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY, enabled).apply();
        MAIN.post(() -> DateOverlay.update());
    }

    /** Scoped by the patcher to caption renderers, not arbitrary model reads. */
    public static String getDesc(Object post) {
        if (post==null) return null;
        String original=null;
        try {
            Object value=post.getClass().getMethod("getDesc").invoke(post);
            original=value instanceof String ? (String)value : null;
            // The date has its own row next to the author, never inside a caption.
            return original;
        } catch (Throwable error) {Diary.note("date caption: "+error);return original;}
    }
    /** Called only when TikTok selects a visible feed cell, never during prefetch. */
    public static void bindPost(Object holder,Object post) {
        VideoActions.selected(holder,post);
        try {
            Object content = first(holder, "getContentView", "getView", "getItemView", "Xb");
            if (!(content instanceof android.view.View) && holder instanceof android.view.View) content = holder;
            if (!(content instanceof android.view.View) || post == null) return;
            final android.view.View view = (android.view.View) content;
            long time = createdAt(post);
            final String date = label(time, System.currentTimeMillis()/1000L);
            if (date == null) {
                MAIN.post(() -> {
                    selectedAid = null; selectedDate = null; selectedInfo = null;
                    if (holder instanceof android.view.View) DateOverlay.unregister((android.view.View) holder);
                });
                return;
            }
            Object user = first(post, "getAuthor", "getUser", "getAuthorInfo");
            Object nick = first(user, "getNickname", "getNickName", "getUniqueId");
            final String author = nick instanceof String ? (String)nick : null;
            String id = NativeRead.aid(post);
            final String aid = id == null ? "" : id;
            final DateInfo.Data info = DateInfo.Data.of(post);
            MAIN.post(() -> {
                selectedAid=aid;selectedDate=date;selectedInfo=info;
                if(aid.length()>0)view.setTag(0x7e0d0334,aid);
                if(info!=null)view.setTag(DateInfo.CELL_TAG,info);
                java.lang.ref.WeakReference<android.view.View> saved=authorRows.get(aid);android.view.View row=saved==null?null:saved.get();
                android.view.View target = row!=null && aid.equals(row.getTag(0x7e0d0334)) && row.isAttachedToWindow() && row.isShown()?row:view;
                DateOverlay.bind(target,date,author);
            });
        } catch(Throwable error){Diary.note("visible date: "+error.getClass().getSimpleName());}
    }

    static String label(long seconds,long now) {
        if (seconds > 100000000000000L) seconds /= 1000000L; // microseconds
        else if (seconds > 100000000000L) seconds /= 1000L;   // milliseconds
        if (seconds<1000000000L || seconds>now+86400L) return null;
        return new java.text.SimpleDateFormat("dd.MM.yyyy",java.util.Locale.getDefault())
                .format(new java.util.Date(seconds*1000L));
    }

    private static SharedPreferences prefs() {
        Context context=Kardinebi.context();
        return context==null ? null : context.getSharedPreferences(Kardinebi.PREFS,Context.MODE_PRIVATE);
    }
}
