package com.vhx.chessengine;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.view.accessibility.AccessibilityManager;
import android.app.AlertDialog;

import java.util.List;

public class MainActivity extends Activity {
    public static final String PREFS = "cheeezie";
    public static final String API_URL = "api_url";
    public static final String TOKEN = "token";
    public static final String BOARD_X = "board_x";
    public static final String BOARD_Y = "board_y";
    public static final String BOARD_SIZE = "board_size";
    public static final String ORIENTATION = "orientation";
    public static final String AUTO_MOVE = "auto_move";
    public static final String USER_SIDE = "user_side";
    public static final String INITIAL_FEN = "initial_fen";
    public static final String DEPTH = "depth";
    public static final String MULTIPV = "multipv";
    public static final String SHOW_EVAL = "show_eval";
    public static final String OVERLAY = "overlay";
    public static final String MOVE_CLASSIFICATION = "move_classification";
    public static final String COACH = "coach";
    public static final String VOICE_COACH = "voice_coach";
    public static final String ANALYZER_RUNNING = "analyzer_running";
    public static final String CAPTURE_STATUS = "capture_status";

    private static final int BG = Color.rgb(8, 11, 10);
    private static final int CARD = Color.rgb(18, 23, 20);
    private static final int CARD_ALT = Color.rgb(22, 28, 24);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(153, 164, 156);
    private static final int ACCENT = Color.rgb(130, 199, 95);
    private static final int BORDER = Color.rgb(45, 57, 49);

    private EditText apiUrl;
    private EditText token;
    private SeekBar depth;
    private TextView depthValue;
    private EditText multipv;

    private Switch overlay;
    private Switch autoMove;
    private Switch showEval;
    private Switch moveClassification;
    private Switch coach;
    private Switch voiceCoach;

    private Button analyzerButton;
    private TextView analyzerState;
    private TextView serviceState;
    private TextView captureState;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        DebugLog.info(this, "MainActivity", "Application opened");

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setBackgroundColor(BG);

        TextView title = text("CHEEZIE", 28, TEXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = text("Universal Android chess analyzer", 13, MUTED);
        subtitle.setPadding(0, 0, 0, dp(18));
        root.addView(subtitle);

        LinearLayout analyzerCard = card();
        TextView analyzerTitle = text("ANALYZER", 12, ACCENT);
        analyzerTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        analyzerCard.addView(analyzerTitle);

        analyzerState = text("", 15, TEXT);
        analyzerState.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        analyzerState.setPadding(0, dp(10), 0, dp(2));
        analyzerCard.addView(analyzerState);

        TextView analyzerHint = text(
                "Works from the visible chessboard instead of depending on a specific chess app.",
                11,
                MUTED
        );
        analyzerHint.setPadding(0, 0, 0, dp(10));
        analyzerCard.addView(analyzerHint);

        analyzerButton = primaryButton("");
        analyzerButton.setOnClickListener(v -> toggleAnalyzer());
        analyzerCard.addView(analyzerButton);

        serviceState = text("", 11, MUTED);
        serviceState.setPadding(0, dp(9), 0, 0);
        analyzerCard.addView(serviceState);

        captureState = text("", 11, MUTED);
        captureState.setPadding(0, dp(5), 0, 0);
        analyzerCard.addView(captureState);

        root.addView(analyzerCard);

        root.addView(section("CONNECTION"));

        apiUrl = field(
                "Termux API URL",
                p.getString(API_URL, "http://127.0.0.1:8765")
        );
        token = field(
                "Bearer token (optional)",
                p.getString(TOKEN, "")
        );
        root.addView(apiUrl);
        root.addView(token);

        root.addView(section("BOARD"));
        TextView boardInfo = text(
                "Automatic board detection is app-independent. No coordinates or board size are required.",
                11,
                MUTED
        );
        boardInfo.setPadding(0, 0, 0, dp(8));
        root.addView(boardInfo);

        Button cameraDetector = secondaryButton("CAMERA BOARD DETECTOR");
        cameraDetector.setOnClickListener(v ->
                startActivity(new Intent(this, CameraActivity.class))
        );
        root.addView(cameraDetector);

        root.addView(section("ENGINE"));

        LinearLayout depthRow = row();
        TextView depthLabel = text("Depth", 12, TEXT);
        depthLabel.setLayoutParams(new LinearLayout.LayoutParams(dp(48), -2));

        depthValue = text(
                String.valueOf(clamp(p.getInt(DEPTH, 12), 1, 30)),
                12,
                TEXT
        );
        depthValue.setGravity(Gravity.CENTER);
        depthValue.setLayoutParams(new LinearLayout.LayoutParams(dp(34), -2));

        depth = new SeekBar(this);
        depth.setMax(29);
        depth.setProgress(clamp(p.getInt(DEPTH, 12), 1, 30) - 1);
        depth.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        depth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { depthValue.setText(String.valueOf(progress + 1)); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        depthRow.addView(depthLabel);
        depthRow.addView(depth);
        depthRow.addView(depthValue);
        root.addView(depthRow);

        multipv = field("MultiPV (1-10)", String.valueOf(clamp(p.getInt(MULTIPV, 5), 1, 10)));
        root.addView(multipv);

        root.addView(section("OVERLAY"));
        overlay = toggle("Show overlay", p.getBoolean(OVERLAY, true));
        root.addView(overlay);

        root.addView(section("FEATURES"));
        autoMove = toggle("Auto move", p.getBoolean(AUTO_MOVE, false));
        showEval = toggle("Evaluation", p.getBoolean(SHOW_EVAL, true));
        moveClassification = toggle("Move classification + accuracy", p.getBoolean(MOVE_CLASSIFICATION, true));
        coach = toggle("Coach message", p.getBoolean(COACH, true));
        voiceCoach = toggle("Voice coach", p.getBoolean(VOICE_COACH, false));
        root.addView(autoMove);
        root.addView(showEval);
        root.addView(moveClassification);
        root.addView(coach);
        root.addView(voiceCoach);

        Button save = primaryButton("SAVE SETTINGS");
        save.setOnClickListener(v -> { saveSettings(); updateStatus(); });
        root.addView(save);

        Button accessibility = secondaryButton("ACCESSIBILITY SETTINGS");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);

        TextView overlayInfo = text(
                "Overlay uses the connected accessibility service. It does not require Display over other apps.",
                11,
                MUTED
        );
        overlayInfo.setPadding(0, dp(8), 0, 0);
        root.addView(overlayInfo);

        Button logs = secondaryButton("VIEW ERROR LOGS");
        logs.setOnClickListener(v -> showLogs());
        root.addView(logs);

        Button test = secondaryButton("TEST TERMUX ENGINE");
        test.setOnClickListener(v -> testEngine());
        root.addView(test);

        Button openTermux = secondaryButton("OPEN TERMUX");
        openTermux.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://termux.com/"));
            startActivity(i);
        });
        root.addView(openTermux);

        scroll.addView(root);
        setContentView(scroll);
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (analyzerButton != null) updateStatus();
    }

    private void showLogs() {
        TextView logView = new TextView(this);
        logView.setText(DebugLog.get(this));
        logView.setTextColor(TEXT);
        logView.setTextSize(11);
        logView.setPadding(dp(14), dp(12), dp(14), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("CHEEZIE ERROR LOG")
                .setView(scroll)
                .setNegativeButton("CLOSE", null)
                .setNeutralButton("CLEAR", (d, which) -> {
                    DebugLog.clear(this);
                    showLogs();
                })
                .create();
        dialog.show();
    }

    private void toggleAnalyzer() {
        saveSettings();
        if (!isAccessibilityServiceEnabled()) {
            DebugLog.warn(this, "MainActivity", "Analyzer start requested but accessibility service is disabled");
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        boolean running = !pref(this, ANALYZER_RUNNING, false);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ANALYZER_RUNNING, running).apply();
        DebugLog.info(this, "MainActivity", running ? "Analyzer started" : "Analyzer stopped");
        updateStatus();
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        String target = getPackageName() + "/" + ChessAccessibilityServiceV2.class.getName();
        for (AccessibilityServiceInfo info : services) {
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                String component = info.getResolveInfo().serviceInfo.packageName + "/" + info.getResolveInfo().serviceInfo.name;
                if (target.equals(component)) return true;
            }
        }
        return false;
    }

    private void updateStatus() {
        boolean running = pref(this, ANALYZER_RUNNING, false);
        boolean serviceEnabled = isAccessibilityServiceEnabled();
        analyzerState.setText(running ? "Analyzer running" : "Analyzer stopped");
        analyzerState.setTextColor(running ? ACCENT : TEXT);
        analyzerButton.setText(running ? "STOP ANALYZER" : "START ANALYZER");
        if (serviceEnabled) {
            serviceState.setText(running ? "Accessibility service enabled. Automatic capture and overlay are active." : "Accessibility service enabled. Ready to analyze.");
        } else {
            serviceState.setText("Accessibility service is disabled. Enable it to analyze any supported chess app.");
        }
        String capture = pref(this, CAPTURE_STATUS, "Waiting for analyzer.");
        captureState.setText("Capture: " + capture);
    }

    private TextView section(String value) { TextView v = text(value, 11, ACCENT); v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); v.setPadding(0, dp(22), 0, dp(8)); return v; }
    private TextView text(String value, int size, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setPadding(0, dp(6), 0, dp(6)); return v; }
    private LinearLayout card() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(16), dp(15), dp(16), dp(15)); layout.setBackground(round(CARD, BORDER, 16)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(4); layout.setLayoutParams(lp); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private EditText field(String hint, String value) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setTextColor(TEXT); e.setHintTextColor(Color.rgb(105, 116, 108)); e.setSingleLine(true); e.setTextSize(12); e.setPadding(dp(12), 0, dp(12), 0); e.setBackground(round(CARD_ALT, BORDER, 12)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46)); lp.bottomMargin = dp(8); e.setLayoutParams(lp); return e; }
    private Switch toggle(String label, boolean checked) { Switch s = new Switch(this); s.setText(label); s.setTextColor(TEXT); s.setTextSize(12); s.setChecked(checked); s.setPadding(dp(2), dp(5), dp(2), dp(5)); s.setLayoutParams(new LinearLayout.LayoutParams(-1, -2)); return s; }
    private Button primaryButton(String label) { Button b = baseButton(label); b.setBackground(round(ACCENT, ACCENT, 13)); b.setTextColor(Color.rgb(8, 12, 9)); return b; }
    private Button secondaryButton(String label) { Button b = baseButton(label); b.setBackground(round(CARD_ALT, BORDER, 13)); b.setTextColor(TEXT); return b; }
    private Button baseButton(String label) { Button b = new Button(this); b.setText(label); b.setTextSize(11); b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setMinHeight(0); b.setMinimumHeight(0); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48)); lp.topMargin = dp(9); b.setLayoutParams(lp); return b; }
    private GradientDrawable round(int fill, int stroke, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), stroke); return drawable; }

    private void saveSettings() {
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        e.putString(API_URL, apiUrl.getText().toString().trim());
        e.putString(TOKEN, token.getText().toString().trim());
        e.putInt(DEPTH, clamp(depth.getProgress() + 1, 1, 30));
        e.putBoolean(OVERLAY, overlay.isChecked());
        e.putInt(MULTIPV, clamp(number(multipv, 5), 1, 10));
        e.putBoolean(AUTO_MOVE, autoMove.isChecked());
        e.putBoolean(SHOW_EVAL, showEval.isChecked());
        e.putBoolean(MOVE_CLASSIFICATION, moveClassification.isChecked());
        e.putBoolean(COACH, coach.isChecked());
        e.putBoolean(VOICE_COACH, voiceCoach.isChecked());
        e.apply();
    }

    private int number(EditText value, int fallback) { try { return Integer.parseInt(value.getText().toString().trim()); } catch (Exception e) { return fallback; } }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void testEngine() {
        saveSettings();
        DebugLog.info(this, "MainActivity", "Testing Termux engine at " + apiUrl.getText().toString().trim());
        TermuxClient.checkHealth(this, apiUrl.getText().toString().trim(), token.getText().toString().trim());
    }

    public static int pref(Context c, String key, int fallback) { return c.getSharedPreferences(PREFS, MODE_PRIVATE).getInt(key, fallback); }
    public static String pref(Context c, String key, String fallback) { return c.getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, fallback); }
    public static boolean pref(Context c, String key, boolean fallback) { return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(key, fallback); }
}
