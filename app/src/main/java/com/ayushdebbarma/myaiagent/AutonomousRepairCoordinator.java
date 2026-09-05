package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONObject;

/** Bridges runtime failures to bounded model-generated source repairs and remote verification. */
public final class AutonomousRepairCoordinator {
    private static final int MAX_SOURCE=48000;
    private AutonomousRepairCoordinator() {}
    public static void onFailure(Context c,String component,String error){onFailure(c,component,error,targetFor(component));}
    public static void onFailure(Context c,String component,String error,String target){if(c==null||!GitHubRepairClient.isConfigured(c)||!SelfCodingEngine.isSafeSourcePath(target))return;final String comp=component==null?"unknown":component.trim(),err=error==null?"unknown failure":error.trim(),path=target.trim();new Thread(()->run(c,comp,err,path),"axtor-autonomous-repair").start();}
    private static void run(Context c,String component,String error,String target){
        String goal="Repair component "+component+" after runtime failure: "+bound(error,4000);
        JSONObject base=SelfModificationOrchestrator.onFailure(c,component,error);
        String previous=GitHubRepairClient.currentMainSha(c);
        String jobId=RepairJobStore.create(c,component,error,target,goal,previous);
        RepairJobStore.update(c,jobId,"diagnosed",base);
        try{
            JSONObject source=GitHubRepairClient.fetchSource(c,target);
            if(!"ok".equals(source.optString("status"))){fail(c,jobId,source.optString("error","Unable to read source"));return;}
            String current=source.optString("content","");
            if(current.isEmpty()||current.length()>MAX_SOURCE){fail(c,jobId,"Source is empty or exceeds 48 KiB");return;}
            String model=AppCore.activeModel(c);
            if((model==null||model.isEmpty())&&!HybridAiRouter.onlineEnabled(c)){fail(c,jobId,"No AI model available for repair generation");return;}
            RepairJobStore.update(c,jobId,"generating",new JSONObject().put("sourceSha",source.optString("sha","")));
            HybridAiRouter.generate(c,model,repairPrompt(component,error,target,current),"You are Axtor's bounded source-repair engine. Return JSON only with keys target_path, replacement, rationale. Replacement must be the complete file. Keep the target path unchanged. Do not modify security-sensitive files. Do not add network, shell, reflection, credential access, or destructive behavior.",512,new HybridAiRouter.Callback(){
                public void onSuccess(String text,double tps){submit(c,jobId,goal,target,current,text,tps);}
                public void onError(String message){fail(c,jobId,"Repair generation failed: "+message);}
            });
        }catch(Exception e){fail(c,jobId,e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
    }
    private static void submit(Context c,String jobId,String goal,String target,String current,String raw,double tps){try{
        JSONObject p=parse(raw);String pt=p.optString("target_path","").trim(),replacement=p.optString("replacement","");
        if(!target.equals(pt)||!SelfCodingEngine.isSafeSourcePath(pt)){fail(c,jobId,"Model returned an invalid repair target");return;}
        if(replacement.isEmpty()||replacement.length()>MAX_SOURCE||replacement.equals(current)){fail(c,jobId,"Model returned an invalid or unchanged replacement");return;}
        JSONObject r=SelfCodingEngine.executeVerified(c,goal,target,replacement);
        if(!"dispatched".equals(r.optString("status"))){fail(c,jobId,r.optString("error","Repair dispatch failed"));return;}
        RepairJobStore.update(c,jobId,"dispatched",new JSONObject().put("proposalId",r.optString("proposalId",SelfCodingEngine.latest(c).optString("id"))).put("rationale",bound(p.optString("rationale",""),4000)).put("generationTps",tps).put("runId",r.optLong("runId",0)).put("dispatchStatus","dispatched"));
        long runId=r.optLong("runId",0);if(runId<=0){fail(c,jobId,"GitHub workflow run was not found after dispatch");return;}poll(c,jobId,runId);
    }catch(Exception e){fail(c,jobId,e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}}
    private static void poll(Context c,String jobId,long runId){for(int i=0;i<30;i++){try{Thread.sleep(3000);}catch(InterruptedException x){Thread.currentThread().interrupt();fail(c,jobId,"Repair polling interrupted");return;}JSONObject r=GitHubRepairClient.run(c,runId);if("completed".equalsIgnoreCase(r.optString("status",""))){boolean ok="success".equalsIgnoreCase(r.optString("conclusion",""));String revision=GitHubRepairClient.currentMainSha(c);if(ok){RepairLoop.completeCiVerification(c,revision,true);RepairLoop.activate(c,revision);RepairJobStore.update(c,jobId,"activated",r);}else{RepairLoop.completeCiVerification(c,revision,false);RepairLoop.rollback(c,RepairJobStore.get(c,jobId).optString("previousRevision",""));RepairJobStore.update(c,jobId,"rolled-back",r);}return;}RepairJobStore.update(c,jobId,"verifying",r);}fail(c,jobId,"Repair verification timed out");}
    private static String targetFor(String c){String x=c==null?"":c.toLowerCase(java.util.Locale.ROOT);if(x.contains("hybrid"))return "app/src/main/java/com/ayushdebbarma/myaiagent/HybridAiRouter.java";if(x.contains("llama"))return "app/src/main/java/com/ayushdebbarma/myaiagent/LlamaRuntime.kt";if(x.contains("agent"))return "app/src/main/java/com/ayushdebbarma/myaiagent/AxtorAgent.java";if(x.contains("repair"))return "app/src/main/java/com/ayushdebbarma/myaiagent/RepairLoop.java";return "app/src/main/java/com/ayushdebbarma/myaiagent/AxtorAgent.java";}
    private static JSONObject parse(String raw)throws Exception{String s=raw==null?"":raw.trim();int a=s.indexOf('{'),b=s.lastIndexOf('}');if(a<0||b<=a)throw new IllegalArgumentException("Repair model did not return JSON");return new JSONObject(s.substring(a,b+1));}
    private static String repairPrompt(String c,String e,String t,String source){return "Repair this Android source file.\ncomponent="+c+"\nerror="+bound(e,4000)+"\ntarget_path="+t+"\nReturn JSON only: {\"target_path\":\""+t+"\",\"replacement\":\"<complete source>\",\"rationale\":\"<short>\"}.\nCURRENT SOURCE:\n"+source;}
    private static void fail(Context c,String id,String message){try{RepairJobStore.update(c,id,"failed",new JSONObject().put("error",bound(message,4000)));}catch(Exception ignored){}}
    private static String bound(String s,int n){String x=s==null?"":s;return x.length()<=n?x:x.substring(0,n);}
}
