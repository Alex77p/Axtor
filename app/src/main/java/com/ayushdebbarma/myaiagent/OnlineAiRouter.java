package com.ayushdebbarma.myaiagent;

import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import org.json.*;

/** Free online reasoning provider. Uses OpenRouter's free-model router; no paid model is selected. */
public final class OnlineAiRouter {
  private static final String PREF="axtor_online", KEY="api_key_blob", KS="AxtorOnlineKey";
  private static final String ENDPOINT="https://openrouter.ai/api/v1/chat/completions";
  private OnlineAiRouter(){}
  public interface Callback { void onSuccess(String text); void onError(String message); }
  public static boolean configured(Context c){return !getKey(c).isEmpty();}
  public static void clear(Context c){c.getSharedPreferences(PREF,0).edit().remove(KEY).apply();}
  public static void setKey(Context c,String apiKey){try{SecretKey key=getOrCreateKey();byte[] iv=new byte[12];new java.security.SecureRandom().nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));byte[] encrypted=cipher.doFinal(apiKey.trim().getBytes(StandardCharsets.UTF_8));String blob=Base64.encodeToString(iv,Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP);c.getSharedPreferences(PREF,0).edit().putString(KEY,blob).apply();}catch(Exception e){throw new IllegalStateException("Could not securely store online AI key",e);}}
  private static String getKey(Context c){try{String blob=c.getSharedPreferences(PREF,0).getString(KEY,"");if(blob.isEmpty())return "";String[] p=blob.split(":",2);if(p.length!=2)return "";byte[] iv=Base64.decode(p[0],Base64.NO_WRAP),encrypted=Base64.decode(p[1],Base64.NO_WRAP);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,getOrCreateKey(),new GCMParameterSpec(128,iv));return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
  private static SecretKey getOrCreateKey() throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(KS))return (SecretKey)ks.getKey(KS,null);KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(KS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return gen.generateKey();}
  public static void generate(Context c,String system,String user,Callback cb){String apiKey=getKey(c);if(apiKey.isEmpty()){cb.onError("ONLINE_AI_NOT_CONFIGURED");return;}new Thread(()->{HttpURLConnection conn=null;try{JSONObject body=new JSONObject();body.put("model","openrouter/free");body.put("temperature",0.1);body.put("max_tokens",900);JSONArray messages=new JSONArray();messages.put(new JSONObject().put("role","system").put("content",system));messages.put(new JSONObject().put("role","user").put("content",user));body.put("messages",messages);URL url=new URL(ENDPOINT);conn=(HttpURLConnection)url.openConnection();conn.setRequestMethod("POST");conn.setConnectTimeout(15000);conn.setReadTimeout(45000);conn.setDoOutput(true);conn.setRequestProperty("Authorization","Bearer "+apiKey);conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("X-OpenRouter-Title","Axtor");try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}int code=conn.getResponseCode();InputStream in=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();String response=read(in);if(code<200||code>=300){cb.onError("ONLINE_AI_HTTP_"+code);return;}JSONObject root=new JSONObject(response);JSONArray choices=root.optJSONArray("choices");if(choices==null||choices.length()==0){cb.onError("ONLINE_AI_EMPTY_RESPONSE");return;}String text=choices.getJSONObject(0).getJSONObject("message").optString("content","");if(text.trim().isEmpty())cb.onError("ONLINE_AI_EMPTY_RESPONSE");else cb.onSuccess(text.trim());}catch(Exception e){cb.onError("ONLINE_AI_ERROR:"+e.getClass().getSimpleName());}finally{if(conn!=null)conn.disconnect();}}).start();}
  private static String read(InputStream in)throws IOException{if(in==null)return "";try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder();String s;while((s=r.readLine())!=null)b.append(s);return b.toString();}}
}
