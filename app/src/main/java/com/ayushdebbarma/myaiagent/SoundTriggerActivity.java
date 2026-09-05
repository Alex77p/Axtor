package com.ayushdebbarma.myaiagent;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

/** UI for configuring opt-in finger-snap sound triggers. */
public class SoundTriggerActivity extends Activity {
    private LinearLayout content;
    private final int bg = Color.rgb(255,245,240), brown = Color.rgb(45,24,16);

    private TextView tv(String s, float z) { TextView t = new TextView(this); t.setText(s); t.setTextSize(z); t.setTextColor(brown); t.setPadding(20,12,20,12); return t; }
    private Button btn(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        build();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
    }

    private void build() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg);
        LinearLayout header = new LinearLayout(this); header.setPadding(22,18,18,14); header.setGravity(Gravity.CENTER_VERTICAL); header.setBackgroundColor(Color.rgb(255,153,102));
        TextView title = tv("Hands-Free Sound Triggers", 22); title.setTextColor(Color.WHITE); title.setTypeface(null,1); header.addView(title, new LinearLayout.LayoutParams(0,-2,1)); root.addView(header);
        ScrollView sc = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(18,18,18,18); sc.addView(content); root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); render();
    }

    private void render() {
        content.removeAllViews();
        android.content.SharedPreferences p = getSharedPreferences("axtor_sound",0);
        boolean enabled = p.getBoolean("enabled",false);
        content.addView(tv("👆 Finger snap → Axtor action", 24));
        content.addView(tv("Axtor listens for short snap-like sound transients. This is not speech recognition. It is OFF by default and uses the microphone only while you enable it.",14));

        Switch sw = new Switch(this); sw.setText("Enable sound trigger monitoring"); sw.setTextSize(16); sw.setChecked(enabled);
        sw.setOnCheckedChangeListener((button, on) -> { p.edit().putBoolean("enabled",on).apply(); if(on) startDetector(); else stopDetector(); render(); }); content.addView(sw);

        EditText single = new EditText(this); single.setSingleLine(true); single.setText(p.getString("single_action","lock screen")); single.setHint("Single snap action, e.g. lock screen"); content.addView(tv("Single snap",17)); content.addView(single);
        EditText dbl = new EditText(this); dbl.setSingleLine(true); dbl.setText(p.getString("double_action","volume down")); dbl.setHint("Double snap action, e.g. volume down"); content.addView(tv("Double snap",17)); content.addView(dbl);

        Button save = btn("✓ Save Trigger Actions"); save.setOnClickListener(v -> { String a=single.getText().toString().trim(), d=dbl.getText().toString().trim(); if(a.isEmpty()||d.isEmpty()){Toast.makeText(this,"Enter both actions.",Toast.LENGTH_SHORT).show();return;} p.edit().putString("single_action",a).putString("double_action",d).apply(); Toast.makeText(this,"Sound trigger actions saved.",Toast.LENGTH_SHORT).show(); }); content.addView(save);

        Button test = btn("🧪 Test Single Snap Action Now"); test.setOnClickListener(v -> { String action=p.getString("single_action","lock screen"); String result=DeviceAutomation.execute(this,action); Toast.makeText(this,result==null?"Action not supported":result,Toast.LENGTH_LONG).show(); }); content.addView(test);
        Button stop = btn("⏹ Stop Sound Trigger Monitoring"); stop.setOnClickListener(v -> { p.edit().putBoolean("enabled",false).apply(); stopDetector(); render(); }); content.addView(stop);
        content.addView(tv("Examples: single snap → lock screen; double snap → volume down. You can use supported Axtor actions such as open an app, volume controls, settings, URLs, or Accessibility actions. Android permissions still apply.",13));
        content.addView(tv("Privacy: a persistent microphone listener can affect battery life and privacy. Axtor shows an ongoing foreground-service notification while monitoring and does not run this listener silently.",13));
        Button back=btn("← Back to Axtor"); back.setOnClickListener(v->finish()); content.addView(back);
    }

    private void startDetector() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10); return; }
        // Do not keep two microphone services active at once.
        stopService(new Intent(this, VoiceAssistantService.class));
        Intent i=new Intent(this,SoundTriggerService.class);
        try { if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); } catch(Exception e) { Toast.makeText(this,"Could not start sound triggers: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
    }

    private void stopDetector() { stopService(new Intent(this,SoundTriggerService.class)); }

    @Override protected void onResume(){super.onResume(); if(content!=null) render();}
}
