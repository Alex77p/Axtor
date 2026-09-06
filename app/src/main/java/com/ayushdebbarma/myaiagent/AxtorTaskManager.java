package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONObject;
import java.util.UUID;

/** Persistent, cancellable task state for multi-step agent work. */
public final class AxtorTaskManager {
    private static final String PREF="axtor_tasks";
    private static final String KEY="active";
    private AxtorTaskManager() {}
    public static synchronized JSONObject start(Context c,String goal){
        JSONObject o=new JSONObject();
        try{o.put("id",UUID.randomUUID().toString());o.put("goal",goal==null?"":goal.trim());o.put("state","running");o.put("step",0);o.put("createdAt",System.currentTimeMillis());save(c,o);}catch(Exception ignored){}
        return o;
    }
    public static synchronized JSONObject update(Context c,String state,int step,String detail){
        JSONObject o=active(c);try{o.put("state",state==null?"unknown":state);o.put("step",Math.max(0,step));o.put("detail",detail==null?"":detail.substring(0,Math.min(2000,detail.length())));o.put("updatedAt",System.currentTimeMillis());save(c,o);}catch(Exception ignored){}return o;
    }
    public static synchronized JSONObject active(Context c){try{return new JSONObject(c.getSharedPreferences(PREF,0).getString(KEY,"{}"));}catch(Exception e){return new JSONObject();}}
    public static synchronized void cancel(Context c){JSONObject o=active(c);try{o.put("state","cancelled").put("updatedAt",System.currentTimeMillis());save(c,o);}catch(Exception ignored){}}
    public static boolean isCancelled(Context c){return "cancelled".equals(active(c).optString("state",""));}
    public static synchronized void clear(Context c){if(c!=null)c.getSharedPreferences(PREF,0).edit().remove(KEY).apply();}
    private static void save(Context c,JSONObject o){if(c!=null)c.getSharedPreferences(PREF,0).edit().putString(KEY,o.toString()).apply();}
}
