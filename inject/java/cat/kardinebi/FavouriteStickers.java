package cat.kardinebi;
import java.lang.reflect.Method;
import com.ss.android.ugc.aweme.im.common.model.StickerItem;
import java.util.List;
import android.os.Handler;
import android.os.Looper;
/** TikTok 47.2.41 repository calls, audited against BasicFavouriteStickerPageCell. */
final class FavouriteStickers {
    private static long lastRead,lastLoad;
    private static String account;
    private static Object repository;
    private static int generation;
    static void accountChanged() {
        synchronized (FavouriteStickers.class) {
            account = null; repository = null; lastRead = 0; lastLoad = 0; generation++;
        }
        if (!Streaks.isEnabled()) return;
        final Handler handler = new Handler(Looper.getMainLooper());
        // Start the lookup as soon as the account transition is observed. The
        // delayed retries cover the interval where TikTok's private repository
        // exists but its favorite-sticker flow has not finished loading yet.
        handler.post(() -> { if (Streaks.isEnabled()) load(null); });
        handler.postDelayed(() -> { if (Streaks.isEnabled()) load(null); }, 1000L);
        handler.postDelayed(() -> { if (Streaks.isEnabled()) load(null); }, 3500L);
        handler.postDelayed(() -> { if (Streaks.isEnabled()) load(null); }, 8000L);
    }

    static synchronized boolean read(){
        String uid=Account.liveId();
        if(uid==null || uid.length()==0) uid=Account.id();
        if(uid==null || uid.length()==0)return false;
        if(!uid.equals(account)){account=uid;repository=null;lastRead=lastLoad=0;generation++;Streaks.clearStickers();}
        long now=android.os.SystemClock.uptimeMillis();if(now-lastRead<300)return false;lastRead=now;
        try{
            if(repository==null){
                Class<?> repoClass=Class.forName("X.1HIS");
                Method getter;
                try { getter=repoClass.getDeclaredMethod("LLLLLZIL"); }
                catch (NoSuchMethodException missing) { getter=repoClass.getMethod("LLLLLZIL"); }
                getter.setAccessible(true); repository=getter.invoke(null);
            }
            return readRepository(repository);
        }catch(Throwable ignored){return false;}
    }
    static boolean readRepository(Object repo)throws Exception{
        if(repo==null)return false;
        Method get;
        try { get=repo.getClass().getMethod("getFavouriteStickersFlow"); }
        catch (NoSuchMethodException missing) { get=repo.getClass().getDeclaredMethod("getFavouriteStickersFlow"); }
        get.setAccessible(true);Object flow=get.invoke(repo);if(flow==null)return false;
        Method current;
        try { current=flow.getClass().getMethod("getValue"); }
        catch (NoSuchMethodException missing) { current=flow.getClass().getDeclaredMethod("getValue"); }
        current.setAccessible(true);Object state=current.invoke(flow);if(state==null)return false;
        // The native flow holds 0IJO<List<StickerItem>>, not a bare List.
        Object items=unwrapList(state, 0);
        if(!(items instanceof List)) return false;
        java.util.ArrayList<StickerItem> normalized = new java.util.ArrayList<StickerItem>();
        for (Object value : (List<?>) items) {
            StickerItem sticker = unwrapSticker(value, 0);
            if (sticker != null) normalized.add(sticker);
        }
        Streaks.replaceFavourites(normalized);
        // true means the repository delivered a real snapshot, even when it is
        // empty or identical to the previous one. The UI must still finish its
        // loading state rather than spin forever waiting for a changed list.
        return true;
    }
    private static Object unwrapList(Object value, int depth) {
        if (value == null || depth > 3) return null;
        if (value instanceof List) return value;
        for (String name : new String[]{"LIZ", "getValue", "getList", "getData", "getStickers", "getItems", "getStickerList"}) {
            try {
                Method method;
                try { method=value.getClass().getMethod(name); }
                catch (NoSuchMethodException missing) { method=value.getClass().getDeclaredMethod(name); }
                method.setAccessible(true);
                Object result=method.invoke(value);
                if (result instanceof List) return result;
            } catch(Throwable ignored) { }
        }
        for (Class<?> type=value.getClass(); type!=null && type!=Object.class; type=type.getSuperclass()) {
            for (java.lang.reflect.Field field:type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                try { field.setAccessible(true); Object result=field.get(value); if (result instanceof List) return result; }
                catch(Throwable ignored) { }
            }
        }
        return null;
    }

    private static StickerItem unwrapSticker(Object value, int depth) {
        if (value instanceof StickerItem) return (StickerItem)value;
        if (value == null || depth > 3) return null;
        for (String name : new String[]{"getStickerItem", "getSticker", "getItem", "getData", "getStickerInfo"}) {
            try {
                Method method;
                try { method=value.getClass().getMethod(name); }
                catch (NoSuchMethodException missing) { method=value.getClass().getDeclaredMethod(name); }
                method.setAccessible(true);
                Object result=method.invoke(value);
                if (result instanceof StickerItem) return (StickerItem)result;
            } catch(Throwable ignored) { }
        }
        for (Class<?> type=value.getClass(); type!=null && type!=Object.class; type=type.getSuperclass()) {
            for (java.lang.reflect.Field field:type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (!StickerItem.class.isAssignableFrom(field.getType())) continue;
                try { field.setAccessible(true); Object result=field.get(value); if (result instanceof StickerItem) return (StickerItem)result; }
                catch(Throwable ignored) { }
            }
        }
        return null;
    }

    private static void notifyReady(Runnable ready) {
        if (ready == null) return;
        new Handler(Looper.getMainLooper()).post(ready);
    }

    static void load(Runnable ready){
        read();
        // The private repository may be renamed in a TikTok update. In that
        // case the settings still show recent stickers observed from chat hooks.
        if(repository==null)return;
        long now=android.os.SystemClock.uptimeMillis();if(now-lastLoad<10000)return;
        final int token=generation;final Object repo=repository;
        try{Method refresh=repo.getClass().getMethod("refreshFavouriteStickers",boolean.class,boolean.class,String.class);refresh.setAccessible(true);refresh.invoke(repo,false,false,"kardinebi_settings");lastLoad=now;}
        catch(Throwable ignored){lastLoad=now-9000L;notifyReady(ready);return;}
        Handler handler=new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable(){int tries;
            public void run(){if(token!=generation || !java.util.Objects.equals(account,Account.liveId()))return;
                try {
                    boolean available = readRepository(repo);
                    if (available) { if (ready != null) ready.run(); return; }
                } catch(Throwable ignored) { }
                if(++tries<24) handler.postDelayed(this,500L);
            }
        },500L);
    }
}
