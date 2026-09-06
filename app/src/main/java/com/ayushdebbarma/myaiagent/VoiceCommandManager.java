package com.ayushdebbarma.myaiagent;

import android.content.Context;
import android.content.pm.PackageManager;
import android.speech.SpeechRecognizer;

/** Direct voice-command mode: every recognized utterance is treated as the command. */
public final class VoiceCommandManager {
    private VoiceCommandManager() {}

    public static String extractCommand(Context c, String transcript) {
        return normalize(transcript);
    }

    public static String normalize(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ");
    }

    public static String diagnose(Context c) {
        StringBuilder s = new StringBuilder();
        if (c.checkSelfPermission("android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
            s.append("✗ Microphone permission missing. ");
        } else {
            s.append("✓ Microphone permission. ");
        }
        if (!SpeechRecognizer.isRecognitionAvailable(c)) {
            s.append("✗ Speech recognition unavailable. ");
        } else {
            s.append("✓ Speech recognition available. ");
        }
        s.append("✓ Direct voice-command mode: speak the command without a calling phrase. ");
        s.append(VoiceServiceState.isRunning() ? "✓ Voice service running." : "⚠ Voice service is not running.");
        return s.toString();
    }

    public static boolean repair(Context c) {
        try {
            c.getSharedPreferences("axtor_voice", 0).edit()
                    .putBoolean("continuous_listening", true)
                    .putBoolean("prefer_offline", true)
                    .putBoolean("snap_trigger_enabled", false)
                    .apply();
            c.getSharedPreferences("axtor", 0).edit()
                    .putBoolean("voice_enabled", true)
                    .apply();
            c.stopService(new android.content.Intent(c, VoiceAssistantService.class));
            android.content.Intent i = new android.content.Intent(c, VoiceAssistantService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
