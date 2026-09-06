package com.ayushdebbarma.myaiagent;

import android.content.Context;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Central agent: deterministic tools first, strictly allowlisted AI actions second, then conversation. */
public final class AxtorAgent {
    public interface Callback { void onReply(String text); void onError(String message); }
    private static final String PREF="axtor_agent";
    private static final String HISTORY="history";
    private static final int MAX_HISTORY=16;
    private AxtorAgent() {}

    public static void handle(Context context,String command,Callback callback){
        final String q=command==null?"":command.trim();
        if(q.isEmpty()){callback.onReply("Say a command or ask a question.");return;}
        ComponentRegistry.register(context,"agent-router","4","running");
        try{
            String lower=q.toLowerCase(Locale.ROOT);
            if(lower.matches(".*\\b(health|diagnostic|diagnostics|self[- ]check|status check)\\b.*")){String answer=AxtorDiagnostics.humanReport(context);remember(context,"user",q);remember(context,"assistant",answer);callback.onReply(answer);return;}
            String action=DeviceAutomation.execute(context,q);
            if(action!=null){String verified=AgentExecutionVerifier.verify(context,q,action);remember(context,"user",q);remember(context,"tool",verified);remember(context,"assistant",verified);callback.onReply(verified);return;}
            if(looksLikeDeviceAction(q)){String answer="I understood that as a device action, but Axtor does not have a safe tool for it yet.";remember(context,"user",q);remember(context,"assistant",answer);callback.onReply(answer);return;}
            String model=AppCore.activeModel(context);
            if((model==null||model.isEmpty())&&!HybridAiRouter.onlineEnabled(context)){String answer=AppCore.answer(q);remember(context,"user",q);remember(context,"assistant",answer);callback.onReply(answer);return;}
            String system="You are Axtor, a private Android assistant. Answer accurately and directly. For a device action, return JSON exactly with type=action and one canonical action value. Allowed values are: OPEN_APP:<installed app label>, GO_HOME, GO_BACK, OPEN_RECENTS, OPEN_NOTIFICATIONS, LOCK_SCREEN, VOLUME_UP, VOLUME_DOWN, MUTE, UNMUTE, OPEN_SETTINGS, OPEN_WIFI_SETTINGS, OPEN_BLUETOOTH_SETTINGS, OPEN_APP_SETTINGS, OPEN_ACCESSIBILITY_SETTINGS, OPEN_VOICE_INPUT_SETTINGS, OPEN_NOTIFICATION_SETTINGS, SOUND_TRIGGER_START, SOUND_TRIGGER_STOP, OPEN_SOUND_TRIGGER_SETTINGS, SET_ALARM_1_MINUTE. For normal questions return plain text. Never claim an action was executed. Never request raw intents, URLs, shell commands, credentials, accessibility changes, destructive actions, security-sensitive operations, or unlocking. Accessibility status: "+accessibilityStatus(context);
            String prompt=buildPrompt(context,q);remember(context,"user",q);
            HybridAiRouter.generate(context,model,prompt,system,192,new HybridAiRouter.Callback(){
                public void onSuccess(String text,double tps){
                    String raw=text==null?"":text.trim();
                    try{
                        JSONObject actionRequest=parseAction(raw);
                        if(actionRequest!=null){
                            String canonical=canonicalModelAction(actionRequest.optString("action",""));
                            if(canonical==null){
                                String blocked="Axtor generated an action outside the safe device-tool policy, so it was not executed.";
                                remember(context,"assistant",blocked);callback.onReply(blocked);return;
                            }
                            String command=commandForCanonical(canonical);
                            String result=DeviceAutomation.execute(context,command);
                            if(result==null){String blocked="Axtor selected a device action that is not currently supported.";remember(context,"tool",blocked);remember(context,"assistant",blocked);callback.onReply(blocked);return;}
                            String verified=AgentExecutionVerifier.verify(context,command,result);
                            remember(context,"tool",verified);remember(context,"assistant",verified);callback.onReply(verified);return;
                        }
                    }catch(Exception ignored){}
                    String answer=raw.isEmpty()?"I couldn't generate a response.":raw;
                    remember(context,"assistant",answer);callback.onReply(answer);
                }
                public void onError(String message){AutonomousRepairCoordinator.onFailure(context,"hybrid-ai-router",message);callback.onError("AI error: "+message);}
            });
        }catch(OutOfMemoryError oom){try{LlamaRuntime.releaseCachedModel();}catch(Throwable ignored){}AutonomousRepairCoordinator.onFailure(context,"llama-runtime","Out of memory");callback.onError("The model needs more memory. Try a smaller quantized GGUF model.");}
        catch(Throwable t){String message=t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();AutonomousRepairCoordinator.onFailure(context,"agent-router",message);callback.onError(message);}
    }

    public static boolean accessibilityAvailable(){return AxtorAccessibilityService.isEnabled();}
    public static String accessibilityStatus(Context context){if(AxtorAccessibilityService.isEnabled())return "Accessibility Service connected.";long last=AxtorAccessibilityService.lastConnectedAt();if(last>0)return "Accessibility Service disconnected. Last connected: "+last+".";return "Accessibility Service is not enabled.";}
    public static JSONArray history(Context context){try{return new JSONArray(context.getSharedPreferences(PREF,0).getString(HISTORY,"[]"));}catch(Exception e){return new JSONArray();}}
    public static void clearHistory(Context context){context.getSharedPreferences(PREF,0).edit().remove(HISTORY).apply();}
    private static void remember(Context context,String role,String text){try{android.content.SharedPreferences p=context.getSharedPreferences(PREF,0);JSONArray old=history(context),out=new JSONArray();int start=Math.max(0,old.length()-(MAX_HISTORY-1));for(int i=start;i<old.length();i++)out.put(old.opt(i));JSONObject item=new JSONObject();item.put("role",role);item.put("text",text==null?"":text.substring(0,Math.min(text.length(),6000)));item.put("time",System.currentTimeMillis());out.put(item);p.edit().putString(HISTORY,out.toString()).apply();}catch(Exception ignored){}}
    private static boolean looksLikeDeviceAction(String value){String l=value.toLowerCase(Locale.ROOT);return l.matches(".*\\b(open|launch|start|stop|enable|disable|turn|switch|set|lock|unlock|mute|unmute|increase|decrease|lower|raise|go|show)\\b.*")&&l.matches(".*\\b(app|application|settings|wifi|wi-fi|bluetooth|volume|screen|phone|device|home|back|recent|notification|alarm|sound trigger|accessibility|voice)\\b.*");}
    private static String buildPrompt(Context context,String current){StringBuilder b=new StringBuilder();b.append("Conversation history:\n");JSONArray h=history(context);for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o==null)continue;b.append(o.optString("role","user")).append(": ").append(o.optString("text","")).append('\\n');}b.append("user: ").append(current).append('\\n').append("assistant:");return b.toString();}
    private static JSONObject parseAction(String raw){if(raw==null||raw.length()>12000)return null;String s=raw.trim();if(!s.startsWith("{")||!s.endsWith("}"))return null;try{JSONObject o=new JSONObject(s);return "action".equalsIgnoreCase(o.optString("type",""))?o:null;}catch(Exception e){return null;}}

    /** Converts model output into one exact, bounded command. Unknown values are rejected. */
    public static String canonicalModelAction(String value){
        String v=value==null?"":value.trim();
        if(v.length()<1||v.length()>200)return null;
        String u=v.toUpperCase(Locale.ROOT);
        if(u.equals("GO_HOME")||u.equals("GO_BACK")||u.equals("OPEN_RECENTS")||u.equals("OPEN_NOTIFICATIONS")||u.equals("LOCK_SCREEN")||u.equals("VOLUME_UP")||u.equals("VOLUME_DOWN")||u.equals("MUTE")||u.equals("UNMUTE")||u.equals("OPEN_SETTINGS")||u.equals("OPEN_WIFI_SETTINGS")||u.equals("OPEN_BLUETOOTH_SETTINGS")||u.equals("OPEN_APP_SETTINGS")||u.equals("OPEN_ACCESSIBILITY_SETTINGS")||u.equals("OPEN_VOICE_INPUT_SETTINGS")||u.equals("OPEN_NOTIFICATION_SETTINGS")||u.equals("SOUND_TRIGGER_START")||u.equals("SOUND_TRIGGER_STOP")||u.equals("OPEN_SOUND_TRIGGER_SETTINGS")||u.equals("SET_ALARM_1_MINUTE"))return u;
        if(u.startsWith("OPEN_APP:")){String name=v.substring(9).trim();if(name.isEmpty()||name.length()>80||name.matches(".*[\\r\\n].*"))return null;return "OPEN_APP:"+name;}
        return null;
    }

    private static String commandForCanonical(String action){
        if(action.startsWith("OPEN_APP:"))return "open "+action.substring(9).trim();
        switch(action){
            case "GO_HOME":return "go home";
            case "GO_BACK":return "go back";
            case "OPEN_RECENTS":return "open recent apps";
            case "OPEN_NOTIFICATIONS":return "open notifications";
            case "LOCK_SCREEN":return "lock screen";
            case "VOLUME_UP":return "volume up";
            case "VOLUME_DOWN":return "volume down";
            case "MUTE":return "mute";
            case "UNMUTE":return "unmute";
            case "OPEN_SETTINGS":return "open settings";
            case "OPEN_WIFI_SETTINGS":return "open wifi settings";
            case "OPEN_BLUETOOTH_SETTINGS":return "open bluetooth settings";
            case "OPEN_APP_SETTINGS":return "open app settings";
            case "OPEN_ACCESSIBILITY_SETTINGS":return "open accessibility settings";
            case "OPEN_VOICE_INPUT_SETTINGS":return "open voice input settings";
            case "OPEN_NOTIFICATION_SETTINGS":return "open notification settings";
            case "SOUND_TRIGGER_START":return "start sound triggers";
            case "SOUND_TRIGGER_STOP":return "stop sound triggers";
            case "OPEN_SOUND_TRIGGER_SETTINGS":return "open sound trigger settings";
            case "SET_ALARM_1_MINUTE":return "set alarm one minute";
            default:return "";
        }
    }
}
