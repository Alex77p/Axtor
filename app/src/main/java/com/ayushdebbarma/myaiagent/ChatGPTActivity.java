package com.ayushdebbarma.myaiagent;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

/**
 * Launcher for Axtor's voice-only mode.
 * Keeps the launcher Activity alive so Android does not immediately return to the home screen
 * after the microphone permission dialog. The actual voice engine runs in VoiceAssistantService.
 */
public class ChatGPTActivity extends Activity {
    private static final int AUDIO = 42;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // Intentionally no chat UI: Axtor remains voice-command-only.
        View voiceOnlySurface = new View(this);
        voiceOnlySurface.setContentDescription("Axtor voice assistant");
        setContentView(voiceOnlySurface);
        startWhenPermitted();
    }

    private void startWhenPermitted() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO);
            return;
        }
        startVoiceService();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceService();
        } else if (requestCode == AUDIO) {
            // Stay in Axtor instead of appearing to crash/close. The user can grant permission later.
            VoiceServiceState.setRunning(false);
        }
    }

    private void startVoiceService() {
        try {
            VoiceCommandManager.repair(this);
        } catch (Exception e) {
            getSharedPreferences("axtor", 0).edit()
                .putString("voice_last_error", "LAUNCHER_SERVICE_START_FAILED:" + e.getClass().getSimpleName())
                .apply();
        }
        // Do NOT call finish(): the launcher is intentionally kept alive.
    }
}
