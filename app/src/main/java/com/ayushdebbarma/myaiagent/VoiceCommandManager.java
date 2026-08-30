package com.ayushdebbarma.myaiagent;

import android.content.Context;
import android.content.pm.PackageManager;
import android.speech.SpeechRecognizer;
import java.util.Locale;

public final class VoiceCommandManager {
    private static final String PREF = "axtor_voice";
    private static final String CALL_PHRASE = "call_phrase";
    private static final String DEFAULT_CALL_PHRASE = "axtor";
    private static final String[] DISALLOWED = {
            "hey ai", "hey a.i", "hey my ai", "hey myai",
            "hey axtor", "hey my a.i", "my ai", "myai"
    };

    private VoiceCommandManager() {}

    public static String getCallPhrase(Context c) {
        String value = c.getSharedPreferences(PREF, 0)
                .getString(CALL_PHRASE, DEFAULT_CALL_PHRASE);
        return normalize(value);
    }

    public static boolean isConfigured(Context c) {
        return c.getSharedPreferences(PREF, 0).getBoolean("configured", false);
    }

    public static boolean setCallPhrase(Context c, String phrase) {
        String normalized = normalize(phrase);
        if (!isValid(normalized)) return false;
        c.getSharedPreferences(PREF, 0).edit()
                .putString(CALL_PHRASE, normalized)
                .putBoolean("configured", true)
                .apply();
        return true;
    }

    public static String extractCommand(Context c, String transcript) {
        String phrase = getCallPhrase(c);
        String text = normalize(transcript);
        if (text.equals(phrase)) return "";
        String prefix = phrase + " ";
        if (text.startsWith(prefix)) return text.substring(prefix.length()).trim();
        return null;
    }

    public static boolean isValid(String phrase) {
        if (phrase == null || phrase.length() < 2 || phrase.length() > 32) return false;
        for (String blocked : DISALLOWED) {
            if (phrase.equals(blocked) || phrase.contains(blocked)) return false;
        }
        return phrase.matches("[a-z0-9][a-z0-9 ._-]*");
    }

    public static String normalize(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
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
        s.append(isConfigured(c) ? "✓ Custom calling phrase: " + getCallPhrase(c) + ". " :
                "⚠ Custom calling phrase is using the default '" + getCallPhrase(c) + "'; set your own in Voice Setup. ");
        s.append(VoiceServiceState.isRunning() ? "✓ Voice service running." : "⚠ Voice service is not running.");
        return s.toString();
    }

    public static boolean repair(Context c) {
        try {
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
