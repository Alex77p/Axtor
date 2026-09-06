package com.ayushdebbarma.myaiagent;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;

/** Lightweight session bridge; actual command recognition stays in the foreground voice service. */
public class VoiceAssistantSession extends VoiceInteractionSession {
    public VoiceAssistantSession(Context c) { super(c); }

    @Override public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        try {
            Intent i = new Intent(getContext(), VoiceAssistantService.class);
            i.putExtra("source", "voice_interaction_session");
            if (Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(i);
            else getContext().startService(i);
        } catch (Throwable ignored) {}
        hide();
    }
}
