package com.ayushdebbarma.myaiagent;

import android.app.*;import android.os.*;import android.graphics.Color;import android.text.InputType;import android.view.*;import android.widget.*;

/** Minimal setup screen only used when the user explicitly asks to configure online AI. */
public class OnlineAiSetupActivity extends Activity{
  @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,48,32,32);
    TextView title=new TextView(this);title.setText("Axtor Online AI");title.setTextSize(24);title.setTextColor(Color.WHITE);box.addView(title);
    TextView info=new TextView(this);info.setText("Enter an OpenRouter API key. Axtor uses the free-model router only. Your key is encrypted with Android Keystore and stored locally.");info.setTextColor(Color.LTGRAY);info.setPadding(0,20,0,20);box.addView(info);
    EditText key=new EditText(this);key.setHint("OpenRouter API key");key.setSingleLine(true);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(key,new LinearLayout.LayoutParams(-1,-2));
    Button save=new Button(this);save.setText("Save and return to voice mode");save.setOnClickListener(v->{String k=key.getText().toString().trim();if(k.length()<10){Toast.makeText(this,"Enter a valid API key",Toast.LENGTH_SHORT).show();return;}OnlineAiRouter.setKey(this,k);Toast.makeText(this,"Online AI enabled",Toast.LENGTH_SHORT).show();finish();});box.addView(save);
    Button clear=new Button(this);clear.setText("Remove online AI key");clear.setOnClickListener(v->{OnlineAiRouter.clear(this);Toast.makeText(this,"Online AI disabled",Toast.LENGTH_SHORT).show();});box.addView(clear);
    box.setBackgroundColor(Color.rgb(18,18,18));setContentView(box);
  }
}
