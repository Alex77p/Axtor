package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Persistent, bounded repair-job journal. */
public final class RepairJobStore {
    private static final String PREF="axtor_repair_jobs",KEY="jobs";
    private static final int MAX=20;
    private RepairJobStore() {}
    public static synchronized String create(Context c,String component,String error,String target,String goal,String previousRevision){String id=java.util.UUID.randomUUID().toString();JSONObject j=new JSONObject();try{j.put("id",id).put("component",safe(component)).put("error",safe(error)).put("target",safe(target)).put("goal",safe(goal)).put("previousRevision",safe(previousRevision)).put("status","created").put("createdAt",System.currentTimeMillis()).put("updatedAt",System.currentTimeMillis());save(c,prepend(c,j));}catch(Exception ignored){}return id;}
    public static synchronized void update(Context c,String id,String status,JSONObject data){JSONArray a=read(c);for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j==null||!id.equals(j.optString("id")))continue;try{j.put("status",status).put("updatedAt",System.currentTimeMillis());if(data!=null){java.util.Iterator<String> k=data.keys();while(k.hasNext()){String x=k.next();j.put(x,data.opt(x));}}}catch(Exception ignored){}break;}save(c,a);}
    public static JSONArray all(Context c){return read(c);}
    public static JSONObject latest(Context c){JSONArray a=read(c);return a.length()==0?new JSONObject():a.optJSONObject(0);}
    public static JSONObject get(Context c,String id){JSONArray a=read(c);for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j!=null&&id.equals(j.optString("id")))return j;}return new JSONObject();}
    private static JSONArray prepend(Context c,JSONObject j){JSONArray old=read(c),out=new JSONArray();out.put(j);for(int i=0;i<Math.min(old.length(),MAX-1);i++)out.put(old.opt(i));return out;}
    private static JSONArray read(Context c){try{return new JSONArray(c.getSharedPreferences(PREF,0).getString(KEY,"[]"));}catch(Exception e){return new JSONArray();}}
    private static void save(Context c,JSONArray a){c.getSharedPreferences(PREF,0).edit().putString(KEY,a.toString()).apply();}
    private static String safe(String s){return s==null?"":s.trim();}
}
