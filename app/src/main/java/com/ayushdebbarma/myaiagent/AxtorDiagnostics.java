package com.ayushdebbarma.myaiagent;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.service.voice.VoiceInteractionService;
import android.speech.SpeechRecognizer;
import java.io.File;
import java.util.Locale;
import org.json.JSONObject;

/** Lightweight on-device health report used for end-to-end validation. */
public final class AxtorDiagnostics {
    private AxtorDiagnostics() {}

    public static String report(Context context) {
        Context c = context.getApplicationContext();
        JSONObject out = new JSONObject();
        try {
            out.put("app", "Axtor");
            out.put("accessibility", AxtorAccessibilityService.isEnabled());
            out.put("accessibilityLastConnectedAt", AxtorAccessibilityService.lastConnectedAt());
            out.put("voiceServiceRunning", VoiceServiceState.isRunning());
            out.put("microphonePermission", c.checkSelfPermission("android.permission.RECORD_AUDIO") == PackageManager.PERMISSION_GRANTED);
            out.put("speechRecognizerAvailable", SpeechRecognizer.isRecognitionAvailable(c));
            out.put("onDeviceSpeechAvailable", android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(c));
            Intent ttsIntent = new Intent("android.intent.action.TTS_SERVICE");
            out.put("ttsEngineAvailable", !c.getPackageManager().queryIntentServices(ttsIntent, PackageManager.MATCH_ALL).isEmpty());
            out.put("voiceInteractionSelected", VoiceInteractionService.isActiveService(c,
                    new ComponentName(c, AxtorVoiceInteractionService.class)));
            out.put("webSearch", WebSearchProtocol.status(c));
            out.put("lastVoiceRoute", c.getSharedPreferences("axtor",0).getString("voice_last_route", ""));
            out.put("lastVoiceError", c.getSharedPreferences("axtor",0).getString("voice_last_error", ""));

            String model = AppCore.activeModel(c);
            out.put("activeModel", model == null ? "" : model);
            File modelFile = model == null || model.isEmpty() ? null : new File(model);
            out.put("modelExists", modelFile != null && modelFile.isFile());
            out.put("modelGguf", modelFile != null && LlamaRuntime.isGguf(modelFile));
            out.put("modelBytes", modelFile != null && modelFile.isFile() ? modelFile.length() : 0);
            out.put("modelLoaded", LlamaRuntime.isModelLoaded());
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            ActivityManager am = (ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.getMemoryInfo(memory);
                out.put("availableRamBytes", memory.availMem);
                out.put("lowMemory", memory.lowMemory);
            }
            out.put("onlineEnabled", HybridAiRouter.onlineEnabled(c));
            out.put("onlineConfigured", HybridAiRouter.isConfigured(c));
            out.put("mainCause", mainCause(out));
            out.put("status", status(out));
        } catch (Throwable t) {
            try { out.put("status", "error"); out.put("mainCause", t.getClass().getSimpleName()); out.put("error", t.toString()); } catch (Exception ignored) {}
        }
        return out.toString();
    }

    public static String humanReport(Context context) {
        try {
            JSONObject o = new JSONObject(report(context));
            StringBuilder b = new StringBuilder("Axtor health check:\n");
            b.append("Voice service: ").append(o.optBoolean("voiceServiceRunning") ? "running" : "stopped").append('\n');
            b.append("System assistant: ").append(o.optBoolean("voiceInteractionSelected") ? "selected" : "not selected").append('\n');
            b.append("Microphone: ").append(o.optBoolean("microphonePermission") ? "allowed" : "permission missing").append('\n');
            b.append("Speech: ").append(o.optBoolean("speechRecognizerAvailable") ? "available" : "unavailable").append('\n');
            b.append("Offline speech: ").append(o.optBoolean("onDeviceSpeechAvailable") ? "available" : "unavailable").append('\n');
            b.append("TTS: ").append(o.optBoolean("ttsEngineAvailable") ? "available" : "unavailable").append('\n');
            b.append("Web search: ").append(o.optString("webSearch", "unknown")).append('\n');
            b.append("Accessibility: ").append(o.optBoolean("accessibility") ? "connected" : "not connected").append('\n');
            b.append("Model: ").append(o.optBoolean("modelGguf") ? "valid GGUF" : "missing/invalid").append('\n');
            b.append("Model loaded: ").append(o.optBoolean("modelLoaded")).append('\n');
            long bytes = o.optLong("modelBytes", 0);
            b.append("Model size: ").append(bytes > 0 ? String.format(Locale.US, "%.1f MB", bytes / 1048576.0) : "n/a").append('\n');
            b.append("Online fallback: ").append(o.optBoolean("onlineEnabled") && o.optBoolean("onlineConfigured") ? "configured" : "off/not configured").append('\n');
            b.append("RAM: ").append(String.format(Locale.US, "%.0f MB available", o.optLong("availableRamBytes", 0) / 1048576.0)).append('\n');
            b.append("Main cause: ").append(o.optString("mainCause", "UNKNOWN")).append('\n');
            b.append("Overall: ").append(o.optString("status", "unknown"));
            return b.toString();
        } catch (Exception e) { return "Axtor health check failed. Main cause: " + e.getMessage(); }
    }

    private static String mainCause(JSONObject o) {
        if (!o.optBoolean("microphonePermission")) return "MIC_PERMISSION_MISSING";
        if (!o.optBoolean("speechRecognizerAvailable")) return "SPEECH_RECOGNIZER_UNAVAILABLE";
        if (!o.optBoolean("ttsEngineAvailable")) return "TTS_UNAVAILABLE";
        String web = o.optString("webSearch", "unavailable");
        if ("unavailable".equals(web)) return "WEB_SEARCH_UNAVAILABLE";
        if (!o.optBoolean("accessibility")) return "ACCESSIBILITY_SERVICE_OFF";
        boolean model = o.optBoolean("modelGguf") && o.optBoolean("modelExists");
        boolean online = o.optBoolean("onlineEnabled") && o.optBoolean("onlineConfigured");
        if (!model && !online) return "MODEL_MISSING_AND_ONLINE_NOT_CONFIGURED";
        if (model && !o.optBoolean("modelLoaded")) return "MODEL_NOT_LOADED";
        return "READY";
    }

    private static String status(JSONObject o) {
        String cause = o.optString("mainCause", "UNKNOWN");
        if ("READY".equals(cause)) return "ready";
        boolean model = o.optBoolean("modelGguf") && o.optBoolean("modelExists");
        boolean online = o.optBoolean("onlineEnabled") && o.optBoolean("onlineConfigured");
        if (model || online) return "partially-ready";
        return "needs-setup";
    }
}
