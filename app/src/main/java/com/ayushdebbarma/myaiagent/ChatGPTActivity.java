package com.ayushdebbarma.myaiagent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Launcher for Axtor's voice-only mode. No chat, composer, menus, or legacy UI. */
public class ChatGPTActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startVoiceService();
        finish();
    }

    private void startVoiceService() {
        try {
            VoiceCommandManager.repair(this);
        } catch (Exception ignored) {}
    }
}
