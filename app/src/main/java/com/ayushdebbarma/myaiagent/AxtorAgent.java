package com.ayushdebbarma.myaiagent;

import android.content.*;
import java.util.*;
import org.json.*;

/** Central voice agent: deterministic Android tools first, then the free online AI planner for multi-step tasks. */
public final class AxtorAgent {
  public interface Callback { void onReply(String text); void onError(String message); }
  private AxtorAgent(){}

  public static void handle(Context context,String command,Callback callback){
    final String q=command==null?"":command.trim();
    if(q.isEmpty()){callback.onError("Empty voice command");return;}
    AxtorReliability.heartbeat(context,"agent-router");
    ComponentRegistry.register(context,"agent-router","6","running");
    String lower=q.toLowerCase(Locale.ROOT);
    try{
      if(lower.equals("cancel task")||lower.equals("stop task")||lower.equals("cancel current task")){AxtorTaskManager.cancel(context);callback.onReply("Current Axtor task cancelled.");return;}
      if(lower.startsWith("remember that ")){AxtorMemory.remember(context,"user_fact",q.substring(14).trim());callback.onReply("Saved locally.");return;}
      if(lower.matches(".*\\b(health|diagnostic|diagnostics|self[- ]check|status check)\\b.*")){callback.onReply(AxtorDiagnostics.humanReport(context));return;}
      if(lower.equals("configure online ai")||lower.equals("setup online ai")||lower.equals("enable online ai")){launch(context,OnlineAiSetupActivity.class);callback.onReply("Opening secure online AI setup.");return;}
      if(lower.equals("grant file access")||lower.equals("give axtor file access")||lower.equals("allow file access")){launch(context,FileAccessActivity.class);callback.onReply("Choose the folder you want Axtor to manage.");return;}
      if(lower.equals("remove online ai")||lower.equals("disable online ai")){OnlineAiRouter.clear(context);callback.onReply("Online AI has been disabled.");return;}
      if(lower.equals("file access status")){callback.onReply(FileAgentTools.hasGrantedTree(context)?"Axtor has access to the selected file folder.":"No shared file folder is granted. Say grant file access.");return;}

      String action=DeviceAutomation.execute(context,q);
      if(action!=null){String verified=AgentExecutionVerifier.verify(context,q,action);callback.onReply(verified);return;}
      if(looksLikeDeviceAction(q)&&!OnlineAiRouter.configured(context)){callback.onError("That device action is not supported safely by Axtor");return;}

      if(!OnlineAiRouter.configured(context)){
        localCommand(context,q,callback);return;
      }
      onlinePlan(context,q,callback,0,null);
    }catch(Throwable t){String m=t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();AxtorReliability.recordFailure(context,"agent-router",m);callback.onError(m);}
  }

  private static void localCommand(Context c,String q,Callback cb){
    String model=AppCore.activeModel(c);
    if(model==null||model.isEmpty()||!AppCore.hasUsableActiveModel(c)){cb.onError("No executable command recognized. Configure free online AI or load a local GGUF model.");return;}
    String system="You are Axtor's offline voice-command parser. Return JSON only as {\"type\":\"action\",\"action\":\"VALUE\"}. Allowed actions: OPEN_APP:<installed app label>, GO_HOME, GO_BACK, OPEN_RECENTS, OPEN_NOTIFICATIONS, LOCK_SCREEN, VOLUME_UP, VOLUME_DOWN, MUTE, UNMUTE, OPEN_SETTINGS, OPEN_WIFI_SETTINGS, OPEN_BLUETOOTH_SETTINGS, OPEN_APP_SETTINGS, OPEN_ACCESSIBILITY_SETTINGS, OPEN_VOICE_INPUT_SETTINGS, OPEN_NOTIFICATION_SETTINGS, SOUND_TRIGGER_START, SOUND_TRIGGER_STOP, OPEN_SOUND_TRIGGER_SETTINGS, SET_ALARM_1_MINUTE. Never output shell, raw intents, URLs, credentials, unlocking, or destructive actions.";
    HybridAiRouter.generate(c,model,"Spoken command: "+q+"\nReturn one executable action JSON.",system,96,new HybridAiRouter.Callback(){
      public void onSuccess(String text,double tps){try{JSONObject o=parseAction(text);String a=o==null?null:canonicalModelAction(o.optString("action",""));if(a==null||a.equals("UNSUPPORTED")){cb.onError("No executable Axtor command was recognized");return;}String dc=commandForCanonical(a);String r=DeviceAutomation.execute(c,dc);if(r==null){cb.onError("The recognized device action is not currently supported");return;}cb.onReply(AgentExecutionVerifier.verify(c,dc,r));}catch(Exception e){cb.onError("Could not parse the local action");}}
      public void onError(String m){cb.onError(m);}
    });
  }

  private static void onlinePlan(Context c,String original,Callback cb,int step,String prior){
    if(step>=6){cb.onError("Axtor reached the safe task-step limit.");return;}
    String system="You are Axtor, a voice-controlled Android agent. Plan one safe operation at a time. Return JSON only: {\"type\":\"tool\",\"action\":\"ACTION\",\"args\":{...}} or {\"type\":\"device\",\"command\":\"...\"} or {\"type\":\"final\",\"message\":\"...\"}. File actions: LIST_FILES(path), SEARCH_FILES(query), READ_FILE(path), WRITE_FILE(path,content), APPEND_FILE(path,content), RENAME_FILE(path,new_name), CREATE_DIRECTORY(path). DELETE_FILE is forbidden unless the user explicitly says 'confirm delete'. Device commands must be ordinary safe Axtor commands such as open an app, go home, volume up/down, mute, settings, notifications, lock screen. Never request shell/ADB/su, arbitrary intents, credentials, security changes, unlocking, or destructive device actions. File access is limited to the folder explicitly granted to Axtor. Keep file reads under 512 KB and responses concise. For a write, produce the complete content to write. After a tool result, decide the next single operation or final answer.";
    String user=original+(prior==null?"":"\nPrevious operation result:\n"+prior)+"\nReturn exactly one next JSON action.";
    OnlineAiRouter.generate(c,system,user,new OnlineAiRouter.Callback(){
      public void onSuccess(String raw){try{
        JSONObject o=parseJson(raw);if(o==null){cb.onError("Online AI returned invalid agent JSON");return;}
        String type=o.optString("type","").toLowerCase(Locale.ROOT);
        if(type.equals("final")){cb.onReply(o.optString("message","Task complete."));return;}
        if(type.equals("device")){String cmd=o.optString("command","").trim();String policy=AxtorCommandSecurityPolicy.authorizeVoice(c,cmd);if(!"OK".equals(policy)){cb.onError("Device action blocked by Axtor security policy");return;}String r=DeviceAutomation.execute(c,cmd);if(r==null){cb.onError("Device action failed or is unsupported");return;}onlinePlan(c,original,cb,step+1,AgentExecutionVerifier.verify(c,cmd,r));return;}
        if(type.equals("tool")){String a=o.optString("action","").trim();JSONObject args=o.optJSONObject("args");if(args==null)args=new JSONObject();if(a.equals("DELETE_FILE")){cb.onError("Deleting files requires an explicit confirmation command and is not enabled through autonomous planning.");return;}String result=FileAgentTools.execute(c,a,args);if(result==null){cb.onError("Unknown Axtor file tool: "+a);return;}onlinePlan(c,original,cb,step+1,result);return;}
        cb.onError("Online AI returned an unsupported agent action");
      }catch(Exception e){cb.onError("Online AI agent parse error");}}
      public void onError(String m){AxtorReliability.recordFailure(c,"online-ai",m);cb.onError(m);}
    });
  }

  private static void launch(Context c,Class<?> cls){Intent i=new Intent(c,cls);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);c.startActivity(i);}
  private static JSONObject parseJson(String raw){if(raw==null||raw.length()>12000)return null;String s=raw.trim();int a=s.indexOf('{'),b=s.lastIndexOf('}');if(a<0||b<=a)return null;try{return new JSONObject(s.substring(a,b+1));}catch(Exception e){return null;}}
  private static JSONObject parseAction(String raw){JSONObject o=parseJson(raw);return o!=null&&"action".equalsIgnoreCase(o.optString("type",""))?o:null;}
  public static String canonicalModelAction(String value){String v=value==null?"":value.trim();if(v.equalsIgnoreCase("UNSUPPORTED"))return "UNSUPPORTED";String u=v.toUpperCase(Locale.ROOT);String[] ok={"GO_HOME","GO_BACK","OPEN_RECENTS","OPEN_NOTIFICATIONS","LOCK_SCREEN","VOLUME_UP","VOLUME_DOWN","MUTE","UNMUTE","OPEN_SETTINGS","OPEN_WIFI_SETTINGS","OPEN_BLUETOOTH_SETTINGS","OPEN_APP_SETTINGS","OPEN_ACCESSIBILITY_SETTINGS","OPEN_VOICE_INPUT_SETTINGS","OPEN_NOTIFICATION_SETTINGS","SOUND_TRIGGER_START","SOUND_TRIGGER_STOP","OPEN_SOUND_TRIGGER_SETTINGS","SET_ALARM_1_MINUTE"};for(String x:ok)if(u.equals(x))return x;if(u.startsWith("OPEN_APP:")&&v.substring(9).trim().length()>0)return "OPEN_APP:"+v.substring(9).trim();return null;}
  private static String commandForCanonical(String a){if(a.startsWith("OPEN_APP:"))return "open "+a.substring(9);switch(a){case "GO_HOME":return "go home";case "GO_BACK":return "go back";case "OPEN_RECENTS":return "open recent apps";case "OPEN_NOTIFICATIONS":return "open notifications";case "LOCK_SCREEN":return "lock screen";case "VOLUME_UP":return "volume up";case "VOLUME_DOWN":return "volume down";case "MUTE":return "mute";case "UNMUTE":return "unmute";case "OPEN_SETTINGS":return "open settings";case "OPEN_WIFI_SETTINGS":return "open wifi settings";case "OPEN_BLUETOOTH_SETTINGS":return "open bluetooth settings";case "OPEN_APP_SETTINGS":return "open app settings";case "OPEN_ACCESSIBILITY_SETTINGS":return "open accessibility settings";case "OPEN_VOICE_INPUT_SETTINGS":return "open voice input settings";case "OPEN_NOTIFICATION_SETTINGS":return "open notification settings";case "SOUND_TRIGGER_START":return "start sound triggers";case "SOUND_TRIGGER_STOP":return "stop sound triggers";case "OPEN_SOUND_TRIGGER_SETTINGS":return "open sound trigger settings";case "SET_ALARM_1_MINUTE":return "set alarm one minute";default:return "";}}
  private static boolean looksLikeDeviceAction(String value){String l=value.toLowerCase(Locale.ROOT);return l.matches(".*\\b(open|launch|start|stop|enable|disable|turn|switch|set|lock|unlock|mute|unmute|increase|decrease|lower|raise|go|show)\\b.*")&&l.matches(".*\\b(app|application|settings|wifi|wi-fi|bluetooth|volume|screen|phone|device|home|back|recent|notification|alarm|sound trigger|accessibility|voice)\\b.*");}
  public static boolean accessibilityAvailable(){return AxtorAccessibilityService.isEnabled();}
  public static String accessibilityStatus(Context c){if(AxtorAccessibilityService.isEnabled())return "Accessibility Service connected.";long last=AxtorAccessibilityService.lastConnectedAt();return last>0?"Accessibility Service disconnected. Last connected: "+last+".":"Accessibility Service is not enabled.";}
}
