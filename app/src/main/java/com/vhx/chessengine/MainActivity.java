package com.vhx.chessengine;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

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

    private EditText apiUrl;
    private EditText token;
    private EditText boardX;
    private EditText boardY;
    private EditText boardSize;
    private EditText orientation;
    private EditText userSide;
    private EditText initialFen;
    private SeekBar depth;
    private TextView depthValue;
    private EditText multipv;

    private Switch overlay;
    private Switch autoMove;
    private Switch showEval;
    private Switch moveClassification;
    private Switch coach;
    private Switch voiceCoach;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);
        root.setBackgroundColor(Color.rgb(9, 12, 10));

        TextView title = text("CHEEZIE ANDROID", 22, Color.WHITE);
        root.addView(title);

        TextView subtitle = text(
                "Native chess-app companion • Termux engine",
                12,
                Color.rgb(165, 175, 167)
        );
        subtitle.setPadding(0, 5, 0, 22);
        root.addView(subtitle);

        TextView serviceState = text(
                "Accessibility service is required for screen capture, overlay arrows, and gesture input.",
                12,
                Color.rgb(130, 140, 132)
        );
        root.addView(serviceState);

        root.addView(text("CONNECTION", 12, Color.rgb(120, 205, 145)));

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

        root.addView(text("BOARD", 12, Color.rgb(120, 205, 145)));

        boardX = field("Board X", String.valueOf(p.getInt(BOARD_X, 0)));
        boardY = field("Board Y", String.valueOf(p.getInt(BOARD_Y, 0)));
        boardSize = field("Board size", String.valueOf(p.getInt(BOARD_SIZE, 0)));
        orientation = field(
                "Orientation: white or black",
                p.getString(ORIENTATION, "white")
        );
        userSide = field(
                "Your side: white or black",
                p.getString(USER_SIDE, "white")
        );
        initialFen = field(
                "Initial FEN",
                p.getString(INITIAL_FEN, BoardDefaults.START_FEN)
        );

        root.addView(boardX);
        root.addView(boardY);
        root.addView(boardSize);
        root.addView(orientation);
        root.addView(userSide);
        root.addView(initialFen);

        root.addView(text("ENGINE", 12, Color.rgb(120, 205, 145)));

        LinearLayout depthRow = new LinearLayout(this);
        depthRow.setOrientation(LinearLayout.HORIZONTAL);
        depthRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView depthLabel = text("Depth", 12, Color.WHITE);
        depthLabel.setPadding(0, 6, 8, 6);

        depthValue = text(String.valueOf(clamp(p.getInt(DEPTH, 12), 1, 30)), 12, Color.WHITE);
        depthValue.setGravity(Gravity.CENTER);

        depth = new SeekBar(this);
        depth.setMax(29);
        depth.setProgress(clamp(p.getInt(DEPTH, 12), 1, 30) - 1);
        depth.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        depth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                depthValue.setText(String.valueOf(progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        depthRow.addView(depthLabel);
        depthRow.addView(depth);
        depthRow.addView(depthValue);
        root.addView(depthRow);

        multipv = field(
                "MultiPV (1-10)",
                String.valueOf(clamp(p.getInt(MULTIPV, 5), 1, 10))
        );
        root.addView(multipv);

        root.addView(text("OVERLAY", 12, Color.rgb(120, 205, 145)));

        overlay = toggle(
                "Overlay",
                p.getBoolean(OVERLAY, true)
        );
        root.addView(overlay);

        root.addView(text("FEATURES", 12, Color.rgb(120, 205, 145)));

        autoMove = toggle(
                "Auto move",
                p.getBoolean(AUTO_MOVE, false)
        );
        showEval = toggle(
                "Evaluation bar",
                p.getBoolean(SHOW_EVAL, true)
        );
        moveClassification = toggle(
                "Move classification + accuracy",
                p.getBoolean(MOVE_CLASSIFICATION, true)
        );
        coach = toggle(
                "Coach message",
                p.getBoolean(COACH, true)
        );
        voiceCoach = toggle(
                "Voice coach",
                p.getBoolean(VOICE_COACH, false)
        );

        root.addView(autoMove);
        root.addView(showEval);
        root.addView(moveClassification);
        root.addView(coach);
        root.addView(voiceCoach);

        Button save = button("SAVE SETTINGS");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save);

        Button accessibility = button("ENABLE ACCESSIBILITY SERVICE");
        accessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        );
        root.addView(accessibility);

        Button test = button("TEST TERMUX ENGINE");
        test.setOnClickListener(v -> testEngine());
        root.addView(test);

        Button openTermux = button("OPEN TERMUX");
        openTermux.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://termux.com/"));
            startActivity(i);
        });
        root.addView(openTermux);

        scroll.addView(root);
        setContentView(scroll);
    }

    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setPadding(0, 8, 0, 8);
        return v;
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(105, 115, 108));
        e.setSingleLine(true);
        e.setPadding(12, 6, 12, 6);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = 8;
        e.setLayoutParams(lp);
        return e;
    }

    private Switch toggle(String label, boolean checked) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(Color.WHITE);
        s.setTextSize(12);
        s.setChecked(checked);
        s.setPadding(0, 6, 0, 6);
        s.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return s;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = 8;
        b.setLayoutParams(lp);
        return b;
    }

    private void saveSettings() {
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();

        e.putString(API_URL, apiUrl.getText().toString().trim());
        e.putString(TOKEN, token.getText().toString().trim());
        e.putInt(BOARD_X, number(boardX, 0));
        e.putInt(BOARD_Y, number(boardY, 0));
        e.putInt(BOARD_SIZE, number(boardSize, 0));
        e.putString(
                ORIENTATION,
                orientation.getText().toString().trim().toLowerCase()
        );
        e.putString(
                USER_SIDE,
                userSide.getText().toString().trim().toLowerCase()
        );
        e.putString(
                INITIAL_FEN,
                initialFen.getText().toString().trim()
        );
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

    private int number(EditText value, int fallback) {
        try {
            return Integer.parseInt(value.getText().toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void testEngine() {
        saveSettings();

        TermuxClient.checkHealth(
                this,
                apiUrl.getText().toString().trim(),
                token.getText().toString().trim()
        );
    }

    public static int pref(Context c, String key, int fallback) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(key, fallback);
    }

    public static String pref(Context c, String key, String fallback) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(key, fallback);
    }

    public static boolean pref(Context c, String key, boolean fallback) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(key, fallback);
    }
}
