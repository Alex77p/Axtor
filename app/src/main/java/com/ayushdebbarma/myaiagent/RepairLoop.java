package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONObject;

/** End-to-end bounded repair state machine. Repository CI remains the source of truth for builds. */
public final class RepairLoop {
    private static final String PREF = "axtor_repair_loop";
    private static final String KEY = "state";
    private RepairLoop() {}

    public static JSONObject start(Context context, String component, String error) {
        String c = component == null || component.trim().isEmpty() ? "unknown" : component.trim();
        String e = error == null || error.trim().isEmpty() ? "unknown failure" : error.trim();
        AutonomousRepairEngine.recordFailure(context, c, e);
        JSONObject plan = SelfImprovementPlanner.plan("Repair " + c + ": " + e);
        JSONObject state = new JSONObject();
        try {
            state.put("status", "diagnosed");
            state.put("component", c);
            state.put("error", e);
            state.put("plan", plan);
            state.put("time", System.currentTimeMillis());
            context.getSharedPreferences(PREF, 0).edit().putString(KEY, state.toString()).apply();
            ComponentEvolutionEngine.transition(context, c, "repair-planned");
        } catch (Exception ignored) {}
        return state;
    }

    public static boolean stage(Context context, JSONObject proposal) {
        if (proposal == null || !"proposed".equals(proposal.optString("status"))) return false;
        String id = proposal.optString("id", "");
        if (id.isEmpty() || !SafeActivationManager.stage(context, id)) return false;
        ComponentEvolutionEngine.transition(context, proposal.optString("target", "unknown"), "staged");
        return true;
    }

    public static boolean completeCiVerification(Context context, String revision, boolean passed) {
        if (!SafeActivationManager.verify(context, revision, passed)) return false;
        ComponentEvolutionEngine.transition(context, revision, passed ? "verified" : "rejected");
        return passed;
    }

    public static boolean activate(Context context, String revision) {
        return SafeActivationManager.activate(context, revision);
    }

    public static boolean rollback(Context context, String previousRevision) {
        return SafeActivationManager.rollback(context, previousRevision);
    }

    public static JSONObject state(Context context) {
        try { return new JSONObject(context.getSharedPreferences(PREF, 0).getString(KEY, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }
}
