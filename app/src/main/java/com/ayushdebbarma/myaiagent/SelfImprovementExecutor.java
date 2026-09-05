package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Controlled executor for improvement lifecycle state. Code/build operations remain CI-side. */
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

    public static JSONObject lastPlan(Context context) {
        try { return new JSONObject(context.getSharedPreferences(PREF, 0).getString(KEY, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }
}
