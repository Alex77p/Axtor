package com.ayushdebbarma.myaiagent;

import org.json.JSONArray;
import org.json.JSONObject;

/** Produces bounded, reviewable improvement plans without applying code changes. */
public final class SelfImprovementPlanner {
    private SelfImprovementPlanner() {}

    public static JSONArray plan(String goal) {
        String g = goal == null ? "" : goal.trim();
        JSONArray steps = new JSONArray();
        add(steps, "analyze", "Analyze the requested change and affected components.");
        add(steps, "design", "Choose the smallest compatible implementation.");
        add(steps, "stage", "Prepare the change in an isolated staging revision.");
        add(steps, "build", "Compile the Android project with the repository CI toolchain.");
        add(steps, "test", "Run unit and integration tests.");
        add(steps, "verify", "Verify the intended behavior and reject regressions.");
        add(steps, "activate", "Activate only a verified revision.");
        add(steps, "rollback", "Restore the previous verified revision if activation fails.");
        return steps;
    }

    private static void add(JSONArray steps, String stage, String detail) {
        try {
            JSONObject item = new JSONObject();
            item.put("stage", stage);
            item.put("detail", detail);
            steps.put(item);
        } catch (Exception ignored) {}
    }
}
