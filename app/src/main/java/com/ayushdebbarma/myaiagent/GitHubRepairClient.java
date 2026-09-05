package com.ayushdebbarma.myaiagent;

import android.content.Context;
import android.util.Base64;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.json.JSONArray;
import org.json.JSONObject;

/** Remote autonomous repair bridge. Tokens are encrypted with Android Keystore. */
public final class GitHubRepairClient {
    private static final String PREF="axtor_github_repair";
    private static final String KEY_TOKEN="token",KEY_REPO="repo",KEY_WORKFLOW="workflow";
    private static final String KEYSTORE="AndroidKeyStore",KEY_ALIAS="AxtorGitHubRepair",API="https://api.github.com";
    private GitHubRepairClient() {}

    public static boolean configure(Context c,String token,String repository,String workflow){
        if(c==null||token==null||token.trim().isEmpty()||repository==null||!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))return false;
        try{c.getSharedPreferences(PREF,0).edit().putString(KEY_TOKEN,encrypt(token.trim())).putString(KEY_REPO,repository.trim()).putString(KEY_WORKFLOW,workflow==null||workflow.trim().isEmpty()?"axtor-autorepair.yml":workflow.trim()).apply();return true;}catch(Exception e){return false;}
    }
    public static boolean isConfigured(Context c){return token(c)!=null&&!repo(c).isEmpty();}

    public static JSONObject dispatch(Context c,String goal,String target,String replacement){
        JSONObject out=new JSONObject();
        if(!SelfCodingEngine.isSafeSourcePath(target))return error(out,"Unsafe target path");
        if(replacement==null||replacement.length()>48000)return error(out,"Repair body exceeds remote workflow limit");
        String t=token(c);if(t==null)return error(out,"GitHub repair is not configured");
        try{
            JSONObject inputs=new JSONObject().put("goal",goal==null?"":goal.trim()).put("target_path",target.trim()).put("replacement",replacement).put("proposal_id",SelfCodingEngine.latest(c).optString("id","unknown"));
            request("POST","/repos/"+repo(c)+"/actions/workflows/"+URLEncoder.encode(workflow(c),"UTF-8")+"/dispatches",t,new JSONObject().put("ref","main").put("inputs",inputs).toString());
            out.put("status","dispatched").put("repository",repo(c)).put("workflow",workflow(c));
            for(int i=0;i<10;i++){try{Thread.sleep(1000);}catch(InterruptedException x){Thread.currentThread().interrupt();break;}JSONObject r=latestWorkflowRun(c);if(r.optLong("id",0)>0){out.put("runId",r.optLong("id"));out.put("url",r.optString("html_url",""));break;}}
        }catch(Exception e){error(out,e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
        return out;
    }

    public static JSONObject run(Context c,long runId){JSONObject out=new JSONObject();String t=token(c);if(t==null||runId<=0)return error(out,"GitHub repair is not configured");try{JSONObject r=request("GET","/repos/"+repo(c)+"/actions/runs/"+runId,t,null);out.put("status",r.optString("status","unknown")).put("conclusion",r.optString("conclusion","")).put("url",r.optString("html_url","")).put("runId",runId);}catch(Exception e){error(out,e.getMessage());}return out;}

    public static JSONObject fetchSource(Context c,String path){JSONObject out=new JSONObject();if(!SelfCodingEngine.isSafeSourcePath(path))return error(out,"Unsafe source path");String t=token(c);if(t==null)return error(out,"GitHub repair is not configured");try{JSONObject r=request("GET","/repos/"+repo(c)+"/contents/"+path+"?ref=main",t,null);String enc=r.optString("content","").replace("\n","");if(enc.isEmpty())return error(out,"GitHub returned no source content");out.put("status","ok").put("sha",r.optString("sha","")).put("content",new String(Base64.decode(enc,Base64.DEFAULT),StandardCharsets.UTF_8));}catch(Exception e){error(out,e.getMessage());}return out;}

    public static String currentMainSha(Context c){String t=token(c);if(t==null)return "";try{return request("GET","/repos/"+repo(c)+"/git/ref/heads/main",t,null).optJSONObject("object").optString("sha","");}catch(Exception e){return "";}}

    private static JSONObject latestWorkflowRun(Context c)throws Exception{String t=token(c);JSONObject r=request("GET","/repos/"+repo(c)+"/actions/workflows/"+URLEncoder.encode(workflow(c),"UTF-8")+"/runs?event=workflow_dispatch&per_page=1",t,null);JSONArray a=r.optJSONArray("workflow_runs");return a==null||a.length()==0?new JSONObject():a.optJSONObject(0);}
    private static JSONObject request(String method,String path,String token,String body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");c.setRequestProperty("User-Agent","Axtor-Repair-Agent");if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}int code=c.getResponseCode();InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();String text="";if(stream!=null)try(InputStream in=stream){byte[] b=new byte[8192];StringBuilder s=new StringBuilder();int n;while((n=in.read(b))!=-1)s.append(new String(b,0,n,StandardCharsets.UTF_8));text=s.toString();}if(code<200||code>=300)throw new IOException("GitHub API "+code+(text.isEmpty()?"":": "+text));return text.isEmpty()?new JSONObject():new JSONObject(text);}
    private static JSONObject error(JSONObject o,String m){try{o.put("status","error").put("error",m==null?"unknown error":m);}catch(Exception ignored){}return o;}
    private static String repo(Context c){return c.getSharedPreferences(PREF,0).getString(KEY_REPO,"");}
    private static String workflow(Context c){return c.getSharedPreferences(PREF,0).getString(KEY_WORKFLOW,"axtor-autorepair.yml");}
    private static String token(Context c){try{String x=c.getSharedPreferences(PREF,0).getString(KEY_TOKEN,null);return x==null?null:decrypt(x);}catch(Exception e){return null;}}
    private static SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance(KEYSTORE);ks.load(null);if(!ks.containsAlias(KEY_ALIAS)){KeyGenerator g=KeyGenerator.getInstance("AES",KEYSTORE);g.init(256);g.generateKey();}return((KeyStore.SecretKeyEntry)ks.getEntry(KEY_ALIAS,null)).getSecretKey();}
    private static String encrypt(String v)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());byte[] iv=c.getIV(),ct=c.doFinal(v.getBytes(StandardCharsets.UTF_8)),all=new byte[iv.length+ct.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(ct,0,all,iv.length,ct.length);return Base64.encodeToString(all,Base64.NO_WRAP);}
    private static String decrypt(String v)throws Exception{byte[] all=Base64.decode(v,Base64.NO_WRAP);if(all.length<13)throw new IllegalArgumentException("Invalid encrypted token");byte[] iv=new byte[12],ct=new byte[all.length-12];System.arraycopy(all,0,iv,0,12);System.arraycopy(all,12,ct,0,ct.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new javax.crypto.spec.GCMParameterSpec(128,iv));return new String(c.doFinal(ct),StandardCharsets.UTF_8);}
}
