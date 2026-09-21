package com.vhx.chessengine;

import android.content.Context;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DebugLog {
    private static final String PREF = "cheeezie_debug";
    private static final String KEY = "entries";
    private static final int MAX_ENTRIES = 250;

    private DebugLog() {
    }

    public static void info(Context context, String tag, String message) {
        write(context, "INFO", tag, message, null);
    }

    public static void error(Context context, String tag, String message, Throwable error) {
        write(context, "ERROR", tag, message, error);
    }

    public static void warn(Context context, String tag, String message) {
        write(context, "WARN", tag, message, null);
    }

    private static synchronized void write(Context context, String level, String tag, String message, Throwable error) {
        String detail = message == null ? "" : message;
        if (error != null) {
            detail += " | " + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        }
        String entry = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())
                + " [" + level + "] " + tag + ": " + detail;

        if ("ERROR".equals(level)) Log.e(tag, detail, error);
        else if ("WARN".equals(level)) Log.w(tag, detail);
        else Log.i(tag, detail);

        String existing = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "");
        String[] lines = existing.isEmpty() ? new String[0] : existing.split("\\n");
        StringBuilder out = new StringBuilder();
        int start = Math.max(0, lines.length - MAX_ENTRIES + 1);
        for (int i = start; i < lines.length; i++) {
            if (out.length() > 0) out.append('\n');
            out.append(lines[i]);
        }
        if (out.length() > 0) out.append('\n');
        out.append(entry);
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, out.toString())
                .apply();
    }

    public static String get(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "No logs yet.");
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }
}
