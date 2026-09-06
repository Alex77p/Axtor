package com.ayushdebbarma.myaiagent;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

/** Launcher for Axtor's voice-only mode. Requests microphone permission before starting the foreground voice service. */
public class ChatGPTActivity extends Activity {
    private static final int AUDIO = 42;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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
            finish();
        }
    }

    private void startVoiceService() {
        try {
            VoiceCommandManager.repair(this);
        } catch (Exception ignored) {}
        finish();
    }
}
