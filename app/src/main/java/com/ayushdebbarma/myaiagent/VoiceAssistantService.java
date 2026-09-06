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

/** Foreground, restart-tolerant hands-free voice service. */
public class VoiceAssistantService extends Service implements RecognitionListener, TextToSpeech.OnInitListener {
  static final int ID=71;
  SpeechRecognizer recognizer; Intent recognizerIntent; TextToSpeech tts;
  boolean running=false,ttsReady=false,awaitingSnapCommand=false;
  final Handler handler=new Handler(Looper.getMainLooper());
  boolean restartScheduled=false,onlineFallback=false,usingOnDevice=false; int consecutiveErrors=0;
  SnapTriggerEngine snapDetector;

  @Override public void onCreate(){
    super.onCreate();running=true;VoiceServiceState.setRunning(true);
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("voice","Axtor Voice",NotificationManager.IMPORTANCE_LOW));
    Intent stop=new Intent(this,VoiceAssistantService.class);stop.setAction("STOP");
    PendingIntent pi=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"voice"):new Notification.Builder(this);
    b.setContentTitle("Axtor voice assistant").setContentText(handsFreeModeText()).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).addAction(new Notification.Action.Builder(null,"Stop",pi).build());
    if(Build.VERSION.SDK_INT>=29)startForeground(ID,b.build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);else startForeground(ID,b.build());
    tts=new TextToSpeech(this,this);tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){}public void onDone(String id){if(running&&continuousListening())armHandsFree();}public void onError(String id){if(running&&continuousListening())armHandsFree();}});
    armHandsFree();
  }
  private boolean continuousListening(){return getSharedPreferences("axtor_voice",0).getBoolean("continuous_listening",true);}
  private boolean snapMode(){return getSharedPreferences("axtor_voice",0).getBoolean("snap_trigger_enabled",true);}
  private String handsFreeModeText(){return snapMode()?"Hands-free: personalized snap trigger":"Hands-free: calling phrase";}

  private void armHandsFree(){
    if(!running||!continuousListening())return;
    awaitingSnapCommand=false;
    if(snapMode()){
      pauseRecognition();
      if(snapDetector==null)snapDetector=new SnapTriggerEngine(this,new SnapTriggerEngine.Listener(){
        public void onSnap(){
          if(!running)return;
          awaitingSnapCommand=true;
          getSharedPreferences("axtor",0).edit().putString("voice_last_trigger","personalized_snap").apply();
          if(snapDetector!=null)snapDetector.stop();
          handler.postDelayed(()->startRecognition(),120);
        }
        public void onDiagnostic(String m){rememberVoiceError(m);}
      });
      if(!SnapTriggerEngine.isEnrolled(this)){rememberVoiceError("SNAP_NOT_ENROLLED");return;}
      snapDetector.start();
    }else startRecognition();
  }
  private void pauseRecognition(){restartScheduled=false;if(recognizer!=null){try{recognizer.cancel();}catch(Exception ignored){}}}

  void startRecognition(){
    if(!running||restartScheduled)return;restartScheduled=false;
    if(checkSelfPermission("android.permission.RECORD_AUDIO")!=PackageManager.PERMISSION_GRANTED){rememberVoiceError("MIC_PERMISSION_MISSING");stopSelf();return;}
    if(!SpeechRecognizer.isRecognitionAvailable(this)){rememberVoiceError("SPEECH_RECOGNIZER_UNAVAILABLE");stopSelf();return;}
    if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}recognizer=null;}
    if(Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);usingOnDevice=true;}else{recognizer=SpeechRecognizer.createSpeechRecognizer(this);usingOnDevice=false;}
    recognizer.setRecognitionListener(this);recognizerIntent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
    boolean preferOffline=getSharedPreferences("axtor_voice",0).getBoolean("prefer_offline",true);recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,preferOffline||usingOnDevice);recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
    try{recognizer.startListening(recognizerIntent);}catch(Exception e){rememberVoiceError("RECOGNIZER_START_FAILED:"+e.getClass().getSimpleName());scheduleRecognitionRestart(1200);}
  }
  String extractCommand(String q){return VoiceCommandManager.extractCommand(this,q);}

  void command(String q){
    String cmd=awaitingSnapCommand?VoiceCommandManager.normalize(q):extractCommand(q);
    awaitingSnapCommand=false;
    if(cmd==null||cmd.isEmpty()){if(!snapMode())say("Say your calling phrase followed by a command.");else say("I did not hear a command.");return;}
    getSharedPreferences("axtor",0).edit().putBoolean("voice_last_command_ok",true).apply();pauseRecognition();
    String searchQuery=WebSearchProtocol.extractQuery(cmd);
    if(searchQuery!=null){String result=WebSearchProtocol.search(this,searchQuery);getSharedPreferences("axtor",0).edit().putBoolean("voice_last_command_ok",!result.contains("failed")).putString("voice_last_route","web-search").putString("voice_last_error",result.contains("failed")?result:"").apply();sayResponse(result);return;}
    getSharedPreferences("axtor",0).edit().putString("voice_last_route","axtor-agent").apply();
    AxtorAgent.handle(this,cmd,new AxtorAgent.Callback(){public void onReply(String text){sayResponse(text);}public void onError(String message){rememberVoiceError("AGENT_ERROR:"+(message==null?"unknown":message));say("Command failed. Main cause: "+(message==null?"unknown error":message));}});
  }
  private void rememberVoiceError(String value){getSharedPreferences("axtor",0).edit().putString("voice_last_error",value).apply();}
  void say(String s){if(ttsReady&&tts!=null&&s!=null&&!s.isEmpty()){pauseRecognition();if(snapDetector!=null)snapDetector.stop();tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"axtor-"+System.nanoTime());}else if(running&&continuousListening())scheduleRecognitionRestart(500);}
  void scheduleRecognitionRestart(long delay){if(!running||!continuousListening()||restartScheduled)return;restartScheduled=true;handler.postDelayed(this::startRecognition,delay);}
  void sayResponse(String text){if(!ttsReady||tts==null||text==null||text.trim().isEmpty()){armHandsFree();return;}pauseRecognition();if(snapDetector!=null)snapDetector.stop();String[] parts=text.trim().split("(?<=[.!?])\\s+");String last="axtor-last-"+System.nanoTime();for(int i=0;i<parts.length;i++){String sentence=parts[i].trim();if(!sentence.isEmpty())tts.speak(sentence,TextToSpeech.QUEUE_ADD,null,i==parts.length-1?last:"axtor-"+System.nanoTime());}}
  public int onStartCommand(Intent i,int f,int s){if(i!=null&&"STOP".equals(i.getAction())){getSharedPreferences("axtor",0).edit().putBoolean("voice_enabled",false).apply();getSharedPreferences("axtor_voice",0).edit().putBoolean("continuous_listening",false).apply();stopSelf();return START_NOT_STICKY;}running=true;VoiceServiceState.setRunning(true);if(recognizer==null&&snapDetector==null)armHandsFree();return START_STICKY;}
  public void onDestroy(){running=false;if(snapDetector!=null){snapDetector.stop();snapDetector=null;}if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}}recognizer=null;ttsReady=false;handler.removeCallbacksAndMessages(null);if(tts!=null){tts.stop();tts.shutdown();}VoiceServiceState.setRunning(false);super.onDestroy();}
  public android.os.IBinder onBind(Intent i){return null;}
  public void onInit(int status){ttsReady=status==TextToSpeech.SUCCESS;if(!ttsReady)rememberVoiceError("TTS_UNAVAILABLE");}
  public void onResults(Bundle r){consecutiveErrors=0;if(onlineFallback){onlineFallback=false;getSharedPreferences("axtor_voice",0).edit().putBoolean("prefer_offline",true).apply();}ArrayList<String>x=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(x!=null){for(String candidate:x){if(awaitingSnapCommand){command(candidate);return;}if(extractCommand(candidate)!=null){command(candidate);return;}}}armHandsFree();}
  public void onError(int e){if(!running)return;consecutiveErrors++;rememberVoiceError("SPEECH_ERROR_"+e);if(!continuousListening()){stopSelf();return;}if(snapMode()){awaitingSnapCommand=false;armHandsFree();return;}if(getSharedPreferences("axtor_voice",0).getBoolean("prefer_offline",true)&&!onlineFallback){onlineFallback=true;getSharedPreferences("axtor_voice",0).edit().putBoolean("prefer_offline",false).apply();scheduleRecognitionRestart(300);return;}onlineFallback=false;getSharedPreferences("axtor_voice",0).edit().putBoolean("prefer_offline",true).apply();scheduleRecognitionRestart(Math.min(5000L,800L+consecutiveErrors*300L));}
  public void onReadyForSpeech(Bundle b){}public void onBeginningOfSpeech(){}public void onRmsChanged(float v){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){}public void onPartialResults(Bundle b){}public void onEvent(int a,Bundle b){}
}
