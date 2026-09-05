package com.ayushdebbarma.myaiagent;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

public final class AppCore {
  public static final String CREATOR="Ayush Debbarma";
  private static final String PREF="myaiagent"; private static final String MODELS="models"; private static final String FLOWS="flows"; private static final String ACTIVE_MODEL="active_model";
  private AppCore(){}
  public static String answer(String input){
    String q=input==null?"":input.trim(); String l=q.toLowerCase(Locale.ROOT);
    if(l.contains("who created")||l.contains("who made")||l.contains("who developed")||l.contains("your creator")||l.equals("creator")||l.contains("who is your creator")) return CREATOR+" is the creator of Axtor.";
    if(l.contains("what are you")) return "I am Axtor, an offline-first Android assistant created by "+CREATOR+".";
    return "I received: "+q+". Connect a compatible local model runtime to enable generative AI; device commands and automations remain available locally.";
  }
  public static JSONArray models(Context c){try{return new JSONArray(c.getSharedPreferences(PREF,0).getString(MODELS,"[]"));}catch(Exception e){return new JSONArray();}}
  public static void addModel(Context c,String name,Uri uri){try{JSONObject o=new JSONObject();o.put("name",name);o.put("uri",uri.toString());o.put("importedAt",System.currentTimeMillis());JSONArray a=models(c);a.put(o);c.getSharedPreferences(PREF,0).edit().putString(MODELS,a.toString()).apply();}catch(Exception ignored){}}
  public static void addLocalModel(Context c,String name,String path){try{JSONObject o=new JSONObject();o.put("name",name);o.put("path",path);o.put("importedAt",System.currentTimeMillis());o.put("runtime","llama.cpp/GGUF");JSONArray a=models(c);a.put(o);c.getSharedPreferences(PREF,0).edit().putString(MODELS,a.toString()).putString(ACTIVE_MODEL,path).apply();}catch(Exception ignored){}}
  public static String activeModel(Context c){return c.getSharedPreferences(PREF,0).getString(ACTIVE_MODEL,"");}
  public static boolean hasUsableActiveModel(Context c){String p=activeModel(c);return !p.isEmpty()&&new File(p).isFile()&&new File(p).length()>4;}
  public static void setActiveModel(Context c,String path){c.getSharedPreferences(PREF,0).edit().putString(ACTIVE_MODEL,path==null?"":path).apply();}
  public static void setFlows(Context c,JSONArray a){c.getSharedPreferences(PREF,0).edit().putString(FLOWS,a==null?"[]":a.toString()).apply();}
  public static JSONArray flows(Context c){try{return new JSONArray(c.getSharedPreferences(PREF,0).getString(FLOWS,"[]"));}catch(Exception e){return new JSONArray();}}
  public static void exportModels(Context c,OutputStream out)throws Exception{out.write(models(c).toString(2).getBytes("UTF-8"));}
  public static void exportFlows(Context c,OutputStream out)throws Exception{out.write(flows(c).toString(2).getBytes("UTF-8"));}
  public static void importFlows(Context c,InputStream in)throws Exception{String s=read(in);JSONArray a=new JSONArray(s);setFlows(c,a);}
  private static String read(InputStream in)throws Exception{BufferedReader b=new BufferedReader(new InputStreamReader(in,"UTF-8"));StringBuilder s=new StringBuilder();String x;while((x=b.readLine())!=null)s.append(x).append('\n');return s.toString();}
}
