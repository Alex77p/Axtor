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
  boolean running=true;
  final String[] wakeWords={"hey myai","hey my ai","myai","my ai","hey axtor","axtor"};

  @Override public void onCreate(){
    super.onCreate();
    VoiceServiceState.setRunning(true);
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    if(Build.VERSION.SDK_INT>=26){
      NotificationChannel c=new NotificationChannel("voice","MyAiAgent Voice",NotificationManager.IMPORTANCE_LOW);
      nm.createNotificationChannel(c);
    }
    Intent stop=new Intent(this,VoiceAssistantService.class);
    stop.setAction("STOP");
    PendingIntent pi=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"voice"):new Notification.Builder(this);
    b.setContentTitle("MyAiAgent voice assistant")
      .setContentText("Listening for Hey MyAI / Axtor")
      .setSmallIcon(android.R.drawable.ic_btn_speak_now)
      .setOngoing(true)
      .addAction(new Notification.Action.Builder(null,"Stop",pi).build());
    if(Build.VERSION.SDK_INT>=29) startForeground(ID,b.build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    else startForeground(ID,b.build());
    tts=new TextToSpeech(this,this);
    startRecognition();
  }

  void startRecognition(){
    if(!running)return;
    if(checkSelfPermission("android.permission.RECORD_AUDIO")!=PackageManager.PERMISSION_GRANTED){say("Microphone permission is required.");return;}
    if(!SpeechRecognizer.isRecognitionAvailable(this)){say("Speech recognition is not available on this device.");return;}
    if(recognizer!=null){recognizer.destroy();recognizer=null;}
    recognizer=SpeechRecognizer.createSpeechRecognizer(this);
    recognizer.setRecognitionListener(this);
    recognizerIntent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
    try{recognizer.startListening(recognizerIntent);}catch(Exception e){
      new Handler(Looper.getMainLooper()).postDelayed(this::startRecognition,1500);
    }
  }

  String extractCommand(String q){
    String l=q.toLowerCase(Locale.ROOT).trim();
    for(String w:wakeWords){
      int i=l.indexOf(w);
      if(i>=0)return q.substring(i+w.length()).trim();
    }
    return null;
  }

  void command(String q){
    String cmd=extractCommand(q);
    if(cmd==null||cmd.isEmpty()){say("Yes?");return;}
    String automation=DeviceAutomation.execute(this,cmd);
    if(automation!=null){say(automation);return;}
    try{
      String path=AppCore.activeModel(this);
      if(path.isEmpty()){say(AppCore.answer(cmd));return;}
      say("Thinking locally.");
      LlamaRuntime.generate(this,path,cmd,
        "You are MyAiAgent, a private offline Android voice assistant created by Ayush Debbarma. Answer briefly. Do not claim internet access.",
        192,new LlamaRuntime.Callback(){
          public void onSuccess(String text,double tps){say(text);}
          public void onError(String m){say("Local model error: "+m);}
        });
    }catch(Exception e){say("I couldn't perform that action: "+e.getMessage());}
  }

  void say(String s){
    if(tts!=null&&s!=null&&!s.isEmpty())tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"myai");
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
    if(tts!=null){tts.stop();tts.shutdown();}
    VoiceServiceState.setRunning(false);
    super.onDestroy();
  }

  public android.os.IBinder onBind(Intent i){return null;}
  public void onInit(int status){}

  public void onResults(Bundle r){
    ArrayList<String> x=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    if(x!=null){
      for(String candidate:x){
        if(extractCommand(candidate)!=null){command(candidate);break;}
      }
    }
    new Handler(Looper.getMainLooper()).postDelayed(this::startRecognition,900);
  }

  public void onError(int e){
    new Handler(Looper.getMainLooper()).postDelayed(this::startRecognition,1200);
  }
  public void onReadyForSpeech(Bundle b){}
  public void onBeginningOfSpeech(){}
  public void onRmsChanged(float v){}
  public void onBufferReceived(byte[] b){}
  public void onEndOfSpeech(){}
  public void onPartialResults(Bundle b){}
  public void onEvent(int a,Bundle b){}
}
