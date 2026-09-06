package com.ayushdebbarma.myaiagent;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;

/** Bounded self-coding proposal generator with an explicit allowlist for verified GitHub execution. */
public final class SelfCodingEngine {
    private static final String PREF = "axtor_self_coding";
    private static final String KEY = "proposal";
    private static final int MAX_BODY = 48000;
    private static final Set<String> SAFE_TARGETS = new HashSet<>(Arrays.asList(
            "app/src/main/java/com/ayushdebbarma/myaiagent/AxtorAgent.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/AgentExecutionVerifier.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/ComponentRegistry.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/DeviceAutomation.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/HybridAiRouter.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/RepairLoop.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/SelfCodingEngine.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/SelfImprovementExecutor.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/SelfImprovementPlanner.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/SelfModificationOrchestrator.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/AutonomousRepairCoordinator.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/GitHubRepairClient.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/RepairJobStore.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/AxtorDiagnostics.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/AppCore.java",
            "app/src/main/java/com/ayushdebbarma/myaiagent/LlamaRuntime.kt"
    ));
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
            ComponentRegistry.register(context, "self-coding", "3", "proposed");
        } catch (Exception e) {
            try { out.put("status", "rejected").put("error", e.getMessage()); } catch (Exception ignored) {}
        }
        return out;
    }

    /** Dispatches the bounded proposal to GitHub for isolated build/test verification and activation. */
    public static JSONObject executeVerified(Context context, String goal, String targetPath, String replacement) {
        JSONObject proposal = propose(context, goal, targetPath, replacement);
        if (!"proposed".equals(proposal.optString("status"))) return proposal;
        JSONObject result = GitHubRepairClient.dispatch(context, goal, targetPath, replacement);
        try {
            result.put("proposalId", proposal.optString("id"));
            result.put("target", targetPath);
            result.put("status", result.optString("status", "error"));
        } catch (Exception ignored) {}
        return result;
    }

    public static JSONObject latest(Context context) {
        try { return new JSONObject(context.getSharedPreferences(PREF, 0).getString(KEY, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    public static boolean isSafeSourcePath(String path) {
        if (path == null || path.isEmpty() || path.length() > 240 || path.contains("..") || path.startsWith("/")) return false;
        return SAFE_TARGETS.contains(path);
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        for (byte x : digest) b.append(String.format("%02x", x));
        return b.toString();
    }
}
