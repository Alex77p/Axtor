package com.ayushdebbarma.myaiagent;

import android.Manifest;import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.graphics.Color;import android.os.*;import android.view.Gravity;import android.widget.*;

/** Snap setup plus a dedicated live diagnostics tab. */
public class SoundTriggerActivity extends Activity{
 LinearLayout content; TextView status; Handler handler=new Handler(Looper.getMainLooper()); boolean diagnosticsTab=false;
 TextView tv(String s,float z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.rgb(32,33,35));t.setPadding(16,10,16,10);return t;}
 Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
 @Override public void onCreate(Bundle b){super.onCreate(b);build();if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);}
 void build(){
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);
  LinearLayout header=new LinearLayout(this);header.setPadding(10,8,10,8);header.setGravity(Gravity.CENTER_VERTICAL);TextView back=tv("‹",32);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(48,58));TextView title=tv("Snap trigger",20);title.setTypeface(null,1);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
  LinearLayout tabs=new LinearLayout(this);tabs.setPadding(8,0,8,4);Button setup=btn("Setup");Button diag=btn("Diagnostics");tabs.addView(setup,new LinearLayout.LayoutParams(0,-2,1));tabs.addView(diag,new LinearLayout.LayoutParams(0,-2,1));root.addView(tabs);
  setup.setOnClickListener(v->{diagnosticsTab=false;render();});diag.setOnClickListener(v->{diagnosticsTab=true;render();});
  ScrollView sc=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(12,12,12,24);sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);render();
 }
 void render(){content.removeAllViews();if(diagnosticsTab){renderDiagnostics();}else{renderSetup();}}
 void renderSetup(){
  android.content.SharedPreferences p=getSharedPreferences("axtor_voice",0);boolean enabled=p.getBoolean("snap_trigger_enabled",true),enrolled=SnapTriggerEngine.isEnrolled(this),extended=SnapTriggerEngine.isExtendedRangeEnabled(this);
  content.addView(tv("Personal snap",28));content.addView(tv(enrolled?"✓ Your adaptive snap profile is enrolled on this device":"Set up your personal snap to use hands-free wake",15));
  status=tv(SnapTriggerEngine.liveStatus(this),14);content.addView(status);
  Switch sw=new Switch(this);sw.setText("Require my enrolled snap");sw.setTextSize(16);sw.setChecked(enabled);sw.setOnCheckedChangeListener((v,on)->{p.edit().putBoolean("snap_trigger_enabled",on).apply();if(on)startVoice();});content.addView(sw);
  Switch range=new Switch(this);range.setText("Extended-range snap detection");range.setTextSize(16);range.setChecked(extended);range.setOnCheckedChangeListener((v,on)->SnapTriggerEngine.setExtendedRangeEnabled(this,on));content.addView(range);content.addView(tv("The detector adapts its energy thresholds to ambient noise. Enrollment learns your actual transient duration and acoustic profile instead of requiring a fixed snap duration.",13));
  Button enroll=btn(enrolled?"↻ Re-learn my snap":"Learn my snap");enroll.setOnClickListener(v->enroll());content.addView(enroll);
  Button clear=btn("Forget enrolled snap");clear.setOnClickListener(v->{SnapTriggerEngine.clearEnrollment(this);Toast.makeText(this,"Personal snap forgotten.",Toast.LENGTH_SHORT).show();render();});content.addView(clear);
  Button start=btn("Start hands-free mode");start.setOnClickListener(v->startVoice());content.addView(start);Button stop=btn("Stop hands-free mode");stop.setOnClickListener(v->{p.edit().putBoolean("continuous_listening",false).apply();stopService(new Intent(this,VoiceAssistantService.class));Toast.makeText(this,"Hands-free mode stopped.",Toast.LENGTH_SHORT).show();});content.addView(stop);
  content.addView(tv("How it works\n• Ambient noise is calibrated locally before enrollment.\n• Three distinct snap candidates are measured for peak, RMS, crest factor, high-frequency energy, zero-crossing rate, peak/noise ratio and measured transient duration.\n• A compact adaptive profile is stored locally; no snap recordings are uploaded.\n• One matching snap starts voice interaction. Three matching snaps within about 2.2 seconds arm the existing emergency-stop path.",14));
  content.addView(tv("Privacy & security\nThe stored profile is acoustic personalization, not a cryptographic biometric. It should not be treated as proof of identity. Commands still pass through Axtor's command authorization policy.",13));
  Button back=btn("Back to chat");back.setOnClickListener(v->finish());content.addView(back);
 }
 void renderDiagnostics(){
  content.addView(tv("Snap diagnostics",28));content.addView(tv("Live microphone, enrollment and detector state. Refreshes twice per second while this tab is open.",14));
  TextView live=tv("",13);live.setTypeface(null,0);content.addView(live);
  Button enroll=btn("Re-enroll / reset profile");enroll.setOnClickListener(v->enroll());content.addView(enroll);
  Button clear=btn("Clear enrollment and diagnostic history");clear.setOnClickListener(v->{SnapTriggerEngine.clearEnrollment(this);Toast.makeText(this,"Snap profile cleared.",Toast.LENGTH_SHORT).show();render();});content.addView(clear);
  Button back=btn("Back to setup");back.setOnClickListener(v->{diagnosticsTab=false;render();});content.addView(back);
  Runnable refresh=new Runnable(){public void run(){if(!diagnosticsTab)return;live.setText(SnapTriggerEngine.diagnosticsSummary(SoundTriggerActivity.this));handler.postDelayed(this,500);}};handler.post(refresh);
 }
 void enroll(){
  if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);return;}
  stopService(new Intent(this,VoiceAssistantService.class));diagnosticsTab=true;render();Toast.makeText(this,"Stay in your normal environment. After calibration, make 3 distinct snaps.",Toast.LENGTH_LONG).show();
  new Thread(()->{boolean ok=SnapTriggerEngine.enroll(this);runOnUiThread(()->{Toast.makeText(this,ok?"Adaptive snap profile saved on this device.":"Enrollment failed. Open Diagnostics to see the exact reason.",Toast.LENGTH_LONG).show();render();});},"AxtorManualSnapEnrollment").start();
 }
 void startVoice(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);return;}p().edit().putBoolean("continuous_listening",true).putBoolean("snap_trigger_enabled",true).apply();Intent i=new Intent(this,VoiceAssistantService.class);try{if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);Toast.makeText(this,SnapTriggerEngine.isEnrolled(this)?"Hands-free snap mode started.":"Voice started; follow the enrollment prompt.",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Android blocked the microphone service: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
 android.content.SharedPreferences p(){return getSharedPreferences("axtor_voice",0);}
 @Override protected void onResume(){super.onResume();if(content!=null)render();}
 @Override protected void onPause(){super.onPause();handler.removeCallbacksAndMessages(null);}
}
