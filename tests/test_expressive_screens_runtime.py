"""Compile and exercise the custom visual pass against small Android doubles."""
import pathlib, shutil, subprocess, tempfile, unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]

STUBS = {
'android/graphics/Color.java': '''package android.graphics;public class Color {public static int red(int c){return (c>>16)&255;}public static int green(int c){return (c>>8)&255;}public static int blue(int c){return c&255;}public static int alpha(int c){return (c>>>24)&255;}public static int rgb(int r,int g,int b){return 0xff000000|(r<<16)|(g<<8)|b;}}''',
'android/content/Context.java': '''package android.content; public class Context { private final android.content.res.Resources r=new android.content.res.Resources(); public android.content.res.Resources getResources(){return r;} }''',
'android/content/res/Resources.java': '''package android.content.res; public class Resources { public static class Metrics {public float density=1f;} private final java.util.Map<Integer,String> n=new java.util.HashMap<>(); public Metrics getDisplayMetrics(){return new Metrics();} public void name(int id,String v){n.put(id,v);} public String getResourceEntryName(int id){String s=n.get(id);if(s==null)throw new RuntimeException();return s;} }''',
'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable; public class Drawable {}''',
'android/graphics/drawable/GradientDrawable.java': '''package android.graphics.drawable; public class GradientDrawable extends Drawable {public enum Orientation{TOP_BOTTOM,LEFT_RIGHT}public int color,stroke;public float radius;public int[] colors;public float[] radii;public void setColor(int c){color=c;}public void setColors(int[] c){colors=c;}public void setOrientation(Orientation o){}public void setCornerRadius(float r){radius=r;}public void setCornerRadii(float[] r){radii=r;}public void setStroke(int w,int c){stroke=c;}}''',
'android/os/SystemClock.java': '''package android.os; public class SystemClock {public static long now=10000;public static long uptimeMillis(){return now;}}''',
'android/view/ViewParent.java': '''package android.view; public interface ViewParent {ViewParent getParent();}''',
'android/view/ViewPropertyAnimator.java': '''package android.view; public class ViewPropertyAnimator {public ViewPropertyAnimator alpha(float f){return this;}public ViewPropertyAnimator setDuration(long d){return this;}public void start(){}}''',
'android/view/View.java': '''package android.view; import android.content.Context;import android.graphics.drawable.Drawable; public class View implements ViewParent {public static final int VISIBLE=0,INVISIBLE=4,GONE=8,NO_ID=-1; private Context c;private int id=-1;public int visibility=0,left=0,top=0,width=300,height=50,pl,pt,pr,pb,minimumHeight;public boolean attached=true,clickable,selected,activated,longClickable;public CharSequence description;public Drawable bg;public float alpha=1f,elevation;public ViewParent parent;public ViewGroup.LayoutParams params;public android.view.View.OnClickListener click;public interface OnClickListener{void onClick(View v);}public View(Context c){this.c=c;}public View(){this(new Context());}private static final java.util.List<Pending> pending=new java.util.ArrayList<>();private static final class Pending{final View owner;final Runnable task;final long at;Pending(View v,Runnable r,long t){owner=v;task=r;at=t;}}public boolean postDelayed(Runnable r,long delay){removeCallbacks(r);pending.add(new Pending(this,r,android.os.SystemClock.uptimeMillis()+Math.max(0L,delay)));return true;}public boolean post(Runnable r){return postDelayed(r,0L);}public void removeCallbacks(Runnable r){for(int i=pending.size()-1;i>=0;i--){Pending p=pending.get(i);if(p.owner==this&&p.task==r)pending.remove(i);}}public static void flushDueCallbacks(){int guard=1000;while(guard-->0){Pending due=null;for(Pending p:pending)if(p.at<=android.os.SystemClock.uptimeMillis()){due=p;break;}if(due==null)return;pending.remove(due);due.task.run();}}public Context getContext(){return c;}public android.content.res.Resources getResources(){return c.getResources();}public int getId(){return id;}public void setId(int v){id=v;}public int getVisibility(){return visibility;}public void setVisibility(int v){visibility=v;}public boolean isAttachedToWindow(){return attached;}public boolean isClickable(){return clickable;}public boolean isLongClickable(){return longClickable;}public boolean isSelected(){return selected;}public boolean isActivated(){return activated;}public CharSequence getContentDescription(){return description;}public void setContentDescription(CharSequence v){description=v;}public ViewParent getParent(){return parent;}public void setBackgroundDrawable(Drawable d){bg=d;}public Drawable getBackground(){return bg;}public void setPadding(int a,int b,int c,int d){pl=a;pt=b;pr=c;pb=d;}public int getPaddingLeft(){return pl;}public int getPaddingTop(){return pt;}public int getPaddingRight(){return pr;}public int getPaddingBottom(){return pb;}public int getWidth(){return width;}public int getHeight(){return height;}public int getMinimumHeight(){return minimumHeight;}public void setMinimumHeight(int value){minimumHeight=value;}public void getLocationOnScreen(int[] xy){int x=left,y=top;ViewParent p=parent;while(p instanceof View){x+=((View)p).left;y+=((View)p).top;p=((View)p).parent;}xy[0]=x;xy[1]=y;}public ViewPropertyAnimator animate(){return new ViewPropertyAnimator();}public void setAlpha(float v){alpha=v;}public float getElevation(){return elevation;}public void setElevation(float v){elevation=v;}public void setOnClickListener(OnClickListener l){click=l;} }''',
'android/view/ViewGroup.java': '''package android.view;import java.util.*;public class ViewGroup extends View {public final java.util.List<View> kids=new ArrayList<>();public ViewGroup(){super();}public ViewGroup(android.content.Context c){super(c);}public void addView(View v){kids.add(v);v.parent=this;}public void addView(View v,ViewGroup.LayoutParams p){addView(v);v.params=p;}public int getChildCount(){return kids.size();}public View getChildAt(int i){return kids.get(i);}public static class LayoutParams{public static final int WRAP_CONTENT=-2,MATCH_PARENT=-1;public int width,height;public LayoutParams(int w,int h){width=w;height=h;}}public ViewGroup.LayoutParams params;public ViewGroup.LayoutParams getLayoutParams(){return params;} }''',
'android/widget/FrameLayout.java': '''package android.widget;public class FrameLayout extends android.view.ViewGroup {public FrameLayout(){super();}public FrameLayout(android.content.Context c){super(c);}}''',
'android/widget/LinearLayout.java': '''package android.widget;public class LinearLayout extends android.view.ViewGroup {public LinearLayout(){super();}public LinearLayout(android.content.Context c){super(c);}}''',
'android/widget/TextView.java': '''package android.widget;public class TextView extends android.view.View {private CharSequence text,hint;private int currentTextColor=0xff222222;public TextView(){super();}public TextView(android.content.Context c){super(c);}public CharSequence getText(){return text;}public void setText(CharSequence v){text=v;}public int getCurrentTextColor(){return currentTextColor;}public void setTextColor(int value){currentTextColor=value;}public CharSequence getHint(){return hint;}public void setHint(CharSequence v){hint=v;}}''',
'android/widget/EditText.java': '''package android.widget;public class EditText extends TextView {public EditText(){super();}public EditText(android.content.Context c){super(c);}}''',
'android/widget/ImageView.java': '''package android.widget;public class ImageView extends android.view.View {public ImageView(){super();}public ImageView(android.content.Context c){super(c);}}''',
'cat/kardinebi/Skin.java': '''package cat.kardinebi;public class Skin {final int page,card,text;private final boolean night;Skin(int p,int c,int t,boolean n){page=p;card=c;text=t;night=n;}boolean dark(){return night;}static Skin material3(android.content.Context c){return new Skin(0xff111610,0xff1b231c,0xffe2e9df,true);}}''',
'cat/kardinebi/Accent.java': '''package cat.kardinebi;public class Accent{public static int colour(){return 0xff9b6dff;}}''',
'cat/kardinebi/Themes.java': '''package cat.kardinebi;public class Themes{public static int text(){return 0xffeeeeee;}public static int backgroundInUse(){return 0xff171717;}}''',
'cat/kardinebi/Motion.java': '''package cat.kardinebi;public class Motion{public static int changes,bumps;public static void change(android.view.View v){changes++;}public static void bump(android.view.View v){bumps++;}}''',
'cat/kardinebi/Diary.java': '''package cat.kardinebi;public class Diary{public static void detail(String s){}}''',
}

HARNESS = '''package cat.kardinebi;
import android.view.*;import android.widget.*;import android.graphics.drawable.*;
public class MintScreenDesignHarness {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static TextView text(String value){TextView t=new TextView();t.setText(value);return t;}
 static void pass(long n){android.os.SystemClock.now+=n;View.flushDueCallbacks();}
 public static void main(String[] args){
  android.content.Context context=new android.content.Context();ViewGroup root=new FrameLayout(context);root.width=400;root.height=800;
  EditText input=new EditText(context);input.setId(1);input.setHint("Search");input.width=300;input.height=48;
  ViewGroup searchBar=new FrameLayout(context);searchBar.setId(2);searchBar.width=340;searchBar.height=56;searchBar.addView(input);root.addView(searchBar);
  LinearLayout tabs=new LinearLayout(context);tabs.setId(3);tabs.width=360;tabs.height=44;TextView top=text("Top"),videos=text("Videos"),users=text("Users");tabs.addView(top);tabs.addView(videos);tabs.addView(users);root.addView(tabs);
  root.getResources().name(1,"search_input");root.getResources().name(2,"search_container");root.getResources().name(3,"search_categories");
  MintScreenDesign.apply(root);
  check(input.getBackground() instanceof GradientDrawable,"search input gets a solid M3 field");
  check(input.getCurrentTextColor()==0xffe2e9df,"search input uses Mint's independent onSurface text role");
  check(input.getCurrentTextColor()!=Themes.text(),"search styling does not sample TikTok's native theme");
  GradientDrawable field=(GradientDrawable)input.getBackground();
  check(field.colors==null && field.color!=0,"search field uses a solid tonal fill, not a gradient");
  check(tabs.getChildAt(0).getBackground() instanceof GradientDrawable,"search categories become restrained chips");
  Drawable originalField=input.getBackground();int changes=Motion.changes;
  pass(240);MintScreenDesign.apply(root);
  check(input.getBackground()==originalField,"stable views reuse their drawable between layout passes");
  check(Motion.changes==changes,"stable views do not replay entrance animation every pass");
  input.setVisibility(View.INVISIBLE);tabs.setVisibility(View.INVISIBLE);searchBar.setVisibility(View.INVISIBLE);
  pass(240);MintScreenDesign.apply(root);
  check(input.getCurrentTextColor()==0xff222222,"native text color is restored when leaving the styled page");

  pass(240);
  android.content.Context context2=new android.content.Context();ViewGroup root2=new FrameLayout(context2);root2.width=400;root2.height=800;
  ViewGroup sheet=new FrameLayout(context2);sheet.setId(9);sheet.width=380;sheet.height=280;sheet.left=10;sheet.top=500;
  String[] names={"Repost","Copy link","Save video"};View firstRow=null;
  for(String name:names){FrameLayout row=new FrameLayout(context2);row.width=360;row.height=52;row.clickable=true;TextView actionText=text(name);row.addView(actionText);ImageView icon=new ImageView(context2);icon.width=40;icon.height=40;row.addView(icon);sheet.addView(row);if(firstRow==null)firstRow=row;}
  root2.addView(sheet);root2.getResources().name(9,"share_sheet");
  View.OnClickListener click=v->{};firstRow.setOnClickListener(click);
  MintScreenDesign.apply(root2);
  check(sheet.getBackground() instanceof GradientDrawable,"share/repost sheet gets a solid tonal surface");
  GradientDrawable sheetShape=(GradientDrawable)sheet.getBackground();
  check(sheetShape.colors==null && sheetShape.radii!=null,"share sheet is solid with M3 top corners");
  check(firstRow.getBackground() instanceof GradientDrawable,"native repost action row gets a visual pass");
  check(((GradientDrawable)firstRow.getBackground()).color!=0,"repost actions use the new tonal action tile");
  check(Motion.changes>0,"surface entrance animation remains enabled");
  check(firstRow.click==click,"native tap handler is preserved");

  pass(240);
  android.content.Context context3=new android.content.Context();ViewGroup root3=new FrameLayout(context3);root3.width=400;root3.height=800;
  FrameLayout commentSheet=new FrameLayout(context3);commentSheet.setId(20);commentSheet.width=390;commentSheet.height=480;commentSheet.top=320;
  TextView commentText=text("This is a comment");
  EditText commentInput=new EditText(context3);commentInput.setHint("Add a comment");commentInput.width=360;commentInput.height=48;
  commentSheet.addView(commentText);commentSheet.addView(commentInput);root3.addView(commentSheet);root3.getResources().name(20,"comment_sheet");
  MintScreenDesign.apply(root3);
  check(commentSheet.getBackground()==null,"comment sheet remains native; no unstable overlay is applied");
  check(commentInput.getBackground()==null,"comment composer remains native and has no black overlay");
  pass(240);MintScreenDesign.apply(root3);
  check(commentSheet.getBackground()==null && commentInput.getBackground()==null,"later hierarchy scans do not paint the comment composer");

  pass(240);
  android.content.Context context4=new android.content.Context();ViewGroup root4=new FrameLayout(context4);root4.width=400;root4.height=800;
  FrameLayout profile=new FrameLayout(context4);profile.setId(30);profile.width=400;profile.height=720;root4.addView(profile);root4.getResources().name(30,"profile_page");
  TextView followers=text("Followers");TextView following=text("Following");profile.addView(followers);profile.addView(following);
  TextView edit=text("Edit profile");edit.clickable=true;profile.addView(edit);
  LinearLayout profileTabs=new LinearLayout(context4);profileTabs.setId(31);profileTabs.width=380;profileTabs.height=48;TextView posts=text("Posts"),liked=text("Liked");posts.selected=true;profileTabs.addView(posts);profileTabs.addView(liked);profile.addView(profileTabs);root4.getResources().name(31,"profile_tabs");
  MintScreenDesign.apply(root4);
  check(edit.getBackground()==null,"profile action remains native");
  check(posts.getBackground()==null && liked.getBackground()==null,"profile tabs remain native");
  System.out.println("search/share retain the M3 layer; comments/profile remain native");
 }
}'''


class MintScreenDesignRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_search_and_repost_surfaces(self):
  with tempfile.TemporaryDirectory() as d:
   work=pathlib.Path(d); sources=dict(STUBS)
   sources['cat/kardinebi/MintScreenDesign.java']=(ROOT/'inject/java/cat/kardinebi/MintScreenDesign.java').read_text(encoding='utf-8')
   sources['cat/kardinebi/MintScreenDesignHarness.java']=HARNESS
   for name, content in sources.items():
    path=work/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content,encoding='utf-8')
   built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',str(work)]+[str(work/name) for name in sources],capture_output=True,text=True)
   self.assertEqual(0,built.returncode,built.stderr)
   run=subprocess.run(['java','-cp',str(work),'cat.kardinebi.MintScreenDesignHarness'],capture_output=True,text=True)
   self.assertEqual(0,run.returncode,run.stderr)

if __name__=='__main__': unittest.main()
