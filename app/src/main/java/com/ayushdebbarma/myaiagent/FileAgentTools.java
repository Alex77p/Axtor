package com.ayushdebbarma.myaiagent;

import android.content.*;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

/** File tools exposed to the online agent. Access is limited to a user-granted SAF tree or Axtor's workspace. */
public final class FileAgentTools {
  private static final String PREF="axtor_files", TREE="tree_uri";
  private FileAgentTools(){}
  public static boolean hasGrantedTree(Context c){String u=c.getSharedPreferences(PREF,0).getString(TREE,"");return !u.isEmpty();}
  public static void saveTree(Context c,Uri uri){c.getSharedPreferences(PREF,0).edit().putString(TREE,uri.toString()).apply();}
  public static void clearTree(Context c){c.getSharedPreferences(PREF,0).edit().remove(TREE).apply();}
  public static String execute(Context c,String action,JSONObject args)throws Exception{switch(action){case "LIST_FILES":return list(c,args.optString("path",""));case "SEARCH_FILES":return search(c,args.optString("query",""));case "READ_FILE":return read(c,args.optString("path",""));case "WRITE_FILE":return write(c,args.optString("path",""),args.optString("content",""),false);case "APPEND_FILE":return write(c,args.optString("path",""),args.optString("content",""),true);case "RENAME_FILE":return rename(c,args.optString("path",""),args.optString("new_name",""));case "CREATE_DIRECTORY":return mkdir(c,args.optString("path",""));case "DELETE_FILE":return delete(c,args.optString("path",""));default:return null;}}
  private static DocumentFile root(Context c){String u=c.getSharedPreferences(PREF,0).getString(TREE,"");if(!u.isEmpty()){DocumentFile d=DocumentFile.fromTreeUri(c,Uri.parse(u));if(d!=null&&d.canRead())return d;}File base=new File(c.getExternalFilesDir(null),"AxtorWorkspace");if(!base.exists())base.mkdirs();return DocumentFile.fromFile(base);}
  private static DocumentFile ensureRoot(Context c){return root(c);}
  private static DocumentFile resolve(Context c,String path,boolean createDirs)throws IOException{String p=path==null?"":path.trim().replace('\\','/');while(p.startsWith("/"))p=p.substring(1);if(p.startsWith("..")||p.contains("/../"))throw new IOException("Unsafe path");DocumentFile cur=ensureRoot(c);if(p.isEmpty())return cur;for(String part:p.split("/")){if(part.isEmpty()||part.equals("."))continue;DocumentFile next=null;for(DocumentFile f:cur.listFiles())if(f.getName()!=null&&f.getName().equals(part)){next=f;break;}if(next==null){if(!createDirs)throw new FileNotFoundException(p);next=cur.createDirectory(part);}cur=next;}return cur;}
  private static String list(Context c,String path)throws Exception{DocumentFile d=resolve(c,path,false);if(!d.isDirectory())return "Not a directory: "+path;StringBuilder b=new StringBuilder("Files:\n");for(DocumentFile f:d.listFiles())b.append(f.isDirectory()?"DIR ":"FILE ").append(f.getName()).append('\n');return cap(b.toString());}
  private static String search(Context c,String q)throws Exception{String needle=q.toLowerCase(Locale.ROOT).trim();if(needle.isEmpty())return "Search query is empty.";StringBuilder b=new StringBuilder();walk(root(c),"",needle,b,0);return b.length()==0?"No matching files found.":cap(b.toString());}
  private static void walk(DocumentFile d,String prefix,String q,StringBuilder out,int depth){if(depth>8||out.length()>12000)return;for(DocumentFile f:d.listFiles()){String n=f.getName()==null?"":f.getName();String p=prefix+n;if(n.toLowerCase(Locale.ROOT).contains(q))out.append(p).append(f.isDirectory()?"/":"").append('\n');if(f.isDirectory())walk(f,p+"/",q,out,depth+1);}}
  private static String read(Context c,String path)throws Exception{DocumentFile f=resolve(c,path,false);if(!f.isFile())return "Not a file: "+path;if(f.length()>512000)return "File is larger than the safe voice read limit (512 KB).";try(InputStream in=c.getContentResolver().openInputStream(f.getUri())){if(in==null)throw new IOException("Cannot open file");return cap(new String(readAll(in),StandardCharsets.UTF_8));}}
  private static String write(Context c,String path,String content,boolean append)throws Exception{String p=path.trim();int slash=p.lastIndexOf('/');String parent=slash<0?"":p.substring(0,slash);String name=slash<0?p:p.substring(slash+1);if(name.isEmpty()||name.contains(".."))throw new IOException("Unsafe filename");DocumentFile dir=resolve(c,parent,true);DocumentFile f=dir.findFile(name);if(f==null)f=dir.createFile("text/plain",name);if(f==null)throw new IOException("Cannot create file");if(append)content=read(c,p)+content;try(OutputStream out=c.getContentResolver().openOutputStream(f.getUri(),"wt")){if(out==null)throw new IOException("Cannot write file");out.write(content.getBytes(StandardCharsets.UTF_8));}return "Wrote "+content.length()+" characters to "+p;}
  private static String rename(Context c,String path,String newName)throws Exception{if(newName.trim().isEmpty()||newName.contains("/"))throw new IOException("Invalid new name");DocumentFile f=resolve(c,path,false);if(!f.renameTo(newName.trim()))throw new IOException("Rename failed");return "Renamed to "+newName.trim();}
  private static String mkdir(Context c,String path)throws Exception{resolve(c,path,true);return "Directory ready: "+path;}
  private static String delete(Context c,String path)throws Exception{DocumentFile f=resolve(c,path,false);if(!f.delete())throw new IOException("Delete failed");return "Deleted "+path;}
  private static byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>512000)throw new IOException("File too large");b.write(buf,0,n);}return b.toByteArray();}
  private static String cap(String s){return s.length()>12000?s.substring(0,12000)+"\n[truncated]":s;}
}
