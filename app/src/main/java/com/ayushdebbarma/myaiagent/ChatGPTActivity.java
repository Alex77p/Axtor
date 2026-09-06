package com.ayushdebbarma.myaiagent;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Axtor's default ChatGPT-style interface. Voice remains the primary hands-free mode,
 * while this screen provides normal chat, status, and feature setup controls.
 */
public class ChatGPTActivity extends Activity {
    private static final int AUDIO = 42;
    private LinearLayout messages;
    private ScrollView scroll;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildInterface();
        startWhenPermitted();
        handler.post(statusUpdater);
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String value, float size) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(Color.rgb(235,235,235));
        v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(dp(4), dp(4), dp(4), dp(4));
        return v;
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(32,33,35));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(16), dp(10), dp(10), dp(10));
        TextView title = text("Axtor", 21); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button menu = new Button(this); menu.setText("⋮"); menu.setTextSize(24); menu.setOnClickListener(v -> showFeatureMenu());
        top.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(top);

        status = text("● Starting Axtor…", 13); status.setTextColor(Color.LTGRAY); status.setPadding(dp(18), 0, dp(18), dp(8));
        root.addView(status);

        scroll = new ScrollView(this); scroll.setFillViewport(true);
        messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(dp(12), dp(8), dp(12), dp(12));
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addBubble("Hello! I'm Axtor. You can type a message or speak a command directly. No wake phrase is required.", false);

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL); composer.setPadding(dp(10), dp(8), dp(10), dp(10));
        EditText input = new EditText(this); input.setHint("Message Axtor…"); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.GRAY);
        input.setSingleLine(false); input.setMaxLines(4); input.setBackgroundColor(Color.rgb(48,49,52)); input.setPadding(dp(14), dp(8), dp(10), dp(8));
        composer.addView(input, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button mic = new Button(this); mic.setText("🎙"); mic.setTextSize(20); mic.setContentDescription("Start voice assistant");
        mic.setOnClickListener(v -> startWhenPermitted()); composer.addView(mic, new LinearLayout.LayoutParams(dp(58), dp(52)));
        Button send = new Button(this); send.setText("➤"); send.setTextSize(20); send.setOnClickListener(v -> sendMessage(input));
        composer.addView(send, new LinearLayout.LayoutParams(dp(58), dp(52)));
        root.addView(composer);
        setContentView(root);
    }

    private void addBubble(String value, boolean user) {
        TextView bubble = text(value, 16);
        bubble.setTextColor(Color.WHITE); bubble.setPadding(dp(16), dp(11), dp(16), dp(11));
        bubble.setBackgroundColor(user ? Color.rgb(16,163,127) : Color.rgb(52,53,65));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(user ? dp(44) : dp(4), dp(6), user ? dp(4) : dp(44), dp(6));
        p.gravity = user ? Gravity.END : Gravity.START; messages.addView(bubble, p);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendMessage(EditText input) {
        String command = input.getText().toString().trim(); if (command.isEmpty()) return;
        addBubble(command, true); input.setText("");
        String policy = AxtorCommandSecurityPolicy.authorizeVoice(this, command);
        if (!"OK".equals(policy)) { addBubble("That command is blocked by Axtor security policy.", false); return; }
        AxtorAgent.handle(this, command, new AxtorAgent.Callback() {
            public void onReply(String response) { runOnUiThread(() -> addBubble(response == null ? "No response." : response, false)); }
            public void onError(String message) { runOnUiThread(() -> addBubble("Command failed: " + (message == null ? "unknown error" : message), false)); }
        });
    }

    private void startWhenPermitted() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO); return;
        }
        startVoiceService();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startVoiceService();
        else if (requestCode == AUDIO) Toast.makeText(this, "Microphone permission is needed for voice mode.", Toast.LENGTH_SHORT).show();
    }

    private void startVoiceService() {
        if (!VoiceServiceState.isRunning()) {
            try { VoiceCommandManager.repair(this); }
            catch (Exception e) { Toast.makeText(this, "Voice service could not start.", Toast.LENGTH_SHORT).show(); }
        }
    }

    private void showFeatureMenu() {
        final String[] items = {"Enable snap commands", "Disable snap commands", "Enroll personal snaps", "Toggle extended-range snap detection", "Run voice diagnostics", "Open setup guide"};
        new android.app.AlertDialog.Builder(this).setTitle("Axtor features").setItems(items, (d, which) -> {
            if (which == 0) {
                if (!SnapTriggerEngine.isEnrolled(this)) { Toast.makeText(this, "Enroll your snaps first.", Toast.LENGTH_SHORT).show(); return; }
                getSharedPreferences("axtor_voice",0).edit().putBoolean("snap_command_patterns_enabled",true).apply();
                startVoiceService(); Toast.makeText(this,"Snap commands enabled.",Toast.LENGTH_SHORT).show();
            } else if (which == 1) {
                getSharedPreferences("axtor_voice",0).edit().putBoolean("snap_command_patterns_enabled",false).apply();
                startVoiceService(); Toast.makeText(this,"Snap commands disabled.",Toast.LENGTH_SHORT).show();
            } else if (which == 2) enrollSnaps();
            else if (which == 3) {
                boolean next=!SnapTriggerEngine.isExtendedRangeEnabled(this); SnapTriggerEngine.setExtendedRangeEnabled(this,next);
                Toast.makeText(this,next?"Extended-range snaps ON":"Extended-range snaps OFF",Toast.LENGTH_SHORT).show();
            } else if (which == 4) Toast.makeText(this, VoiceCommandManager.diagnose(this), Toast.LENGTH_LONG).show();
            else showGuide();
        }).show();
    }

    private void enrollSnaps() {
        Toast.makeText(this,"Make 3 clear snaps when prompted by the detector.",Toast.LENGTH_LONG).show();
        new Thread(() -> {
            boolean ok = SnapTriggerEngine.enroll(this);
            runOnUiThread(() -> Toast.makeText(this, ok ? "Personal snap enrolled." : "Snap enrollment failed. Try again in a quiet room.", Toast.LENGTH_LONG).show());
        }, "AxtorSnapEnrollment").start();
    }

    private void showGuide() {
        new android.app.AlertDialog.Builder(this).setTitle("Axtor quick guide")
            .setMessage("Voice: tap 🎙 and speak directly.\n\nSnaps: Features → Enroll personal snaps, then Enable snap commands. 1 snap = listen; 2 snaps = volume down; 3 snaps = emergency stop; 4 snaps = notification settings.\n\nAI: use the chat box for typed commands. Local GGUF is the offline path; configured online AI is the online path.\n\nFiles: grant Axtor a workspace/storage folder when the file-agent setup asks.\n\nSee docs/FEATURE_GUIDE.md in the repository for the complete setup and troubleshooting guide.")
            .setPositiveButton("OK", null).show();
    }

    private final Runnable statusUpdater = new Runnable() {
        @Override public void run() {
            if (status != null) status.setText(VoiceServiceState.isRunning() ? "● Voice assistant active  •  Hybrid AI" : "○ Voice assistant stopped  •  Tap 🎙 to start");
            handler.postDelayed(this, 1500);
        }
    };

    @Override protected void onDestroy() { handler.removeCallbacks(statusUpdater); super.onDestroy(); }
}
