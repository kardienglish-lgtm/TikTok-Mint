"""Execute font ownership, weights, restoration and bounded traversal."""
import unittest
import test_settings_safety_runtime as safety
from kardinebi import dexpatch
class FontCoverageTest(unittest.TestCase):
 def test_real_font_application(self):
  body=''.join(safety.method('Fonts.java',m) for m in ('    private static boolean drawsGlyphs','    public static void applyTree','    private static void applyTree','    public static void apply(TextView','    private static Typeface keep','    public static void setTypeface(TextView view, Typeface face)','    public static void setTypeface(TextView view, Typeface face, int style)'))
  sources={
   'android/graphics/Typeface.java':'''package android.graphics;public class Typeface {public static int NORMAL=0,BOLD=1,ITALIC=2,created;public static Typeface DEFAULT=new Typeface("default",0);public String name;public int style;public Typeface(String n,int s){name=n;style=s;}public int getStyle(){return style;}public static Typeface create(Typeface base,int style){created++;return new Typeface(base.name,style);}public static Typeface create(Typeface base,int weight,boolean italic){created++;return new Typeface(base.name,(weight>=600?BOLD:NORMAL)|(italic?ITALIC:NORMAL));}}''',
   'android/os/Build.java':'''package android.os;public class Build {public static class VERSION {public static int SDK_INT=28;}}''',
   'android/view/View.java':'package android.view;public class View {}',
   'android/view/ViewGroup.java':'package android.view;public class ViewGroup extends View {public java.util.List<View> children=new java.util.ArrayList<>();public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}}',
   'android/widget/TextView.java':'package android.widget;import android.graphics.Typeface;public class TextView extends android.view.View {public Typeface type;public int writes;public Typeface getTypeface(){return type;}public void setTypeface(Typeface f){type=f;writes++;}public void setTypeface(Typeface f,int style){type=Typeface.create(f,style);writes++;}}',
   'Test.java':'''import android.graphics.Typeface;import android.widget.TextView;public class Test {
 static Typeface selected=new Typeface("custom",0);static Typeface chosenFace(){return selected;}
 static boolean boldMode; static int fontWeight=700; static boolean isBold(){return fontWeight>=600;} static int weight(){return fontWeight;}
 static class OwnedFont {Typeface original,applied;OwnedFont(Typeface f){original=f;}}
 static java.util.Map<TextView,OwnedFont> ownedFonts=new java.util.WeakHashMap<>();static java.util.Map<Typeface,java.util.Map<Integer,Typeface>> weightedFaces=new java.util.IdentityHashMap<>();static class Fonts {}static final java.util.concurrent.ConcurrentHashMap<Class<?>,Boolean> glyphs=new java.util.concurrent.ConcurrentHashMap<>();
 '''+body+'''
 static class IconText extends TextView {}static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  TextView name=new TextView();Typeface original=new Typeface("native",1);name.type=original;apply(name);check(name.type.name.equals("custom") && name.type.style==1,"chat name retains bold");
  for(int i=0;i<100;i++)apply(name);check(name.writes==1 && Typeface.created==1,"layout passes reuse styled face without redraws");
  selected=new Typeface("second-account",0);apply(name);check(name.type.name.equals("second-account") && name.type.style==1,"account font changes without losing weight");
  selected=null;fontWeight=400;apply(name);check(name.type==original,"system selection restores native face");fontWeight=700;selected=new Typeface("custom",0);
  selected=new Typeface("custom",0);apply(name);Typeface nativeItalic=new Typeface("native",2);name.type=nativeItalic;apply(name);check(name.type.style==3,"custom weight and native italic both respected");fontWeight=400;selected=null;apply(name);check(name.type==nativeItalic,"restore most recent native style");fontWeight=700;
  selected=new Typeface("custom",0);TextView nativeSet=new TextView();Typeface bold=new Typeface("native-bold",1);setTypeface(nativeSet,bold);check(nativeSet.type.name.equals("custom") && nativeSet.type.style==1,"native typeface setter routes selected face");selected=null;apply(nativeSet);check(nativeSet.type.name.equals("native-bold") && nativeSet.type.style==1,"native setter preserves family in weight-only mode");
  selected=new Typeface("custom",0);setTypeface(nativeSet,bold,2);check(nativeSet.type.style==3,"style overload preserves italic and requested weight");fontWeight=400;selected=null;apply(nativeSet);check(nativeSet.type.name.equals("native-bold") && nativeSet.type.style==2,"style overload restores original family and weight");
  selected=new Typeface("custom",0);fontWeight=700;IconText icon=new IconText();icon.type=original;apply(icon);check(icon.type==original,"icon glyph fonts preserved");
  android.view.ViewGroup root=new android.view.ViewGroup();root.children.add(name);TextView message=new TextView();message.type=new Typeface("native",0);root.children.add(message);applyTree(root);check(message.type.name.equals("custom") && message.type.style==1,"new chat messages included with selected weight");
  android.view.ViewGroup parent=root;for(int i=0;i<50;i++){android.view.ViewGroup child=new android.view.ViewGroup();parent.children.add(child);parent=child;}TextView tooDeep=new TextView();parent.children.add(tooDeep);applyTree(root);check(tooDeep.writes==0,"traversal bounded");
      selected=null;fontWeight=700;TextView systemFamily=new TextView();Typeface oem=new Typeface("oem-system",0);systemFamily.type=oem;apply(systemFamily);check(systemFamily.type.name.equals("oem-system") && systemFamily.type.style==1,"system family supports user weight");fontWeight=400;apply(systemFamily);check(systemFamily.type==oem,"system family restores original after weight reset");
 }
}'''}
  # the methods are lifted into a class of their own, outside the mod's package
  sources['Probe.java']='class Probe{static final int FONTS=0;static long start(){return 0L;}static void stop(int what,long started){}}'
  safety.SafetyRuntimeTest().run_java(sources)
 def test_resource_and_hint_hooks(self):
  for owner in (dexpatch.TEXT_VIEW,dexpatch.TUX_TEXT,'Landroid/widget/EditText;','Landroid/widget/Button;'):
   for name,sig in (('setText','(I)V'),('setHint','(Ljava/lang/CharSequence;)V'),('setTextAppearance','(I)V')):
    text='invoke-virtual {v0, v1}, '+owner+'->'+name+sig
    for _,pattern,target in dexpatch.model_rules():text=pattern.sub(target,text)
    self.assertIn('Fonts;->'+name,text)
