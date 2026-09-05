package com.ayushdebbarma.myaiagent;

import android.content.Context;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Central deterministic agent router: automation first, hybrid AI second. */
public final class AxtorAgent {
    public interface Callback { void onReply(String text); void onError(String message); }
    private static final String PREF="axtor_agent";
    private static final String HISTORY="history";
    private static final int MAX_HISTORY=12;
    private AxtorAgent() {}

    public static void handle(Context context,String command,Callback callback){
        final String q=command==null?"":command.trim();
        if(q.isEmpty()){callback.onReply("Say a command or ask a question.");return;}
        ComponentRegistry.register(context,"agent-router","1","running");
        try{
            String action=DeviceAutomation.execute(context,q);
            if(action!=null){String verified=AgentExecutionVerifier.verify(context,q,action);remember(context,"user",q);remember(context,"tool",verified);remember(context,"assistant",verified);callback.onReply(verified);return;}
            if(looksLikeDeviceAction(q)){String answer="I understood that as a device action, but Axtor does not have a safe tool for it yet.";remember(context,"user",q);remember(context,"assistant",answer);callback.onReply(answer);return;}
            String model=AppCore.activeModel(context);
            if((model==null||model.isEmpty())&&!HybridAiRouter.onlineEnabled(context)){String answer=AppCore.answer(q);remember(context,"user",q);remember(context,"assistant",answer);callback.onReply(answer);return;}
            String system="You are Axtor, a private Android assistant. Answer accurately and directly. Never claim internet access when operating locally. Device actions are executed only by Axtor's tool layer; never pretend an action was executed. Keep answers concise when the user asks for a simple fact or command. Accessibility status: "+accessibilityStatus(context);
            String prompt=buildPrompt(context,q);remember(context,"user",q);
            HybridAiRouter.generate(context,model,prompt,system,128,new HybridAiRouter.Callback(){
                public void onSuccess(String text,double tps){String answer=text==null?"":text.trim();if(answer.isEmpty())answer="I couldn't generate a response.";remember(context,"assistant",answer);callback.onReply(answer);}
                public void onError(String message){SelfModificationOrchestrator.onFailure(context,"hybrid-ai-router",message);callback.onError("AI error: "+message);}
            });
        }catch(OutOfMemoryError oom){try{LlamaRuntime.releaseCachedModel();}catch(Throwable ignored){}SelfModificationOrchestrator.onFailure(context,"llama-runtime","Out of memory");callback.onError("The model needs more memory. Try a smaller quantized GGUF model.");}
        catch(Throwable t){SelfModificationOrchestrator.onFailure(context,"agent-router",t.toString());callback.onError(t.getMessage()==null?t.getClass().getSimpleName():t.getMessage());}
    }

    public static boolean accessibilityAvailable(){return AxtorAccessibilityService.isEnabled();}
    public static String accessibilityStatus(Context context){if(AxtorAccessibilityService.isEnabled())return "Accessibility Service connected.";long last=AxtorAccessibilityService.lastConnectedAt();if(last>0)return "Accessibility Service disconnected. Last connected: "+last+".";return "Accessibility Service is not enabled.";}
    public static JSONArray history(Context context){try{return new JSONArray(context.getSharedPreferences(PREF,0).getString(HISTORY,"[]"));}catch(Exception e){return new JSONArray();}}
    public static void clearHistory(Context context){context.getSharedPreferences(PREF,0).edit().remove(HISTORY).apply();}
    private static void remember(Context context,String role,String text){try{android.content.SharedPreferences p=context.getSharedPreferences(PREF,0);JSONArray old=history(context);JSONArray out=new JSONArray();int start=Math.max(0,old.length()-(MAX_HISTORY-1));for(int i=start;i<old.length();i++)out.put(old.opt(i));JSONObject item=new JSONObject();item.put("role",role);item.put("text",text);item.put("time",System.currentTimeMillis());out.put(item);p.edit().putString(HISTORY,out.toString()).apply();}catch(Exception ignored){}}
    private static boolean looksLikeDeviceAction(String value){String l=value.toLowerCase(Locale.ROOT).trim();return l.matches(".*\\b(open|launch|start|stop|enable|disable|turn|switch|set|lock|unlock|mute|unmute|increase|decrease|lower|raise|go|show)\\b.*")&&l.matches(".*\\b(app|application|settings|wifi|wi-fi|bluetooth|volume|screen|phone|device|home|back|recent|notification|alarm|sound trigger|accessibility|voice)\\b.*");}
    private static String buildPrompt(Context context,String current){StringBuilder b=new StringBuilder();b.append("Conversation history:\n");JSONArray h=history(context);for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o==null)continue;b.append(o.optString("role","user")).append(": ").append(o.optString("text","")).append('\n');}b.append("user: ").append(current).append('\n');b.append("assistant:");return b.toString();}
}
