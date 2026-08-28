package com.ayushdebbarma.myaiagent;

import android.app.*;
import android.content.*;
import android.os.*;

public final class WatchdogScheduler {
  private static final int REQUEST=731;
  private WatchdogScheduler(){}

  public static void schedule(Context c){
    AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
    Intent i=new Intent(c,WatchdogReceiver.class);
    PendingIntent p=PendingIntent.getBroadcast(c,REQUEST,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    long interval=15*60*1000L;
    long first=System.currentTimeMillis()+interval;
    if(Build.VERSION.SDK_INT>=23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,first,p);
    else am.set(AlarmManager.RTC_WAKEUP,first,p);
  }

  public static void cancel(Context c){
    AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
    Intent i=new Intent(c,WatchdogReceiver.class);
    PendingIntent p=PendingIntent.getBroadcast(c,REQUEST,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    am.cancel(p);
  }
}
