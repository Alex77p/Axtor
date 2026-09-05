package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded component evolution policy and lifecycle tracking. */
public final class ComponentEvolutionEngine {
    private static final int MAX_COMPONENTS_PER_PLAN = 8;
    private ComponentEvolutionEngine() {}

    public static JSONArray plan(Context context, String goal) {
        JSONArray out = new JSONArray();
        String g = goal == null ? "" : goal.trim();
        add(out, "inspect", "Inspect affected component and its registered dependencies.");
        add(out, "modify", g.isEmpty() ? "Prepare the smallest compatible component change." : "Prepare a bounded change for: " + g);
        add(out, "test", "Add or update tests for the changed behavior.");
        add(out, "verify", "Run repository CI and behavioral verification before activation.");
        while (out.length() > MAX_COMPONENTS_PER_PLAN) out.remove(out.length() - 1);
        ComponentRegistry.register(context, "component-evolution", "1", "planned");
        return out;
    }

    public static boolean transition(Context context, String component, String state) {
        if (component == null || component.trim().isEmpty() || state == null || state.trim().isEmpty()) return false;
        ComponentRegistry.register(context, component.trim(), "1", state.trim());
        return true;
    }

    private static void add(JSONArray a, String stage, String detail) {
        try { a.put(new JSONObject().put("stage", stage).put("detail", detail)); }
        catch (Exception ignored) {}
    }
}
