package com.ayushdebbarma.myaiagent;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.*;

/** Compatibility snap service with selectable classic and research transient protocols. */
public class SoundTriggerService extends Service {
    private static final int NOTIFICATION_ID=72, SAMPLE_RATE=16000;
    private volatile boolean running; private Thread worker; private AudioRecord recorder;
    private int snapCount; private long lastSnapAt,lastActionAt; private double noiseFloor=700.0;
    private double previousEnergy=0.0, noveltyMean=0.0, noveltyDev=1.0; private int researchQuietFrames=0;
    private final Biquad lowPass=new Biquad(), highPass=new Biquad();

    @Override public void onCreate(){
        super.onCreate();
        if(getSharedPreferences("axtor_voice",0).getBoolean("snap_trigger_enabled",true)){stopSelf();return;}
        running=true;
        if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("sound_triggers","Axtor Sound Triggers",NotificationManager.IMPORTANCE_LOW));
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"sound_triggers"):new Notification.Builder(this);
        b.setContentTitle("Axtor sound triggers").setContentText("Listening for "+SnapDetectionProtocol.label(this)).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true);
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,b.build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);else startForeground(NOTIFICATION_ID,b.build());
        startDetector();
    }
    private void startDetector(){
        if(worker!=null)return;
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission("android.permission.RECORD_AUDIO")!=PackageManager.PERMISSION_GRANTED){stopSelf();return;}
        int min=AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0){stopSelf();return;}
        try{recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min*2,4096));if(recorder.getState()!=AudioRecord.STATE_INITIALIZED){recorder.release();recorder=null;stopSelf();return;}recorder.startRecording();}catch(Exception e){stopSelf();return;}
        worker=new Thread(this::detectLoop,"AxtorSoundTrigger");worker.start();
    }
    private void detectLoop(){if(SnapDetectionProtocol.RESEARCH.equals(SnapDetectionProtocol.get(this)))researchLoop();else legacyLoop();}
    private void legacyLoop(){
        short[] s=new short[256];while(running&&recorder!=null){int n;try{n=recorder.read(s,0,s.length);}catch(Exception e){break;}if(n<=0)continue;double sum=0;int peak=0,zc=0;short prev=s[0];for(int i=0;i<n;i++){int v=Math.abs((int)s[i]);sum+=(double)s[i]*s[i];if(v>peak)peak=v;if((prev<0&&s[i]>=0)||(prev>=0&&s[i]<0))zc++;prev=s[i];}double rms=Math.sqrt(sum/n),zcr=(double)zc/Math.max(1,n);if(rms<noiseFloor*1.6)noiseFloor=noiseFloor*.98+rms*.02;double threshold=Math.max(1400,noiseFloor*3.2);if(rms>threshold&&peak>6000&&zcr>.16)registerSnap();}
    }
    /** Published finger-snap approach adapted to 16 kHz Android capture: 1.5-3.5 kHz band-pass, 256-sample frames, 50% overlap, and abrupt short-time-energy rise. */
    private void researchLoop(){
        short[] in=new short[128];double[] frame=new double[256];int filled=0;lowPass.configureLowPass(3500,SAMPLE_RATE);highPass.configureHighPass(1500,SAMPLE_RATE);
        while(running&&recorder!=null){int n;try{n=recorder.read(in,0,in.length);}catch(Exception e){break;}if(n<=0)continue;for(int i=0;i<n;i++){double x=in[i]/32768.0;x=lowPass.process(x);x=highPass.process(x);frame[filled++]=x;if(filled==256){double energy=0,peak=0;for(double v:frame){energy+=v*v;peak=Math.max(peak,Math.abs(v));}energy/=256.0;double novelty=Math.max(0,energy-previousEnergy);previousEnergy=energy;if(researchQuietFrames<16){noveltyMean=.9*noveltyMean+.1*novelty;noveltyDev=.9*noveltyDev+.1*Math.abs(novelty-noveltyMean);researchQuietFrames++;}else{noveltyMean=.985*noveltyMean+.015*novelty;noveltyDev=.985*noveltyDev+.015*Math.abs(novelty-noveltyMean);}double threshold=noveltyMean+Math.max(.0007,3.0*noveltyDev);double rms=Math.sqrt(energy);if(novelty>threshold&&rms>.012&&peak>.06)registerSnap();System.arraycopy(frame,128,frame,0,128);filled=128;}}}
    private void registerSnap(){long now=System.currentTimeMillis();if(now-lastSnapAt<180||now-lastActionAt<500)return;lastSnapAt=now;snapCount=Math.min(4,snapCount+1);final int count=snapCount;new Handler(Looper.getMainLooper()).postDelayed(()->{if(snapCount!=count)return;snapCount=0;executeConfiguredAction(count>=4?"quad":count==3?"triple":count==2?"double":"single");},750);}
    private void executeConfiguredAction(String type){if(!running)return;lastActionAt=System.currentTimeMillis();SharedPreferences p=getSharedPreferences("axtor_sound",0);String fallback=type.equals("single")?"wake screen":type.equals("double")?"volume down":type.equals("triple")?"open settings":"open notification settings";String action=p.getString(type+"_action",fallback).trim();if(action.isEmpty())return;String policy=AxtorCommandSecurityPolicy.authorizeVoice(this,action);if(!"OK".equals(policy)){p.edit().putString("last_result","blocked:"+policy).apply();return;}if(p.getBoolean("wake_on_screen_off",true)){PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);if(pm!=null&&!pm.isInteractive())DeviceAutomation.execute(this,"wake screen");}String result=DeviceAutomation.execute(this,action);p.edit().putString("last_trigger",type+":"+action).putString("last_result",result==null?"unsupported":result).apply();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(getSharedPreferences("axtor_voice",0).getBoolean("snap_trigger_enabled",true)){stopSelf();return START_NOT_STICKY;}if(intent!=null&&"STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}return START_NOT_STICKY;}
    @Override public void onDestroy(){running=false;if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}if(worker!=null){worker.interrupt();worker=null;}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    private static final class Biquad{double b0,b1,b2,a1,a2,x1,x2,y1,y2;void configureLowPass(double f,double fs){double w=2*Math.PI*f/fs,a=Math.sin(w)/(2*.7071),c=Math.cos(w),a0=1+a;b0=((1-c)/2)/a0;b1=(1-c)/a0;b2=b0;a1=(-2*c)/a0;a2=(1-a)/a0;reset();}void configureHighPass(double f,double fs){double w=2*Math.PI*f/fs,a=Math.sin(w)/(2*.7071),c=Math.cos(w),a0=1+a;b0=((1+c)/2)/a0;b1=(-(1+c))/a0;b2=b0;a1=(-2*c)/a0;a2=(1-a)/a0;reset();}double process(double x){double y=b0*x+b1*x1+b2*x2-a1*y1-a2*y2;x2=x1;x1=x;y2=y1;y1=y;return y;}void reset(){x1=x2=y1=y2=0;}}
}
