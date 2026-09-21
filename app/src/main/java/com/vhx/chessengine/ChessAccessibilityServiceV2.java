package com.vhx.chessengine;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import android.speech.tts.TextToSpeech;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class ChessAccessibilityServiceV2 extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private OverlayViewV2 overlay;
    private NativeChessEngine nativeEngine;

    private String lastAutoMove = null;
    private TextToSpeech textToSpeech;
    private String lastCoachKey = null;
    private String lastAutoMoveKey = null;
    private boolean captureBusy = false;
    private volatile boolean requestBusy = false;

    private void updateOverlayVisibility() {
        if (overlay != null) {
            overlay.setHidden(!MainActivity.pref(this, MainActivity.OVERLAY, true));
        }
    }

    private final Runnable captureLoop = new Runnable() {
        @Override
        public void run() {
            capture();
            updateOverlayVisibility();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new OverlayViewV2(this);
        overlay.setHidden(!MainActivity.pref(this, MainActivity.OVERLAY, true));
        try {
            nativeEngine = new NativeChessEngine(prepareNativeEngineDirectory());
        } catch (Exception ignored) {
            nativeEngine = null;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlay, params);

        textToSpeech = new TextToSpeech(
                this,
                status -> {
                    if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                        textToSpeech.setLanguage(java.util.Locale.US);
                    }
                }
        );

        handler.post(captureLoop);
    }

    private void capture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || captureBusy) {
            return;
        }

        captureBusy = true;

        try {
            takeScreenshot(
                    getDisplayIdCompat(),
                    getMainExecutor(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult result) {
                            HardwareBuffer hardwareBuffer = result.getHardwareBuffer();
                            Bitmap wrapped = null;

                            try {
                                wrapped = Bitmap.wrapHardwareBuffer(
                                        hardwareBuffer,
                                        result.getColorSpace()
                                );

                                if (wrapped == null) {
                                    return;
                                }

                                Bitmap copy = wrapped.copy(
                                        Bitmap.Config.ARGB_8888,
                                        false
                                );

                                if (copy == null) {
                                    return;
                                }

                                sendFrame(copy);
                                copy.recycle();
                            } finally {
                                if (wrapped != null) {
                                    wrapped.recycle();
                                }

                                hardwareBuffer.close();
                                captureBusy = false;
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            captureBusy = false;
                        }
                    }
            );
        } catch (Exception ignored) {
            captureBusy = false;
        }
    }

    private int getDisplayIdCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.Display display = getDisplay();
            if (display != null) {
                return display.getDisplayId();
            }
        }

        return android.view.Display.DEFAULT_DISPLAY;
    }

    private void sendFrame(Bitmap screen) {
        updateOverlayVisibility();

        if (requestBusy) {
            return;
        }

        String baseUrl = MainActivity.pref(
                this,
                MainActivity.API_URL,
                "http://127.0.0.1:8765"
        );

        String token = MainActivity.pref(
                this,
                MainActivity.TOKEN,
                ""
        );

        int x = MainActivity.pref(this, MainActivity.BOARD_X, 0);
        int y = MainActivity.pref(this, MainActivity.BOARD_Y, 0);
        int size = MainActivity.pref(this, MainActivity.BOARD_SIZE, 0);

        String orientation = MainActivity.pref(
                this,
                MainActivity.ORIENTATION,
                "white"
        );

        if (size <= 0) {
            return;
        }

        int safeX = Math.max(0, Math.min(x, screen.getWidth() - 1));
        int safeY = Math.max(0, Math.min(y, screen.getHeight() - 1));

        int safeSize = Math.min(
                size,
                Math.min(
                        screen.getWidth() - safeX,
                        screen.getHeight() - safeY
                )
        );

        if (safeSize < 80) {
            return;
        }

        if (overlay != null) {
            handler.post(() -> {
                overlay.setBoard(
                        safeX,
                        safeY,
                        safeSize,
                        orientation
                );
                overlay.clearAnalysis();
            });
        }

        String cells = sampleCells(screen, safeX, safeY, safeSize);

        String initialFen = MainActivity.pref(
                this,
                MainActivity.INITIAL_FEN,
                BoardDefaults.START_FEN
        );

        int multipv = MainActivity.pref(this, MainActivity.MULTIPV, 5);
        int depth = MainActivity.pref(this, MainActivity.DEPTH, 12);

        String json =
                "{"
                        + "\"session_id\":\"android-main\","
                        + "\"cells\":" + cells + ","
                        + "\"initial_fen\":\"" + escape(initialFen) + "\","
                        + "\"orientation\":\"" + escape(orientation) + "\","
                        + "\"multipv\":" + clamp(multipv, 1, 10) + ","
                        + "\"depth\":" + clamp(depth, 1, 30)
                        + "}";

        requestBusy = true;

        new Thread(() -> {
            try {
                String response = TermuxClient.postJson(
                        baseUrl,
                        "/detect",
                        token,
                        json
                );

                applyResponse(
                        response,
                        safeX,
                        safeY,
                        safeSize,
                        orientation
                );
            } catch (Exception ignored) {
            } finally {
                requestBusy = false;
            }
        }).start();
    }

    private String sampleCells(Bitmap bitmap, int x, int y, int size) {
        StringBuilder json = new StringBuilder("[");
        float cell = size / 8f;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                float left = x + col * cell + cell * .16f;
                float top = y + row * cell + cell * .16f;
                float right = x + (col + 1) * cell - cell * .16f;
                float bottom = y + (row + 1) * cell - cell * .16f;

                long sumR = 0;
                long sumG = 0;
                long sumB = 0;
                long sumGray = 0;
                long sumGray2 = 0;
                int count = 0;

                int sx = Math.max(1, (int) (cell / 9f));

                for (int py = (int) top; py < (int) bottom; py += sx) {
                    for (int px = (int) left; px < (int) right; px += sx) {
                        int pixel = bitmap.getPixel(px, py);

                        int r = (pixel >> 16) & 255;
                        int g = (pixel >> 8) & 255;
                        int b = pixel & 255;
                        int gray = (r + g + b) / 3;

                        sumR += r;
                        sumG += g;
                        sumB += b;
                        sumGray += gray;
                        sumGray2 += (long) gray * gray;
                        count++;
                    }
                }

                double meanR = sumR / (double) Math.max(1, count);
                double meanG = sumG / (double) Math.max(1, count);
                double meanB = sumB / (double) Math.max(1, count);
                double meanGray = sumGray / (double) Math.max(1, count);
                double variance =
                        sumGray2 / (double) Math.max(1, count)
                                - meanGray * meanGray;

                if (json.length() > 1) {
                    json.append(',');
                }

                json.append(round(meanR)).append(',')
                        .append(round(meanG)).append(',')
                        .append(round(meanB)).append(',')
                        .append(round(Math.max(0, variance)));
            }
        }

        json.append(']');
        return json.toString();
    }

    private String round(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private void applyResponse(
            String response,
            int x,
            int y,
            int size,
            String orientation
    ) {
        try {
            JSONObject root = new JSONObject(response);

            String fen = root.optString("fen", "");
            int nativeDepth = MainActivity.pref(this, MainActivity.DEPTH, 12);
            String nativeBestMove = "";
            if (nativeEngine != null && !fen.isEmpty()) {
                try {
                    if (nativeEngine.setPosition(fen)
                            && nativeEngine.analyze(nativeDepth, 0, 1, 128, 1)) {
                        nativeBestMove = nativeEngine.getBestMove();
                    }
                } catch (Exception ignored) {
                }
            }

            JSONArray lines = root.optJSONArray("lines");
            List<OverlayViewV2.Arrow> next = new ArrayList<>();

            int[] colors = {
                    0xFF82C75F,
                    0xFF5EA23F,
                    0xFFE1C34A,
                    0xFFE58A3A,
                    0xFFE05A5A
            };

            if (lines != null) {
                for (int i = 0; i < Math.min(10, lines.length()); i++) {
                    JSONObject line = lines.optJSONObject(i);
                    if (line == null) {
                        continue;
                    }

                    String move = line.optString("bestmove_uci", "");
                    if (i == 0 && isValidUciMove(nativeBestMove)) {
                        move = nativeBestMove;
                    }
                    if (!isValidUciMove(move)) {
                        continue;
                    }

                    next.add(
                            new OverlayViewV2.Arrow(
                                    move.substring(0, 2),
                                    move.substring(2, 4),
                                    colors[Math.min(i, colors.length - 1)],
                                    Math.max(5, size / 75f)
                            )
                    );

                    if (next.size() >= 5) {
                        break;
                    }
                }
            }

            Integer evalCp = root.has("score_cp_white")
                    && !root.isNull("score_cp_white")
                    ? root.optInt("score_cp_white")
                    : null;

            Integer evalMate = root.has("mate_white")
                    && !root.isNull("mate_white")
                    ? root.optInt("mate_white")
                    : null;

            double accuracy = -1;
            String classification = "";
            String coach = "";

            JSONObject moveAnalysis = root.optJSONObject("move_analysis");

            if (moveAnalysis != null) {
                if (moveAnalysis.has("accuracy")
                        && !moveAnalysis.isNull("accuracy")) {
                    accuracy = moveAnalysis.optDouble("accuracy", -1);
                }

                classification = moveAnalysis.optString(
                        "classification",
                        ""
                );
                coach = moveAnalysis.optString("coach", "");
            }

            boolean overlayEnabled = MainActivity.pref(
                    this,
                    MainActivity.OVERLAY,
                    true
            );

            boolean showEval = MainActivity.pref(
                    this,
                    MainActivity.SHOW_EVAL,
                    true
            );

            boolean showClassification = MainActivity.pref(
                    this,
                    MainActivity.MOVE_CLASSIFICATION,
                    true
            );

            boolean showCoach = MainActivity.pref(
                    this,
                    MainActivity.COACH,
                    true
            );

            boolean changed = root.optBoolean("changed", false);
            boolean gameOver = root.optBoolean("game_over", false);
            String side = root.optString("side", "");
            String best = isValidUciMove(nativeBestMove)
                    ? nativeBestMove
                    : root.optString("bestmove_uci", "");
            String detectedMove = root.optString("move_uci", "");
            boolean voiceCoach = MainActivity.pref(
                    this,
                    MainActivity.VOICE_COACH,
                    false
            );

            String coachKey = root.optString("previous_fen", "")
                    + ":" + detectedMove + ":" + classification;

            if (
                    changed
                            && voiceCoach
                            && !coach.isEmpty()
                            && !coachKey.equals(lastCoachKey)
            ) {
                lastCoachKey = coachKey;
                speakCoach(coach);
            }

            final Integer overlayEvalCp = evalCp;
            final Integer overlayEvalMate = evalMate;
            final double overlayAccuracy = accuracy;
            final String overlayClassification = classification;
            final String overlayCoach = coach;

            handler.post(() -> {
                if (overlay != null) {
                    overlay.setHidden(!overlayEnabled);
                    overlay.setBoard(x, y, size, orientation);
                    overlay.setArrows(next);
                    overlay.setAnalysis(
                            overlayEvalCp,
                            overlayEvalMate,
                            overlayAccuracy,
                            overlayClassification,
                            overlayCoach,
                            showEval,
                            showClassification,
                            showCoach
                    );
                }
            });

            String userSide = MainActivity.pref(
                    this,
                    MainActivity.USER_SIDE,
                    "white"
            );

            boolean autoMove = MainActivity.pref(
                    this,
                    MainActivity.AUTO_MOVE,
                    false
            );

            boolean correctTurn =
                    side.equalsIgnoreCase(userSide)
                            && !gameOver;

            // Promotion moves need an explicit promotion-piece interaction
            // and are therefore intentionally excluded from auto input here.
            boolean normalMove = best.length() == 4;
            String autoMoveKey = fen + ":" + best;

            if (
                    autoMove
                            && changed
                            && correctTurn
                            && normalMove
                            && !autoMoveKey.equals(lastAutoMoveKey)
            ) {
                lastAutoMoveKey = autoMoveKey;
                dispatchChessMove(
                        best,
                        x,
                        y,
                        size,
                        orientation
                );
            }
        } catch (Exception ignored) {
        }
    }

    private String prepareNativeEngineDirectory() throws Exception {
        File directory = new File(getFilesDir(), "engine");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create engine directory");
        }

        File network = new File(directory, "nn-1a298aa575a0.nnue");
        if (!network.exists() || network.length() == 0) {
            try (InputStream input = getAssets().open("nn-1a298aa575a0.nnue");
                 FileOutputStream output = new FileOutputStream(network)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
        }

        return directory.getAbsolutePath();
    }

    private boolean isValidUciMove(String move) {
        if (move == null || (move.length() != 4 && move.length() != 5)) {
            return false;
        }

        return isValidSquare(move.substring(0, 2))
                && isValidSquare(move.substring(2, 4))
                && !move.substring(0, 2).equals(move.substring(2, 4));
    }

    private boolean isValidSquare(String square) {
        if (square == null || square.length() != 2) {
            return false;
        }

        char file = square.charAt(0);
        char rank = square.charAt(1);

        return file >= 'a' && file <= 'h'
                && rank >= '1' && rank <= '8';
    }

    private void speakCoach(String message) {
        if (textToSpeech == null || message == null || message.isEmpty()) {
            return;
        }

        textToSpeech.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "cheeezie-coach"
        );
    }

    private void dispatchChessMove(
            String move,
            int x,
            int y,
            int size,
            String orientation
    ) {
        String from = move.substring(0, 2);
        String to = move.substring(2, 4);
        float cell = size / 8f;

        Point a = center(from, x, y, cell, orientation);
        Point b = center(to, x, y, cell, orientation);

        Path path = new Path();
        path.moveTo(a.x, a.y);
        path.lineTo(b.x, b.y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(
                        path,
                        0,
                        280
                );

        dispatchGesture(
                new GestureDescription.Builder()
                        .addStroke(stroke)
                        .build(),
                null,
                null
        );
    }

    private Point center(
            String square,
            int x,
            int y,
            float cell,
            String orientation
    ) {
        int file = square.charAt(0) - 'a';
        int rank = square.charAt(1) - '1';

        int screenFile;
        int screenRank;

        if ("black".equalsIgnoreCase(orientation)) {
            screenFile = 7 - file;
            screenRank = rank;
        } else {
            screenFile = file;
            screenRank = 7 - rank;
        }

        return new Point(
                x + (screenFile + .5f) * cell,
                y + (screenRank + .5f) * cell
        );
    }

    private static final class Point {
        final float x;
        final float y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // App-specific window detection will be added through profiles.
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

        if (nativeEngine != null) {
            try {
                nativeEngine.close();
            } catch (Exception ignored) {
            }
            nativeEngine = null;
        }

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }

        super.onDestroy();
    }
}
