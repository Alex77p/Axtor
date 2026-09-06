package com.ayushdebbarma.myaiagent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** ChatGPT-inspired Axtor shell: conversation, history, model/voice controls and local tools. */
public class ChatGPTActivity extends Activity {
    private LinearLayout messages;
    private EditText composer;
    private ScrollView scroll;
    private TextView modelStatus;
    private final int bg=Color.rgb(255,255,255), text=Color.rgb(20,20,20), muted=Color.rgb(105,105,105), userBg=Color.rgb(240,240,240), accent=Color.rgb(16,163,127);
    @Override public void onCreate(Bundle b){super.onCreate(b);build();loadHistory();}
    private TextView label(String s,float size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(text);t.setPadding(16,8,16,8);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(8,6,8,4);
        Button menu=button("☰");menu.setTextSize(22);menu.setOnClickListener(v->showMenu());top.addView(menu,new LinearLayout.LayoutParams(54,56));
        LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(LinearLayout.VERTICAL);TextView title=label("Axtor",19);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);modelStatus=label(modelText(),11);modelStatus.setTextColor(muted);titleBox.addView(title);titleBox.addView(modelStatus);top.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
        Button newChat=button("＋");newChat.setTextSize(22);newChat.setOnClickListener(v->newChat());top.addView(newChat,new LinearLayout.LayoutParams(54,56));root.addView(top);
        scroll=new ScrollView(this);messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);messages.setPadding(12,10,12,18);scroll.addView(messages);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composerBar=new LinearLayout(this);composerBar.setGravity(Gravity.CENTER_VERTICAL);composerBar.setPadding(10,6,10,4);
        composer=new EditText(this);composer.setHint("Message Axtor…");composer.setSingleLine(false);composer.setMaxLines(5);composer.setBackgroundColor(userBg);composer.setPadding(16,10,16,10);composerBar.addView(composer,new LinearLayout.LayoutParams(0,-2,1));
        Button send=button("↑");send.setTextSize(22);send.setTextColor(Color.WHITE);send.setBackgroundColor(accent);send.setOnClickListener(v->send());composerBar.addView(send,new LinearLayout.LayoutParams(56,56));root.addView(composerBar);
        LinearLayout quick=new LinearLayout(this);quick.setGravity(Gravity.CENTER);quick.setPadding(4,0,4,6);
        Button voice=button("🎙 Voice");voice.setOnClickListener(v->startVoice());Button models=button("＋ Model");models.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));Button tools=button("Tools");tools.setOnClickListener(v->startActivity(new Intent(this,SoundTriggerActivity.class)));quick.addView(voice,new LinearLayout.LayoutParams(0,52,1));quick.addView(models,new LinearLayout.LayoutParams(0,52,1));quick.addView(tools,new LinearLayout.LayoutParams(0,52,1));root.addView(quick);setContentView(root);
        if(messages.getChildCount()==0)addAssistant("Hi! I'm Axtor. Ask me anything, or tell me what you want done on your Android device.");
    }
    private String modelText(){String m=AppCore.activeModel(this);if(AppCore.hasUsableActiveModel(this))return "Local GGUF • "+shortName(m);if(HybridAiRouter.onlineEnabled(this)&&HybridAiRouter.isConfigured(this))return "Online fallback ready";return "Local tools • no model loaded";}
    private String shortName(String p){int i=Math.max(p.lastIndexOf('/'),p.lastIndexOf('\\'));return i>=0?p.substring(i+1):p;}
    private void loadHistory(){try{JSONArray h=AxtorAgent.history(this);if(h.length()==0)return;messages.removeAllViews();for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o==null)continue;String role=o.optString("role","");if("user".equals(role))addBubble(o.optString("text",""),true);else if("assistant".equals(role))addBubble(o.optString("text",""),false);}scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}catch(Exception ignored){}}
    private void newChat(){AxtorAgent.clearHistory(this);messages.removeAllViews();addAssistant("New chat started. How can I help?");}
    private void showMenu(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("New chat");p.getMenu().add("Models & import");p.getMenu().add("Snap & hands-free");p.getMenu().add("Diagnostics");p.setOnMenuItemClickListener(item->{String s=item.getTitle().toString();if(s.startsWith("New")){newChat();return true;}if(s.startsWith("Models")){startActivity(new Intent(this,MainActivity.class));return true;}if(s.startsWith("Snap")){startActivity(new Intent(this,SoundTriggerActivity.class));return true;}if(s.startsWith("Diagnostics")){composer.setText("run self-check diagnostics");send();return true;}return false;});p.show();}
    private void addUser(String s){addBubble(s,true);}
    private void addAssistant(String s){addBubble(s,false);}
    private void addBubble(String s,boolean user){LinearLayout row=new LinearLayout(this);row.setGravity(user?Gravity.RIGHT:Gravity.LEFT);TextView t=label(s,15);t.setTextColor(text);if(user)t.setBackgroundColor(userBg);row.addView(t,new LinearLayout.LayoutParams(-2,-2));messages.addView(row);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    private void send(){String q=composer.getText().toString().trim();if(q.isEmpty())return;composer.setText("");addUser(q);addAssistant("Thinking…");int index=messages.getChildCount()-1;AxtorAgent.handle(this,q,new AxtorAgent.Callback(){public void onReply(String s){runOnUiThread(()->{if(index<messages.getChildCount())messages.removeViewAt(index);addAssistant(s);modelStatus.setText(modelText());});}public void onError(String e){runOnUiThread(()->{if(index<messages.getChildCount())messages.removeViewAt(index);addAssistant("Error: "+e);});}});}
    private void startVoice(){try{VoiceCommandManager.repair(this);Toast.makeText(this,"Voice assistant started. Use your enrolled snap when hands-free snap mode is enabled.",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Voice unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
}
