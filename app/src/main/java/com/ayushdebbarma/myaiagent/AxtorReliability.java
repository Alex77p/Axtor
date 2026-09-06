package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONObject;

/** Runtime reliability guard: heartbeat, bounded failures, cancellation and emergency-stop propagation. */
public final class AxtorReliability {
    private static final String PREF="axtor_reliability";
    private static final String HEARTBEAT="heartbeat";
    private static final String FAILURE="failure";
    private AxtorReliability() {}
    public static void heartbeat(Context c,String component){if(c==null)return;c.getSharedPreferences(PREF,0).edit().putString(HEARTBEAT,(component==null?"unknown":component)+"|"+System.currentTimeMillis()).apply();}
    public static long lastHeartbeat(Context c){String s=c==null?"":c.getSharedPreferences(PREF,0).getString(HEARTBEAT,"");int i=s.lastIndexOf('|');try{return i<0?0:Long.parseLong(s.substring(i+1));}catch(Exception e){return 0;}}
    public static void recordFailure(Context c,String component,String error){if(c==null)return;try{String x=error==null?"unknown":error;JSONObject o=new JSONObject().put("component",component==null?"unknown":component).put("error",x.substring(0,Math.min(2000,x.length()))).put("time",System.currentTimeMillis());c.getSharedPreferences(PREF,0).edit().putString(FAILURE,o.toString()).apply();}catch(Exception ignored){}}
    public static JSONObject lastFailure(Context c){try{return new JSONObject(c.getSharedPreferences(PREF,0).getString(FAILURE,"{}"));}catch(Exception e){return new JSONObject();}}
    public static boolean shouldAbort(Context c){return AxtorTaskManager.isCancelled(c);}
    public static void emergencyStop(Context c){if(c!=null)AxtorTaskManager.cancel(c);}
}
