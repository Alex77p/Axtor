package com.ayushdebbarma.myaiagent;

import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

/** Creates the lightweight voice session used by Android's system assistant integration. */
public class AxtorVoiceInteractionSessionService extends VoiceInteractionSessionService {
    @Override public VoiceInteractionSession onNewSession() {
        return new AxtorVoiceInteractionSession(this);
    }

    static final class AxtorVoiceInteractionSession extends VoiceInteractionSession {
        AxtorVoiceInteractionSession(android.content.Context context) { super(context); }

        @Override public void onShow(android.os.Bundle args, int showFlags) {
            super.onShow(args, showFlags);
            try {
                android.content.Intent i = new android.content.Intent(getContext(), VoiceAssistantService.class);
                i.putExtra("source", "voice_interaction_session");
                if (android.os.Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(i);
                else getContext().startService(i);
            } catch (Throwable ignored) {}
            hide();
        }
    }
}
