"""Run the real Dates.java author binding against Android API doubles."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

SOURCES = {
    "android/content/Context.java": '''package android.content;
public class Context { public static final int MODE_PRIVATE=0; private final SharedPreferences prefs=new SharedPreferences();
 public SharedPreferences getSharedPreferences(String n,int m){return prefs;} }''',
    "android/content/SharedPreferences.java": '''package android.content;
public class SharedPreferences { private final java.util.Map<String,Object> values=new java.util.HashMap<>();
 public boolean getBoolean(String k,boolean d){Object v=values.get(k);return v instanceof Boolean?((Boolean)v).booleanValue():d;}
 public Editor edit(){return new Editor();}
 public class Editor { public Editor putBoolean(String k,boolean v){values.put(k,Boolean.valueOf(v));return this;} public void apply(){} }
}''',
    "android/os/Looper.java": '''package android.os; public class Looper {private static final Looper MAIN=new Looper();public static Looper getMainLooper(){return MAIN;}}''',
    "android/os/Handler.java": '''package android.os; public class Handler {public static int instances;public Handler(Looper l){instances++;}public boolean post(Runnable r){r.run();return true;}}''',
    "android/view/View.java": '''package android.view;
public class View {private final java.util.Map<Integer,Object> tags=new java.util.HashMap<>();
 public boolean isAttachedToWindow(){return true;} public boolean isShown(){return true;}
 public void setTag(int id,Object value){tags.put(Integer.valueOf(id),value);} public Object getTag(int id){return tags.get(Integer.valueOf(id));} }''',
    "cat/kardinebi/Diary.java": "package cat.kardinebi; public class Diary {static void note(String s){}}",
    "cat/kardinebi/Kardinebi.java": '''package cat.kardinebi; public class Kardinebi {static final String PREFS="test";static android.content.Context c=new android.content.Context();static android.content.Context context(){return c;}}''',
    "cat/kardinebi/NativeRead.java": '''package cat.kardinebi; public class NativeRead {static String aid(Object obj){try{Object v=obj.getClass().getMethod("getAid").invoke(obj);return v instanceof String?(String)v:"";}catch(Throwable e){return "";}}static Object item(Object obj){return null;}}''',
    "cat/kardinebi/DateInfo.java": '''package cat.kardinebi; public class DateInfo {static final int CELL_TAG=123456;static class Data {final Object post;Data(Object p){post=p;}static Data of(Object p){return p==null?null:new Data(p);}}}''',
    "cat/kardinebi/DateOverlay.java": '''package cat.kardinebi;
public class DateOverlay {static android.view.View lastView,unregistered;static String lastDate;static int registrations,unregisters;
 static void update(){} static void register(android.view.View v,String d){lastView=v;lastDate=d;registrations++;}
 static void unregister(android.view.View v){unregistered=v;unregisters++;}
 static void bind(android.view.View v,String d){lastView=v;lastDate=d;}static void bind(android.view.View v,String d,String a){bind(v,d);}}''',
    "cat/kardinebi/VideoActions.java": '''package cat.kardinebi; public class VideoActions {static void selected(Object h,Object p){}}''',
    "cat/kardinebi/DatesHarness.java": '''package cat.kardinebi;
public class DatesHarness {
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static class Post {private final String aid;private final long created;Post(String a,long c){aid=a;created=c;}public String getAid(){return aid;}public long getCreateTime(){return created;}}
 public static class AuthorRow {final android.view.View row=new android.view.View();public android.view.View Xb(){return row;}}
 public static void main(String[] args)throws Exception{
  AuthorRow holder=new AuthorRow();long now=System.currentTimeMillis()/1000L;Post post=new Post("post-1",now-86400L);
  Dates.bindAuthor(holder,post);
  check(DateOverlay.lastView==holder.row,"obfuscated Xb() accessor resolves the real author view");
  check("post-1".equals(holder.row.getTag(0x7e0d0334)),"post id is attached to recycled author row");
  check(DateOverlay.lastDate!=null&&DateOverlay.lastDate.length()==10,"native create time is formatted as a date");
  int count=DateOverlay.registrations;Dates.bindAuthor(holder,new Post("post-2",0L));
  check(DateOverlay.unregisters==1&&DateOverlay.unregistered==holder.row,"missing timestamp removes stale date from reused row");
  check(holder.row.getTag(0x7e0d0334)==null,"recycled row clears its old post id when the new post has no timestamp");
  java.lang.reflect.Field rowsField=Dates.class.getDeclaredField("authorRows");rowsField.setAccessible(true);
  java.util.Map<?,?> rows=(java.util.Map<?,?>)rowsField.get(null);
  check(!rows.containsKey("post-1"),"old post id is removed from the author row registry after invalid rebind");
  Dates.bindAuthor(holder,post);check(DateOverlay.registrations==count+1,"date binds again after a recycled row receives a valid post");
  check(android.os.Handler.instances==1,"main-thread Handler is reused instead of allocated per feed bind");
 }
}''',
}

class DatesAuthorRuntime(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK required")
    def test_real_author_rebind(self):
        with tempfile.TemporaryDirectory() as temp:
            work = pathlib.Path(temp)
            sources = dict(SOURCES)
            sources["cat/kardinebi/Dates.java"] = (ROOT / "inject/java/cat/kardinebi/Dates.java").read_text(encoding="utf-8")
            for name, content in sources.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
            result = subprocess.run(["javac", "-source", "8", "-target", "8", "-encoding", "UTF-8", "-d", str(work), *[str(p) for p in work.rglob("*.java")]], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            run = subprocess.run(["java", "-cp", str(work), "cat.kardinebi.DatesHarness"], capture_output=True, text=True)
            self.assertEqual(0, run.returncode, run.stderr)

if __name__ == "__main__":
    unittest.main()
