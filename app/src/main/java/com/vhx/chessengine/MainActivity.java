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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    private EditText apiUrl;
    private EditText token;
    private EditText boardX;
    private EditText boardY;
    private EditText boardSize;
    private EditText orientation;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);

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

        apiUrl = field("Termux API URL", p.getString(API_URL, "http://127.0.0.1:8765"));
        token = field("Bearer token (optional)", p.getString(TOKEN, ""));
        boardX = field("Board X", String.valueOf(p.getInt(BOARD_X, 0)));
        boardY = field("Board Y", String.valueOf(p.getInt(BOARD_Y, 0)));
        boardSize = field("Board size", String.valueOf(p.getInt(BOARD_SIZE, 0)));
        orientation = field("Orientation: white or black", p.getString(ORIENTATION, "white"));

        root.addView(apiUrl);
        root.addView(token);
        root.addView(boardX);
        root.addView(boardY);
        root.addView(boardSize);
        root.addView(orientation);

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

        setContentView(root);
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
        e.putString(ORIENTATION, orientation.getText().toString().trim().toLowerCase());
        e.apply();
    }

    private int number(EditText value, int fallback) {
        try {
            return Integer.parseInt(value.getText().toString().trim());
        } catch (Exception e) {
            return fallback;
        }
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
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getInt(key, fallback);
    }

    public static String pref(Context c, String key, String fallback) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, fallback);
    }

    public static boolean pref(Context c, String key, boolean fallback) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(key, fallback);
    }
}
