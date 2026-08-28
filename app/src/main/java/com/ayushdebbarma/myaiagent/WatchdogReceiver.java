package com.ayushdebbarma.myaiagent;

import android.app.*;
import android.content.*;
import android.os.*;

public class WatchdogReceiver extends BroadcastReceiver {
  private static final int ID=732;

  @Override public void onReceive(Context context, Intent intent) {
    boolean enabled=context.getSharedPreferences("axtor",Context.MODE_PRIVATE)
        .getBoolean("watchdog_enabled",true);
    if(!enabled) return;

    // Android controls background service starts. We don't bypass those rules.
    // If voice mode is enabled, surface a recovery notification when the
    // service is not currently active.
    boolean voice=context.getSharedPreferences("axtor",Context.MODE_PRIVATE)
        .getBoolean("voice_enabled",false);

    if(voice && !VoiceServiceState.isRunning()) {
      NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
      if(Build.VERSION.SDK_INT>=26) {
        nm.createNotificationChannel(new NotificationChannel(
          "watchdog","Axtor Watchdog",NotificationManager.IMPORTANCE_LOW));
      }
      Intent open=new Intent(context,MainActivity.class)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      PendingIntent pi=PendingIntent.getActivity(context,ID,open,
          PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
      Notification.Builder b=Build.VERSION.SDK_INT>=26
          ?new Notification.Builder(context,"watchdog")
          :new Notification.Builder(context);
      b.setSmallIcon(android.R.drawable.ic_dialog_info)
       .setContentTitle("Axtor voice assistant")
       .setContentText("Voice service needs to be resumed.")
       .setContentIntent(pi).setAutoCancel(true);
      nm.notify(ID,b.build());
    }

    WatchdogScheduler.schedule(context);
  }
}
