package com.ayushdebbarma.myaiagent;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.Gravity;
import android.widget.*;

/** UI for the strict personalized snap wake mode. */
public class SoundTriggerActivity extends Activity {
  LinearLayout content;
  TextView tv(String s,float z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.rgb(45,24,16));t.setPadding(20,12,20,12);return t;}
  Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
  @Override public void onCreate(Bundle b){super.onCreate(b);build();if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);}
  void build(){
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(255,245,240));
    LinearLayout header=new LinearLayout(this);header.setPadding(22,18,18,14);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackgroundColor(Color.rgb(255,153,102));
    TextView title=tv("Personal Snap Trigger",22);title.setTextColor(Color.WHITE);title.setTypeface(null,1);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
    ScrollView sc=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(18,18,18,18);sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);render();
  }
  void render(){
    content.removeAllViews();android.content.SharedPreferences p=getSharedPreferences("axtor_voice",0);
    boolean enabled=p.getBoolean("snap_trigger_enabled",true);boolean enrolled=SnapTriggerEngine.isEnrolled(this);
    content.addView(tv("👏 Your personal snap wake-up",24));
    content.addView(tv("Axtor now uses a two-stage trigger. It first looks for the acoustic shape of your enrolled finger snap. Only after a valid match does it open speech recognition. Other voices, music, claps and unrelated sounds never get routed to the AI command processor.",14));
    content.addView(tv(enrolled?"✓ Personal snap pattern: enrolled":"⚠ Personal snap pattern: not enrolled",16));
    Switch sw=new Switch(this);sw.setText("Require my enrolled snap before listening");sw.setTextSize(16);sw.setChecked(enabled);sw.setOnCheckedChangeListener((v,on)->{p.edit().putBoolean("snap_trigger_enabled",on).apply();if(on)startVoice();});content.addView(sw);
    Button enroll=btn(enrolled?"🔁 Re-learn my snap":"🎙 Learn my snap");enroll.setOnClickListener(v->enroll());content.addView(enroll);
    Button clear=btn("🗑 Forget enrolled snap");clear.setOnClickListener(v->{SnapTriggerEngine.clearEnrollment(this);Toast.makeText(this,"Personal snap forgotten.",Toast.LENGTH_SHORT).show();render();});content.addView(clear);
    Button start=btn("✓ Start strict hands-free mode");start.setOnClickListener(v->startVoice());content.addView(start);
    Button stop=btn("⏹ Stop hands-free mode");stop.setOnClickListener(v->{p.edit().putBoolean("continuous_listening",false).apply();stopService(new Intent(this,VoiceAssistantService.class));Toast.makeText(this,"Hands-free mode stopped.",Toast.LENGTH_SHORT).show();});content.addView(stop);
    content.addView(tv("How it works:\n1. You enroll three of your normal snaps.\n2. Axtor stores only a compact acoustic fingerprint, not the recordings.\n3. A snap must pass both the snap-shape test and your enrolled fingerprint threshold.\n4. Then Axtor listens for one command.\n5. After the command, the microphone returns to snap-only monitoring.",14));
    content.addView(tv("Important: this is acoustic personalization, not a cryptographic biometric guarantee. A very similar snap recorded in the same acoustic environment can sometimes pass. Increase strictness in a future calibration step if needed.",13));
    Button back=btn("← Back to Axtor");back.setOnClickListener(v->finish());content.addView(back);
  }
  void enroll(){
    if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);return;}
    stopService(new Intent(this,VoiceAssistantService.class));
    Toast.makeText(this,"Snap 3 times normally during enrollment. This can take a few seconds.",Toast.LENGTH_LONG).show();
    new Thread(()->{boolean ok=SnapTriggerEngine.enroll(this);runOnUiThread(()->{Toast.makeText(this,ok?"Your snap pattern is saved on this device.":"Could not capture 3 clear snaps. Try again in a quiet room.",Toast.LENGTH_LONG).show();render();});},"AxtorManualSnapEnrollment").start();
  }
  void startVoice(){
    if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);return;}
    p().edit().putBoolean("continuous_listening",true).putBoolean("snap_trigger_enabled",true).apply();
    Intent i=new Intent(this,VoiceAssistantService.class);try{if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);Toast.makeText(this,SnapTriggerEngine.isEnrolled(this)?"Strict personalized snap mode started.":"Voice started; it will guide you through snap enrollment.",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Android blocked the microphone service: "+e.getMessage(),Toast.LENGTH_LONG).show();}
  }
  android.content.SharedPreferences p(){return getSharedPreferences("axtor_voice",0);}
  @Override protected void onResume(){super.onResume();if(content!=null)render();}
}
