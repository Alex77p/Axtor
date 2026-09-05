package com.ayushdebbarma.myaiagent;

import android.app.ActivityManager;
import android.content.Context;
import java.io.File;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Lightweight on-device health report used for end-to-end validation. */
public final class AxtorDiagnostics {
    private AxtorDiagnostics() {}

    public static String report(Context context) {
        Context c = context.getApplicationContext();
        JSONObject out = new JSONObject();
        try {
            out.put("app", "Axtor");
            out.put("accessibility", AxtorAccessibilityService.isEnabled());
            out.put("accessibilityLastConnectedAt", AxtorAccessibilityService.lastConnectedAt());
            String model = AppCore.activeModel(c);
            out.put("activeModel", model == null ? "" : model);
            File modelFile = model == null || model.isEmpty() ? null : new File(model);
            out.put("modelExists", modelFile != null && modelFile.isFile());
            out.put("modelGguf", modelFile != null && LlamaRuntime.isGguf(modelFile));
            out.put("modelBytes", modelFile != null && modelFile.isFile() ? modelFile.length() : 0);
            out.put("modelLoaded", LlamaRuntime.isModelLoaded());
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            ActivityManager am = (ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.getMemoryInfo(memory);
                out.put("availableRamBytes", memory.availMem);
                out.put("lowMemory", memory.lowMemory);
            }
            out.put("onlineEnabled", HybridAiRouter.onlineEnabled(c));
            out.put("onlineConfigured", HybridAiRouter.isConfigured(c));
            out.put("status", status(out));
        } catch (Throwable t) {
            try { out.put("status", "error"); out.put("error", t.toString()); } catch (Exception ignored) {}
        }
        return out.toString();
    }

    public static String humanReport(Context context) {
        try {
            JSONObject o = new JSONObject(report(context));
            StringBuilder b = new StringBuilder("Axtor health check:\n");
            b.append("Accessibility: ").append(o.optBoolean("accessibility") ? "connected" : "not connected").append('\n');
            b.append("Model: ").append(o.optBoolean("modelGguf") ? "valid GGUF" : "missing/invalid").append('\n');
            b.append("Model loaded: ").append(o.optBoolean("modelLoaded")).append('\n');
            long bytes = o.optLong("modelBytes", 0);
            b.append("Model size: ").append(bytes > 0 ? String.format(Locale.US, "%.1f MB", bytes / 1048576.0) : "n/a").append('\n');
            b.append("Online fallback: ").append(o.optBoolean("onlineEnabled") && o.optBoolean("onlineConfigured") ? "configured" : "off/not configured").append('\n');
            b.append("RAM: ").append(String.format(Locale.US, "%.0f MB available", o.optLong("availableRamBytes", 0) / 1048576.0)).append('\n');
            b.append("Overall: ").append(o.optString("status", "unknown"));
            return b.toString();
        } catch (Exception e) { return "Axtor health check failed: " + e.getMessage(); }
    }

    private static String status(JSONObject o) {
        boolean model = o.optBoolean("modelGguf") && o.optBoolean("modelExists");
        boolean accessibility = o.optBoolean("accessibility");
        boolean online = o.optBoolean("onlineEnabled") && o.optBoolean("onlineConfigured");
        if (model && accessibility) return "ready";
        if (model || online) return "partially-ready";
        return accessibility ? "needs-model" : "needs-setup";
    }
}
