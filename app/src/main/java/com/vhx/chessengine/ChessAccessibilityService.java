package com.vhx.chessengine;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChessAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private OverlayView overlay;

    private final Runnable captureLoop = new Runnable() {
        @Override
        public void run() {
            capture();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new OverlayView(this);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        lp.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlay, lp);

        handler.post(captureLoop);
    }

    private void capture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;

        try {
            takeScreenshot(
                    getDisplayIdCompat(),
                    getMainExecutor(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult result) {
                            Bitmap b = null;
                            try {
                                b = Bitmap.wrapHardwareBuffer(
                                        result.getHardwareBuffer(),
                                        result.getColorSpace()
                                );
                                if (b == null) return;

                                Bitmap copy = b.copy(Bitmap.Config.ARGB_8888, false);
                                if (copy == null) return;

                                sendFrame(copy);
                                copy.recycle();
                            } finally {
                                if (b != null) {
                                    b.recycle();
                                }
                                result.getHardwareBuffer().close();
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            // The Android system rate-limits screenshot capture.
                        }
                    }
            );
        } catch (Exception ignored) {
        }
    }

    private int getDisplayIdCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.Display display = getDisplay();
            if (display != null) return display.getDisplayId();
        }
        return android.view.Display.DEFAULT_DISPLAY;
    }

    private void sendFrame(Bitmap bitmap) {
        String baseUrl = MainActivity.pref(this, MainActivity.API_URL, "http://127.0.0.1:8765");
        String token = MainActivity.pref(this, MainActivity.TOKEN, "");

        int x = MainActivity.pref(this, MainActivity.BOARD_X, 0);
        int y = MainActivity.pref(this, MainActivity.BOARD_Y, 0);
        int size = MainActivity.pref(this, MainActivity.BOARD_SIZE, 0);
        String orientation = MainActivity.pref(this, MainActivity.ORIENTATION, "white");

        if (size <= 0) return;

        int safeX = Math.max(0, Math.min(x, bitmap.getWidth() - 1));
        int safeY = Math.max(0, Math.min(y, bitmap.getHeight() - 1));
        int safeSize = Math.min(
                size,
                Math.min(bitmap.getWidth() - safeX, bitmap.getHeight() - safeY)
        );

        if (safeSize < 80) return;

        Bitmap board = Bitmap.createBitmap(bitmap, safeX, safeY, safeSize, safeSize);
        String image = TermuxClient.bitmapToBase64(board);
        board.recycle();

        String json = "{"
                + "\"session_id\":\"android-main\","
                + "\"image_b64\":\"" + image + "\","
                + "\"orientation\":\"" + escape(orientation) + "\""
                + "}";

        new Thread(() -> {
            try {
                String response = TermuxClient.postJson(
                        baseUrl,
                        "/detect",
                        token,
                        json
                );
                applyAnalysis(response, safeX, safeY, safeSize, orientation, baseUrl, token);
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void applyAnalysis(
            String response,
            int x,
            int y,
            int size,
            String orientation,
            String baseUrl,
            String token
    ) {
        // The Termux API returns a compact JSON document.
        // We parse the line fields needed by the overlay without a third-party JSON library.
        List<OverlayView.Arrow> arrows = new ArrayList<>();

        Pattern pattern = Pattern.compile(
                "\"bestmove_uci\"\s*:\s*\"([a-h][1-8][a-h][1-8][qrbn]?)\""
        );
        Matcher m = pattern.matcher(response);

        if (m.find()) {
            String move = m.group(1);
            int color = Color.rgb(130, 199, 95);
            arrows.add(new OverlayView.Arrow(
                    move.substring(0, 2),
                    move.substring(2, 4),
                    color,
                    Math.max(6, size / 70f)
            ));
        }

        final List<OverlayView.Arrow> out = arrows;

        handler.post(() -> {
            if (overlay != null) {
                overlay.setBoard(x, y, size);
                overlay.setArrows(out);
            }
        });

        if (MainActivity.pref(this, MainActivity.AUTO_MOVE, false) && m.find()) {
            // Auto-move dispatch is deliberately left behind an explicit user setting.
            dispatchChessMove(m.group(1), x, y, size, orientation);
        }
    }

    private void dispatchChessMove(String move, int x, int y, int size, String orientation) {
        if (move.length() < 4) return;

        float cell = size / 8f;
        String from = move.substring(0, 2);
        String to = move.substring(2, 4);

        Point fromPoint = center(from, x, y, cell, orientation);
        Point toPoint = center(to, x, y, cell, orientation);

        Path path = new Path();
        path.moveTo(fromPoint.x, fromPoint.y);
        path.lineTo(toPoint.x, toPoint.y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 280);

        GestureDescription gesture =
                new GestureDescription.Builder().addStroke(stroke).build();

        dispatchGesture(gesture, null, null);
    }

    private Point center(String square, int x, int y, float cell, String orientation) {
        int file = square.charAt(0) - 'a';
        int rank = square.charAt(1) - '1';

        if ("black".equalsIgnoreCase(orientation)) {
            file = 7 - file;
            rank = rank;
        } else {
            rank = 7 - rank;
        }

        return new Point(
                Math.round(x + (file + .5f) * cell),
                Math.round(y + (rank + .5f) * cell)
        );
    }

    private static class Point {
        final float x;
        final float y;
        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Window/app events are reserved for future app profiles and auto-start detection.
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(captureLoop);
        if (windowManager != null && overlay != null) {
            try {
                windowManager.removeView(overlay);
            } catch (Exception ignored) {
            }
        }
        overlay = null;
    }

    @Override
    public void onDestroy() {
        onInterrupt();
        super.onDestroy();
    }
}
