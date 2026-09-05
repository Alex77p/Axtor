package com.ayushdebbarma.myaiagent;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Delivers the local reminder created by DeviceAutomation. */
public class AlarmReceiver extends BroadcastReceiver {
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "alarms";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Axtor Alarms", NotificationManager.IMPORTANCE_HIGH));
        }

        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                context, NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(context, CHANNEL_ID)
                : new android.app.Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Axtor alarm")
                .setContentText("Your Axtor reminder is due.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(android.app.Notification.CATEGORY_ALARM)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .setDefaults(android.app.Notification.DEFAULT_ALL);
        nm.notify(NOTIFICATION_ID, builder.build());
    }
}
