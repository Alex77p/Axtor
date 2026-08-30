package com.ayushdebbarma.myaiagent;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Safe runtime self-repair for Axtor.
 * Repairs/reinitializes known runtime components without modifying APK code.
 */
public final class SelfRepairManager {
    private static final String PREFS = "axtor_self_repair";
    private static final String KEY_ENABLED = "enabled";
    private static final int MAX_ATTEMPTS = 3;

    private SelfRepairManager() {}

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static void reportVoiceFailure(Context context, int errorCode) {
        if (!isEnabled(context)) return;
        int attempts = context.getSharedPreferences(PREFS, 0)
                .getInt("voice_attempts", 0);
        if (attempts >= MAX_ATTEMPTS) return;
        context.getSharedPreferences(PREFS, 0).edit()
                .putInt("voice_attempts", attempts + 1).apply();
        // Give the service a short cooldown before the next recognition attempt.
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                context.getSharedPreferences(PREFS, 0).edit()
                        .putInt("voice_attempts", 0).apply(), 8000L);
    }

    public static void reportSuccess(Context context) {
        context.getSharedPreferences(PREFS, 0).edit()
                .putInt("voice_attempts", 0).apply();
    }

    public static void repair(Context context) {
        if (!isEnabled(context)) return;
        // Safe repair boundary: clear transient repair state. Components are
        // re-created by their normal lifecycle rather than forcing process restarts.
        context.getSharedPreferences(PREFS, 0).edit()
                .putInt("voice_attempts", 0).apply();
    }
}
