package com.ayushdebbarma.myaiagent;

import android.content.Context;
import android.content.Intent;

/** Global emergency-stop latch for hands-free automation. */
public final class EmergencyStopController {
    private static volatile boolean requested;
    private EmergencyStopController() {}

    public static void clear() { requested = false; }
    public static boolean isRequested() { return requested; }

    /** Latches STOP, interrupts Axtor's active foreground voice service and legacy sound service. */
    public static void request(Context context) {
        requested = true;
        if (context == null) return;
        try { context.stopService(new Intent(context, VoiceAssistantService.class)); } catch (Exception ignored) {}
        try { context.stopService(new Intent(context, SoundTriggerService.class)); } catch (Exception ignored) {}
        try {
            context.getSharedPreferences("axtor", 0).edit()
                    .putString("voice_last_trigger", "emergency_triple_snap_stop")
                    .putString("voice_last_error", "EMERGENCY_STOP_REQUESTED")
                    .putBoolean("automation_abort", true)
                    .apply();
        } catch (Exception ignored) {}
    }
}
