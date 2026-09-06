package com.ayushdebbarma.myaiagent;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import java.util.ArrayList;

/** Clean ChatGPT-inspired conversation surface for Axtor. */
public class ChatGPTActivity extends Activity {
    private LinearLayout messages;
    private EditText composer;
    private ScrollView scroll;
    private final int bg=Color.rgb(255,255,255), text=Color.rgb(20,20,20), muted=Color.rgb(105,105,105), userBg=Color.rgb(240,240,240), accent=Color.rgb(16,163,127);
    @Override public void onCreate(Bundle b){super.onCreate(b);build();}
    private TextView label(String s,float size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(text);t.setPadding(16,8,16,8);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(10,8,10,8);
        TextView menu=label("☰",25);menu.setOnClickListener(v->finish());top.addView(menu,new LinearLayout.LayoutParams(52,56));
        LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(LinearLayout.VERTICAL);TextView title=label("Axtor",19);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);TextView sub=label("Private AI",11);sub.setTextColor(muted);titleBox.addView(title);titleBox.addView(sub);top.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=label("●",15);status.setTextColor(accent);top.addView(status,new LinearLayout.LayoutParams(34,56));root.addView(top);
        scroll=new ScrollView(this);messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);messages.setPadding(12,10,12,18);scroll.addView(messages);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composerBar=new LinearLayout(this);composerBar.setGravity(Gravity.CENTER_VERTICAL);composerBar.setPadding(10,6,10,8);
        composer=new EditText(this);composer.setHint("Message Axtor…");composer.setSingleLine(false);composer.setMaxLines(5);composer.setBackgroundColor(userBg);composer.setPadding(16,10,16,10);composerBar.addView(composer,new LinearLayout.LayoutParams(0,-2,1));
        Button send=button("↑");send.setTextSize(22);send.setTextColor(Color.WHITE);send.setBackgroundColor(accent);send.setOnClickListener(v->send());composerBar.addView(send,new LinearLayout.LayoutParams(56,56));root.addView(composerBar);
        LinearLayout quick=new LinearLayout(this);quick.setGravity(Gravity.CENTER);quick.setPadding(4,0,4,6);Button voice=button("🎙");voice.setOnClickListener(v->startVoice());Button models=button("＋ Model");models.setOnClickListener(v->startActivity(new android.content.Intent(this,MainActivity.class)));quick.addView(voice,new LinearLayout.LayoutParams(0,52,1));quick.addView(models,new LinearLayout.LayoutParams(0,52,1));root.addView(quick);setContentView(root);
        addAssistant("Hi! I'm Axtor. Ask me anything, or tell me what you want done on your Android device.");
    }
    private void addUser(String s){addBubble(s,true);}
    private void addAssistant(String s){addBubble(s,false);}
    private void addBubble(String s,boolean user){
        LinearLayout row=new LinearLayout(this);row.setGravity(user?Gravity.RIGHT:Gravity.LEFT);TextView t=label(s,15);t.setTextColor(text);if(user)t.setBackgroundColor(userBg);row.addView(t,new LinearLayout.LayoutParams(-2,-2));messages.addView(row);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void send(){String q=composer.getText().toString().trim();if(q.isEmpty())return;composer.setText("");addUser(q);addAssistant("Thinking…");int index=messages.getChildCount()-1;AxtorAgent.handle(this,q,new AxtorAgent.Callback(){public void onReply(String s){runOnUiThread(()->{messages.removeViewAt(index);addAssistant(s);});}public void onError(String e){runOnUiThread(()->{messages.removeViewAt(index);addAssistant("Error: "+e);});}});}
    private void startVoice(){try{VoiceCommandManager.repair(this);Toast.makeText(this,"Voice assistant started.",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Voice unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
}
