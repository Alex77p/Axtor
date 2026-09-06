package com.ayushdebbarma.myaiagent;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.Gravity;
import android.widget.*;

/** UI for configuring opt-in hands-free sound triggers. */
public class SoundTriggerActivity extends Activity {
    private LinearLayout content;
    private final int bg = Color.rgb(255,245,240), brown = Color.rgb(45,24,16);

    private TextView tv(String s, float z) { TextView t = new TextView(this); t.setText(s); t.setTextSize(z); t.setTextColor(brown); t.setPadding(20,12,20,12); return t; }
    private Button btn(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); build();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
    }

    private void build() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg);
        LinearLayout header = new LinearLayout(this); header.setPadding(22,18,18,14); header.setGravity(Gravity.CENTER_VERTICAL); header.setBackgroundColor(Color.rgb(255,153,102));
        TextView title = tv("Hands-Free Sound Triggers", 22); title.setTextColor(Color.WHITE); title.setTypeface(null,1); header.addView(title, new LinearLayout.LayoutParams(0,-2,1)); root.addView(header);
        ScrollView sc = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(18,18,18,18); sc.addView(content); root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); render();
    }

    private EditText actionField(android.content.SharedPreferences p, String key, String title, String fallback) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setText(p.getString(key, fallback)); e.setHint("Custom Axtor action, e.g. open Chrome");
        content.addView(tv(title,17)); content.addView(e); return e;
    }

    private void render() {
        content.removeAllViews();
        android.content.SharedPreferences p = getSharedPreferences("axtor_sound",0);
        boolean enabled = p.getBoolean("enabled",false);
        boolean wake = p.getBoolean("wake_on_screen_off",true);
        content.addView(tv("👆 Hands-Free Snap Automation", 24));
        content.addView(tv("Use one, two, three, or four rapid snaps. Each pattern can run a custom Axtor action. The listener is opt-in and remains visible as a foreground microphone service.",14));

        Switch sw = new Switch(this); sw.setText("Enable sound trigger monitoring"); sw.setTextSize(16); sw.setChecked(enabled);
        sw.setOnCheckedChangeListener((button, on) -> { p.edit().putBoolean("enabled",on).apply(); if(on) startDetector(); else stopDetector(); }); content.addView(sw);

        Switch wakeSw = new Switch(this); wakeSw.setText("Wake screen when a snap is detected while screen is off"); wakeSw.setTextSize(16); wakeSw.setChecked(wake);
        wakeSw.setOnCheckedChangeListener((button,on) -> p.edit().putBoolean("wake_on_screen_off",on).apply()); content.addView(wakeSw);
        content.addView(tv("Screen-off mode: Axtor can keep the foreground microphone service listening after the display turns off. Android/OEM battery restrictions may still stop background services.",13));

        EditText single = actionField(p,"single_action","1 snap — Custom action","wake screen");
        EditText dbl = actionField(p,"double_action","2 snaps — Custom action","volume down");
        EditText triple = actionField(p,"triple_action","3 snaps — Custom action","open settings");
        EditText quad = actionField(p,"quad_action","4 snaps — Custom action","open notification settings");

        Button save = btn("✓ Save Custom Snap Actions");
        save.setOnClickListener(v -> {
            String a=single.getText().toString().trim(), d=dbl.getText().toString().trim(), t=triple.getText().toString().trim(), q=quad.getText().toString().trim();
            if(a.isEmpty()||d.isEmpty()||t.isEmpty()||q.isEmpty()){Toast.makeText(this,"Enter an action for all four snap patterns.",Toast.LENGTH_SHORT).show();return;}
            p.edit().putString("single_action",a).putString("double_action",d).putString("triple_action",t).putString("quad_action",q).apply();
            Toast.makeText(this,"Custom snap actions saved.",Toast.LENGTH_SHORT).show();
        }); content.addView(save);

        Button test = btn("🧪 Test 1-Snap Custom Action Now");
        test.setOnClickListener(v -> { String action=p.getString("single_action","wake screen"); String result=DeviceAutomation.execute(this,action); Toast.makeText(this,result==null?"Action not supported":result,Toast.LENGTH_LONG).show(); }); content.addView(test);
        Button stop = btn("⏹ Stop Sound Trigger Monitoring"); stop.setOnClickListener(v -> { p.edit().putBoolean("enabled",false).apply(); stopDetector(); render(); }); content.addView(stop);
        content.addView(tv("Supported examples: wake screen, lock screen, go home, go back, volume up/down, mute/unmute, open settings, open an installed app, open Wi-Fi/Bluetooth settings, and other Axtor-supported actions. You can also use saved custom flows. Android permissions still apply.",13));
        content.addView(tv("Privacy: the listener uses the microphone only while enabled and shows an ongoing foreground-service notification. Axtor does not silently hide the microphone listener.",13));
        Button back=btn("← Back to Axtor"); back.setOnClickListener(v->finish()); content.addView(back);
    }

    private void startDetector() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10); return; }
        stopService(new Intent(this, VoiceAssistantService.class));
        Intent i=new Intent(this,SoundTriggerService.class);
        try { if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); } catch(Exception e) { Toast.makeText(this,"Could not start sound triggers: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    private void stopDetector() { stopService(new Intent(this,SoundTriggerService.class)); }
    @Override protected void onResume(){super.onResume(); if(content!=null) render();}
}
