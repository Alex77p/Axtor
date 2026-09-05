package com.ayushdebbarma.myaiagent;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.AudioManager;
import android.provider.Settings;

import java.util.Locale;

public final class DeviceAutomation {
    private DeviceAutomation() {}

    public static String execute(Context context, String command) {
        String q = command == null ? "" : command.trim();
        String l = q.toLowerCase(Locale.ROOT);
        try {
            android.content.SharedPreferences flowPrefs = context.getSharedPreferences("myaiagent", 0);
            String rawFlows = flowPrefs.getString("flows", "[]");
            org.json.JSONArray saved = new org.json.JSONArray(rawFlows);
            for (int i = 0; i < saved.length(); i++) {
                org.json.JSONObject rule = saved.optJSONObject(i);
                if (rule == null) continue;
                String trigger = rule.optString("trigger", "").trim().toLowerCase(Locale.ROOT);
                String action = rule.optString("action", "").trim();
                if (!trigger.isEmpty() && !action.isEmpty() &&
                        (l.equals(trigger) || l.startsWith(trigger + " "))) {
                    return execute(context, action);
                }
                String normalizedTrigger = trigger.replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
                String normalizedInput = l.replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
                if (!normalizedTrigger.isEmpty() && (normalizedInput.equals(normalizedTrigger) || normalizedInput.startsWith(normalizedTrigger + " "))) {
                    return execute(context, action);
                }
            }

            if (l.startsWith("intent ")) {
                String spec = q.substring(7).trim();
                if (spec.isEmpty()) return "Provide an Android Intent action.";
                String[] parts = spec.split("\\s+", 2);
                String action = parts[0];
                if (!action.matches("[A-Za-z0-9_.]+")) return "Invalid Android Intent action.";
                Intent intent = new Intent(action);
                if (parts.length > 1 && !parts[1].trim().isEmpty()) intent.setData(Uri.parse(parts[1].trim()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) return "Android has no app available for that action.";
                context.startActivity(intent);
                return "Custom Android action executed.";
            }

            if (l.startsWith("url ")) {
                String uri = q.substring(4).trim();
                if (!uri.matches("(?i)https?://\\S+")) return "Only http/https URLs are supported.";
                Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return "URL opened.";
            }

            if (l.contains("open sound trigger") || l.contains("sound trigger settings") || l.contains("open hands-free sound")) {
                Intent i = new Intent(context, SoundTriggerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
                return "Hands-free sound trigger settings opened.";
            }
            if (l.equals("start sound triggers") || l.equals("enable sound triggers")) {
                context.getSharedPreferences("axtor_sound", 0).edit().putBoolean("enabled", true).apply();
                Intent i = new Intent(context, SoundTriggerService.class);
                if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(i); else context.startService(i);
                return "Sound triggers enabled.";
            }
            if (l.equals("stop sound triggers") || l.equals("disable sound triggers")) {
                context.getSharedPreferences("axtor_sound", 0).edit().putBoolean("enabled", false).apply();
                context.stopService(new Intent(context, SoundTriggerService.class));
                return "Sound triggers disabled.";
            }

            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            if (l.contains("volume up") || l.contains("increase volume") || l.contains("turn up volume")) {
                audio.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
                return "Volume increased.";
            }
            if (l.contains("volume down") || l.contains("decrease volume") || l.contains("turn down volume")) {
                audio.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND);
                return "Volume decreased.";
            }
            if (l.equals("mute") || l.contains("mute volume")) {
                audio.adjustVolume(AudioManager.ADJUST_MUTE, 0);
                return "Volume muted.";
            }
            if (l.contains("unmute")) {
                audio.adjustVolume(AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_PLAY_SOUND);
                return "Volume unmuted.";
            }

            if (l.equals("go home") || l.equals("home") || l.contains("go to home screen")) {
                return AxtorAccessibilityService.home() ? "Home opened." : accessibilityRequired();
            }
            if (l.contains("go back") || l.equals("back")) {
                return AxtorAccessibilityService.back() ? "Went back." : accessibilityRequired();
            }
            if (l.contains("open recent") || l.contains("show recent apps")) {
                return AxtorAccessibilityService.recents() ? "Recent apps opened." : accessibilityRequired();
            }
            if (l.contains("open notifications") || l.contains("show notifications")) {
                return AxtorAccessibilityService.notifications() ? "Notifications opened." : accessibilityRequired();
            }
            if (l.contains("lock screen") || l.contains("lock the screen") ||
                    l.contains("lock my screen") || l.contains("lock my phone") ||
                    l.contains("lock the phone") || l.equals("lock phone") ||
                    l.equals("lock device") || l.contains("lock my device")) {
                return AxtorAccessibilityService.lockScreen() ? "Screen locked." : accessibilityRequired();
            }

            if (l.contains("open app settings") || l.contains("application settings")) {
                context.startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return "App settings opened.";
            }
            if (l.contains("open accessibility settings")) {
                context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return "Accessibility settings opened.";
            }
            if (l.contains("open voice input settings") || l.contains("speech settings")) {
                context.startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return "Voice input settings opened.";
            }
            if (l.contains("open notification settings") || l.contains("notification settings")) {
                Intent notificationSettings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(notificationSettings);
                return "Notification settings opened.";
            }

            if (l.contains("open settings")) {
                context.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return "Settings opened.";
            }
            if (l.contains("open wifi") || l.contains("wi-fi settings")) {
                context.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return "Wi-Fi settings opened.";
            }
            if (l.contains("open bluetooth") || l.contains("bluetooth settings")) {
                context.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return "Bluetooth settings opened.";
            }

            if (l.startsWith("settings ")) {
                String key=l.substring(9).trim().replace(' ', '_');
                String action=null;
                if(key.equals("wifi")||key.equals("wifi_settings")) action=Settings.ACTION_WIFI_SETTINGS;
                else if(key.equals("bluetooth")||key.equals("bluetooth_settings")) action=Settings.ACTION_BLUETOOTH_SETTINGS;
                else if(key.equals("sound")||key.equals("sound_settings")) action=Settings.ACTION_SOUND_SETTINGS;
                else if(key.equals("display")||key.equals("display_settings")) action=Settings.ACTION_DISPLAY_SETTINGS;
                else if(key.equals("battery")||key.equals("battery_settings")) action=Settings.ACTION_BATTERY_SAVER_SETTINGS;
                else if(key.equals("accessibility")||key.equals("accessibility_settings")) action=Settings.ACTION_ACCESSIBILITY_SETTINGS;
                else if(key.equals("notification")||key.equals("notification_settings")) action=Settings.ACTION_APP_NOTIFICATION_SETTINGS;
                else if(key.equals("app")||key.equals("app_settings")) action=Settings.ACTION_APPLICATION_SETTINGS;
                else if(key.equals("language")||key.equals("language_settings")) action=Settings.ACTION_LOCALE_SETTINGS;
                else if(key.equals("date")||key.equals("date_settings")) action=Settings.ACTION_DATE_SETTINGS;
                else if(key.equals("developer")||key.equals("developer_settings")) action=Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS;
                else if(key.equals("security")||key.equals("security_settings")) action=Settings.ACTION_SECURITY_SETTINGS;
                if(action!=null){
                    Intent settingsIntent = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (action.equals(Settings.ACTION_APP_NOTIFICATION_SETTINGS)) settingsIntent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                    context.startActivity(settingsIntent);
                    return "Settings opened.";
                }
                return "That Android setting is not supported by this version of Axtor.";
            }

            if (l.startsWith("open ") || l.startsWith("launch ") || l.startsWith("start ")) {
                String prefix = l.startsWith("open ") ? "open " : (l.startsWith("launch ") ? "launch " : "start ");
                String name = q.substring(prefix.length()).trim();
                String packageName = findPackage(context, name);
                if (packageName == null && name.matches("[A-Za-z0-9_]+\\.[A-Za-z0-9_.]+")) packageName = name;
                if (packageName != null) {
                    Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(launch); return "Opened " + name + "."; }
                }
                return "I couldn't find an installed app named " + name + ".";
            }

            if (l.startsWith("set an alarm") || l.startsWith("set alarm")) {
                long when = System.currentTimeMillis() + 60_000L;
                Intent alarm = new Intent(context, AlarmReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(context, 1001, alarm, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
                return "Alarm set for one minute from now.";
            }

            return null;
        } catch (SecurityException e) {
            return "Android blocked that action. Please grant the required permission in Settings.";
        } catch (Exception e) {
            return "Automation failed: " + e.getMessage();
        }
    }

    private static String accessibilityRequired() { return "Please enable Axtor Accessibility Service in Android Settings for this device action."; }

    private static String findPackage(Context c, String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (android.content.pm.ApplicationInfo ai : c.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA)) {
            CharSequence label = c.getPackageManager().getApplicationLabel(ai);
            if (label != null) {
                String actual = label.toString().toLowerCase(Locale.ROOT).trim();
                if (actual.equals(wanted) || actual.replaceAll("[^a-z0-9 ]", "").equals(wanted.replaceAll("[^a-z0-9 ]", ""))) return ai.packageName;
            }
        }
        return null;
    }
}
