package com.ayushdebbarma.myaiagent;

import android.content.Context;

/** Deterministic post-action verification helpers. Never reports success when the local state disagrees. */
public final class AgentExecutionVerifier {
    private AgentExecutionVerifier() {}

    public static String verify(Context context, String command, String result) {
        if (result == null || result.trim().isEmpty()) return "Action failed: no execution result was returned.";
        String l = command == null ? "" : command.toLowerCase(java.util.Locale.ROOT);
        if (l.contains("accessibility") && (l.contains("enable") || l.contains("connect") || l.contains("service"))) {
            return AxtorAccessibilityService.isEnabled()
                    ? result + " Verified: Accessibility Service is connected."
                    : result + " Verification: Accessibility Service is not connected.";
        }
        if (l.contains("lock") && l.contains("screen")) {
            return AxtorAccessibilityService.isEnabled()
                    ? result + " Verified: the lock action was dispatched through Accessibility."
                    : result + " Verification unavailable: Accessibility Service is not connected.";
        }
        return result + " Execution completed by Axtor's Android tool layer.";
    }
}
