package com.ayushdebbarma.myaiagent;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.provider.Settings;

import java.util.Locale;

public final class DeviceAutomation {
    private DeviceAutomation() {}

    public static String execute(Context context, String command) {
        String q = command == null ? "" : command.trim();
        String l = q.toLowerCase(Locale.ROOT);
        try {
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
            if (l.contains("lock screen") || l.equals("lock phone")) {
                return AxtorAccessibilityService.lockScreen() ? "Screen locked." : accessibilityRequired();
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

            if (l.startsWith("open ")) {
                String name = q.substring(5).trim();
                String packageName = findPackage(context, name);
                if (packageName != null) {
                    Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(launch);
                        return "Opened " + name + ".";
                    }
                }
                return "I couldn't find an installed app named " + name + ".";
            }

            if (l.startsWith("set an alarm") || l.startsWith("set alarm")) {
                long when = System.currentTimeMillis() + 60_000L;
                Intent alarm = new Intent(context, AlarmReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(context, 1001, alarm,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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

    private static String accessibilityRequired() {
        return "Please enable Axtor Accessibility Service in Android Settings for this device action.";
    }

    private static String findPackage(Context c, String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (android.content.pm.ApplicationInfo ai : c.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA)) {
            CharSequence label = c.getPackageManager().getApplicationLabel(ai);
            if (label != null && label.toString().toLowerCase(Locale.ROOT).equals(wanted)) return ai.packageName;
        }
        return null;
    }
}
