package com.ayushdebbarma.myaiagent;

import android.content.Context;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Central agent: deterministic tools first, structured AI tool reasoning second, then conversation. */
public final class AxtorAgent {
    public interface Callback { void onReply(String text); void onError(String message); }
    private static final String PREF="axtor_agent";
    private static final String HISTORY="history";
    private static final int MAX_HISTORY=16;
    private AxtorAgent() {}

    public static void handle(Context context,String command,Callback callback){
        final String q=command==null?"":command.trim();
        if(q.isEmpty()){callback.onReply("Say a command or ask a question.");return;}
        ComponentRegistry.register(context,"agent-router","3","running");
        try{
            String lower=q.toLowerCase(Locale.ROOT);
            if(lower.matches(".*\\b(health|diagnostic|diagnostics|self[- ]check|status check)\\b.*")){String answer=AxtorDiagnostics.humanReport(context);remember(context,"user",q);remember(context,"assistant",answer);callback.onReply(answer);return;}
            String action=DeviceAutomation.execute(context,q);
            if(action!=null){String verified=AgentExecutionVerifier.verify(context,q,action);remember(context,"user",q);remember(context,"tool",verified);remember(context,"assistant",verified);callback.onReply(verified);return;}
            if(looksLikeDeviceAction(q)){String answer="I understood that as a device action, but Axtor does not have a safe tool for it yet.";remember(context,"user",q);remember(context,"assistant",answer);callback.onReply(answer);return;}
            String model=AppCore.activeModel(context);
            if((model==null||model.isEmpty())&&!HybridAiRouter.onlineEnabled(context)){String answer=AppCore.answer(q);remember(context,"user",q);remember(context,"assistant",answer);callback.onReply(answer);return;}
            String system="You are Axtor, a private Android assistant. Answer accurately and directly. You may request a device action only by returning JSON exactly like {\"type\":\"action\",\"action\":\"<safe natural-language device command>\"}. Otherwise return normal text. Never claim an action was executed. Only request simple supported actions such as open an installed app, go home/back/recents, lock screen, volume up/down/mute/unmute, open Android settings, or sound-trigger controls. Never request raw intents, URLs, shell commands, credentials, accessibility changes, destructive actions, or security-sensitive operations. Accessibility status: "+accessibilityStatus(context);
            String prompt=buildPrompt(context,q);remember(context,"user",q);
            HybridAiRouter.generate(context,model,prompt,system,192,new HybridAiRouter.Callback(){
                public void onSuccess(String text,double tps){
                    String raw=text==null?"":text.trim();
                    try{
                        JSONObject actionRequest=parseAction(raw);
                        if(actionRequest!=null){
                            String requested=actionRequest.optString("action","").trim();
                            if(!isModelActionSafe(requested)){
                                String blocked="Axtor generated an action that is outside the safe device-tool policy, so it was not executed.";
                                remember(context,"assistant",blocked);callback.onReply(blocked);return;
                            }
                            String result=DeviceAutomation.execute(context,requested);
                            if(result==null){String blocked="Axtor selected a device action that is not currently supported.";remember(context,"tool",blocked);remember(context,"assistant",blocked);callback.onReply(blocked);return;}
                            String verified=AgentExecutionVerifier.verify(context,requested,result);
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
    private static boolean isModelActionSafe(String action){String l=action==null?"":action.toLowerCase(Locale.ROOT).trim();if(l.isEmpty()||l.length()>240||l.startsWith("intent ")||l.startsWith("url ")||l.contains("shell")||l.contains("command line")||l.contains("password")||l.contains("credential")||l.contains("root")||l.contains("wipe")||l.contains("delete")||l.contains("uninstall")||l.contains("factory reset")||l.contains("unlock"))return false;return l.matches(".*\\b(open|launch|start|stop|enable|disable|turn|switch|lock|mute|unmute|increase|decrease|lower|raise|go|show)\\b.*");}
}
