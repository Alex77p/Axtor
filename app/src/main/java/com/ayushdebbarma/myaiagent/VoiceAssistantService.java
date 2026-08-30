package com.ayushdebbarma.myaiagent;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import java.util.*;

public class VoiceAssistantService extends Service implements RecognitionListener, TextToSpeech.OnInitListener {
  static final int ID=71;
  SpeechRecognizer recognizer;
  Intent recognizerIntent;
  TextToSpeech tts;
  boolean running=false;
  boolean ttsReady=false;
  final Handler handler=new Handler(Looper.getMainLooper());
  boolean restartScheduled=false;
  boolean onlineFallback=false;
  boolean usingOnDevice=false;

  @Override public void onCreate(){
    super.onCreate();
    running=true;
    VoiceServiceState.setRunning(true);
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    if(Build.VERSION.SDK_INT>=26){
      NotificationChannel c=new NotificationChannel("voice","Axtor Voice",NotificationManager.IMPORTANCE_LOW);
      nm.createNotificationChannel(c);
    }
    Intent stop=new Intent(this,VoiceAssistantService.class);
    stop.setAction("STOP");
    PendingIntent pi=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"voice"):new Notification.Builder(this);
    b.setContentTitle("Axtor voice assistant")
      .setContentText("Listening for your custom calling phrase")
      .setSmallIcon(android.R.drawable.ic_btn_speak_now)
      .setOngoing(true)
      .addAction(new Notification.Action.Builder(null,"Stop",pi).build());
    if(Build.VERSION.SDK_INT>=29) startForeground(ID,b.build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    else startForeground(ID,b.build());
    tts=new TextToSpeech(this,this);
    startRecognition();
  }

  void startRecognition(){
    if(!running || restartScheduled)return;
    restartScheduled=false;
    if(checkSelfPermission("android.permission.RECORD_AUDIO")!=PackageManager.PERMISSION_GRANTED){return;}
    if(!SpeechRecognizer.isRecognitionAvailable(this)){return;}
    if(recognizer!=null){recognizer.destroy();recognizer=null;}
    if(Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){
      recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
      usingOnDevice=true;
    }else{
      recognizer=SpeechRecognizer.createSpeechRecognizer(this);
      usingOnDevice=false;
    }
    recognizer.setRecognitionListener(this);
    recognizerIntent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
    boolean preferOffline=getSharedPreferences("axtor_voice",0).getBoolean("prefer_offline",true);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,preferOffline || usingOnDevice);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
    try{recognizer.startListening(recognizerIntent);}catch(Exception e){
      scheduleRecognitionRestart(1500);
    }
  }

  String extractCommand(String q){
    return VoiceCommandManager.extractCommand(this, q);
  }

  void command(String q){
    String cmd=extractCommand(q);
    if(cmd==null)return;
    if(cmd.isEmpty()){say("Say your calling phrase followed by a command.");return;}
    getSharedPreferences("axtor",0).edit().putBoolean("voice_last_command_ok",true).apply();
    String automation=DeviceAutomation.execute(this,cmd);
    if(automation!=null){say(automation);return;}
    try{
      String path=AppCore.activeModel(this);
      if(path.isEmpty()){say(AppCore.answer(cmd));return;}
      LlamaRuntime.generate(this,path,cmd,
        "You are Axtor, an offline assistant. Answer accurately in one short sentence under 15 words. No internet claims.",
        96,new LlamaRuntime.Callback(){
          public void onSuccess(String text,double tps){sayResponse(text);}
          public void onError(String m){say("Local model error: "+m);}
        });
    }catch(Exception e){say("I couldn't perform that action: "+e.getMessage());}
  }

  void say(String s){
    if(ttsReady&&tts!=null&&s!=null&&!s.isEmpty())tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"axtor");
  }
  void scheduleRecognitionRestart(long delay){
    if(!running||restartScheduled)return;
    restartScheduled=true;
    handler.postDelayed(this::startRecognition,delay);
  }

  void sayResponse(String text){
    if(!ttsReady||tts==null||text==null||text.trim().isEmpty())return;
    String[] parts=text.trim().split("(?<=[.!?])\\s+");
    for(String part:parts){
      String sentence=part.trim();
      if(!sentence.isEmpty())tts.speak(sentence,TextToSpeech.QUEUE_ADD,null,"axtor-"+System.nanoTime());
    }
  }

  public int onStartCommand(Intent i,int f,int s){
    if(i!=null&&"STOP".equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
    running=true;
    if(recognizer==null)startRecognition();
    return START_STICKY;
  }

  public void onDestroy(){
    running=false;
    if(recognizer!=null){recognizer.cancel();recognizer.destroy();}
    ttsReady=false;
    handler.removeCallbacksAndMessages(null);
    if(tts!=null){tts.stop();tts.shutdown();}
    VoiceServiceState.setRunning(false);
    super.onDestroy();
  }

  public android.os.IBinder onBind(Intent i){return null;}
  public void onInit(int status){ttsReady=(status==TextToSpeech.SUCCESS);}

  public void onResults(Bundle r){
    if(onlineFallback){
      onlineFallback=false;
      getSharedPreferences("axtor_voice",0).edit().putBoolean("prefer_offline",true).apply();
    }
    ArrayList<String> x=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    if(x!=null){
      for(String candidate:x){
        if(extractCommand(candidate)!=null){command(candidate);break;}
      }
    }
    scheduleRecognitionRestart(650);
  }

  public void onError(int e){
    if(!running)return;
    if(getSharedPreferences("axtor_voice",0).getBoolean("prefer_offline",true) && !onlineFallback){
      onlineFallback=true;
      getSharedPreferences("axtor_voice",0).edit().putBoolean("prefer_offline",false).apply();
      scheduleRecognitionRestart(300);
      return;
    }
    onlineFallback=false;
    getSharedPreferences("axtor_voice",0).edit().putBoolean("prefer_offline",true).apply();
    scheduleRecognitionRestart(800);
  }
  public void onReadyForSpeech(Bundle b){}
  public void onBeginningOfSpeech(){}
  public void onRmsChanged(float v){}
  public void onBufferReceived(byte[] b){}
  public void onEndOfSpeech(){}
  public void onPartialResults(Bundle b){}
  public void onEvent(int a,Bundle b){}
}
