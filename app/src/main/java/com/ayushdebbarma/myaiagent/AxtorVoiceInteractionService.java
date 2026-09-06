package com.ayushdebbarma.myaiagent;

import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;

/** Lightweight system-assistant bridge. Heavy recognition remains in VoiceAssistantService. */
public class AxtorVoiceInteractionService extends VoiceInteractionService {
    @Override public void onReady() {
        super.onReady();
        try { setInvocationEffectEnabled(true); } catch (Throwable ignored) {}
    }

    @Override public void onLaunchVoiceAssistFromKeyguard() {
        super.onLaunchVoiceAssistFromKeyguard();
        startAxtorVoice();
    }

    @Override public void onPrepareToShowSession(Bundle args, int flags) {
        super.onPrepareToShowSession(args, flags);
        try { showSession(args, flags); } catch (Throwable ignored) { startAxtorVoice(); }
    }

    private void startAxtorVoice() {
        try {
            Intent i = new Intent(this, VoiceAssistantService.class);
            i.putExtra("source", "system_voice_interaction");
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Throwable ignored) {}
    }
}
