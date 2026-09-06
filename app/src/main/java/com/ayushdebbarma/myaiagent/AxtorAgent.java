package com.ayushdebbarma.myaiagent;

import android.content.Context;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Central command router for Axtor voice mode. It executes only safe device commands. */
public final class AxtorAgent {
    public interface Callback { void onReply(String text); void onError(String message); }
    private static final String PREF="axtor_agent";
    private static final String HISTORY="history";
    private static final int MAX_HISTORY=16;
    private AxtorAgent() {}

    public static void handle(Context context,String command,Callback callback){
        final String q=command==null?"":command.trim();
        if(q.isEmpty()){callback.onError("Empty voice command");return;}
        AxtorReliability.heartbeat(context,"agent-router");
        ComponentRegistry.register(context,"agent-router","5","running");
        try{
            String lower=q.toLowerCase(Locale.ROOT);
            if(lower.equals("cancel task")||lower.equals("stop task")||lower.equals("cancel current task")){AxtorTaskManager.cancel(context);callback.onReply("Current Axtor task cancelled.");return;}
            if(lower.startsWith("remember that ")){String fact=q.substring(14).trim();AxtorMemory.remember(context,"user_fact",fact);callback.onReply("Saved locally.");return;}
            if(lower.matches(".*\\b(health|diagnostic|diagnostics|self[- ]check|status check)\\b.*")){callback.onReply(AxtorDiagnostics.humanReport(context));return;}

            String action=DeviceAutomation.execute(context,q);
            if(action!=null){String verified=AgentExecutionVerifier.verify(context,q,action);callback.onReply(verified);return;}
            if(looksLikeDeviceAction(q)){callback.onError("That device action is not supported safely by Axtor");return;}

            String model=AppCore.activeModel(context);
            if(model==null||model.isEmpty()||!AppCore.hasUsableActiveModel(context)){
                callback.onError("No executable command recognized and no local GGUF command model is loaded");
                return;
            }

            String system="You are Axtor's voice-command parser. Return JSON only, exactly in the form {\"type\":\"action\",\"action\":\"VALUE\"}. Never answer questions or produce conversational text. Only use these actions: OPEN_APP:<installed app label>, GO_HOME, GO_BACK, OPEN_RECENTS, OPEN_NOTIFICATIONS, LOCK_SCREEN, VOLUME_UP, VOLUME_DOWN, MUTE, UNMUTE, OPEN_SETTINGS, OPEN_WIFI_SETTINGS, OPEN_BLUETOOTH_SETTINGS, OPEN_APP_SETTINGS, OPEN_ACCESSIBILITY_SETTINGS, OPEN_VOICE_INPUT_SETTINGS, OPEN_NOTIFICATION_SETTINGS, SOUND_TRIGGER_START, SOUND_TRIGGER_STOP, OPEN_SOUND_TRIGGER_SETTINGS, SET_ALARM_1_MINUTE. If the spoken request cannot be represented by one of these actions, return {\"type\":\"action\",\"action\":\"UNSUPPORTED\"}. Never request raw intents, URLs, shell commands, credentials, accessibility changes, destructive operations, security-sensitive operations, or unlocking.";
            String prompt="Spoken command: "+q+"\nReturn the single executable action JSON.";
            HybridAiRouter.generate(context,model,prompt,system,96,new HybridAiRouter.Callback(){
                public void onSuccess(String text,double tps){
                    try{
                        JSONObject actionRequest=parseAction(text);
                        if(actionRequest==null){callback.onError("Voice command was not returned as an executable action");return;}
                        String canonical=canonicalModelAction(actionRequest.optString("action",""));
                        if(canonical==null){callback.onError("Voice command is outside the safe Axtor action policy");return;}
                        if(canonical.equals("UNSUPPORTED")){callback.onError("No executable Axtor command was recognized");return;}
                        String deviceCommand=commandForCanonical(canonical);
                        String result=DeviceAutomation.execute(context,deviceCommand);
                        if(result==null){callback.onError("The recognized device action is not currently supported");return;}
                        callback.onReply(AgentExecutionVerifier.verify(context,deviceCommand,result));
                    }catch(Exception e){callback.onError("Could not parse the voice command action");}
                }
                public void onError(String message){AxtorReliability.recordFailure(context,"llama-runtime",message);callback.onError(message);}
            });
        }catch(OutOfMemoryError oom){AxtorReliability.recordFailure(context,"llama-runtime","Out of memory");try{LlamaRuntime.releaseCachedModel();}catch(Throwable ignored){}callback.onError("Local command model needs more memory");}
        catch(Throwable t){String message=t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();AxtorReliability.recordFailure(context,"agent-router",message);callback.onError(message);}
    }

    public static boolean accessibilityAvailable(){return AxtorAccessibilityService.isEnabled();}
    public static String accessibilityStatus(Context context){if(AxtorAccessibilityService.isEnabled())return "Accessibility Service connected.";long last=AxtorAccessibilityService.lastConnectedAt();if(last>0)return "Accessibility Service disconnected. Last connected: "+last+".";return "Accessibility Service is not enabled.";}
    public static JSONArray history(Context context){try{return new JSONArray(context.getSharedPreferences(PREF,0).getString(HISTORY,"[]"));}catch(Exception e){return new JSONArray();}}
    public static void clearHistory(Context context){context.getSharedPreferences(PREF,0).edit().remove(HISTORY).apply();}
    private static boolean looksLikeDeviceAction(String value){String l=value.toLowerCase(Locale.ROOT);return l.matches(".*\\b(open|launch|start|stop|enable|disable|turn|switch|set|lock|unlock|mute|unmute|increase|decrease|lower|raise|go|show)\\b.*")&&l.matches(".*\\b(app|application|settings|wifi|wi-fi|bluetooth|volume|screen|phone|device|home|back|recent|notification|alarm|sound trigger|accessibility|voice)\\b.*");}
    private static JSONObject parseAction(String raw){if(raw==null||raw.length()>4000)return null;String s=raw.trim();if(!s.startsWith("{")||!s.endsWith("}"))return null;try{JSONObject o=new JSONObject(s);return "action".equalsIgnoreCase(o.optString("type",""))?o:null;}catch(Exception e){return null;}}
    public static String canonicalModelAction(String value){String v=value==null?"":value.trim();if(v.length()<1||v.length()>200)return null;String u=v.toUpperCase(Locale.ROOT);if(u.equals("UNSUPPORTED"))return u;if(u.equals("GO_HOME")||u.equals("GO_BACK")||u.equals("OPEN_RECENTS")||u.equals("OPEN_NOTIFICATIONS")||u.equals("LOCK_SCREEN")||u.equals("VOLUME_UP")||u.equals("VOLUME_DOWN")||u.equals("MUTE")||u.equals("UNMUTE")||u.equals("OPEN_SETTINGS")||u.equals("OPEN_WIFI_SETTINGS")||u.equals("OPEN_BLUETOOTH_SETTINGS")||u.equals("OPEN_APP_SETTINGS")||u.equals("OPEN_ACCESSIBILITY_SETTINGS")||u.equals("OPEN_VOICE_INPUT_SETTINGS")||u.equals("OPEN_NOTIFICATION_SETTINGS")||u.equals("SOUND_TRIGGER_START")||u.equals("SOUND_TRIGGER_STOP")||u.equals("OPEN_SOUND_TRIGGER_SETTINGS")||u.equals("SET_ALARM_1_MINUTE"))return u;if(u.startsWith("OPEN_APP:")){String name=v.substring(9).trim();if(name.isEmpty()||name.length()>80||name.matches(".*[\\r\\n].*"))return null;return "OPEN_APP:"+name;}return null;}
    private static String commandForCanonical(String action){if(action.startsWith("OPEN_APP:"))return "open "+action.substring(9).trim();switch(action){case "GO_HOME":return "go home";case "GO_BACK":return "go back";case "OPEN_RECENTS":return "open recent apps";case "OPEN_NOTIFICATIONS":return "open notifications";case "LOCK_SCREEN":return "lock screen";case "VOLUME_UP":return "volume up";case "VOLUME_DOWN":return "volume down";case "MUTE":return "mute";case "UNMUTE":return "unmute";case "OPEN_SETTINGS":return "open settings";case "OPEN_WIFI_SETTINGS":return "open wifi settings";case "OPEN_BLUETOOTH_SETTINGS":return "open bluetooth settings";case "OPEN_APP_SETTINGS":return "open app settings";case "OPEN_ACCESSIBILITY_SETTINGS":return "open accessibility settings";case "OPEN_VOICE_INPUT_SETTINGS":return "open voice input settings";case "OPEN_NOTIFICATION_SETTINGS":return "open notification settings";case "SOUND_TRIGGER_START":return "start sound triggers";case "SOUND_TRIGGER_STOP":return "stop sound triggers";case "OPEN_SOUND_TRIGGER_SETTINGS":return "open sound trigger settings";case "SET_ALARM_1_MINUTE":return "set alarm one minute";default:return "";}}
}
