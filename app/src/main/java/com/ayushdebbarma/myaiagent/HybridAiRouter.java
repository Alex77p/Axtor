package com.ayushdebbarma.myaiagent;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Offline-first model router with an optional user-configured OpenAI-compatible endpoint. */
public final class HybridAiRouter {
    private static final String PREF="axtor_hybrid";
    private static final int MAX_RESPONSE_BYTES=2*1024*1024;
    private HybridAiRouter() {}
    public interface Callback { void onSuccess(String text,double tps); void onError(String message); }
    public static void generate(Context context,String model,String prompt,String system,int maxTokens,Callback cb){
        if(model!=null&&!model.isEmpty()&&AppCore.hasUsableActiveModel(context)){LlamaRuntime.generate(context,model,prompt,system,maxTokens,new LlamaRuntime.Callback(){public void onSuccess(String text,double tps){cb.onSuccess(text,tps);}public void onError(String message){onlineFallback(context,prompt,system,maxTokens,cb,message);}});return;}
        onlineFallback(context,prompt,system,maxTokens,cb,"No usable local model");
    }
    public static void setEndpoint(Context c,String v){c.getSharedPreferences(PREF,0).edit().putString("endpoint",v==null?"":v.trim()).apply();}
    public static void setApiKey(Context c,String v){c.getSharedPreferences(PREF,0).edit().putString("api_key",v==null?"":v.trim()).apply();}
    public static void setOnlineModel(Context c,String v){c.getSharedPreferences(PREF,0).edit().putString("online_model",v==null?"":v.trim()).apply();}
    public static void setOnlineEnabled(Context c,boolean v){c.getSharedPreferences(PREF,0).edit().putBoolean("enabled",v).apply();}
    public static boolean onlineEnabled(Context c){return c.getSharedPreferences(PREF,0).getBoolean("enabled",false);}
    public static boolean isConfigured(Context c){String endpoint=c.getSharedPreferences(PREF,0).getString("endpoint","").trim();return isHttpUrl(endpoint);}
    private static void onlineFallback(Context c,String prompt,String system,int maxTokens,Callback cb,String localError){
        final String endpoint=c.getSharedPreferences(PREF,0).getString("endpoint","").trim();if(!onlineEnabled(c)||endpoint.isEmpty()){cb.onError(localError);return;}
        if(!isHttpUrl(endpoint)){cb.onError(localError+"; online endpoint must use http:// or https://");return;}
        new Thread(()->{HttpURLConnection conn=null;try{URL url=new URL(endpoint);conn=(HttpURLConnection)url.openConnection();conn.setRequestMethod("POST");conn.setConnectTimeout(10000);conn.setReadTimeout(30000);conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");String key=c.getSharedPreferences(PREF,0).getString("api_key","");if(!key.isEmpty())conn.setRequestProperty("Authorization","Bearer "+key);JSONObject body=new JSONObject();body.put("model",c.getSharedPreferences(PREF,0).getString("online_model",""));JSONArray messages=new JSONArray();messages.put(new JSONObject().put("role","system").put("content",system));messages.put(new JSONObject().put("role","user").put("content",prompt));body.put("messages",messages);body.put("max_tokens",maxTokens);byte[] request=body.toString().getBytes(StandardCharsets.UTF_8);if(request.length>512*1024)throw new IOException("Request is too large");conn.getOutputStream().write(request);int code=conn.getResponseCode();InputStream in=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();String response=read(in,MAX_RESPONSE_BYTES);if(code<200||code>=300)throw new IOException("HTTP "+code);JSONObject json=new JSONObject(response);JSONArray choices=json.optJSONArray("choices");String text="";if(choices!=null&&choices.length()>0){JSONObject choice=choices.optJSONObject(0);JSONObject msg=choice==null?null:choice.optJSONObject("message");if(msg!=null)text=msg.optString("content","");}if(text.trim().isEmpty())throw new IOException("Empty online response");cb.onSuccess(text.trim(),0);}catch(Exception e){cb.onError(localError+"; online fallback failed: "+e.getMessage());}finally{if(conn!=null)conn.disconnect();}},"axtor-online-ai").start();
    }
    private static boolean isHttpUrl(String value){try{URL u=new URL(value);return "http".equalsIgnoreCase(u.getProtocol())||"https".equalsIgnoreCase(u.getProtocol());}catch(Exception e){return false;}}
    private static String read(InputStream in,int maxBytes)throws Exception{if(in==null)return "";ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int total=0,n;while((n=in.read(buf))!=-1){total+=n;if(total>maxBytes)throw new IOException("Response is too large");out.write(buf,0,n);}return out.toString(StandardCharsets.UTF_8.name());}
}
