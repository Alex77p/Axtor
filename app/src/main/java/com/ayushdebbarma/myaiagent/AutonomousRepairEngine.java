package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Safe repair state machine. It records failures and proposals but never self-installs arbitrary code. */
public final class AutonomousRepairEngine {
    public enum Stage { DETECT, DIAGNOSE, PROPOSE, STAGE, VERIFY, ACTIVATE, ROLLBACK }
    private static final String PREF = "axtor_repair";
    private static final String KEY = "last_event";
    private AutonomousRepairEngine() {}

    public static void recordFailure(Context context, String component, String error) {
        record(context, Stage.DETECT, component, error);
    }

    public static void record(Context context, Stage stage, String component, String detail) {
        try {
            JSONObject o = new JSONObject();
            o.put("stage", stage.name());
            o.put("component", component == null ? "unknown" : component);
            o.put("detail", detail == null ? "" : detail);
            o.put("time", System.currentTimeMillis());
            context.getSharedPreferences(PREF, 0).edit().putString(KEY, o.toString()).apply();
            ComponentRegistry.mark(context, component == null ? "unknown" : component, stage.name().toLowerCase());
        } catch (Exception ignored) {}
    }

    public static JSONObject lastEvent(Context context) {
        try { return new JSONObject(context.getSharedPreferences(PREF, 0).getString(KEY, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    /** Returns a bounded repair plan that can be consumed by a future controlled executor. */
    public static JSONArray planForFailure(String component, String error) {
        JSONArray plan = new JSONArray();
        add(plan, "detect", component, error);
        add(plan, "diagnose", component, "Classify the failure and identify the smallest safe change.");
        add(plan, "propose", component, "Generate a reviewable patch proposal.");
        add(plan, "stage", component, "Apply only to an isolated staging copy.");
        add(plan, "verify", component, "Build and run tests before activation.");
        add(plan, "activate", component, "Activate only after verification succeeds.");
        add(plan, "rollback", component, "Restore the previous working version if verification fails.");
        return plan;
    }

    private static void add(JSONArray plan, String stage, String component, String detail) {
        try {
            JSONObject o = new JSONObject();
            o.put("stage", stage);
            o.put("component", component == null ? "unknown" : component);
            o.put("detail", detail);
            plan.put(o);
        } catch (Exception ignored) {}
    }
}
