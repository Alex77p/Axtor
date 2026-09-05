package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONObject;

/** Coordinates failure events with a reviewable improvement plan. */
public final class SelfModificationOrchestrator {
    private SelfModificationOrchestrator() {}

    public static JSONObject onFailure(Context context, String component, String error) {
        AutonomousRepairEngine.recordFailure(context, component, error);
        JSONObject plan = SelfImprovementExecutor.prepare(context,
                "Repair component " + (component == null ? "unknown" : component) + " after failure: " + (error == null ? "unknown" : error));
        try {
            plan.put("source", "failure");
            plan.put("component", component == null ? "unknown" : component);
        } catch (Exception ignored) {}
        return plan;
    }
}
