package com.ayushdebbarma.myaiagent;
import android.service.voice.*; import android.content.*; import android.os.*; import android.view.*; import android.widget.*;
public class VoiceAssistantSession extends VoiceInteractionSession {
 public VoiceAssistantSession(Context c){super(c);}
 @Override public void onCreate(){super.onCreate();}
 @Override public void onShow(Bundle args,int flags){super.onShow(args,flags);}
}
