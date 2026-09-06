package com.ayushdebbarma.myaiagent;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.VibrationEffect;

/** Delivers the local reminder created by DeviceAutomation. */
public class AlarmReceiver extends BroadcastReceiver {
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "alarms";

    @Override public void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wake = null;
        try {
            if (pm != null) { wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Axtor:Alarm"); wake.acquire(10000L); }
        } catch (Exception ignored) {}
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Axtor Alarms", NotificationManager.IMPORTANCE_HIGH));
                Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent pi = PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(context, CHANNEL_ID) : new android.app.Notification.Builder(context);
                builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Axtor alarm").setContentText("Your Axtor one-minute reminder is due.")
                        .setContentIntent(pi).setAutoCancel(true).setCategory(android.app.Notification.CATEGORY_ALARM)
                        .setPriority(android.app.Notification.PRIORITY_HIGH).setDefaults(android.app.Notification.DEFAULT_ALL);
                nm.notify(NOTIFICATION_ID, builder.build());
            }
            try {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                if (alarmUri != null) {
                    Ringtone ringtone = RingtoneManager.getRingtone(context.getApplicationContext(), alarmUri);
                    if (ringtone != null) { ringtone.play(); new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> { try { ringtone.stop(); } catch (Exception ignored) {} }, 5000L); }
                }
            } catch (Exception ignored) {}
            try {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,500,300,500}, -1));
                    else vibrator.vibrate(new long[]{0,500,300,500}, -1);
                }
            } catch (Exception ignored) {}
        } finally { if (wake != null && wake.isHeld()) wake.release(); }
    }
}
