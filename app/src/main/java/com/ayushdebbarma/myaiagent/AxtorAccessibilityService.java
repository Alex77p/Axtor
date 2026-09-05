package com.ayushdebbarma.myaiagent;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.KeyEvent;
import android.media.AudioManager;

public class AxtorAccessibilityService extends AccessibilityService {
    private static volatile AxtorAccessibilityService instance;
    private static volatile long lastConnectedAt;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        lastConnectedAt = System.currentTimeMillis();
        getSharedPreferences("axtor_health", MODE_PRIVATE).edit()
                .putBoolean("accessibility_connected", true)
                .putLong("accessibility_connected_at", lastConnectedAt)
                .apply();
        android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN &&
                event.getAction() == KeyEvent.ACTION_DOWN &&
                event.getRepeatCount() == 0) {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audio != null) {
                audio.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND);
                return true;
            }
        }
        return false;
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        getSharedPreferences("axtor_health", MODE_PRIVATE).edit()
                .putBoolean("accessibility_connected", false)
                .putLong("accessibility_disconnected_at", System.currentTimeMillis())
                .apply();
        super.onDestroy();
    }

    public static boolean isEnabled() { return instance != null; }

    public static long lastConnectedAt() { return lastConnectedAt; }

    public static boolean globalAction(int action) {
        AxtorAccessibilityService s = instance;
        return s != null && s.performGlobalAction(action);
    }

    public static boolean back() { return globalAction(GLOBAL_ACTION_BACK); }
    public static boolean home() { return globalAction(GLOBAL_ACTION_HOME); }
    public static boolean recents() { return globalAction(GLOBAL_ACTION_RECENTS); }
    public static boolean notifications() { return globalAction(GLOBAL_ACTION_NOTIFICATIONS); }
    public static boolean lockScreen() { return globalAction(GLOBAL_ACTION_LOCK_SCREEN); }

    /** Opens Android's Accessibility settings so the owner can re-enable Axtor. */
    public static void openAccessibilitySettings(android.content.Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
