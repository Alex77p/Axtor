package com.ayushdebbarma.myaiagent;

import android.Manifest; import android.app.*; import android.content.*; import android.content.pm.PackageManager; import android.graphics.Color; import android.os.*; import android.provider.Settings; import android.view.Gravity; import android.widget.*; import org.json.*; import java.io.*; import java.util.*;

public class MainActivity extends Activity {
  static final int AUDIO=10, FILE=11, EXPORT_MODELS=12, IMPORT_FLOW=13, EXPORT_FLOW=14, EXPORT_GGUF=15, IMPORT_BACKEND=16;
  LinearLayout content; String pending=""; int orange=Color.rgb(255,153,102), bg=Color.rgb(255,245,240), brown=Color.rgb(45,24,16);
  TextView tv(String s,float z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(brown);t.setPadding(20,12,20,12);return t;}
  Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
  public void onCreate(Bundle b){super.onCreate(b);build();if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO);}
  void build(){
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);
    LinearLayout header=new LinearLayout(this);header.setPadding(22,18,18,14);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackgroundColor(orange);
    LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);TextView a=tv("Axtor",24);a.setTextColor(Color.WHITE);a.setTypeface(null,1);TextView s=tv("PRIVATE · OFFLINE AI ASSISTANT",10);s.setTextColor(Color.WHITE);titles.addView(a);titles.addView(s);header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
    TextView dot=tv("● READY",11);dot.setTextColor(Color.WHITE);header.addView(dot);root.addView(header);
    ScrollView sc=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(18,18,18,18);sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER);nav.setPadding(6,7,6,7);nav.setBackgroundColor(Color.WHITE);
    String[] labels={"💬 Chat","🎙 Voice","⚙ Automate","🧠 Models","☰ More"};for(int i=0;i<labels.length;i++){final int n=i;Button b=btn(labels[i]);b.setTextSize(11);b.setPadding(2,6,2,6);b.setOnClickListener(v->{if(n==0)chat();else if(n==1)voice();else if(n==2)flows();else if(n==3)models();else more();});nav.addView(b,new LinearLayout.LayoutParams(0,62,1));}root.addView(nav);setContentView(root);home();
  }
  void clear(){content.removeAllViews();}
  void home(){clear();content.addView(tv("Welcome back",27));content.addView(tv("Your private assistant runs locally on this device.",14));
    LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(18,18,18,18);card.setBackgroundColor(Color.WHITE);TextView t=tv("QUICK START",12);t.setTypeface(null,1);card.addView(t);card.addView(tv("Ask questions, use voice commands, automate Android actions, or import your own GGUF model.",14));content.addView(card,new LinearLayout.LayoutParams(-1,-2));
    content.addView(tv("STATUS",12));String status="● Voice service: "+(getSharedPreferences("axtor",0).getBoolean("voice_enabled",false)?"Enabled":"Off")+"\n● Watchdog: "+(getSharedPreferences("axtor",0).getBoolean("watchdog_enabled",true)?"On":"Off")+"\n● Model: "+(AppCore.activeModel(this).isEmpty()?"Not loaded":new File(AppCore.activeModel(this)).getName());content.addView(tv(status,14));
    Button c=btn("Start a conversation →");c.setOnClickListener(v->chat());content.addView(c);Button v=btn("Start voice assistant →");v.setOnClickListener(x->voice());content.addView(v);
    content.addView(tv("Creator: "+AppCore.CREATOR,12));}
  void repairLab(){clear();content.addView(tv("Developer Repair Lab",26));content.addView(tv("If a default Axtor component fails, choose an alternate installed backend or import a compatible open-source configuration. Axtor does not execute arbitrary downloaded code; imported components must be packaged and reviewed for Android compatibility and licensing.",14));
    String[] names={"Default Axtor","Local GGUF / llama.cpp","Android SpeechRecognizer","Rule-based Offline Commands"};
    String current=getSharedPreferences("axtor",0).getString("backend","Default Axtor");Spinner sp=new Spinner(this);sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));for(int i=0;i<names.length;i++)if(names[i].equals(current))sp.setSelection(i);content.addView(sp);
    Button apply=btn("✓ Apply Backend");apply.setOnClickListener(v->{String x=String.valueOf(sp.getSelectedItem());getSharedPreferences("axtor",0).edit().putString("backend",x).apply();Toast.makeText(this,"Backend selected: "+x,Toast.LENGTH_SHORT).show();});content.addView(apply);
    Button imp=btn("📥 Import Open-Source Backend Manifest");imp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT_BACKEND);});content.addView(imp);
    Button reset=btn("↻ Restore Default Backend");reset.setOnClickListener(v->{getSharedPreferences("axtor",0).edit().remove("backend").apply();Toast.makeText(this,"Default backend restored",Toast.LENGTH_SHORT).show();});content.addView(reset);
    content.addView(tv("Tip: for source projects such as llama.cpp, build the Android-compatible library/AAR and integrate it as a reviewed dependency rather than downloading executable code at runtime. Android and dependency licenses must be respected.",13));Button back=btn("← Back");back.setOnClickListener(v->more());content.addView(back);}
  void learning(){clear();content.addView(tv("Self-Learning",26));content.addView(tv("Axtor can learn locally from your corrections, preferences and explicitly saved memories. Learning is stored on-device and does not silently retrain the GGUF model.",14));
    android.content.SharedPreferences p=getSharedPreferences("axtor_learning",0);boolean on=p.getBoolean("enabled",true);Switch sw=new Switch(this);sw.setText("Enable local learning");sw.setTextSize(16);sw.setChecked(on);sw.setOnCheckedChangeListener((b,v)->p.edit().putBoolean("enabled",v).apply());content.addView(sw);
    EditText mem=new EditText(this);mem.setHint("Teach Axtor something to remember…");content.addView(mem);Button save=btn("＋ Save Memory");save.setOnClickListener(v->{String s=mem.getText().toString().trim();if(s.isEmpty())return;if(!p.getBoolean("enabled",true)){Toast.makeText(this,"Enable local learning first",Toast.LENGTH_SHORT).show();return;}try{JSONArray a=new JSONArray(p.getString("memories","[]"));JSONObject o=new JSONObject();o.put("text",s);o.put("created",System.currentTimeMillis());a.put(o);p.edit().putString("memories",a.toString()).apply();mem.setText("");Toast.makeText(this,"Learned locally",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Learning error: "+e.getMessage(),Toast.LENGTH_LONG).show();}});content.addView(save);
    Button clear=btn("🗑 Clear learned memories");clear.setOnClickListener(v->{p.edit().remove("memories").apply();Toast.makeText(this,"Learned memories cleared",Toast.LENGTH_SHORT).show();});content.addView(clear);content.addView(tv("Learned items: "+memoryCount(p),14));Button back=btn("← Back");back.setOnClickListener(v->more());content.addView(back);}
  int memoryCount(android.content.SharedPreferences p){try{return new JSONArray(p.getString("memories","[]")).length();}catch(Exception e){return 0;}}
  void more(){clear();content.addView(tv("More",27));content.addView(tv("Settings, persistence and creator information",14));Button p=btn("🛡 Persistence Setup");p.setOnClickListener(v->persistenceSetup());content.addView(p);Button r=btn("🧪 Developer Repair Lab");r.setOnClickListener(v->repairLab());content.addView(r);Button l=btn("🧠 Self-Learning");l.setOnClickListener(v->learning());content.addView(l);Button ab=btn("ⓘ About / Creator");ab.setOnClickListener(v->about());content.addView(ab);Button back=btn("← Home");back.setOnClickListener(v->home());content.addView(back);}
  void chat(){clear();content.addView(tv("Offline Chat",22));String active=AppCore.activeModel(this);content.addView(tv(active.isEmpty()?"No GGUF model loaded. Import a model in Models.":"Active local model: "+new File(active).getName(),12));EditText q=new EditText(this);q.setHint("Ask MyAiAgent…");content.addView(q);Button send=btn("Send");TextView out=tv("",15);content.addView(out);send.setOnClickListener(v->{String x=q.getText().toString().trim();if(x.isEmpty())return;q.setText("");String path=AppCore.activeModel(this);if(path.isEmpty()){out.setText("MyAiAgent: "+AppCore.answer(x));return;}out.setText("MyAiAgent is thinking locally…");LlamaRuntime.generate(this,path,x,"You are MyAiAgent. Answer accurately and directly in one short sentence under 15 words unless detail is requested. Never claim internet access when operating locally.",128,new LlamaRuntime.Callback(){public void onSuccess(String text,double tps){runOnUiThread(()->out.setText("MyAiAgent: "+text+"\n\n"+String.format(Locale.US,"%.1f tok/s · local",tps)));}public void onError(String m){runOnUiThread(()->out.setText("Local model error: "+m));}});});content.addView(send);Button back=btn("← Back");back.setOnClickListener(v->home());content.addView(back);}
  void persistenceSetup(){clear();content.addView(tv("Maximum Persistence Setup",22));content.addView(tv("Android controls service lifecycles. Axtor cannot make itself unkillable or bypass Android restrictions. These settings maximize reliability while remaining within the OS security model.",14));Button a=btn(AxtorAccessibilityService.isEnabled()?"✓ Accessibility enabled":"♿ Enable Accessibility Service");a.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));content.addView(a);Button b=btn("🔋 Open Battery Optimization Settings");b.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,android.net.Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}});content.addView(b);Button n=btn("🔔 Open App Notification Settings");n.setOnClickListener(v->{Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);});content.addView(n);Button w=btn(getSharedPreferences("axtor",MODE_PRIVATE).getBoolean("watchdog_enabled",true)?"🛡 Watchdog: ON":"🛡 Watchdog: OFF");w.setOnClickListener(x->{boolean on=!getSharedPreferences("axtor",MODE_PRIVATE).getBoolean("watchdog_enabled",true);getSharedPreferences("axtor",MODE_PRIVATE).edit().putBoolean("watchdog_enabled",on).apply();w.setText(on?"🛡 Watchdog: ON":"🛡 Watchdog: OFF");if(on)WatchdogScheduler.schedule(this);else WatchdogScheduler.cancel(this);});content.addView(w);Button v=btn("🎙 Start Voice Assistant");v.setOnClickListener(x->voice());content.addView(v);Button back=btn("← Back");back.setOnClickListener(x->home());content.addView(back);}
  void voice(){
    clear();
    content.addView(tv("Voice Assistant",22));
    content.addView(tv("Calling phrase: “"+VoiceCommandManager.getCallPhrase(this)+"”",14));
    content.addView(tv("Say your custom phrase first, then the command. For example: “"+VoiceCommandManager.getCallPhrase(this)+" turn the volume down”. Axtor ignores speech that does not start with your phrase.",14));
    Button change=btn("✎ Change Calling Phrase");
    change.setOnClickListener(v->voiceSetup());
    content.addView(change);
    Button test=btn("🧪 Test Voice System");
    test.setOnClickListener(v->{
      String d=VoiceCommandManager.diagnose(this);
      Toast.makeText(this,d,Toast.LENGTH_LONG).show();
      if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO);
      }
    });
    content.addView(test);
    Button repair=btn("🛠 Repair / Restart Voice");
    repair.setOnClickListener(v->{
      boolean ok=VoiceCommandManager.repair(this);
      Toast.makeText(this,ok?"Voice service restarted.":"Android blocked the service start; check microphone and battery settings.",Toast.LENGTH_LONG).show();
    });
    content.addView(repair);
    Button start=btn(VoiceServiceState.isRunning()?"✓ Voice Assistant Running":"🎙 Start Voice Assistant");
    start.setOnClickListener(v->{
      if(!VoiceCommandManager.isConfigured(this)){voiceSetup();return;}
      getSharedPreferences("axtor",MODE_PRIVATE).edit().putBoolean("voice_enabled",true).apply();
      if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO);return;
      }
      Intent i=new Intent(this,VoiceAssistantService.class);
      try{if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
      catch(Exception e){Toast.makeText(this,"Could not start voice: "+e.getMessage(),Toast.LENGTH_LONG).show();return;}
      Toast.makeText(this,"Voice assistant started.",Toast.LENGTH_SHORT).show();
      voice();
    });
    content.addView(start);
    Button back=btn("← Back");
    back.setOnClickListener(v->home());
    content.addView(back);
  }
  void voiceSetup(){
    clear();
    content.addView(tv("Custom Voice Calling",26));
    content.addView(tv("First-time setup: choose a short phrase that calls Axtor, then say the phrase followed by your command. Do not use wake phrases such as “Hey AI”, “Hey MyAI”, or similar variants. Voice recognition stays active only while the Axtor voice service is enabled.",14));
    EditText phrase=new EditText(this);
    phrase.setSingleLine(true);
    phrase.setHint("Example: computer, assistant, axtor");
    phrase.setText(VoiceCommandManager.getCallPhrase(this));
    content.addView(phrase);
    Button save=btn("✓ Save Calling Phrase");
    save.setOnClickListener(v->{
      String p=phrase.getText().toString();
      if(!VoiceCommandManager.setCallPhrase(this,p)){
        Toast.makeText(this,"Choose 2–32 letters/numbers/spaces and avoid Hey AI-style wake phrases.",Toast.LENGTH_LONG).show();
        return;
      }
      getSharedPreferences("axtor",MODE_PRIVATE).edit().putBoolean("voice_enabled",true).apply();
      Toast.makeText(this,"Calling phrase saved: "+VoiceCommandManager.getCallPhrase(this),Toast.LENGTH_SHORT).show();
    });
    content.addView(save);
    Button test=btn("🧪 Test Voice Command System");
    test.setOnClickListener(v->Toast.makeText(this,VoiceCommandManager.diagnose(this),Toast.LENGTH_LONG).show());
    content.addView(test);
    Button repair=btn("🛠 Repair Voice Commands");
    repair.setOnClickListener(v->{
      boolean ok=VoiceCommandManager.repair(this);
      Toast.makeText(this,ok?"Voice service restarted for repair.":"Android blocked the restart; open Voice again from Axtor.",Toast.LENGTH_LONG).show();
    });
    content.addView(repair);
    content.addView(tv("Example: if your phrase is “computer”, say “computer turn the volume down”. Axtor ignores ordinary speech that does not begin with your calling phrase.",13));
    Button start=btn("🎙 Start Axtor Voice Assistant");
    start.setOnClickListener(v->{if(VoiceCommandManager.isValid(phrase.getText().toString())){VoiceCommandManager.setCallPhrase(this,phrase.getText().toString());voice();}else Toast.makeText(this,"Save a valid calling phrase first.",Toast.LENGTH_SHORT).show();});
    content.addView(start);
    Button back=btn("← Back");
    back.setOnClickListener(v->home());
    content.addView(back);
  }

  void models(){clear();content.addView(tv("Models",22));content.addView(tv("Import and manage local GGUF models. For fastest responses, use a small 0.5B–1.5B GGUF quantized for your device.",14));JSONArray a=AppCore.models(this);String active=AppCore.activeModel(this);content.addView(tv("Imported models: "+a.length()+"\nActive: "+(active.isEmpty()?"none":new File(active).getName()),14));Button imp=btn("📥 Import custom GGUF model");imp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,FILE);});content.addView(imp);Button exp=btn("📤 Export model manifest");exp.setOnClickListener(v->exportFile(EXPORT_MODELS,"myaiagent-models.json"));content.addView(exp);Button expModel=btn("📤 Export active GGUF model");expModel.setOnClickListener(v->{String p=AppCore.activeModel(this);if(p.isEmpty()){Toast.makeText(this,"No active GGUF model",Toast.LENGTH_SHORT).show();return;}exportFile(EXPORT_GGUF,new File(p).getName());});content.addView(expModel);Button back=btn("← Back");back.setOnClickListener(v->home());content.addView(back);}
  void customAutomation(){clear();content.addView(tv("Custom Automation",22));content.addView(tv("Create a simple automation with a voice phrase and an Android action. The saved rule can be exported and imported on another Axtor installation.",14));EditText trigger=new EditText(this);trigger.setHint("Voice phrase, e.g. turn volume up");content.addView(trigger);Spinner action=new Spinner(this);String[] options={"Open Settings","Open Wi-Fi settings","Open Bluetooth settings","Go Home","Go Back","Open Recent Apps","Open Notifications","Lock Screen","Volume Up","Volume Down","Mute","Unmute","Set Alarm"};action.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options));content.addView(action);Button save=btn("＋ Save Custom Automation");save.setOnClickListener(v->{String t=trigger.getText().toString().trim();if(t.isEmpty()){Toast.makeText(this,"Enter a voice phrase",Toast.LENGTH_SHORT).show();return;}try{JSONArray a=AppCore.flows(this);JSONObject o=new JSONObject();o.put("trigger",t);o.put("condition",true);o.put("action",action.getSelectedItem().toString());a.put(o);AppCore.setFlows(this,a);Toast.makeText(this,"Custom automation saved",Toast.LENGTH_SHORT).show();trigger.setText("");}catch(Exception e){Toast.makeText(this,"Could not save: "+e.getMessage(),Toast.LENGTH_LONG).show();}});content.addView(save);content.addView(tv("Saved custom automations",18));JSONArray saved=AppCore.flows(this);for(int i=0;i<saved.length();i++){try{JSONObject o=saved.getJSONObject(i);TextView row=tv((i+1)+". "+o.optString("trigger")+" → "+o.optString("action"),14);content.addView(row);}catch(Exception ignored){}}Button back=btn("← Back");back.setOnClickListener(v->flows());content.addView(back);}
  void flows(){clear();content.addView(tv("Automation",22));content.addView(tv("Voice/manual actions now execute supported Android actions. App navigation and global actions require enabling Axtor Accessibility Service.",14));Button custom=btn("＋ Create Custom Automation");custom.setOnClickListener(v->customAutomation());content.addView(custom);Button access=btn(AxtorAccessibilityService.isEnabled()?"✓ Accessibility service enabled":"🔐 Enable Axtor Accessibility Service");access.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}});content.addView(access);String[] acts={"Open Settings","Open Wi-Fi settings","Open Bluetooth settings","Go Home","Go Back","Open Recent Apps","Open Notifications","Lock Screen","Volume Up","Volume Down","Mute","Unmute","Set Alarm"};for(String s:acts){Button b=btn("▶ "+s);b.setOnClickListener(v->{String result=DeviceAutomation.execute(this,s);Toast.makeText(this,result==null?"Action not supported":result,Toast.LENGTH_LONG).show();try{JSONArray a=AppCore.flows(this);JSONObject o=new JSONObject();o.put("trigger","manual/voice");o.put("condition",true);o.put("action",s);a.put(o);AppCore.setFlows(this,a);}catch(Exception ignored){}});content.addView(b);}content.addView(tv("Saved flows: "+AppCore.flows(this).length(),14));Button imp=btn("📥 Import automation JSON");imp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT_FLOW);});content.addView(imp);Button exp=btn("📤 Export automation JSON");exp.setOnClickListener(v->exportFile(EXPORT_FLOW,"myaiagent-automations.json"));content.addView(exp);Button back=btn("← Back");back.setOnClickListener(v->home());content.addView(back);}
  void about(){clear();content.addView(tv("About MyAiAgent",22));content.addView(tv("Creator\n"+AppCore.CREATOR+"\n\nMyAiAgent is an offline-first Android assistant. It supports local model management, voice commands and device automation within Android's security model.",16));Button s=btn("Open Android voice input settings");s.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}});content.addView(s);Button a=btn("Open Accessibility settings");a.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));content.addView(a);Button back=btn("← Back");back.setOnClickListener(v->home());content.addView(back);}
  void exportFile(int code,String name){pending="";Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,code);}
  byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))!=-1)b.write(x,0,n);in.close();return b.toByteArray();}
  protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(c!=RESULT_OK||d==null)return;try{if(r==IMPORT_BACKEND){InputStream in=getContentResolver().openInputStream(d.getData());String json=new String(readAll(in),"UTF-8");JSONObject o=new JSONObject(json);String name=o.optString("name","Imported Backend");String type=o.optString("type","config");if(!"backend-manifest-v1".equals(o.optString("format")))throw new IOException("Unsupported backend manifest");getSharedPreferences("axtor",0).edit().putString("backend",name).putString("backend_manifest",json).apply();Toast.makeText(this,"Imported backend profile: "+name,Toast.LENGTH_LONG).show();}else if(r==FILE){String n=String.valueOf(d.getData().getLastPathSegment());InputStream in=getContentResolver().openInputStream(d.getData());File local=LlamaRuntime.copyModel(this,in,n);if(!LlamaRuntime.isGguf(local)){local.delete();throw new IOException("Not a valid GGUF model (missing GGUF magic)");}AppCore.addLocalModel(this,local.getName(),local.getAbsolutePath());Toast.makeText(this,"GGUF model imported and selected: "+local.getName(),Toast.LENGTH_LONG).show();}else if(r==IMPORT_FLOW){AppCore.importFlows(this,getContentResolver().openInputStream(d.getData()));Toast.makeText(this,"Automations imported",Toast.LENGTH_SHORT).show();}else if(r==EXPORT_MODELS){AppCore.exportModels(this,getContentResolver().openOutputStream(d.getData()));Toast.makeText(this,"Models exported",Toast.LENGTH_SHORT).show();}else if(r==EXPORT_FLOW){AppCore.exportFlows(this,getContentResolver().openOutputStream(d.getData()));Toast.makeText(this,"Automations exported",Toast.LENGTH_SHORT).show();}else if(r==EXPORT_GGUF){InputStream in=new FileInputStream(AppCore.activeModel(this));OutputStream out=getContentResolver().openOutputStream(d.getData());byte[] buf=new byte[1024*1024];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();out.close();Toast.makeText(this,"GGUF model exported",Toast.LENGTH_LONG).show();}}catch(Exception e){Toast.makeText(this,"File error: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
}
