package com.ayushdebbarma.myaiagent;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * On-device acoustic finger-snap trigger. It does not send audio to a server.
 * A trigger is accepted only when the transient shape is snap-like AND, when
 * enrolled, its acoustic fingerprint is sufficiently close to the owner's
 * enrolled snap samples. Ordinary speech/noise is rejected before ASR starts.
 *
 * Three enrolled snap-like transients inside the emergency window immediately
 * latch the global emergency stop. Once a normal snap arms a command, the
 * detector stays in emergency-only mode while voice/AI execution is active.
 */
public final class SnapTriggerEngine {
    public interface Listener { void onSnap(); void onDiagnostic(String message); }
    private static final String PREF="axtor_snap";
    private static final String TEMPLATE="template";
    private static final int RATE=16000;
    private static final int FRAME=512;
    private static final long COOLDOWN_MS=1400;
    private static final long EMERGENCY_WINDOW_MS=2200;
    private static final int EMERGENCY_SNAP_COUNT=3;
    private final Context context;
    private volatile boolean running;
    private volatile boolean emergencyOnly;
    private Thread thread;
    private final Listener listener;
    private long lastTrigger;
    private final Deque<Long> recentSnaps=new ArrayDeque<>();

    public SnapTriggerEngine(Context c, Listener l){context=c.getApplicationContext();listener=l;}
    public boolean isEnrolled(){return !context.getSharedPreferences(PREF,0).getString(TEMPLATE,"").isEmpty();}
    public static boolean isEnrolled(Context c){return !c.getSharedPreferences(PREF,0).getString(TEMPLATE,"").isEmpty();}
    /** Arm only the emergency watcher while a command is being executed. */
    public void setEmergencyOnly(boolean value){emergencyOnly=value;synchronized(recentSnaps){recentSnaps.clear();}}
    public void start(){
        if(running){emergencyOnly=false;synchronized(recentSnaps){recentSnaps.clear();}return;}
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=0){listener.onDiagnostic("SNAP_MIC_PERMISSION_MISSING");return;}
        running=true;emergencyOnly=false;thread=new Thread(this::loop,"AxtorSnapDetector");thread.start();
    }
    /**
     * A stop requested after a recognized snap leaves the microphone watcher in
     * emergency-only mode. It self-terminates when the voice service actually
     * dies, preventing a second independent long-lived microphone service.
     */
    public void stop(){
        if(emergencyOnly && VoiceServiceState.isRunning()){
            synchronized(recentSnaps){recentSnaps.clear();}
            return;
        }
        running=false;if(thread!=null){try{thread.interrupt();}catch(Exception ignored){}}thread=null;synchronized(recentSnaps){recentSnaps.clear();}
    }
    private void loop(){
        int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(min<=0){listener.onDiagnostic("SNAP_AUDIO_UNAVAILABLE");running=false;return;}
        int size=Math.max(min,FRAME*4);AudioRecord r=null;
        try{
            r=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);
            if(r.getState()!=AudioRecord.STATE_INITIALIZED){listener.onDiagnostic("SNAP_AUDIO_INIT_FAILED");running=false;return;}
            short[] buf=new short[FRAME];r.startRecording();
            while(running){
                if(!VoiceServiceState.isRunning()){running=false;break;}
                int n=r.read(buf,0,buf.length);
                if(n==buf.length){
                    Features f=features(buf,n);
                    if(isSnapLike(f)&&matchesTemplate(f)){
                        long now=System.currentTimeMillis();
                        if(now-lastTrigger>COOLDOWN_MS){
                            lastTrigger=now;
                            if(recordEmergencySnap(now)){EmergencyStopController.request(context);return;}
                            if(!emergencyOnly){emergencyOnly=true;listener.onSnap();}
                        }
                    }
                }
            }
        }catch(Throwable t){listener.onDiagnostic("SNAP_DETECTOR_ERROR:"+t.getClass().getSimpleName());}
        finally{if(r!=null){try{r.stop();}catch(Exception ignored){}try{r.release();}catch(Exception ignored){}}}
    }
    private boolean recordEmergencySnap(long now){
        synchronized(recentSnaps){
            while(!recentSnaps.isEmpty()&&now-recentSnaps.peekFirst()>EMERGENCY_WINDOW_MS)recentSnaps.removeFirst();
            recentSnaps.addLast(now);while(recentSnaps.size()>EMERGENCY_SNAP_COUNT)recentSnaps.removeFirst();
            return recentSnaps.size()>=EMERGENCY_SNAP_COUNT;
        }
    }
    public static boolean enroll(Context c){
        if(c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=0)return false;
        int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0)return false;
        AudioRecord r=null;
        try{
            r=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*4));
            if(r.getState()!=AudioRecord.STATE_INITIALIZED)return false;r.startRecording();List<double[]> samples=new ArrayList<>();short[] b=new short[FRAME];long end=System.currentTimeMillis()+9000;
            while(System.currentTimeMillis()<end&&samples.size()<3){int n=r.read(b,0,b.length);if(n!=FRAME)continue;Features f=features(b,n);if(isSnapLike(f)){samples.add(f.vector());try{Thread.sleep(650);}catch(InterruptedException ignored){}}}
            if(samples.size()<3)return false;double[] avg=new double[6];for(double[] v:samples)for(int i=0;i<avg.length;i++)avg[i]+=v[i];for(int i=0;i<avg.length;i++)avg[i]/=samples.size();StringBuilder s=new StringBuilder();for(int i=0;i<avg.length;i++){if(i>0)s.append(',');s.append(avg[i]);}
            c.getSharedPreferences(PREF,0).edit().putString(TEMPLATE,s.toString()).putFloat("threshold",0.86f).apply();return true;
        }catch(Throwable ignored){return false;}finally{if(r!=null){try{r.stop();}catch(Exception ignored){}try{r.release();}catch(Exception ignored){}}}
    }
    public static void clearEnrollment(Context c){c.getSharedPreferences(PREF,0).edit().clear().apply();}
    private boolean matchesTemplate(Features f){
        SharedPreferences p=context.getSharedPreferences(PREF,0);String raw=p.getString(TEMPLATE,"");if(raw.isEmpty())return false;
        try{String[] a=raw.split(",");double[] t=new double[a.length];for(int i=0;i<a.length;i++)t[i]=Double.parseDouble(a[i]);return similarity(f.vector(),t)>=p.getFloat("threshold",0.86f);}catch(Exception e){return false;}
    }
    private static boolean isSnapLike(Features f){return f.peak>0.30&&f.crest>4.0&&f.hf>0.30&&f.durationMs<180&&f.zcr>0.04;}
    private static double similarity(double[] a,double[] b){if(a.length!=b.length)return 0;double dot=0,aa=0,bb=0;for(int i=0;i<a.length;i++){dot+=a[i]*b[i];aa+=a[i]*a[i];bb+=b[i]*b[i];}if(aa==0||bb==0)return 0;return dot/(Math.sqrt(aa)*Math.sqrt(bb));}
    private static Features features(short[] x,int n){double sum=0,peak=0;int z=0;for(int i=0;i<n;i++){double v=Math.abs(x[i])/32768.0;sum+=v*v;if(v>peak)peak=v;if(i>0&&((x[i]>=0)!=(x[i-1]>=0)))z++;}double rms=Math.sqrt(sum/n);double hf=0,total=0;for(int k=1;k<n/2;k++){double re=0,im=0;double w=2*Math.PI*k/n;for(int i=0;i<n;i+=2){double a=x[i]/32768.0;re+=a*Math.cos(w*i);im-=a*Math.sin(w*i);}double mag=re*re+im*im;total+=mag;if(k>n/8)hf+=mag;}double crest=peak/Math.max(rms,0.0001);return new Features(peak,crest,hf/Math.max(total,0.0001),(z/(double)n),n*1000.0/RATE,rms);}
    private static final class Features{final double peak,crest,hf,zcr,durationMs,rms;Features(double p,double c,double h,double z,double d,double r){peak=p;crest=c;hf=h;zcr=z;durationMs=d;rms=r;}double[] vector(){return new double[]{peak,crest/10.0,hf,zcr*10.0,durationMs/100.0,rms*10.0};}}
}
