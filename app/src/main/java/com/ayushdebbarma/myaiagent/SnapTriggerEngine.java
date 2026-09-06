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

/** On-device personalized snap trigger with adaptive noise handling and safe direct command patterns. */
public final class SnapTriggerEngine {
    public interface Listener { void onSnap(); void onDiagnostic(String message); }
    private static final String PREF="axtor_snap";
    private static final String TEMPLATE="template";
    private static final int RATE=16000, FRAME=2048;
    private static final long NORMAL_COOLDOWN_MS=1000, EMERGENCY_COOLDOWN_MS=300;
    private static final long EMERGENCY_WINDOW_MS=2200;
    private static final int EMERGENCY_SNAP_COUNT=3;
    private final Context context; private volatile boolean running, emergencyOnly;
    private Thread thread; private final Listener listener; private long lastTrigger;
    private final Deque<Long> recentSnaps=new ArrayDeque<>(); private volatile int patternCount;
    private volatile double liveNoiseRms=.003, liveNoisePeak=.03;

    public SnapTriggerEngine(Context c, Listener l){context=c.getApplicationContext();listener=l;ExtendedRangeState.enabled=isExtendedRangeEnabled(context);}
    public boolean isEnrolled(){return isEnrolled(context);}
    public static boolean isEnrolled(Context c){return !c.getSharedPreferences(PREF,0).getString(TEMPLATE,"").isEmpty();}
    public static int patternCount(Context c){return c.getSharedPreferences(PREF,0).getInt("pattern_count",0);}
    public static boolean emergencyArmed(Context c){return c.getSharedPreferences(PREF,0).getBoolean("emergency_armed",false);}
    public static String liveStatus(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,0);
        if(!isEnrolled(c)) return "Not enrolled • enroll 3 snaps";
        if(p.getBoolean("emergency_armed",false)) return "3 snaps detected • emergency stop armed (snap once more)";
        int n=p.getInt("pattern_count",0);
        return "Ready • snaps detected: "+n+"/4";
    }
    public static int enrollmentProgress(Context c){return c.getSharedPreferences(PREF,0).getInt("enrollment_progress",0);}
    public static void setEnrollmentProgress(Context c,int value){c.getSharedPreferences(PREF,0).edit().putInt("enrollment_progress",Math.max(0,Math.min(3,value))).apply();}
    public static boolean isExtendedRangeEnabled(Context c){return c.getSharedPreferences(PREF,0).getBoolean("extended_range",true);}
    public static void setExtendedRangeEnabled(Context c,boolean value){c.getSharedPreferences(PREF,0).putBoolean("extended_range",value).apply();ExtendedRangeState.enabled=value;}
    public void setEmergencyOnly(boolean value){emergencyOnly=value; synchronized(recentSnaps){recentSnaps.clear();} patternCount=0; persistPattern();}
    public void start(){if(running){emergencyOnly=false;synchronized(recentSnaps){recentSnaps.clear();}patternCount=0;persistPattern();return;}if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=0){listener.onDiagnostic("SNAP_MIC_PERMISSION_MISSING");return;}ExtendedRangeState.enabled=isExtendedRangeEnabled(context);liveNoiseRms=.003;liveNoisePeak=.03;running=true;emergencyOnly=false;patternCount=0;persistPattern();thread=new Thread(this::loop,"AxtorSnapDetector");thread.start();}
    public void stop(){if(emergencyOnly&&VoiceServiceState.isRunning()){synchronized(recentSnaps){recentSnaps.clear();}return;}running=false;if(thread!=null){try{thread.interrupt();}catch(Exception ignored){}}thread=null;synchronized(recentSnaps){recentSnaps.clear();}patternCount=0;persistPattern();}

    private void loop(){
        int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(min<=0){listener.onDiagnostic("SNAP_AUDIO_UNAVAILABLE");running=false;return;}
        AudioRecord r=null;
        try{
            r=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*2));
            if(r.getState()!=AudioRecord.STATE_INITIALIZED){listener.onDiagnostic("SNAP_AUDIO_INIT_FAILED");running=false;return;}
            r.startRecording(); short[] buf=new short[FRAME]; int calibrationFrames=0;
            while(running){
                if(!VoiceServiceState.isRunning()){running=false;break;}
                int n=r.read(buf,0,buf.length); if(n!=FRAME)continue;
                Features f=features(buf,n);
                if(calibrationFrames<24){updateLiveNoise(f);calibrationFrames++;continue;}
                updateLiveNoiseQuietly(f);
                if(isAdaptiveSnapLike(f,liveNoiseRms,liveNoisePeak)&&matchesTemplate(f)){
                    long now=System.currentTimeMillis(); long cooldown=emergencyOnly?EMERGENCY_COOLDOWN_MS:NORMAL_COOLDOWN_MS;
                    if(now-lastTrigger>cooldown){lastTrigger=now;registerPattern(now);}
                }
            }
        }catch(Throwable t){listener.onDiagnostic("SNAP_DETECTOR_ERROR:"+t.getClass().getSimpleName());}
        finally{if(r!=null){try{r.stop();}catch(Exception ignored){}try{r.release();}catch(Exception ignored){}}}
    }
    private void updateLiveNoise(Features f){liveNoiseRms=Math.max(.0008,f.rms*1.15);liveNoisePeak=Math.max(.008,f.peak*1.15);}
    private void updateLiveNoiseQuietly(Features f){
        double gateRms=liveNoiseRms*1.55, gatePeak=liveNoisePeak*1.7;
        if(f.rms<gateRms&&f.peak<gatePeak){liveNoiseRms=.97*liveNoiseRms+.03*Math.max(.0008,f.rms);liveNoisePeak=.97*liveNoisePeak+.03*Math.max(.008,f.peak);}
    }
    private void registerPattern(long now){if(emergencyOnly){if(recordEmergencySnap(now)){EmergencyStopController.request(context);persistEmergency(false);return;}return;}patternCount=Math.min(4,patternCount+1);persistPattern();final int count=patternCount;if(count==3){patternCount=0;emergencyOnly=true;persistPattern();persistEmergency(true);synchronized(recentSnaps){recentSnaps.clear();}recentSnaps.addLast(now);listener.onDiagnostic("SNAP_PATTERN_TRIPLE_EMERGENCY_ARMED");return;}new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(!running||patternCount!=count)return;patternCount=0;persistPattern();if(count==1){listener.onSnap();}else executeConfiguredPattern(count==2?"double":"quad");},750);}
    private void persistPattern(){context.getSharedPreferences(PREF,0).edit().putInt("pattern_count",patternCount).apply();}
    private void persistEmergency(boolean value){context.getSharedPreferences(PREF,0).edit().putBoolean("emergency_armed",value).apply();}
    private boolean recordEmergencySnap(long now){synchronized(recentSnaps){while(!recentSnaps.isEmpty()&&now-recentSnaps.peekFirst()>EMERGENCY_WINDOW_MS)recentSnaps.removeFirst();recentSnaps.addLast(now);return recentSnaps.size()>=EMERGENCY_SNAP_COUNT;}}
    private void executeConfiguredPattern(String type){SharedPreferences p=context.getSharedPreferences("axtor_sound",0);String fallback=type.equals("double")?"volume down":"open notification settings";String action=p.getString(type+"_action",fallback).trim();if(action.isEmpty())return;String policy=AxtorCommandSecurityPolicy.authorizeVoice(context,action);if(!"OK".equals(policy)){listener.onDiagnostic("SNAP_COMMAND_BLOCKED:"+policy);return;}String result=DeviceAutomation.execute(context,action);p.edit().putString("last_trigger",type+":"+action).putString("last_result",result==null?"unsupported":result).apply();if(result==null)listener.onDiagnostic("SNAP_COMMAND_UNSUPPORTED:"+type);else listener.onDiagnostic("SNAP_COMMAND_EXECUTED:"+type+":"+action);}

    /**
     * Enrollment is deliberately adaptive: first learns the local noise floor, then keeps the
     * strongest three transient candidates. Fans, traffic, TV and continuous speech are treated
     * as background unless a short transient rises clearly above that floor.
     */
    public static boolean enroll(Context c){
        setEnrollmentProgress(c,0);
        if(c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=0){setEnrollmentDiagnostic(c,"SNAP_ENROLL_MIC_PERMISSION_MISSING");return false;}
        int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(min<=0){setEnrollmentDiagnostic(c,"SNAP_ENROLL_AUDIO_UNAVAILABLE");return false;}
        AudioRecord r=null;
        try{
            r=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*2));
            if(r.getState()!=AudioRecord.STATE_INITIALIZED){setEnrollmentDiagnostic(c,"SNAP_ENROLL_AUDIO_INIT_FAILED");return false;}
            r.startRecording();
            short[] b=new short[FRAME];
            final int calibrationFrames=32; int frames=0; int candidates=0;
            double noiseRms=.001, noisePeak=.01;
            List<Candidate> best=new ArrayList<>();
            long end=System.currentTimeMillis()+20000;
            setEnrollmentDiagnostic(c,"SNAP_ENROLL_CALIBRATING_NOISE");
            while(System.currentTimeMillis()<end&&best.size()<3){
                int n=r.read(b,0,b.length); if(n!=FRAME)continue; frames++;
                Features f=features(b,n);
                if(frames<=calibrationFrames){
                    noiseRms=.90*noiseRms+.10*Math.max(.0008,f.rms);
                    noisePeak=.90*noisePeak+.10*Math.max(.008,f.peak);
                    if(frames==calibrationFrames)setEnrollmentDiagnostic(c,"SNAP_ENROLL_LISTENING_NOISE_RMS:"+round(noiseRms)+"_PEAK:"+round(noisePeak));
                    continue;
                }
                double quietRms=noiseRms*1.45, quietPeak=noisePeak*1.65;
                if(f.rms<quietRms&&f.peak<quietPeak){
                    noiseRms=.98*noiseRms+.02*Math.max(.0008,f.rms);
                    noisePeak=.98*noisePeak+.02*Math.max(.008,f.peak);
                }
                if(isAdaptiveSnapLike(f,noiseRms,noisePeak)){
                    candidates++;
                    double score=snapScore(f,noiseRms,noisePeak);
                    addCandidate(best,new Candidate(f,score));
                    setEnrollmentProgress(c,best.size());
                    setEnrollmentDiagnostic(c,"SNAP_ENROLL_CANDIDATE:"+candidates+"_BEST:"+best.size()+"/3_NOISE_RMS:"+round(noiseRms)+"_NOISE_PEAK:"+round(noisePeak));
                    try{Thread.sleep(350);}catch(InterruptedException ignored){Thread.currentThread().interrupt();break;}
                }
            }
            if(best.size()<3){
                setEnrollmentDiagnostic(c,"SNAP_ENROLL_NOT_ENOUGH_SNAP_SAMPLES:"+best.size()+"/3_CANDIDATES:"+candidates+"_FRAMES:"+frames+"_NOISE_RMS:"+round(noiseRms)+"_NOISE_PEAK:"+round(noisePeak));
                return false;
            }
            double[] avg=new double[6];
            for(Candidate q:best)for(int i=0;i<avg.length;i++)avg[i]+=q.features.vector()[i];
            for(int i=0;i<avg.length;i++)avg[i]/=best.size();
            StringBuilder s=new StringBuilder();for(int i=0;i<avg.length;i++){if(i>0)s.append(',');s.append(avg[i]);}
            c.getSharedPreferences(PREF,0).edit().putString(TEMPLATE,s.toString()).putFloat("threshold",0.62f).putInt("pattern_count",0).putBoolean("emergency_armed",false).putFloat("noise_rms",(float)noiseRms).putFloat("noise_peak",(float)noisePeak).putInt("snap_candidates",candidates).putInt("snap_samples_captured",3).putString("last_enrollment_diagnostic","SNAP_ENROLL_SUCCESS:3/3_CANDIDATES:"+candidates).apply();
            setEnrollmentProgress(c,3);return true;
        }catch(Throwable t){setEnrollmentDiagnostic(c,"SNAP_ENROLL_ERROR:"+t.getClass().getSimpleName());return false;}
        finally{if(r!=null){try{r.stop();}catch(Exception ignored){}try{r.release();}catch(Exception ignored){}}}
    }
    private static boolean isAdaptiveSnapLike(Features f,double noiseRms,double noisePeak){
        boolean extended=ExtendedRangeState.enabled;
        double peakFloor=extended?.075:.12;
        double crestFloor=extended?2.15:2.6;
        double hfFloor=extended?.045:.07;
        double zcrFloor=extended?.012:.018;
        double rmsFloor=Math.max(.0045,noiseRms*2.0);
        double peakAdaptive=Math.max(peakFloor,noisePeak*2.25);
        return f.peak>peakAdaptive&&f.rms>rmsFloor&&f.crest>crestFloor&&f.hf>hfFloor&&f.zcr>zcrFloor&&f.durationMs>=90&&f.durationMs<=180;
    }
    private static double snapScore(Features f,double noiseRms,double noisePeak){
        double peakRatio=f.peak/Math.max(noisePeak,.005), rmsRatio=f.rms/Math.max(noiseRms,.001);
        return Math.min(8,peakRatio)+Math.min(6,rmsRatio)+f.crest+f.hf*4+f.zcr*10;
    }
    private static void addCandidate(List<Candidate> list,Candidate candidate){list.add(candidate);list.sort((a,b)->Double.compare(b.score,a.score));while(list.size()>3)list.remove(list.size()-1);}
    private static String round(double v){return String.format(java.util.Locale.US,"%.4f",v);}
    private static final class Candidate{final Features features;final double score;Candidate(Features f,double s){features=f;score=s;}}
    private static void setEnrollmentDiagnostic(Context c,String value){c.getSharedPreferences(PREF,0).edit().putString("last_enrollment_diagnostic",value).apply();}
    public static String enrollmentDiagnostic(Context c){return c.getSharedPreferences(PREF,0).getString("last_enrollment_diagnostic","SNAP_ENROLL_NOT_RUN");}
    public static void clearEnrollment(Context c){c.getSharedPreferences(PREF,0).edit().clear().apply();}
    private boolean matchesTemplate(Features f){SharedPreferences p=context.getSharedPreferences(PREF,0);String raw=p.getString(TEMPLATE,"");if(raw.isEmpty())return false;try{String[] a=raw.split(",");double[] t=new double[a.length];for(int i=0;i<a.length;i++)t[i]=Double.parseDouble(a[i]);return similarity(f.vector(),t)>=p.getFloat("threshold",0.62f);}catch(Exception e){return false;}}
    private static double similarity(double[] a,double[] b){if(a.length!=b.length)return 0;double dot=0,aa=0,bb=0;for(int i=0;i<a.length;i++){dot+=a[i]*b[i];aa+=a[i]*a[i];bb+=b[i]*b[i];}if(aa==0||bb==0)return 0;return dot/(Math.sqrt(aa)*Math.sqrt(bb));}
    private static Features features(short[] x,int n){
        double sum=0,peak=0;int z=0;double diffEnergy=0;
        for(int i=0;i<n;i++){double v=Math.abs(x[i])/32768.0;sum+=v*v;if(v>peak)peak=v;if(i>0){if((x[i]>=0)!=(x[i-1]>=0))z++;double d=(x[i]-x[i-1])/32768.0;diffEnergy+=d*d;}}
        double rms=Math.sqrt(sum/Math.max(1,n));
        double hf=diffEnergy/Math.max(4*sum,.0000001);
        double crest=peak/Math.max(rms,.0001);
        return new Features(peak,crest,Math.min(1,hf),(z/(double)n),n*1000.0/RATE,rms);
    }
    private static final class Features{final double peak,crest,hf,zcr,durationMs,rms;Features(double p,double c,double h,double z,double d,double r){peak=p;crest=c;hf=h;zcr=z;durationMs=d;rms=r;}double[] vector(){return new double[]{peak,crest/10.0,hf,zcr*10.0,durationMs/100.0,rms*10.0};}}
    static final class ExtendedRangeState{static volatile boolean enabled=true;}
}
