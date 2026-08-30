package com.ayushdebbarma.myaiagent;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.KeyEvent;
import android.media.AudioManager;

public class AxtorAccessibilityService extends AccessibilityService {
    private static volatile AxtorAccessibilityService instance;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
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
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }

    public static boolean isEnabled() { return instance != null; }

    public static boolean globalAction(int action) {
        AxtorAccessibilityService s = instance;
        return s != null && s.performGlobalAction(action);
    }

    public static boolean back() { return globalAction(GLOBAL_ACTION_BACK); }
    public static boolean home() { return globalAction(GLOBAL_ACTION_HOME); }
    public static boolean recents() { return globalAction(GLOBAL_ACTION_RECENTS); }
    public static boolean notifications() { return globalAction(GLOBAL_ACTION_NOTIFICATIONS); }
    public static boolean lockScreen() { return globalAction(GLOBAL_ACTION_LOCK_SCREEN); }
}
