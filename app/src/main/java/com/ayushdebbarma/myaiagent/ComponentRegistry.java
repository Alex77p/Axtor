package com.ayushdebbarma.myaiagent;

import android.content.Context;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Lightweight persistent registry for Axtor components and their lifecycle state. */
public final class ComponentRegistry {
    private static final String PREF = "axtor_components";
    private static final String KEY = "registry";
    private ComponentRegistry() {}

    public static void register(Context context, String id, String version, String status) {
        if (context == null || id == null || id.trim().isEmpty()) return;
        try {
            JSONArray old = read(context);
            JSONArray out = new JSONArray();
            boolean replaced = false;
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item == null) continue;
                if (id.equals(item.optString("id"))) {
                    item.put("version", version == null ? "" : version);
                    item.put("status", status == null ? "unknown" : status);
                    item.put("updatedAt", System.currentTimeMillis());
                    replaced = true;
                }
                out.put(item);
            }
            if (!replaced) {
                JSONObject item = new JSONObject();
                item.put("id", id);
                item.put("version", version == null ? "" : version);
                item.put("status", status == null ? "unknown" : status);
                item.put("updatedAt", System.currentTimeMillis());
                out.put(item);
            }
            context.getSharedPreferences(PREF, 0).edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static JSONArray read(Context context) {
        try { return new JSONArray(context.getSharedPreferences(PREF, 0).getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static void mark(Context context, String id, String status) {
        register(context, id, "", status);
    }

    public static Set<String> ids(Context context) {
        Set<String> ids = new LinkedHashSet<>();
        JSONArray all = read(context);
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);
            if (item != null) {
                String id = item.optString("id", "").trim();
                if (!id.isEmpty()) ids.add(id);
            }
        }
        return ids;
    }
}
