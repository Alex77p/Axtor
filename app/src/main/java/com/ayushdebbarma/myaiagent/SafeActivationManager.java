package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONObject;

/** Explicit lifecycle gate: only verified changes may be activated. */
public final class SafeActivationManager {
    private static final String PREF = "axtor_activation";
    private static final String KEY = "state";
    private SafeActivationManager() {}

    public static boolean stage(Context context, String revision) {
        return save(context, "staged", revision);
    }

    public static boolean verify(Context context, String revision, boolean passed) {
        return save(context, passed ? "verified" : "rejected", revision);
    }

    public static boolean activate(Context context, String revision) {
        JSONObject state = state(context);
        if (!"verified".equals(state.optString("status")) || !revision.equals(state.optString("revision"))) return false;
        return save(context, "active", revision);
    }

    public static boolean rollback(Context context, String previousRevision) {
        return save(context, "rolled_back", previousRevision);
    }

    public static JSONObject state(Context context) {
        try { return new JSONObject(context.getSharedPreferences(PREF, 0).getString(KEY, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    private static boolean save(Context context, String status, String revision) {
        if (context == null || revision == null || revision.trim().isEmpty()) return false;
        try {
            JSONObject o = new JSONObject();
            o.put("status", status);
            o.put("revision", revision);
            o.put("time", System.currentTimeMillis());
            context.getSharedPreferences(PREF, 0).edit().putString(KEY, o.toString()).apply();
            ComponentRegistry.register(context, "safe-activation", "1", status);
            return true;
        } catch (Exception e) { return false; }
    }
}
