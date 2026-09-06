package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small, local-only memory store. Nothing is learned or uploaded unless the user explicitly configures an online endpoint. */
public final class AxtorMemory {
    private static final String PREF="axtor_memory";
    private static final String KEY="items";
    private static final int MAX_ITEMS=64;
    private AxtorMemory() {}

    public static void remember(Context c,String key,String value){
        if(c==null||key==null||value==null)return;
        String k=key.trim(),v=value.trim();
        if(k.isEmpty()||v.isEmpty()||k.length()>120||v.length()>2000)return;
        JSONArray old=items(c),out=new JSONArray();
        for(int i=0;i<old.length();i++){JSONObject o=old.optJSONObject(i);if(o==null)continue;if(k.equalsIgnoreCase(o.optString("key","")))continue;out.put(o);}
        try{out.put(new JSONObject().put("key",k).put("value",v).put("time",System.currentTimeMillis()));}catch(Exception ignored){}
        while(out.length()>MAX_ITEMS){JSONArray trimmed=new JSONArray();for(int i=1;i<out.length();i++)trimmed.put(out.opt(i));out=trimmed;}
        c.getSharedPreferences(PREF,0).edit().putString(KEY,out.toString()).apply();
    }
    public static String get(Context c,String key){if(c==null||key==null)return "";JSONArray a=items(c);for(int i=a.length()-1;i>=0;i--){JSONObject o=a.optJSONObject(i);if(o!=null&&key.trim().equalsIgnoreCase(o.optString("key","")))return o.optString("value","");}return "";}
    public static JSONArray items(Context c){try{return new JSONArray(c.getSharedPreferences(PREF,0).getString(KEY,"[]"));}catch(Exception e){return new JSONArray();}}
    public static void forget(Context c,String key){if(c==null||key==null)return;JSONArray old=items(c),out=new JSONArray();for(int i=0;i<old.length();i++){JSONObject o=old.optJSONObject(i);if(o!=null&&!key.trim().equalsIgnoreCase(o.optString("key","")))out.put(o);}c.getSharedPreferences(PREF,0).edit().putString(KEY,out.toString()).apply();}
    public static void clear(Context c){if(c!=null)c.getSharedPreferences(PREF,0).edit().remove(KEY).apply();}
}
