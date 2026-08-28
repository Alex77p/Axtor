package com.ayushdebbarma.myaiagent;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class AxtorAccessibilityService extends AccessibilityService {
    private static volatile AxtorAccessibilityService instance;

    @Override public void onServiceConnected() { super.onServiceConnected(); instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
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
