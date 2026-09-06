package com.ayushdebbarma.myaiagent;

import android.content.Context;
import android.content.SharedPreferences;

/** Selectable snap detector protocol. */
public final class SnapDetectionProtocol {
    public static final String PREF = "axtor_sound";
    public static final String KEY_MODE = "snap_detection_protocol";
    public static final String ADAPTIVE = "adaptive";
    public static final String LEGACY = "legacy";
    public static final String RESEARCH = "research";
    private SnapDetectionProtocol() {}
    public static String get(Context c) { return c.getSharedPreferences(PREF,0).getString(KEY_MODE,ADAPTIVE); }
    public static void set(Context c,String mode) { if(!ADAPTIVE.equals(mode)&&!LEGACY.equals(mode)&&!RESEARCH.equals(mode)) mode=ADAPTIVE; c.getSharedPreferences(PREF,0).edit().putString(KEY_MODE,mode).apply(); }
    public static String label(Context c) { String m=get(c); if(LEGACY.equals(m)) return "Classic / old detector"; if(RESEARCH.equals(m)) return "Band-pass transient detector"; return "Adaptive personal detector"; }
}
