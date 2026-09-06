package com.ayushdebbarma.myaiagent;

import android.content.Context;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Lowest-level policy for commands arriving from hands-free voice. */
public final class AxtorCommandSecurityPolicy {
    private AxtorCommandSecurityPolicy() {}

    public static String authorizeVoice(Context context, String command) {
        String q = command == null ? "" : command.trim();
        String l = q.toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return "EMPTY_COMMAND";
        if (matchesSavedFlow(context, l)) return "CUSTOM_FLOW_REQUIRES_EXPLICIT_UI";
        if (l.matches(".*\\b(intent|shell|adb|am|pm|su|terminal|exec|command line)\\b.*") || l.startsWith("url ") || l.matches(".*https?://.*")) return "ARBITRARY_EXECUTION_BLOCKED";
        if (l.matches(".*\\b(unlock|bypass.*lock|disable.*security|turn off.*security|remove.*protection)\\b.*")) return "SECURITY_BYPASS_BLOCKED";
        if (l.matches(".*\\b(install|uninstall|factory reset|wipe data|erase device|delete all data)\\b.*")) return "DESTRUCTIVE_ACTION_REQUIRES_UI";
        if (l.matches(".*\\b(password|passcode|pin|otp|one[- ]time code|token|api key|private key|secret|credential)\\b.*")) return "SECRET_ACCESS_BLOCKED";
        if (l.matches(".*\\b(change|replace|reset|delete|forget|disable)\\b.*\\b(owner|biometric|fingerprint|face|snap enrollment|snap security|security engine)\\b.*")) return "SECURITY_CONFIGURATION_REQUIRES_BIOMETRIC";
        if (l.matches(".*\\b(enable|disable|turn on|turn off)\\b.*\\b(accessibility|developer options|developer mode|security settings)\\b.*")) return "SECURITY_SETTINGS_REQUIRE_UI";
        if (l.startsWith("settings ") && !isSafeSettings(l)) return "UNSAFE_SETTINGS_BLOCKED";
        if (l.equals("go home") || l.equals("home") || l.equals("go back") || l.equals("back") || l.contains("recent apps") || l.contains("notifications")) return "OK";
        if (l.contains("volume") || l.equals("mute") || l.equals("unmute") || l.contains("lock screen") || l.equals("lock phone") || l.equals("lock device")) return "OK";
        if (l.equals("wake screen") || l.equals("wake screen up")) return "OK";
        if (l.equals("open settings") || l.contains("open wifi") || l.contains("open bluetooth") || l.contains("voice input settings") || l.contains("notification settings") || l.contains("app settings")) return "OK";
        if (l.startsWith("open ") || l.startsWith("launch ") || l.startsWith("start ") || l.startsWith("show ") || l.startsWith("go to ")) return "OK";
        if (l.startsWith("set alarm") || l.startsWith("set an alarm")) return "OK";
        if (l.equals("stop all") || l.equals("stop automation") || l.equals("cancel automation")) return "OK";
        if (l.equals("stop sound triggers") || l.equals("disable sound triggers") || l.equals("open sound trigger settings")) return "OK";
        if (l.equals("start sound triggers") || l.equals("enable sound triggers") || l.equals("sound trigger start")) return "SECURITY_CONFIRMATION_REQUIRED";
        return "OK";
    }

    public static boolean isPrivileged(String command) {
        String l = command == null ? "" : command.toLowerCase(Locale.ROOT);
        return l.matches(".*\\b(owner|biometric|fingerprint|face|security|credential|secret|token|api key|private key|developer|accessibility)\\b.*")
                || l.matches(".*\\b(install|uninstall|factory reset|wipe|erase|unlock|bypass)\\b.*");
    }

    private static boolean matchesSavedFlow(Context context, String input) {
        try {
            JSONArray saved = new JSONArray(context.getSharedPreferences("myaiagent", 0).getString("flows", "[]"));
            String normalized = normalize(input);
            for (int i = 0; i < saved.length(); i++) {
                JSONObject rule = saved.optJSONObject(i); if (rule == null) continue;
                String trigger = normalize(rule.optString("trigger", ""));
                if (!trigger.isEmpty() && (normalized.equals(trigger) || normalized.startsWith(trigger + " "))) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim(); }
    private static boolean isSafeSettings(String l) {
        return l.equals("settings wifi") || l.equals("settings wifi_settings") || l.equals("settings bluetooth") || l.equals("settings bluetooth_settings")
                || l.equals("settings sound") || l.equals("settings sound_settings") || l.equals("settings display") || l.equals("settings display_settings")
                || l.equals("settings battery") || l.equals("settings battery_settings") || l.equals("settings app") || l.equals("settings app_settings")
                || l.equals("settings language") || l.equals("settings language_settings") || l.equals("settings date") || l.equals("settings date_settings");
    }
}
