package cat.kardinebi;
import android.view.View;import android.view.ViewGroup;import android.view.ViewTreeObserver;import android.widget.TextView;import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.regex.Pattern;
/** Reuse a semantic native time row when present; fall back inside the current post cell. */
final class DateOverlay {
    // Resource IDs are reassigned between TikTok releases. In 47.4.42 this old
    // 47.2.41 ID resolves to an unrelated obfuscated resource, so never trust it.
    private static final String[] DATE_ID_WORDS={"posttime","post_time","post_time_view","video_date","video_time","publish_time","publication_time","timestamp","create_time","feed_time","tvdate","date_label","author_date"};
    private static final Pattern RELATIVE_DATE=Pattern.compile("(?iu)^(?:today|yesterday|сегодня|вчера|позавчера|昨天|\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[./-]\\d{1,2}(?:[./-]\\d{2,4})?|\\d{1,2}\\s+(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?|янв(?:аря)?|фев(?:раля)?|мар(?:та)?|апр(?:еля)?|мая|июн(?:я)?|июл(?:я)?|авг(?:уста)?|сен(?:тября)?|окт(?:ября)?|ноя(?:бря)?|дек(?:абря)?)(?:[,.]?\\s+\\d{4})?|(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?|янв(?:аря)?|фев(?:раля)?|мар(?:та)?|апр(?:еля)?|мая|июн(?:я)?|июл(?:я)?|авг(?:уста)?|сен(?:тября)?|окт(?:ября)?|ноя(?:бря)?|дек(?:абря)?)\\s+\\d{1,2}(?:,?\\s+\\d{4})?|\\d+\\s*(?:s|sec|secs|m|min|mins|h|hr|hrs|d|day|days|w|wk|week|weeks|mo|month|months|y|yr|year|years|сек(?:унд(?:а|ы)?)?|мин(?:ут(?:а|ы)?)?|ч(?:ас(?:а|ов)?)?|д(?:ень|ня|ней)?|недел(?:я|и|ь)?|месяц(?:а|ев)?|год(?:а|лет)?)\\s*(?:\\.?\\s*)?(?:ago|назад)?|\\d+\\s*(?:小时前|天前|周前|个月前|年前))$");
    private static WeakReference<View> source=new WeakReference<>(null),watched=new WeakReference<>(null);
    private static WeakReference<TextView> label=new WeakReference<>(null);
    private static ViewTreeObserver.OnPreDrawListener observer;
    private static WeakReference<ViewTreeObserver> tree=new WeakReference<>(null);
    private static String date;private static CharSequence original;private static float alpha;private static int visibility;
    private static boolean applied,tapped,wasClickable;private static long searched,chosenAt;
    private static final java.util.LinkedHashMap<java.lang.ref.WeakReference<View>,String> candidates=new java.util.LinkedHashMap<>();
    private static WeakReference<ViewGroup> fallbackParent=new WeakReference<>(null);private static WeakReference<TextView> fallback=new WeakReference<>(null);private static int fallbackBottom;private static boolean fallbackSibling;private static WeakReference<View> fallbackAnchor=new WeakReference<>(null);
    static void register(View view,String text){if(view==null||text==null)return;java.util.Iterator<java.lang.ref.WeakReference<View>> it=candidates.keySet().iterator();while(it.hasNext()){View old=it.next().get();if(old==null||old==view)it.remove();}candidates.put(new WeakReference<>(view),text);while(candidates.size()>32)candidates.remove(candidates.keySet().iterator().next());
        android.graphics.Rect rect=new android.graphics.Rect();if(view.isShown()&&view.getGlobalVisibleRect(rect)){bind(view,text);}else if(source.get()==null){source=new WeakReference<>(view);date=text;resume(view.getRootView());}
    }
    static void unregister(View view){
        if(view==null)return;
        java.util.Iterator<java.lang.ref.WeakReference<View>> it=candidates.keySet().iterator();
        while(it.hasNext()){
            View old=it.next().get();
            if(old==null||old==view)it.remove();
        }
        if(source.get()==view){
            detach();
            source=new WeakReference<>(null);
            date=null;
        }
    }
    private static void chooseVisible(){long now=android.os.SystemClock.uptimeMillis();if(now-chosenAt<250)return;chosenAt=now;View root=watched.get();if(root==null)return;android.graphics.Rect rect=new android.graphics.Rect();View best=null;String text=null;int area=0;
        for(java.util.Map.Entry<WeakReference<View>,String> entry:candidates.entrySet()){View v=entry.getKey().get();if(v==null||v.getRootView()!=root||!v.isShown()||!v.isAttachedToWindow()||!v.getGlobalVisibleRect(rect))continue;int size=rect.width()*rect.height();if(size>area){area=size;best=v;text=entry.getValue();}}
        if(best!=null&&best!=source.get()){TextView old=label.get();if(old!=null)restore(old);removeFallback();label=new WeakReference<>(null);source=new WeakReference<>(best);date=text;searched=0;}
    }
    private static void removeFallback(){
        ViewGroup group=fallbackParent.get();TextView view=fallback.get();
        if(group!=null&&view!=null&&view.getParent()==group){
            group.removeView(view);
            if(!fallbackSibling && fallbackAnchor.get()==group)group.setPadding(group.getPaddingLeft(),group.getPaddingTop(),group.getPaddingRight(),fallbackBottom);
        }
        fallback=new WeakReference<>(null);fallbackParent=new WeakReference<>(null);fallbackAnchor=new WeakReference<>(null);fallbackSibling=false;
    }
    private static TextView fallback(View cell){
        if(cell==null || cell.getTag(0x7e0d0334)==null)return null;
        if(cell instanceof android.widget.FrameLayout){
            if(fallback.get()!=null&&fallbackParent.get()==cell)return fallback.get();
            removeFallback();android.widget.FrameLayout group=(android.widget.FrameLayout)cell;fallbackBottom=group.getPaddingBottom();
            int height=(int)Math.ceil(18*group.getResources().getDisplayMetrics().density);
            group.setPadding(group.getPaddingLeft(),group.getPaddingTop(),group.getPaddingRight(),fallbackBottom+height);
            TextView view=new TextView(group.getContext());styleFallback(view,group);android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,height);lp.gravity=android.view.Gravity.BOTTOM|android.view.Gravity.LEFT;lp.leftMargin=group.getPaddingLeft();lp.bottomMargin=fallbackBottom;group.addView(view,lp);
            fallback=new WeakReference<>(view);fallbackParent=new WeakReference<>(group);fallbackAnchor=new WeakReference<>(group);fallbackSibling=false;return view;
        }
        // Some builds put the author row inside a vertical stack instead of a
        // FrameLayout. Add the date just below that row rather than at the end
        // of a horizontal author/Follow row or over the video itself.
        Object parent=cell.getParent();
        if(parent instanceof android.widget.LinearLayout && isVertical((android.widget.LinearLayout)parent)){
            android.widget.LinearLayout group=(android.widget.LinearLayout)parent;
            if(fallback.get()!=null&&fallbackParent.get()==group&&fallbackSibling)return fallback.get();
            removeFallback();int index=0;for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i)==cell){index=i+1;break;}
            TextView view=new TextView(group.getContext());styleFallback(view,group);android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT);try{lp.getClass().getField("topMargin").setInt(lp,(int)Math.ceil(2*group.getResources().getDisplayMetrics().density));}catch(Throwable ignored){}
            try{group.getClass().getMethod("addView",View.class,int.class,ViewGroup.LayoutParams.class).invoke(group,view,index,lp);}catch(Throwable ignored){group.addView(view,lp);}fallback=new WeakReference<>(view);fallbackParent=new WeakReference<>(group);fallbackAnchor=new WeakReference<>(cell);fallbackSibling=true;return view;
        }
        return null;
    }
    private static boolean isVertical(android.widget.LinearLayout group){
        try{return ((Integer)group.getClass().getMethod("getOrientation").invoke(group))==1;}catch(Throwable ignored){return false;}
    }
    private static void styleFallback(TextView view,View anchor){
        view.setTextSize(11);
        boolean dark = Themes.isDark();
        view.setTextColor(dark ? 0xFFF6F0FA : 0xFF211A26);
        view.setShadowLayer(dark ? 0.5f : 1.5f, 0, 1, dark ? 0x55000000 : 0x66FFFFFF);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        android.graphics.drawable.GradientDrawable pill=new android.graphics.drawable.GradientDrawable();
        pill.setColor(dark ? 0xB823202B : 0xE6F7F2FA);
        pill.setStroke(Math.max(1,(int)Math.ceil(anchor.getResources().getDisplayMetrics().density)),
                (SecondaryAccent.colour() & 0x00FFFFFF) | 0x4A000000);
        pill.setCornerRadius((float)Math.ceil(12*anchor.getResources().getDisplayMetrics().density));
        view.setBackgroundDrawable(pill);
        float d=anchor.getResources().getDisplayMetrics().density;view.setPadding((int)(5*d),(int)(1*d),(int)(5*d),(int)(1*d));
    }
    static void bind(View view,String text){bind(view,text,null);}
    static void bind(View view,String text,String author){
        detach();source=new WeakReference<>(view);date=text;searched=0;
        if(view==null || text==null)return;
        view.post(()->{if(source.get()==view)resume(view.getRootView());});
    }
    /** Reconnect after the window returns or its ViewTreeObserver was replaced. */
    static void resume(View root){
        View cell=source.get();if(cell==null || date==null || cell.getRootView()!=root)return;
        ViewTreeObserver current=root.getViewTreeObserver();
        if(current!=tree.get() || observer==null){
            ViewTreeObserver old=tree.get();if(old!=null && old.isAlive() && observer!=null)old.removeOnPreDrawListener(observer);
            watched=new WeakReference<>(root);tree=new WeakReference<>(current);
            observer=()->{long probe=Probe.start();update();Probe.stop(Probe.DATE_FRAME,probe);return true;};current.addOnPreDrawListener(observer);
        }
        TextView found=find(cell), row=label.get();
        if(found!=row){if(row!=null)restore(row);label=new WeakReference<>(null);searched=0;}
        update();
    }

    private static TextView find(View view){
        View cursor=view;
        for(int depth=0;cursor!=null&&depth<10;depth++){
            TextView named=findCandidate(cursor,0,new int[]{320},true);
            if(named!=null)return named;
            TextView temporal=findCandidate(cursor,0,new int[]{320},false);
            if(temporal!=null)return temporal;
            Object parent=cursor.getParent();if(!(parent instanceof View))break;
            String type=parent.getClass().getName();if(type.contains("RecyclerView")||type.contains("ViewPager"))break;
            cursor=(View)parent;
        }
        return null;
    }
    private static TextView findCandidate(View view,int depth,int[] budget,boolean byId){
        if(view==null||depth>16||budget[0]--<=0||view.getVisibility()!=View.VISIBLE)return null;
        if(view instanceof TextView){
            TextView text=(TextView)view;String name=resourceName(view);
            if(byId&&isDateId(name))return text;
            if(!byId&&looksLikeDate(text.getText()))return text;
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;int many=Math.min(group.getChildCount(),80);for(int i=0;i<many;i++){TextView found=findCandidate(group.getChildAt(i),depth+1,budget,byId);if(found!=null)return found;}}
        return null;
    }
    private static String resourceName(View view){
        try{int id=((Integer)view.getClass().getMethod("getId").invoke(view)).intValue();if(id==0||id==-1)return "";Object resources=view.getClass().getMethod("getResources").invoke(view);return String.valueOf(resources.getClass().getMethod("getResourceEntryName",int.class).invoke(resources,id)).toLowerCase(Locale.ROOT);}catch(Throwable ignored){return "";}
    }
    private static boolean isDateId(String name){
        if(name==null||name.length()==0||name.equals("yau"))return false;
        for(String word:DATE_ID_WORDS)if(name.contains(word))return true;return false;
    }
    private static boolean looksLikeDate(CharSequence value){
        if(value==null)return false;String text=value.toString().trim().toLowerCase(Locale.ROOT).replaceAll("^[·•\\-\\s]+|[·•\\-\\s]+$","");
        return RELATIVE_DATE.matcher(text).matches();
    }
    /**
     * Whether the comments are open over the video, asked four times a second.
     *
     * With the screen saver off nobody keeps the answer, and finding it out is
     * a walk over every view on the screen. This is called before every frame,
     * so that walk was being taken a hundred and twenty times a second for as
     * long as a video with a date on it was showing.
     */
    private static long panelAsked;private static boolean panelWas;private static WeakReference<View> panelRoot=new WeakReference<>(null);
    private static boolean panelOpen(View root){
        long now=android.os.SystemClock.uptimeMillis();
        if(panelRoot.get()==root&&now-panelAsked<250L)return panelWas;
        panelAsked=now;panelRoot=new WeakReference<>(root);panelWas=Dim.panelOpen(root);return panelWas;
    }
    static void update(){
        if(!Dates.isEnabled()){TextView old=label.get();if(old!=null)restore(old);if(fallback.get()!=null){removeFallback();label=new WeakReference<>(null);}return;}
        chooseVisible();View cell=source.get();if(cell==null || date==null)return;
        TextView row=label.get();if(row!=null && (!row.isAttachedToWindow() || row.getRootView()!=cell.getRootView())){restore(row);label=new WeakReference<>(null);row=null;searched=0;}if(row==null){long now=android.os.SystemClock.uptimeMillis();if(now-searched<250)return;searched=now;row=find(cell);if(row==null)row=fallback(cell);if(row==null)return;
            label=new WeakReference<>(row);original=row.getText();alpha=row.getAlpha();visibility=row.getVisibility();wasClickable=row.isClickable();Fonts.apply(row);}
        Object parent=row.getParent();View anchor=parent instanceof View?(View)parent:cell;
        boolean enabled=Dates.isEnabled();boolean show=enabled && anchor.isShown() && anchor.isAttachedToWindow() && !panelOpen(cell.getRootView());
        if(!enabled){restore(row);return;}
        CharSequence current=row.getText();if(applied && current!=null && !date.contentEquals(current))original=current;
        if(row.getText()==null||!date.contentEquals(row.getText()))row.setText(date);
        row.setTextColor(Themes.isDark() ? 0xFFF6F0FA : 0xFF211A26);
        if(Math.abs(row.getAlpha()-1f)>.001f)row.setAlpha(1f);
        int next=show?View.VISIBLE:View.GONE;if(row.getVisibility()!=next)row.setVisibility(next);applied=true;
        // A tap on the date opens the full publication time (date, time to the second, zone, age).
        // Never replace an existing native click handler. The extra details sheet
        // is attached only when this date row has no native click behavior.
        if(!tapped && !wasClickable){row.setOnClickListener(tap->DateInfo.open(tap,source.get()));tapped=true;}
    }
    private static void restore(TextView row){if(tapped){row.setOnClickListener(null);row.setClickable(wasClickable);tapped=false;}if(!applied)return;if(date!=null && row.getText()!=null && date.contentEquals(row.getText()))row.setText(original);row.setAlpha(alpha);row.setVisibility(visibility);applied=false;}
    private static void detach(){removeFallback();TextView row=label.get();if(row!=null)restore(row);ViewTreeObserver root=tree.get();if(root!=null && observer!=null && root.isAlive())root.removeOnPreDrawListener(observer);tree=new WeakReference<>(null);observer=null;label=new WeakReference<>(null);watched=new WeakReference<>(null);}
}
