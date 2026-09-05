package com.ayushdebbarma.myaiagent;

import android.content.Context;
import org.json.JSONObject;

/** Coordinates failures, bounded self-coding proposals, verification and rollback. */
public final class SelfModificationOrchestrator {
    private SelfModificationOrchestrator() {}

    public static JSONObject onFailure(Context context, String component, String error) {
        JSONObject state = RepairLoop.start(context, component, error);
        try { state.put("source", "failure"); } catch (Exception ignored) {}
        return state;
    }

    public static JSONObject proposeRepair(Context context, String goal, String targetPath, String replacement) {
        return SelfImprovementExecutor.stageProposal(context, goal, targetPath, replacement);
    }

    public static boolean verifyAndActivate(Context context, String revision, boolean ciPassed) {
        if (!SelfImprovementExecutor.acceptCiResult(context, revision, ciPassed)) return false;
        return SelfImprovementExecutor.activateVerified(context, revision);
    }

    public static boolean rollback(Context context, String previousRevision) {
        return SelfImprovementExecutor.rollback(context, previousRevision);
    }
}
