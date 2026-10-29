import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK required")
class RecolourCacheRuntime(unittest.TestCase):
    def test_hit_rgb_key_and_six_colour_lru(self):
        sources = {
            "android/content/Context.java": """package android.content;
import java.io.File; import android.content.pm.PackageManager;
public class Context { private final File root; public Context(File root){this.root=root;}
public File getFilesDir(){return root;} public String getPackageName(){return "test";}
public PackageManager getPackageManager(){return new PackageManager();}}
""",
            "android/content/pm/PackageManager.java": """package android.content.pm;
public class PackageManager {public static class PackageInfo {public long lastUpdateTime=1;}
public PackageInfo getPackageInfo(String name,int flags){return new PackageInfo();}}
""",
            "android/graphics/Bitmap.java": """package android.graphics;
import java.io.OutputStream;
public class Bitmap {public enum CompressFormat{PNG} public boolean compress(CompressFormat f,int q,OutputStream out){return false;}}
""",
            "android/graphics/BitmapFactory.java": """package android.graphics;
public class BitmapFactory {public static Bitmap decodeByteArray(byte[] b,int o,int n){return null;}}
""",
            "cat/kardinebi/Kardinebi.java": """package cat.kardinebi;
import android.content.Context; import java.io.File;
public class Kardinebi {static Context c;static Context context(){return c;} }
""",
            "cat/kardinebi/RecolourCache.java": (
                ROOT / "inject/java/cat/kardinebi/RecolourCache.java"
            ).read_text(encoding="utf-8"),
            "cat/kardinebi/Test.java": """package cat.kardinebi;
import java.io.File; import java.io.RandomAccessFile; import java.nio.charset.StandardCharsets; import java.nio.ByteBuffer; import java.security.MessageDigest;
public class Test {
 static void check(boolean yes){if(!yes)throw new AssertionError();}
 static String oldHash(int[] pixels,int width,int height)throws Exception{
  ByteBuffer bytes=ByteBuffer.allocate(pixels.length*4+8);bytes.putInt(width).putInt(height);bytes.asIntBuffer().put(pixels);
  byte[] digest=MessageDigest.getInstance("SHA-1").digest(bytes.array());StringBuilder out=new StringBuilder();
  for(byte b:digest)out.append(Character.forDigit((b>>4)&15,16)).append(Character.forDigit(b&15,16));
  return out.toString();
 }
 static File entry(String key)throws Exception{
  java.lang.reflect.Method folder=RecolourCache.class.getDeclaredMethod("folder");folder.setAccessible(true);
  File root=(File)folder.invoke(null);
  java.lang.reflect.Method make=RecolourCache.class.getDeclaredMethod("entry",File.class,String.class,boolean.class);make.setAccessible(true);
  return (File)make.invoke(null,root,key,false);
 }
 static void await(String key)throws Exception{
  for(int n=0;n<300;n++){if(RecolourCache.get(key)!=null&&entry(key).isFile())return;Thread.sleep(10);}
  check(RecolourCache.get(key)!=null&&entry(key).isFile());
 }
 public static void main(String[] args)throws Exception{
  Kardinebi.c=new android.content.Context(new File(args[0]));
  int[] pixels={0,1,-1,0x12345678,0xFF00AA11,0x80000000};
  check(RecolourCache.hash(pixels,3,2).equals(oldHash(pixels,3,2)));
  int[] larger=new int[10003];for(int i=0;i<larger.length;i++)larger[i]=(i*0x9E3779B9)^(i>>>2);
  check(RecolourCache.hash(larger,101,99).equals(oldHash(larger,101,99)));
  check(RecolourCache.hash(new int[0],0,0).equals(oldHash(new int[0],0,0)));
  String a=RecolourCache.likeKey("heart",0x12AABBCC);
  check(a.equals(RecolourCache.likeKey("heart",0xFFAABBCC)));
  check(!a.equals(RecolourCache.likeKey("heart",0xFFAABBBD)));
  String first="lottie|"+a;
  RecolourCache.put(first,"ready".getBytes(StandardCharsets.UTF_8));await(first);
  check(new String(RecolourCache.get(first),StandardCharsets.UTF_8).equals("ready"));
  String memoryKey="memory-check";
  RecolourCache.put(memoryKey,"warm".getBytes(StandardCharsets.UTF_8));await(memoryKey);
  check(entry(memoryKey).delete());
  check(new String(RecolourCache.get(memoryKey),StandardCharsets.UTF_8).equals("warm"));
  for(int i=1;i<6;i++){String key="lottie|"+RecolourCache.likeKey("heart",i);RecolourCache.put(key,new byte[]{(byte)i});await(key);Thread.sleep(15);}
  check(RecolourCache.get(first)!=null);Thread.sleep(15);
  String seventh="lottie|"+RecolourCache.likeKey("heart",6);
  RecolourCache.put(seventh,new byte[]{6});await(seventh);
  File[] colours=new File(args[0],"kardinebi/recolour-cache/like").listFiles(File::isDirectory);
  check(colours!=null&&colours.length==6);
  check(RecolourCache.get(first)!=null);
  check(RecolourCache.get("lottie|"+RecolourCache.likeKey("heart",1))==null);
  String png="png|aabbcc|"+RecolourCache.ALGORITHM_VERSION+"|frame";
  RecolourCache.put(png,new byte[]{42});await(png);
  check(new File(args[0],"kardinebi/recolour-cache/like/aabbcc").listFiles().length==2);
  RecolourCache.clear();
  for(int i=0;i<300&&RecolourCache.get(png)!=null;i++)Thread.sleep(10);
  check(RecolourCache.get(png)==null);
  java.lang.reflect.Field clearing=RecolourCache.class.getDeclaredField("clearing");clearing.setAccessible(true);
  for(int i=0;i<300&&clearing.getBoolean(null);i++)Thread.sleep(10);
  java.lang.reflect.Method folder=RecolourCache.class.getDeclaredMethod("folder");folder.setAccessible(true);File cache=(File)folder.invoke(null);
  java.lang.reflect.Field writerField=RecolourCache.class.getDeclaredField("writer");writerField.setAccessible(true);Object queueOwner=writerField.get(null);
  check(queueOwner instanceof java.util.concurrent.ThreadPoolExecutor);
  java.util.concurrent.ThreadPoolExecutor executor=(java.util.concurrent.ThreadPoolExecutor)queueOwner;
  check(executor.getQueue() instanceof java.util.concurrent.ArrayBlockingQueue);
  check(executor.getQueue().size()+executor.getQueue().remainingCapacity()==4);
  java.lang.reflect.Field maxField=RecolourCache.class.getDeclaredField("MAX_DISK_ENTRY_BYTES");maxField.setAccessible(true);
  byte[] oversized=new byte[(int)maxField.getLong(null)];String oversizedKey="oversized-cache-entry";RecolourCache.put(oversizedKey,oversized);
  File oversizedFile=entry(oversizedKey);for(int i=0;i<300&&!oversizedFile.exists();i++)Thread.sleep(10);
  check(!oversizedFile.exists());
  File nested=new File(new File(new File(cache,"like"),"123456"),"sparse.bin");nested.getParentFile().mkdirs();
  RandomAccessFile sparse=new RandomAccessFile(nested,"rw");sparse.setLength(97L*1024*1024);sparse.close();
  java.lang.reflect.Method trim=RecolourCache.class.getDeclaredMethod("trim",File.class);trim.setAccessible(true);trim.invoke(null,cache);
  check(!nested.exists());

 }
}
""",
        }
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            __import__("android_doubles").with_mod(sources)
            for path, content in sources.items():
                target = root / "src" / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content, encoding="utf-8")
            files = [str(path) for path in (root / "src").rglob("*.java")]
            subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(root / "classes"), *files], check=True)
            subprocess.run(["java", "-cp", str(root / "classes"),
                            "cat.kardinebi.Test", str(root / "data")], check=True)
