package com.ayushdebbarma.myaiagent;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.*;

/** Lightweight opt-in detector for short snap/clap-like audio transients. */
public class SoundTriggerService extends Service {
    private static final int NOTIFICATION_ID = 72;
    private static final int SAMPLE_RATE = 16000;
    private volatile boolean running;
    private Thread worker;
    private AudioRecord recorder;
    private int snapCount;
    private long lastSnapAt;
    private long lastActionAt;
    private double noiseFloor = 700.0;

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("sound_triggers", "Axtor Sound Triggers", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "sound_triggers") : new Notification.Builder(this);
        b.setContentTitle("Axtor sound triggers").setContentText("Listening for configured snap triggers")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true);
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(NOTIFICATION_ID, b.build());
        startDetector();
    }

    private void startDetector() {
        if (worker != null) return;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission("android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) { stopSelf(); return; }
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) { stopSelf(); return; }
        int bufferSize = Math.max(min * 2, 2048);
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) { recorder.release(); recorder = null; stopSelf(); return; }
            recorder.startRecording();
        } catch (Exception e) { stopSelf(); return; }
        worker = new Thread(() -> detectLoop(), "AxtorSoundTrigger");
        worker.start();
    }

    private void detectLoop() {
        short[] samples = new short[256];
        while (running && recorder != null) {
            int n;
            try { n = recorder.read(samples, 0, samples.length); } catch (Exception e) { break; }
            if (n <= 0) continue;
            double sum = 0.0;
            int peak = 0;
            int zeroCrossings = 0;
            short previous = samples[0];
            for (int i = 0; i < n; i++) {
                int v = Math.abs((int) samples[i]);
                sum += (double) samples[i] * samples[i];
                if (v > peak) peak = v;
                if ((previous < 0 && samples[i] >= 0) || (previous >= 0 && samples[i] < 0)) zeroCrossings++;
                previous = samples[i];
            }
            double rms = Math.sqrt(sum / n);
            double zcr = (double) zeroCrossings / Math.max(1, n);
            if (rms < noiseFloor * 1.6) noiseFloor = noiseFloor * 0.98 + rms * 0.02;
            double threshold = Math.max(1400.0, noiseFloor * 3.2);
            if (rms > threshold && peak > 6000 && zcr > 0.16) registerSnap();
        }
    }

    private void registerSnap() {
        long now = System.currentTimeMillis();
        if (now - lastSnapAt < 220 || now - lastActionAt < 500) return;
        lastSnapAt = now;
        snapCount++;
        final int count = snapCount;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (snapCount != count) return;
            snapCount = 0;
            executeConfiguredAction(count >= 2 ? "double" : "single");
        }, 700);
    }

    private void executeConfiguredAction(String type) {
        if (!running) return;
        lastActionAt = System.currentTimeMillis();
        android.content.SharedPreferences p = getSharedPreferences("axtor_sound", 0);
        String fallback = type.equals("single") ? "lock screen" : "volume down";
        String action = p.getString(type + "_action", fallback).trim();
        if (action.isEmpty()) return;
        String result = DeviceAutomation.execute(this, action);
        p.edit().putString("last_trigger", type + ":" + action).putString("last_result", result == null ? "unsupported" : result).apply();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) {} recorder.release(); recorder = null; }
        if (worker != null) { worker.interrupt(); worker = null; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
