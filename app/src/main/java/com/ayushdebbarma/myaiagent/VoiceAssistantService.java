package com.ayushdebbarma.myaiagent;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.*;

/** Foreground voice-command service. No calling phrase. Optional personalized snap patterns can execute safe commands directly. */
public class VoiceAssistantService extends Service implements RecognitionListener, TextToSpeech.OnInitListener {
  static final int ID=71;
  private static final String PREF="axtor_voice";
  SpeechRecognizer recognizer; Intent recognizerIntent; TextToSpeech tts; SnapTriggerEngine snapEngine;
  boolean running=false,ttsReady=false;
  final Handler handler=new Handler(Looper.getMainLooper());
  boolean restartScheduled=false,usingOnDevice=false,snapListening=false; int consecutiveErrors=0;

  @Override public void onCreate(){
    super.onCreate(); running=true; VoiceServiceState.setRunning(true);
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(new NotificationChannel("voice","Axtor Voice",NotificationManager.IMPORTANCE_LOW));
    Intent stop=new Intent(this,VoiceAssistantService.class); stop.setAction("STOP");
    PendingIntent pi=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"voice"):new Notification.Builder(this);
    b.setContentTitle("Axtor voice commands").setContentText("Listening for commands").setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true)
      .addAction(new Notification.Action.Builder(null,"Stop",pi).build());
    if(Build.VERSION.SDK_INT>=29) startForeground(ID,b.build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE); else startForeground(ID,b.build());
    tts=new TextToSpeech(this,this);
    tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
      public void onStart(String id){}
      public void onDone(String id){if(running&&continuousListening())resumeListeningAfterSpeech(250);}
      public void onError(String id){if(running&&continuousListening())resumeListeningAfterSpeech(250);}
    });
    if(snapPatternMode()){startSnapMode();}else startRecognition();
  }

  private boolean continuousListening(){return getSharedPreferences(PREF,0).getBoolean("continuous_listening",true);}
  private boolean snapPatternMode(){return getSharedPreferences(PREF,0).getBoolean("snap_command_patterns_enabled",false)&&SnapTriggerEngine.isEnrolled(this);}
  private void startSnapMode(){
    if(!running||snapEngine!=null)return;
    snapListening=false;
    pauseRecognition();
    snapEngine=new SnapTriggerEngine(this,new SnapTriggerEngine.Listener(){
      public void onSnap(){
        // Single personalized snap is the hands-free listen trigger. Hand the microphone to SpeechRecognizer.
        stopSnapMode();
        snapListening=true;
        startRecognition(true);
      }
      public void onDiagnostic(String message){rememberVoiceError(message);}
    });
    snapEngine.start();
  }
  private void stopSnapMode(){if(snapEngine!=null){snapEngine.stop();snapEngine=null;}}
  private void resumeListeningAfterSpeech(long delay){
    if(!running||!continuousListening())return;
    handler.postDelayed(()->{
      if(!running||!continuousListening())return;
      if(snapPatternMode()){snapListening=false;startSnapMode();}
      else startRecognition();
    },delay);
  }

  void startRecognition(){startRecognition(false);}
  private void startRecognition(boolean fromSnap){
    if(!running||restartScheduled||(!fromSnap&&snapPatternMode()))return;
    restartScheduled=false;
    if(checkSelfPermission("android.permission.RECORD_AUDIO")!=PackageManager.PERMISSION_GRANTED){rememberVoiceError("MIC_PERMISSION_MISSING");stopSelf();return;}
    if(!SpeechRecognizer.isRecognitionAvailable(this)){rememberVoiceError("SPEECH_RECOGNIZER_UNAVAILABLE");stopSelf();return;}
    if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){} recognizer=null;}
    if(Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){
      recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this); usingOnDevice=true;
    }else{
      recognizer=SpeechRecognizer.createSpeechRecognizer(this); usingOnDevice=false;
    }
    recognizer.setRecognitionListener(this);
    recognizerIntent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
    try{recognizer.startListening(recognizerIntent);}catch(Exception e){rememberVoiceError("RECOGNIZER_START_FAILED:"+e.getClass().getSimpleName());scheduleRecognitionRestart(1200);}
  }

  void command(String q){
    String cmd=VoiceCommandManager.normalize(q);
    if(cmd.isEmpty()){resumeListeningAfterSpeech(250);return;}
    String lower=cmd.toLowerCase(Locale.ROOT);
    if(lower.equals("enable snap commands")||lower.equals("enable snap command mode")){
      if(!SnapTriggerEngine.isEnrolled(this)){say("Enroll your personal snap first.");return;}
      getSharedPreferences(PREF,0).edit().putBoolean("snap_command_patterns_enabled",true).apply();
      say("Snap command patterns enabled.");
      return;
    }
    if(lower.equals("disable snap commands")||lower.equals("disable snap command mode")){
      getSharedPreferences(PREF,0).edit().putBoolean("snap_command_patterns_enabled",false).apply();
      snapListening=false; stopSnapMode(); startRecognition(); say("Snap command patterns disabled.");
      return;
    }
    String policy=AxtorCommandSecurityPolicy.authorizeVoice(this,cmd);
    if(!"OK".equals(policy)){rememberVoiceError("VOICE_BLOCKED:"+policy);say("That command is blocked by Axtor security policy.");return;}
    getSharedPreferences("axtor",0).edit().putBoolean("voice_last_command_ok",true).putString("voice_last_route","axtor-agent").apply();
    pauseRecognition();
    AxtorAgent.handle(this,cmd,new AxtorAgent.Callback(){
      public void onReply(String text){sayResponse(text);}
      public void onError(String message){rememberVoiceError("AGENT_ERROR:"+(message==null?"unknown":message));say("Command failed: "+(message==null?"unknown error":message));}
    });
  }

  private void rememberVoiceError(String value){getSharedPreferences("axtor",0).edit().putString("voice_last_error",value).apply();}
  private void pauseRecognition(){restartScheduled=false;if(recognizer!=null){try{recognizer.cancel();}catch(Exception ignored){}}}
  void scheduleRecognitionRestart(long delay){
    if(!running||!continuousListening()||restartScheduled||snapPatternMode())return;
    restartScheduled=true;handler.postDelayed(this::startRecognition,delay);
  }

  void say(String s){
    if(ttsReady&&tts!=null&&s!=null&&!s.isEmpty()){pauseRecognition();tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"axtor-"+System.nanoTime());}
    else resumeListeningAfterSpeech(500);
  }

  void sayResponse(String text){
    if(!ttsReady||tts==null||text==null||text.trim().isEmpty()){resumeListeningAfterSpeech(250);return;}
    pauseRecognition();
    String[] parts=text.trim().split("(?<=[.!?])\\s+");
    String last="axtor-last-"+System.nanoTime();
    for(int i=0;i<parts.length;i++){
      String sentence=parts[i].trim(); if(!sentence.isEmpty()) tts.speak(sentence,TextToSpeech.QUEUE_ADD,null,i==parts.length-1?last:"axtor-"+System.nanoTime());
    }
  }

  public int onStartCommand(Intent i,int f,int s){
    if(i!=null&&"STOP".equals(i.getAction())){
      getSharedPreferences("axtor",0).edit().putBoolean("voice_enabled",false).apply();
      getSharedPreferences(PREF,0).edit().putBoolean("continuous_listening",false).apply();
      stopSelf(); return START_NOT_STICKY;
    }
    running=true; VoiceServiceState.setRunning(true); if(snapPatternMode()){startSnapMode();}else if(recognizer==null)startRecognition(); return START_STICKY;
  }

  public void onDestroy(){
    running=false; snapListening=false; stopSnapMode(); if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}} recognizer=null;
    ttsReady=false; handler.removeCallbacksAndMessages(null); if(tts!=null){tts.stop();tts.shutdown();}
    VoiceServiceState.setRunning(false); super.onDestroy();
  }
  public android.os.IBinder onBind(Intent i){return null;}
  public void onInit(int status){ttsReady=status==TextToSpeech.SUCCESS;if(!ttsReady)rememberVoiceError("TTS_UNAVAILABLE");}
  public void onResults(Bundle r){
    consecutiveErrors=0;
    ArrayList<String>x=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    if(x!=null){for(String candidate:x){if(candidate!=null&&!candidate.trim().isEmpty()){snapListening=false;command(candidate);return;}}}
    resumeListeningAfterSpeech(250);
  }
  public void onError(int e){
    if(!running)return; consecutiveErrors++; rememberVoiceError("SPEECH_ERROR_"+e);
    if(snapListening){snapListening=false;if(snapPatternMode())startSnapMode();else if(continuousListening())scheduleRecognitionRestart(Math.min(5000L,500L+consecutiveErrors*300L));return;}
    if(!continuousListening()||snapPatternMode()){if(snapPatternMode())startSnapMode();else stopSelf();return;}
    scheduleRecognitionRestart(Math.min(5000L,500L+consecutiveErrors*300L));
  }
  public void onReadyForSpeech(Bundle b){} public void onBeginningOfSpeech(){} public void onRmsChanged(float v){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){} public void onPartialResults(Bundle b){} public void onEvent(int a,Bundle b){}
}
