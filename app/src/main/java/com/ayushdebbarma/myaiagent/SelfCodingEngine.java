package com.ayushdebbarma.myaiagent;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Safe self-coding layer: creates bounded patch proposals, never executes arbitrary code. */
public final class SelfCodingEngine {
    private static final String PREF = "axtor_self_coding";
    private static final String KEY = "proposal";
    private static final int MAX_BODY = 65536;
    private SelfCodingEngine() {}

    public static JSONObject propose(Context context, String goal, String targetPath, String replacement) {
        JSONObject out = new JSONObject();
        try {
            String path = targetPath == null ? "" : targetPath.trim();
            String body = replacement == null ? "" : replacement;
            if (!isSafeSourcePath(path)) throw new IllegalArgumentException("Unsafe target path");
            if (body.length() > MAX_BODY) throw new IllegalArgumentException("Proposal is too large");
            out.put("id", sha256(path + "\n" + body + "\n" + System.currentTimeMillis()));
            out.put("goal", goal == null ? "" : goal.trim());
            out.put("target", path);
            out.put("replacement", body);
            out.put("action", "replace-file");
            out.put("status", "proposed");
            out.put("createdAt", System.currentTimeMillis());
            context.getSharedPreferences(PREF, 0).edit().putString(KEY, out.toString()).apply();
            ComponentRegistry.register(context, "self-coding", "1", "proposed");
        } catch (Exception e) {
            try { out.put("status", "rejected").put("error", e.getMessage()); } catch (Exception ignored) {}
        }
        return out;
    }

    public static JSONObject latest(Context context) {
        try { return new JSONObject(context.getSharedPreferences(PREF, 0).getString(KEY, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    public static boolean isSafeSourcePath(String path) {
        if (path == null || path.isEmpty() || path.length() > 240 || path.contains("..") || path.startsWith("/")) return false;
        if (!(path.startsWith("app/src/main/") || path.startsWith("app/src/test/") || path.startsWith("app/src/androidTest/"))) return false;
        if (path.contains("AndroidManifest.xml")) return false;
        return path.endsWith(".java") || path.endsWith(".kt") || path.endsWith(".xml") || path.endsWith(".json");
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        for (byte x : digest) b.append(String.format("%02x", x));
        return b.toString();
    }
}
