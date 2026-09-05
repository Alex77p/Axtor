package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Controlled executor. It stages proposals and accepts CI verification; it never executes generated code. */
public final class SelfImprovementExecutor {
    private static final String PREF = "axtor_improvement";
    private static final String KEY = "last_plan";
    private SelfImprovementExecutor() {}

    public static JSONObject prepare(Context context, String goal) {
        JSONObject out = new JSONObject();
        try {
            JSONArray plan = SelfImprovementPlanner.plan(goal);
            out.put("goal", goal == null ? "" : goal.trim());
            out.put("status", "planned");
            out.put("plan", plan);
            out.put("createdAt", System.currentTimeMillis());
            context.getSharedPreferences(PREF, 0).edit().putString(KEY, out.toString()).apply();
            ComponentRegistry.register(context, "self-improvement", "1", "planned");
        } catch (Exception e) {
            try { out.put("status", "error").put("error", e.getMessage()); } catch (Exception ignored) {}
        }
        return out;
    }

    public static JSONObject stageProposal(Context context, String goal, String targetPath, String replacement) {
        JSONObject proposal = SelfCodingEngine.propose(context, goal, targetPath, replacement);
        if ("proposed".equals(proposal.optString("status")) && RepairLoop.stage(context, proposal)) {
            try { proposal.put("status", "staged"); } catch (Exception ignored) {}
            ComponentRegistry.register(context, "self-improvement", "1", "staged");
        }
        return proposal;
    }

    public static boolean acceptCiResult(Context context, String revision, boolean passed) {
        return RepairLoop.completeCiVerification(context, revision, passed);
    }

    public static boolean activateVerified(Context context, String revision) {
        return RepairLoop.activate(context, revision);
    }

    public static boolean rollback(Context context, String previousRevision) {
        return RepairLoop.rollback(context, previousRevision);
    }

    public static JSONObject lastPlan(Context context) {
        try { return new JSONObject(context.getSharedPreferences(PREF, 0).getString(KEY, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }
}
