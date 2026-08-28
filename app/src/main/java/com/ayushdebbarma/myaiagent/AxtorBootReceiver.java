package com.ayushdebbarma.myaiagent;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.provider.Settings;

public class AxtorBootReceiver extends BroadcastReceiver {
  private static final int ID=909;
  @Override public void onReceive(Context context, Intent intent) {
    String a=intent==null?null:intent.getAction();
    if (!Intent.ACTION_BOOT_COMPLETED.equals(a) &&
        !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(a) &&
        !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;

    // Android restricts microphone foreground-service startup from BOOT_COMPLETED.
    // We therefore restore a user-visible status notification instead of attempting
    // to bypass the platform restriction.
    if (!context.getSharedPreferences("axtor", Context.MODE_PRIVATE)
        .getBoolean("voice_enabled", false)) return;

    NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    if(Build.VERSION.SDK_INT>=26){
      NotificationChannel ch=new NotificationChannel("health","Axtor Service Status",NotificationManager.IMPORTANCE_LOW);
      nm.createNotificationChannel(ch);
    }
    Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    PendingIntent pi=PendingIntent.getActivity(context,909,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,"health"):new Notification.Builder(context);
    b.setSmallIcon(android.R.drawable.ic_lock_idle_lock)
      .setContentTitle("Axtor voice assistant")
      .setContentText("Tap to resume voice listening after reboot.")
      .setContentIntent(pi).setAutoCancel(true);
    nm.notify(ID,b.build());
  }
}
