package com.ayushdebbarma.myaiagent;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

/** Compatibility entry point. Axtor has no interactive UI; it is a voice-command-only assistant. */
public class MainActivity extends Activity {
    private static final int AUDIO = 10;

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
        if (requestCode == AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startVoiceService();
    }

    private void startVoiceService() {
        try {
            VoiceCommandManager.repair(this);
        } catch (Exception ignored) {}
        finish();
    }
}
