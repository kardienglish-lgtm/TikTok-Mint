"""Compile/run the guarded profile button/date helpers against small Android doubles."""
import pathlib, shutil, subprocess, tempfile, unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = {
'android/content/Context.java': '''package android.content; public class Context { public android.content.res.Resources getResources(){return new android.content.res.Resources();} }''',
'android/content/res/Resources.java': '''package android.content.res; public class Resources { public static class Metrics { public float density=1f; } public Metrics getDisplayMetrics(){return new Metrics();} public String getResourceEntryName(int id){return "other";} }''',
'android/os/SystemClock.java': '''package android.os; public class SystemClock {static long now; public static long uptimeMillis(){return ++now*200L;} }''',
'android/graphics/Outline.java': '''package android.graphics; public class Outline { public float radius; public void setRoundRect(int l,int t,int r,int b,float rad){radius=rad;} }''',
'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable; public class Drawable {public Drawable mutate(){return this;}}''',
'android/graphics/drawable/GradientDrawable.java': '''package android.graphics.drawable; public class GradientDrawable extends Drawable { public int color; public float radius; public void setColor(int c){color=c;} public void setCornerRadius(float r){radius=r;} }''',
'android/graphics/drawable/InsetDrawable.java': '''package android.graphics.drawable; public class InsetDrawable extends Drawable {private Drawable d;public InsetDrawable(Drawable x){d=x;}public Drawable getDrawable(){return d;}}''',
'android/graphics/drawable/LayerDrawable.java': '''package android.graphics.drawable; public class LayerDrawable extends Drawable {private Drawable[] d;public LayerDrawable(Drawable... x){d=x;}public int getNumberOfLayers(){return d.length;}public Drawable getDrawable(int i){return d[i];}}''',
'android/view/ViewOutlineProvider.java': '''package android.view; public abstract class ViewOutlineProvider { public abstract void getOutline(View v, android.graphics.Outline o); }''',
'android/view/Gravity.java': '''package android.view; public class Gravity { public static final int BOTTOM=1, LEFT=2; }''',
'android/view/View.java': '''package android.view;
import java.util.*;
public class View {
 public interface OnLayoutChangeListener {void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}
 public interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 public static final int VISIBLE=0,GONE=8,NO_ID=-1; private final Map<Integer,Object> tags=new HashMap<>();
 public ViewGroup parent; public boolean clickable,clip; public int visibility=0,width=100,height=40,id=-1; public ViewOutlineProvider outline; public android.graphics.drawable.Drawable background; public int pl,pt,pr,pb; public final List<Runnable> pending=new ArrayList<>();
 public View(android.content.Context c){} public View(){this(new android.content.Context());}
 public android.content.Context getContext(){return new android.content.Context();} public android.content.res.Resources getResources(){return getContext().getResources();}
 public Object getParent(){return parent;} public View getRootView(){return parent==null?this:parent.getRootView();}
 public Object getTag(){return tags.get(0);} public Object getTag(int k){return tags.get(k);} public void setTag(Object o){tags.put(0,o);} public void setTag(int k,Object o){tags.put(k,o);}
 public void post(Runnable r){r.run();} public boolean postDelayed(Runnable r,long d){pending.add(r);return true;} public void runPending(){List<Runnable> c=new ArrayList<>(pending);pending.clear();for(Runnable r:c)r.run();}
 public void addOnLayoutChangeListener(OnLayoutChangeListener l){} public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){}
 public void setOutlineProvider(ViewOutlineProvider p){outline=p;} public ViewOutlineProvider getOutlineProvider(){return outline;} public void setClipToOutline(boolean b){clip=b;} public void invalidateOutline(){}
 public boolean isClickable(){return clickable;} public boolean isEnabled(){return true;} public boolean isSelected(){return false;} public void setClickable(boolean b){clickable=b;} public int getWidth(){return width;} public int getHeight(){return height;} public int getId(){return id;}
 public void setVisibility(int v){visibility=v;} public void bringToFront(){} public void setPadding(int a,int b,int c,int d){pl=a;pt=b;pr=c;pb=d;} public void setBackground(android.graphics.drawable.Drawable b){background=b;} public android.graphics.drawable.Drawable getBackground(){return background;} public void invalidate(){}
}''',
'android/view/ViewGroup.java': '''package android.view; import java.util.*; public class ViewGroup extends View { public static class LayoutParams {public static final int WRAP_CONTENT=-2;public int width,height;public LayoutParams(int w,int h){width=w;height=h;}} public final List<View> children=new ArrayList<>(); public ViewGroup(android.content.Context c){super(c);} public ViewGroup(){super();} public void addView(View v,LayoutParams p){children.add(v);v.parent=this;} public void removeView(View v){children.remove(v);v.parent=null;} public int getChildCount(){return children.size();} public View getChildAt(int i){return children.get(i);} }''',
'android/widget/FrameLayout.java': '''package android.widget; public class FrameLayout extends android.view.ViewGroup { public FrameLayout(android.content.Context c){super(c);} public FrameLayout(){super();} public static class LayoutParams extends android.view.ViewGroup.LayoutParams {public int leftMargin,bottomMargin;public LayoutParams(int w,int h,int g){super(w,h);} } }''',
'android/widget/TextView.java': '''package android.widget; public class TextView extends android.view.View {private CharSequence text="";public TextView(android.content.Context c){super(c);}public void setText(CharSequence s){text=s;}public CharSequence getText(){return text;}public void setTextSize(float f){}public void setTextColor(int c){}public void setSingleLine(boolean b){} }''',
'cat/kardinebi/Diary.java': '''package cat.kardinebi; class Diary {static void note(String s){}}''',
'cat/kardinebi/Motion.java': '''package cat.kardinebi;class Motion {static int bumps=0;static void bump(android.view.View v){bumps++;}}''',
'cat/kardinebi/Fonts.java': '''package cat.kardinebi; class Fonts {static void apply(android.widget.TextView t){}}''',
'cat/kardinebi/Dates.java': '''package cat.kardinebi; class Dates {static boolean isEnabled(){return true;}static String label(long t,long now){return t>0?"09.10.2022":null;}}''',
'cat/kardinebi/DateInfo.java': '''package cat.kardinebi; class DateInfo {static final int CELL_TAG=0x7e0d0335;}''',
'cat/kardinebi/Corners.java': '''package cat.kardinebi; class Corners {static boolean isEnabled(){return true;}static boolean holdsCover(android.view.View v){return true;}}''',
'cat/kardinebi/NativeRead.java': '''package cat.kardinebi; class NativeRead {static Object get(Object o,String n){try{return o.getClass().getMethod(n).invoke(o);}catch(Throwable t){return null;}}static long number(Object o){return o instanceof Number?((Number)o).longValue():0;}static String aid(Object o){Object a=get(o,"getAid");return a instanceof String?(String)a:"";}}''',
'com/ss/android/ugc/profile/ProfileHeaderCTA.java': '''package com.ss.android.ugc.profile; public class ProfileHeaderCTA {}''',
'com/ss/android/ugc/profile/ProfileContainer.java': '''package com.ss.android.ugc.profile; public class ProfileContainer extends android.view.ViewGroup {public ProfileContainer(){super();}}''',
'com/ss/android/ugc/profile/ClickButton.java': '''package com.ss.android.ugc.profile; public class ClickButton extends android.view.ViewGroup {public ClickButton(){super();} }''',
'com/ss/android/ugc/profile/ProfileGridCell.java': '''package com.ss.android.ugc.profile; public class ProfileGridCell extends android.widget.FrameLayout {public ProfileGridCell(){super();}}''',
'cat/kardinebi/ProfileHarness.java': '''package cat.kardinebi;
import android.view.*;import android.widget.*;import com.ss.android.ugc.profile.*;
public class ProfileHarness {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static class Aweme {public long getCreateTime(){return 1665273600L;} public String getAid(){return "post-1";}}
 public static void main(String[]x){
   ProfileContainer root=new ProfileContainer();root.width=400;root.height=800;
   ClickButton button=new ClickButton();button.clickable=true;button.width=110;button.height=42;android.graphics.drawable.GradientDrawable buttonBackground=new android.graphics.drawable.GradientDrawable();buttonBackground.setCornerRadius(40f);button.setBackground(buttonBackground);TextView label=new TextView(new android.content.Context());label.setText("Подписаться");root.addView(button,new ViewGroup.LayoutParams(110,42));button.addView(label,new ViewGroup.LayoutParams(100,24));
   ProfileButtons.bind(new ProfileHeaderCTA(),root);
   check(button.clip && button.outline!=null,"follow button gets rounded clipping");check(buttonBackground.radius==11f,"follow background becomes a rounded rectangle instead of a pill");label.setText("Подписки");ProfileButtons.bind(new ProfileHeaderCTA(),root);check(Motion.bumps==1,"a follow-state label change gets a small confirmation animation");
   View other=new View();other.clickable=true;root.addView(other,new ViewGroup.LayoutParams(90,42));
   ProfileGridCell cell=new ProfileGridCell();root.addView(cell,new ViewGroup.LayoutParams(180,250));cell.setTag(DateInfo.CELL_TAG,new Aweme());
   ProfileGridDates.watch(cell);cell.runPending();
   check(cell.getChildCount()==1,"profile tile gets one date badge when a real model is available");
   check(((TextView)cell.getChildAt(0)).getText().toString().equals("09.10.2022"),"date comes from post model");
   System.out.println("profile button and guarded date checks passed");
 }
}'''
}
# Test-only helper for adding children to clickable plain Views.
STUBS['android/view/View.java'] = STUBS['android/view/View.java'].replace('public void addOnLayoutChangeListener(OnLayoutChangeListener l){}', 'public void addViewForTest(View v){if(this instanceof ViewGroup)((ViewGroup)this).addView(v,new ViewGroup.LayoutParams(-2,-2));} public void addOnLayoutChangeListener(OnLayoutChangeListener l){}')
class ProfileMintRuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_profile_buttons_and_optional_date(self):
        with tempfile.TemporaryDirectory() as directory:
            work=pathlib.Path(directory)
            sources=dict(STUBS)
            for filename in ('MintGeometry.java','ProfileButtons.java','ProfileGridDates.java'):
                sources['cat/kardinebi/'+filename]=(ROOT/'inject/java/cat/kardinebi'/filename).read_text(encoding='utf-8')
            for name,content in sources.items():
                target=work/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(content,encoding='utf-8')
            built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/name) for name in sources],capture_output=True,text=True)
            self.assertEqual(0,built.returncode,built.stderr)
            run=subprocess.run(['java','-cp',str(work),'cat.kardinebi.ProfileHarness'],capture_output=True,text=True)
            self.assertEqual(0,run.returncode,run.stderr)
            self.assertIn('profile button and guarded date checks passed',run.stdout)
if __name__=='__main__': unittest.main()
