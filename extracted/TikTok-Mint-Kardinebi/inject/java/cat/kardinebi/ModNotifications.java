package cat.kardinebi;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import java.util.Locale;

/** Filters notifications posted by TikTok itself. Native Android permission remains authoritative. */
public final class ModNotifications {
    static final String MESSAGES="notify_messages", PROMOS="notify_promos", OTHER="notify_other", NO_PROMPTS="notify_no_prompts";
    static final String REPEATED_REPOSTS="notify_repeated_reposts", FRIEND_MESSAGES="notify_friend_messages", OWN_VIDEO_ACTIVITY="notify_own_video_activity";
    private static volatile boolean[] options;

    static boolean option(int i) {
        boolean[] values=options;
        if(values==null) {
            values=new boolean[]{true,true,true,false,true,true,true};
            try {
                android.content.SharedPreferences p=AccountAppearance.prefs(Kardinebi.context());
                String[] keys={MESSAGES,PROMOS,OTHER,NO_PROMPTS,REPEATED_REPOSTS,FRIEND_MESSAGES,OWN_VIDEO_ACTIVITY};
                for(int n=0;n<keys.length;n++) values[n]=p.getBoolean(keys[n],values[n]);
            } catch(Throwable ignored) {}
            if(Kardinebi.context()!=null) options=values;
        }
        return i>=0 && i<values.length && values[i];
    }

    static void reload(){options=null;}

    static void set(String key,boolean on) {
        android.content.SharedPreferences.Editor edit=AccountAppearance.prefs(Kardinebi.context()).edit().putBoolean(key,on);
        if(!on&&!NO_PROMPTS.equals(key)) edit.putBoolean(NO_PROMPTS,true);
        edit.apply(); reload();
    }

    public static boolean noPrompts(){return option(3);}

    /** Text and small metadata fields are the best available public signal in a posted push notification. */
    private static String payload(Notification n) {
        StringBuilder out=new StringBuilder();
        try { append(out,n.tickerText); } catch(Throwable ignored) {}
        try {
            Bundle extras=n.extras;
            if(extras!=null) {
                append(out,extras.getCharSequence(Notification.EXTRA_TITLE));
                append(out,extras.getCharSequence(Notification.EXTRA_TEXT));
                append(out,extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
                append(out,extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
                append(out,extras.getCharSequence(Notification.EXTRA_INFO_TEXT));
                // Some TikTok builds place localised push strings under nonstandard keys.
                for(String key:extras.keySet()) {
                    Object value=extras.get(key);
                    if(value instanceof CharSequence) append(out,(CharSequence)value);
                    else if(value instanceof CharSequence[]) for(CharSequence line:(CharSequence[])value) append(out,line);
                }
            }
        } catch(Throwable ignored) {}
        return out.toString().toLowerCase(Locale.ROOT).replace('ё','е').replace('’','\'');
    }

    private static void append(StringBuilder out,CharSequence value) {
        if(value!=null && value.length()>0) out.append(' ').append(value);
    }

    private static boolean has(String text,String... parts) {
        for(String part:parts) if(text.contains(part)) return true;
        return false;
    }

    /** A deliberately narrow match: the text must mention reposting and a prior repost by the recipient. */
    static boolean isRepeatedRepost(String text) {
        boolean repost=has(text,"repost","re-post","репост","репостнув","репостил","репостила","репостнули","репостнул","репостила","репостила");
        boolean prior=has(text,
            "video you reposted","video you've reposted","video you have reposted","you reposted",
            "you've reposted","you have reposted","you already reposted","your repost",
            "которое вы репост","которую вы репост","который вы репост","которое ты репост",
            "которую ты репост","который ты репост","вы уже репост","ты уже репост",
            "которое вы репостнули","которое ты репостнул","ви вже репост","яке ви репост");
        return repost && prior;
    }

    static boolean isOwnVideoActivity(String text) {
        return has(text,
            "your video","your videos","your post","your photo","on your video","on your post",
            "commented on your","liked your video","likes your video","shared your video","replied to your video",
            "ваше видео","ваши видео","твоё видео","твое видео","твой ролик","ваш ролик",
            "вашей публикац","вашу публикац","под вашим видео","под твоим видео","к вашему видео",
            "вашему видео","на ваше видео","ваше видео понравилось","комментарий к вашему видео",
            "ваше відео","ваші відео","під вашим відео","під твоїм відео","до вашого відео",
            "вашого відео","вашій публікац","вашу публікац");
    }

    static boolean isFriendMessage(Notification notification,String text) {
        if(Notification.CATEGORY_MESSAGE.equals(notification.category)) return true;
        return has(text,"sent you a message","new message from","message from your friend","direct message",
            "написал вам","написала вам","написали вам","отправил вам сообщение","отправила вам сообщение",
            "новое сообщение от","сообщение от друга","сообщение от подруги","повідомлення від",
            "написав вам","написала вам","надіслав повідомлення","надіслала повідомлення");
    }

    static boolean allow(Notification notification) {
        if(notification==null) return false;
        String text=payload(notification);
        if(isRepeatedRepost(text) && !option(4)) return false;
        // Check authored-video activity before generic message classification: comment/reply pushes may
        // use CATEGORY_MESSAGE even though they are not direct messages.
        if(isOwnVideoActivity(text) && !option(6)) return false;
        if(isFriendMessage(notification,text) && !option(5)) return false;
        return option(Notification.CATEGORY_MESSAGE.equals(notification.category)?0:
            Notification.CATEGORY_PROMO.equals(notification.category)?1:2);
    }

    public static void notify(NotificationManager manager,int id,Notification notification) {
        if(manager==null||!allow(notification)) return;
        try { manager.notify(id,notification); }
        catch(SecurityException error) { Diary.note("notifications: system permission denied"); }
    }

    public static void notify(NotificationManager manager,String tag,int id,Notification notification) {
        if(manager==null||!allow(notification)) return;
        try { manager.notify(tag,id,notification); }
        catch(SecurityException error) { Diary.note("notifications: system permission denied"); }
    }

    static void settings(Context c) {
        try {
            Intent intent;
            if(Build.VERSION.SDK_INT>=26) {
                intent=new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
                intent.putExtra("android.provider.extra.APP_PACKAGE",c.getPackageName());
            } else intent=new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",Uri.parse("package:"+c.getPackageName()));
            c.startActivity(intent);
        } catch(Throwable error) { Diary.note("notification settings: "+error); Screen.say(Text.PLAYER_NOT_READY); }
    }
}
