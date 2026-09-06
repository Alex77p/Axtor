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
import java.util.Locale;

/** On-device personalized snap trigger with adaptive noise handling and detailed diagnostics. */
public final class SnapTriggerEngine {
    public interface Listener { void onSnap(); void onDiagnostic(String message); }
    private static final String PREF="axtor_snap";
    private static final String TEMPLATE="template";
    private static final String TEMPLATE_V2="template_v2";
    private static final int RATE=16000, FRAME=2048, HOP=512;
    private static final long NORMAL_COOLDOWN_MS=500;
    private static final long PATTERN_WINDOW_MS=2200;
    private final Context context; private volatile boolean running, emergencyOnly;
    private Thread thread; private final Listener listener; private long lastTrigger;
    private final Deque<Long> recentSnaps=new ArrayDeque<>(); private volatile int patternCount;
    private volatile double liveNoiseRms=.003, liveNoisePeak=.03;

    public SnapTriggerEngine(Context c, Listener l){context=c.getApplicationContext();listener=l;ExtendedRangeState.enabled=isExtendedRangeEnabled(context);}
    public boolean isEnrolled(){return isEnrolled(context);}
    public static boolean isEnrolled(Context c){SharedPreferences p=c.getSharedPreferences(PREF,0);return !p.getString(TEMPLATE_V2,"").isEmpty()||!p.getString(TEMPLATE,"").isEmpty();}
    public static int patternCount(Context c){return c.getSharedPreferences(PREF,0).getInt("pattern_count",0);}
    public static boolean emergencyArmed(Context c){return c.getSharedPreferences(PREF,0).getBoolean("emergency_armed",false);}
    public static String liveStatus(Context c){SharedPreferences p=c.getSharedPreferences(PREF,0);if(!isEnrolled(c))return "Not enrolled • enroll 3 snaps";if(p.getBoolean("emergency_armed",false))return "3 snaps detected • emergency stop armed";return "Ready • snaps detected: "+p.getInt("pattern_count",0)+"/4";}
    public static int enrollmentProgress(Context c){return c.getSharedPreferences(PREF,0).getInt("enrollment_progress",0);}
    public static void setEnrollmentProgress(Context c,int value){c.getSharedPreferences(PREF,0).edit().putInt("enrollment_progress",Math.max(0,Math.min(3,value))).apply();}
    public static boolean isExtendedRangeEnabled(Context c){return c.getSharedPreferences(PREF,0).getBoolean("extended_range",true);}
    public static void setExtendedRangeEnabled(Context c,boolean value){c.getSharedPreferences(PREF,0).edit().putBoolean("extended_range",value).apply();ExtendedRangeState.enabled=value;}
    public static String captureDiagnostics(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,0);
        return "MIC="+p.getBoolean("mic_capturing",false)+" SAMPLE_RATE="+RATE+" RMS="+p.getFloat("last_rms",0)+" PEAK="+p.getFloat("last_peak",0)+" CREST="+p.getFloat("last_crest",0)+" HF="+p.getFloat("last_hf",0)+" ZCR="+p.getFloat("last_zcr",0)+" DURATION_MS="+p.getFloat("last_duration_ms",0)+" PEAK_NOISE_RATIO="+p.getFloat("last_peak_noise_ratio",0)+" SIM="+p.getFloat("last_similarity",0)+" THRESHOLD="+p.getFloat("threshold",0)+" NOISE_RMS="+p.getFloat("noise_rms",0)+" NOISE_PEAK="+p.getFloat("noise_peak",0)+" REASON="+p.getString("last_reject_reason","none")+" ENROLL="+enrollmentDiagnostic(c);
    }
    public static String diagnosticsSummary(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,0);
        String d=enrollmentDiagnostic(c);
        return "Snap diagnostics\n"+
                "Microphone capture: "+(p.getBoolean("mic_capturing",false)?"RUNNING":"stopped")+"\n"+
                "Sample rate: "+RATE+" Hz\n"+
                "Enrollment: "+(isEnrolled(c)?"READY":"NOT ENROLLED")+" ("+enrollmentProgress(c)+"/3)\n"+
                "Enrollment result: "+d+"\n"+
                "Ambient RMS: "+round(p.getFloat("noise_rms",0))+"\n"+
                "Ambient peak: "+round(p.getFloat("noise_peak",0))+"\n"+
                "Last RMS: "+round(p.getFloat("last_rms",0))+"\n"+
                "Last peak: "+round(p.getFloat("last_peak",0))+"\n"+
                "Last duration: "+round(p.getFloat("last_duration_ms",0))+" ms\n"+
                "Crest: "+round(p.getFloat("last_crest",0))+"  HF: "+round(p.getFloat("last_hf",0))+"  ZCR: "+round(p.getFloat("last_zcr",0))+"\n"+
                "Peak/noise: "+round(p.getFloat("last_peak_noise_ratio",0))+"x\n"+
                "Similarity: "+round(p.getFloat("last_similarity",0))+" / "+round(p.getFloat("threshold",0))+"\n"+
                "Last decision: "+p.getString("last_decision","NONE")+"\n"+
                "Reject reason: "+p.getString("last_reject_reason","none")+"\n"+
                "Candidates seen: "+p.getInt("snap_candidates",0)+"\n"+
                "Pattern count: "+p.getInt("pattern_count",0)+"/4\n"+
                "Emergency armed: "+p.getBoolean("emergency_armed",false)+"\n"+
                "Detector error: "+p.getString("last_detector_error","none");
    }
    public void setEmergencyOnly(boolean value){emergencyOnly=value;synchronized(recentSnaps){recentSnaps.clear();}patternCount=0;persistPattern();}
    public void start(){
        if(running)return;
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=0){diag("SNAP_MIC_PERMISSION_MISSING");return;}
        ExtendedRangeState.enabled=isExtendedRangeEnabled(context);liveNoiseRms=.003;liveNoisePeak=.03;running=true;patternCount=0;persistPattern();
        context.getSharedPreferences(PREF,0).edit().putBoolean("mic_capturing",false).putString("last_detector_error","").apply();
        thread=new Thread(this::loop,"AxtorSnapDetector");thread.start();
    }
    public void stop(){running=false;if(thread!=null){try{thread.interrupt();}catch(Exception ignored){}}thread=null;synchronized(recentSnaps){recentSnaps.clear();}patternCount=0;persistPattern();context.getSharedPreferences(PREF,0).edit().putBoolean("mic_capturing",false).apply();}
    private void loop(){
        int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(min<=0){diag("SNAP_AUDIO_UNAVAILABLE");running=false;return;}
        AudioRecord r=null;
        try{
            r=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*4));
            if(r.getState()!=AudioRecord.STATE_INITIALIZED){diag("SNAP_AUDIO_INIT_FAILED");running=false;return;}
            r.startRecording();context.getSharedPreferences(PREF,0).edit().putBoolean("mic_capturing",true).apply();
            short[] rolling=new short[FRAME],chunk=new short[HOP];int filled=0,calibrationFrames=0;
            while(running){
                int n=r.read(chunk,0,chunk.length);if(n!=HOP){if(n<0)diag("SNAP_AUDIO_READ_ERROR:"+n);continue;}
                System.arraycopy(rolling,HOP,rolling,0,FRAME-HOP);System.arraycopy(chunk,0,rolling,FRAME-HOP,HOP);filled+=HOP;if(filled<FRAME)continue;
                Features f=features(rolling,FRAME,liveNoisePeak);saveFeatures(f);
                if(calibrationFrames<24){updateLiveNoise(f);calibrationFrames++;continue;}
                updateLiveNoiseQuietly(f);
                boolean shape=isTransientShape(f,liveNoiseRms,liveNoisePeak);boolean match=matchesTemplate(f);
                if(shape&&match){
                    context.getSharedPreferences(PREF,0).edit().putString("last_decision","ACCEPTED").putString("last_reject_reason","none").apply();
                    long now=System.currentTimeMillis();if(now-lastTrigger>NORMAL_COOLDOWN_MS){lastTrigger=now;registerPattern(now);}
                }else{
                    String reason=shape?"template_mismatch":rejectReason(f,liveNoiseRms,liveNoisePeak);
                    context.getSharedPreferences(PREF,0).edit().putString("last_decision","REJECTED").putString("last_reject_reason",reason).apply();
                    if(shape||f.peak>Math.max(.03,liveNoisePeak*1.8))listener.onDiagnostic("SNAP_AUDIO:"+captureDiagnostics(context));
                }
            }
        }catch(Throwable t){context.getSharedPreferences(PREF,0).edit().putString("last_detector_error",t.getClass().getSimpleName()+":"+String.valueOf(t.getMessage())).apply();diag("SNAP_DETECTOR_ERROR:"+t.getClass().getSimpleName());}
        finally{context.getSharedPreferences(PREF,0).edit().putBoolean("mic_capturing",false).apply();if(r!=null){try{r.stop();}catch(Exception ignored){}try{r.release();}catch(Exception ignored){}}}
    }
    private void diag(String s){if(listener!=null)listener.onDiagnostic(s);}
    private void saveFeatures(Features f){context.getSharedPreferences(PREF,0).edit().putFloat("last_rms",(float)f.rms).putFloat("last_peak",(float)f.peak).putFloat("last_crest",(float)f.crest).putFloat("last_hf",(float)f.hf).putFloat("last_zcr",(float)f.zcr).putFloat("last_duration_ms",(float)f.durationMs).putFloat("last_peak_noise_ratio",(float)(f.peak/Math.max(liveNoisePeak,.0001))).apply();}
    private void updateLiveNoise(Features f){liveNoiseRms=Math.max(.0005,f.rms*1.10);liveNoisePeak=Math.max(.006,f.peak*1.10);persistNoise();}
    private void updateLiveNoiseQuietly(Features f){double gateRms=liveNoiseRms*1.45,gatePeak=liveNoisePeak*1.55;if(f.rms<gateRms&&f.peak<gatePeak){liveNoiseRms=.97*liveNoiseRms+.03*Math.max(.0005,f.rms);liveNoisePeak=.97*liveNoisePeak+.03*Math.max(.006,f.peak);persistNoise();}}
    private void persistNoise(){context.getSharedPreferences(PREF,0).edit().putFloat("noise_rms",(float)liveNoiseRms).putFloat("noise_peak",(float)liveNoisePeak).apply();}

    /** No fixed snap duration gate: duration is measured from the local amplitude envelope around the peak. */
    private static boolean isTransientShape(Features f,double noiseRms,double noisePeak){
        double peakFloor=ExtendedRangeState.enabled?.035:.06;
        double rmsFloor=Math.max(.0018,noiseRms*1.30);
        double peakAdaptive=Math.max(peakFloor,noisePeak*1.30);
        return f.peak>peakAdaptive&&f.rms>rmsFloor&&f.crest>1.35&&f.hf>.012&&f.zcr>.004;
    }
    private String rejectReason(Features f,double nr,double np){
        if(f.peak<=Math.max(ExtendedRangeState.enabled?.035:.06,np*1.30))return "peak_below_adaptive_noise_floor";
        if(f.rms<=Math.max(.0018,nr*1.30))return "rms_below_adaptive_noise_floor";
        if(f.crest<=1.35)return "crest_too_low";
        if(f.hf<=.012)return "insufficient_high_frequency_energy";
        if(f.zcr<=.004)return "insufficient_zero_crossing_rate";
        return "unknown";
    }
    private void registerPattern(long now){
        synchronized(recentSnaps){while(!recentSnaps.isEmpty()&&now-recentSnaps.peekFirst()>PATTERN_WINDOW_MS)recentSnaps.removeFirst();recentSnaps.addLast(now);}
        patternCount=Math.min(4,patternCount+1);persistPattern();final int count=patternCount;listener.onDiagnostic("SNAP_COUNT:"+count+"/4");
        if(count==3){emergencyOnly=true;persistEmergency(true);listener.onDiagnostic("SNAP_PATTERN_TRIPLE_EMERGENCY_ARMED");return;}
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(!running||patternCount!=count)return;patternCount=0;persistPattern();if(count==1)listener.onSnap();else if(count==2)executeConfiguredPattern("double");else if(count==4)executeConfiguredPattern("quad");},900);
    }
    private void persistPattern(){context.getSharedPreferences(PREF,0).edit().putInt("pattern_count",patternCount).apply();}
    private void persistEmergency(boolean value){context.getSharedPreferences(PREF,0).edit().putBoolean("emergency_armed",value).apply();}
    private void executeConfiguredPattern(String type){SharedPreferences p=context.getSharedPreferences("axtor_sound",0);String fallback=type.equals("double")?"volume down":"open notification settings";String action=p.getString(type+"_action",fallback).trim();if(action.isEmpty())return;String policy=AxtorCommandSecurityPolicy.authorizeVoice(context,action);if(!"OK".equals(policy)){listener.onDiagnostic("SNAP_COMMAND_BLOCKED:"+policy);return;}String result=DeviceAutomation.execute(context,action);p.edit().putString("last_trigger",type+":"+action).putString("last_result",result==null?"unsupported":result).apply();if(result==null)listener.onDiagnostic("SNAP_COMMAND_UNSUPPORTED:"+type);else listener.onDiagnostic("SNAP_COMMAND_EXECUTED:"+type+":"+action);}

    public static boolean enroll(Context c){
        setEnrollmentProgress(c,0);SharedPreferences p=c.getSharedPreferences(PREF,0);p.edit().putString("last_enrollment_diagnostic","SNAP_ENROLL_STARTING").putInt("snap_candidates",0).apply();
        if(c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=0){setEnrollmentDiagnostic(c,"SNAP_ENROLL_MIC_PERMISSION_MISSING");return false;}
        int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0){setEnrollmentDiagnostic(c,"SNAP_ENROLL_AUDIO_UNAVAILABLE");return false;}
        AudioRecord r=null;
        try{
            r=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*4));
            if(r.getState()!=AudioRecord.STATE_INITIALIZED){setEnrollmentDiagnostic(c,"SNAP_ENROLL_AUDIO_INIT_FAILED");return false;}
            r.startRecording();short[] rolling=new short[FRAME],chunk=new short[HOP];final int calibrationFrames=40;int filled=0,frames=0,candidates=0;double noiseRms=.001,noisePeak=.006;List<Candidate> best=new ArrayList<>();long end=System.currentTimeMillis()+25000;
            setEnrollmentDiagnostic(c,"SNAP_ENROLL_CALIBRATING_NOISE:hold still and stay quiet");
            while(System.currentTimeMillis()<end&&best.size()<3){
                int n=r.read(chunk,0,chunk.length);if(n!=HOP)continue;System.arraycopy(rolling,HOP,rolling,0,FRAME-HOP);System.arraycopy(chunk,0,rolling,FRAME-HOP,HOP);filled+=HOP;if(filled<FRAME)continue;frames++;
                Features f=features(rolling,FRAME,noisePeak);
                if(frames<=calibrationFrames){noiseRms=.90*noiseRms+.10*Math.max(.0005,f.rms);noisePeak=.90*noisePeak+.10*Math.max(.006,f.peak);p.edit().putFloat("noise_rms",(float)noiseRms).putFloat("noise_peak",(float)noisePeak).apply();if(frames==calibrationFrames)setEnrollmentDiagnostic(c,"SNAP_ENROLL_LISTENING_NOISE_RMS:"+round(noiseRms)+"_PEAK:"+round(noisePeak)+"; now make 3 distinct snaps");continue;}
                double quietRms=noiseRms*1.30,quietPeak=noisePeak*1.50;if(f.rms<quietRms&&f.peak<quietPeak){noiseRms=.98*noiseRms+.02*Math.max(.0005,f.rms);noisePeak=.98*noisePeak+.02*Math.max(.006,f.peak);}
                // Enrollment intentionally has no hard duration requirement. It learns the measured transient duration.
                boolean candidate=f.peak>Math.max(.025,noisePeak*1.70)&&f.rms>Math.max(.0012,noiseRms*1.25)&&f.crest>1.25&&f.hf>.010&&f.zcr>.003;
                if(candidate){
                    candidates++;Candidate q=new Candidate(f,snapScore(f,noiseRms,noisePeak));addCandidate(best,q);setEnrollmentProgress(c,best.size());
                    p.edit().putInt("snap_candidates",candidates).putFloat("last_duration_ms",(float)f.durationMs).putFloat("last_peak_noise_ratio",(float)(f.peak/Math.max(noisePeak,.0001))).apply();
                    setEnrollmentDiagnostic(c,"SNAP_ENROLL_CANDIDATE:"+candidates+"_BEST:"+best.size()+"/3_DURATION_MS:"+round(f.durationMs)+"_PEAK_NOISE:"+round(f.peak/Math.max(noisePeak,.0001))+"_RMS:"+round(f.rms));
                    try{Thread.sleep(180);}catch(InterruptedException ignored){Thread.currentThread().interrupt();break;}
                }
            }
            if(best.size()<3){setEnrollmentDiagnostic(c,"SNAP_ENROLL_NOT_ENOUGH_SNAP_SAMPLES:"+best.size()+"/3_CANDIDATES:"+candidates+"_FRAMES:"+frames+"_NOISE_RMS:"+round(noiseRms)+"_NOISE_PEAK:"+round(noisePeak));return false;}
            double[] mean=new double[7],std=new double[7];for(Candidate q:best){double[] v=q.features.vector();for(int i=0;i<7;i++)mean[i]+=v[i];}for(int i=0;i<7;i++)mean[i]/=best.size();for(Candidate q:best){double[] v=q.features.vector();for(int i=0;i<7;i++){double d=v[i]-mean[i];std[i]+=d*d;}}for(int i=0;i<7;i++){std[i]=Math.sqrt(std[i]/Math.max(1,best.size()-1));std[i]=Math.max(std[i],Math.abs(mean[i])*.08+.015);}
            String template=serialize(mean),profile=serialize(std);p.edit().putString(TEMPLATE_V2,template).putString("profile_std_v2",profile).putFloat("threshold",.64f).putInt("pattern_count",0).putBoolean("emergency_armed",false).putFloat("noise_rms",(float)noiseRms).putFloat("noise_peak",(float)noisePeak).putInt("snap_candidates",candidates).putInt("snap_samples_captured",3).putString("last_decision","ENROLLMENT_SUCCESS").putString("last_reject_reason","none").apply();setEnrollmentProgress(c,3);setEnrollmentDiagnostic(c,"SNAP_ENROLL_SUCCESS:3/3_CANDIDATES:"+candidates+"_ADAPTIVE_PROFILE_SAVED");return true;
        }catch(Throwable t){setEnrollmentDiagnostic(c,"SNAP_ENROLL_ERROR:"+t.getClass().getSimpleName()+":"+String.valueOf(t.getMessage()));return false;}
        finally{if(r!=null){try{r.stop();}catch(Exception ignored){}try{r.release();}catch(Exception ignored){}}}
    }
    private static double snapScore(Features f,double noiseRms,double noisePeak){double peakRatio=f.peak/Math.max(noisePeak,.004),rmsRatio=f.rms/Math.max(noiseRms,.0008);return Math.min(8,peakRatio)+Math.min(6,rmsRatio)+f.crest+f.hf*4+f.zcr*10;}
    private static void addCandidate(List<Candidate> list,Candidate candidate){list.add(candidate);list.sort((a,b)->Double.compare(b.score,a.score));while(list.size()>3)list.remove(list.size()-1);}
    private static String round(double v){return String.format(Locale.US,"%.4f",v);}
    private static String serialize(double[] a){StringBuilder s=new StringBuilder();for(int i=0;i<a.length;i++){if(i>0)s.append(',');s.append(a[i]);}return s.toString();}
    private static double[] parse(String raw){String[] a=raw.split(",");double[] out=new double[a.length];for(int i=0;i<a.length;i++)out[i]=Double.parseDouble(a[i]);return out;}
    private static final class Candidate{final Features features;final double score;Candidate(Features f,double s){features=f;score=s;}}
    private static void setEnrollmentDiagnostic(Context c,String value){c.getSharedPreferences(PREF,0).edit().putString("last_enrollment_diagnostic",value).apply();}
    public static String enrollmentDiagnostic(Context c){return c.getSharedPreferences(PREF,0).getString("last_enrollment_diagnostic","SNAP_ENROLL_NOT_RUN");}
    public static void clearEnrollment(Context c){c.getSharedPreferences(PREF,0).edit().clear().apply();}

    private boolean matchesTemplate(Features f){
        SharedPreferences p=context.getSharedPreferences(PREF,0);String raw=p.getString(TEMPLATE_V2,"");
        try{
            if(!raw.isEmpty()){
                double[] t=parse(raw);double[] std=parse(p.getString("profile_std_v2",""));double similarity=profileSimilarity(f.vector(),t,std);p.edit().putFloat("last_similarity",(float)similarity).apply();return similarity>=p.getFloat("threshold",.64f);
            }
            raw=p.getString(TEMPLATE,"");if(raw.isEmpty())return false;double[] t=parse(raw);double similarity=cosineSimilarity(f.vectorLegacy(),t);p.edit().putFloat("last_similarity",(float)similarity).apply();return similarity>=p.getFloat("threshold",.35f);
        }catch(Exception e){p.edit().putString("last_reject_reason","template_parse_error").putString("last_detector_error",e.toString()).apply();return false;}
    }
    private static double profileSimilarity(double[] v,double[] mean,double[] std){if(v.length!=mean.length||std.length!=mean.length)return 0;double sum=0;for(int i=0;i<v.length;i++){double z=Math.abs(v[i]-mean[i])/Math.max(std[i],.0001);sum+=Math.min(4,z);}return Math.max(0,1.0-sum/(v.length*3.0));}
    private static double cosineSimilarity(double[] a,double[] b){if(a.length!=b.length)return 0;double dot=0,aa=0,bb=0;for(int i=0;i<a.length;i++){dot+=a[i]*b[i];aa+=a[i]*a[i];bb+=b[i]*b[i];}if(aa==0||bb==0)return 0;return dot/(Math.sqrt(aa)*Math.sqrt(bb));}

    private static Features features(short[] x,int n,double noisePeak){
        double sum=0,peak=0;int z=0;double diffEnergy=0;int peakIndex=0;
        for(int i=0;i<n;i++){double v=Math.abs(x[i])/32768.0;sum+=v*v;if(v>peak){peak=v;peakIndex=i;}if(i>0){if((x[i]>=0)!=(x[i-1]>=0))z++;double d=(x[i]-x[i-1])/32768.0;diffEnergy+=d*d;}}
        double rms=Math.sqrt(sum/Math.max(1,n));double hf=diffEnergy/Math.max(4*sum,.0000001);double crest=peak/Math.max(rms,.0001);
        double envelopeFloor=Math.max(noisePeak*1.18,peak*.20);int left=peakIndex,right=peakIndex;while(left>0&&Math.abs(x[left])/32768.0>=envelopeFloor)left--;while(right<n-1&&Math.abs(x[right])/32768.0>=envelopeFloor)right++;double durationMs=(right-left+1)*1000.0/RATE;
        return new Features(peak,crest,Math.min(1,hf),z/(double)n,durationMs,rms);
    }
    private static final class Features{
        final double peak,crest,hf,zcr,durationMs,rms;Features(double p,double c,double h,double z,double d,double r){peak=p;crest=c;hf=h;zcr=z;durationMs=d;rms=r;}
        double[] vector(){return new double[]{peak,crest/10.0,hf,zcr*10.0,durationMs/100.0,rms*10.0,peak/Math.max(rms,.0001)};}
        double[] vectorLegacy(){return new double[]{peak,crest/10.0,hf,zcr*10.0,durationMs/100.0,rms*10.0};}
    }
    static final class ExtendedRangeState{static volatile boolean enabled=true;}
}
