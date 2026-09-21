package com.vhx.chessengine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OverlayViewV2 extends View {
    public static final class Arrow {
        public final String from;
        public final String to;
        public final int color;
        public final float width;

        public Arrow(String from, String to, int color, float width) {
            this.from = from;
            this.to = to;
            this.color = color;
            this.width = width;
        }
    }

    private static final int PANEL = 0xD91A201C;
    private static final int PANEL_STROKE = 0x805A6A60;
    private static final int WHITE = 0xFFF4F7F4;
    private static final int MUTED = 0xFFB5C0B8;
    private static final int GREEN = 0xFF82C75F;
    private static final int YELLOW = 0xFFE1C34A;
    private static final int ORANGE = 0xFFE58A3A;
    private static final int RED = 0xFFE05A5A;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Arrow> arrows = new ArrayList<>();

    private int boardX;
    private int boardY;
    private int boardSize;
    private String orientation = "white";

    private boolean hidden = true;
    private boolean showEval = true;
    private boolean showClassification = true;
    private boolean showCoach = true;

    private Integer evalCp;
    private Integer evalMate;
    private double accuracy = -1;
    private String classification = "";
    private String coach = "";

    public OverlayViewV2(Context context) {
        super(context);
        paint.setTypeface(Typeface.DEFAULT);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public synchronized void setBoard(int x, int y, int size, String orientation) {
        boardX = x;
        boardY = y;
        boardSize = size;
        this.orientation = orientation == null ? "white" : orientation;
        invalidate();
    }

    public synchronized void setHidden(boolean hidden) {
        this.hidden = hidden;
        invalidate();
    }

    public synchronized void setArrows(List<Arrow> next) {
        arrows.clear();
        if (next != null) {
            arrows.addAll(next);
        }
        invalidate();
    }

    public synchronized void clearAnalysis() {
        arrows.clear();
        evalCp = null;
        evalMate = null;
        accuracy = -1;
        classification = "";
        coach = "";
        invalidate();
    }

    public synchronized void setAnalysis(
            Integer evalCp,
            Integer evalMate,
            double accuracy,
            String classification,
            String coach,
            boolean showEval,
            boolean showClassification,
            boolean showCoach
    ) {
        this.evalCp = evalCp;
        this.evalMate = evalMate;
        this.accuracy = accuracy;
        this.classification = classification == null ? "" : classification;
        this.coach = coach == null ? "" : coach;
        this.showEval = showEval;
        this.showClassification = showClassification;
        this.showCoach = showCoach;
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        if (hidden || boardSize <= 0) {
            return;
        }

        drawBoardFrame(canvas);
        drawArrows(canvas);

        if (showEval) {
            drawEvalBar(canvas);
            drawEvaluationLabel(canvas);
        }

        if (showClassification && !classification.isEmpty()) {
            drawClassification(canvas);
        }

        if (showCoach && !coach.isEmpty()) {
            drawCoach(canvas);
        }
    }

    private void drawBoardFrame(Canvas canvas) {
        float inset = Math.max(2f, boardSize / 180f);
        stroke.setStrokeWidth(Math.max(2f, boardSize / 160f));
        stroke.setColor(0x705F6C64);
        canvas.drawRoundRect(
                new RectF(
                        boardX - inset,
                        boardY - inset,
                        boardX + boardSize + inset,
                        boardY + boardSize + inset
                ),
                Math.max(4f, boardSize / 90f),
                Math.max(4f, boardSize / 90f),
                stroke
        );
    }

    private void drawArrows(Canvas canvas) {
        for (int i = 0; i < arrows.size(); i++) {
            Arrow arrow = arrows.get(i);
            PointF a = center(arrow.from);
            PointF b = center(arrow.to);

            float width = Math.max(4f, arrow.width);
            stroke.setStrokeWidth(width + Math.max(2f, width * .55f));
            stroke.setColor(0x55000000);
            canvas.drawLine(a.x, a.y, b.x, b.y, stroke);

            stroke.setStrokeWidth(width);
            stroke.setColor(arrow.color);
            canvas.drawLine(a.x, a.y, b.x, b.y, stroke);

            drawHead(canvas, a, b, arrow.color, width);
        }
    }

    private void drawEvalBar(Canvas canvas) {
        float barWidth = Math.max(9f, boardSize / 40f);
        float gap = Math.max(6f, boardSize / 70f);
        float left = Math.max(2f, boardX - barWidth - gap);
        float top = boardY;
        float right = left + barWidth;
        float bottom = boardY + boardSize;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF101311);
        canvas.drawRoundRect(
                new RectF(left, top, right, bottom),
                barWidth / 2f,
                barWidth / 2f,
                paint
        );

        double score = evaluationForBar();
        double normalized = 1.0 / (1.0 + Math.exp(-score / 400.0));
        float whiteHeight = (float) (boardSize * normalized);

        paint.setColor(WHITE);
        canvas.drawRoundRect(
                new RectF(
                        left,
                        bottom - whiteHeight,
                        right,
                        bottom
                ),
                barWidth / 2f,
                barWidth / 2f,
                paint
        );

        paint.setColor(0xFF2A302C);
        canvas.drawCircle(
                (left + right) / 2f,
                bottom - boardSize * .5f,
                Math.max(1.5f, barWidth * .13f),
                paint
        );
    }

    private double evaluationForBar() {
        if (evalMate != null) {
            return evalMate > 0 ? 1200.0 : -1200.0;
        }

        return evalCp == null ? 0.0 : Math.max(-1200, Math.min(1200, evalCp));
    }

    private void drawEvaluationLabel(Canvas canvas) {
        String value;

        if (evalMate != null) {
            value = (evalMate < 0 ? "-" : "+") + "M" + Math.abs(evalMate);
        } else if (evalCp == null) {
            value = "0.00";
        } else {
            value = String.format(Locale.US, "%+.2f", evalCp / 100.0);
        }

        float size = Math.max(13f, boardSize / 30f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(size);
        paint.setColor(WHITE);
        paint.setStyle(Paint.Style.FILL);

        float width = paint.measureText(value);
        float x = boardX + 8f;
        float y = Math.max(size + 5f, boardY - 10f);

        drawPanel(canvas, x - 6f, y - size - 6f, x + width + 6f, y + 6f, 10f);
        paint.setColor(WHITE);
        canvas.drawText(value, x, y, paint);
    }

    private void drawClassification(Canvas canvas) {
        String label = classification.trim();
        if (accuracy >= 0) {
            label += "  " + String.format(Locale.US, "%.1f%%", accuracy);
        }

        float size = Math.max(11f, boardSize / 38f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(size);
        float width = paint.measureText(label);
        float x = boardX + boardSize - width - 14f;
        float y = Math.max(size + 5f, boardY - 10f);

        drawPanel(canvas, x - 8f, y - size - 6f, x + width + 8f, y + 6f, 10f);
        paint.setColor(classificationColor(classification));
        canvas.drawText(label, x, y, paint);
    }

    private void drawCoach(Canvas canvas) {
        float size = Math.max(11f, boardSize / 46f);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(size);

        float maxWidth = Math.max(130f, boardSize - 20f);
        String text = trimText(coach, maxWidth);

        float x = boardX + 10f;
        float y = boardY + boardSize + size + 16f;
        float width = Math.min(maxWidth, paint.measureText(text) + 16f);

        drawPanel(canvas, x - 6f, y - size - 7f, x + width, y + 7f, 10f);
        paint.setColor(MUTED);
        canvas.drawText(text, x, y, paint);
    }

    private int classificationColor(String value) {
        String text = value == null ? "" : value.toLowerCase(Locale.US);

        if (text.contains("blunder")) {
            return RED;
        }
        if (text.contains("mistake")) {
            return ORANGE;
        }
        if (text.contains("inaccuracy")) {
            return YELLOW;
        }
        if (text.contains("good")
                || text.contains("best")
                || text.contains("excellent")
                || text.contains("book")) {
            return GREEN;
        }

        return WHITE;
    }

    private void drawPanel(
            Canvas canvas,
            float left,
            float top,
            float right,
            float bottom,
            float radius
    ) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL);
        canvas.drawRoundRect(
                new RectF(left, top, right, bottom),
                radius,
                radius,
                paint
        );

        stroke.setStrokeWidth(Math.max(1f, boardSize / 500f));
        stroke.setColor(PANEL_STROKE);
        canvas.drawRoundRect(
                new RectF(left, top, right, bottom),
                radius,
                radius,
                stroke
        );
    }

    private String trimText(String text, float maxWidth) {
        if (paint.measureText(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        String value = text;

        while (value.length() > 8
                && paint.measureText(value + suffix) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }

        return value + suffix;
    }

    private PointF center(String square) {
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

        float cell = boardSize / 8f;

        return new PointF(
                boardX + (screenFile + .5f) * cell,
                boardY + (screenRank + .5f) * cell
        );
    }

    private void drawHead(
            Canvas canvas,
            PointF from,
            PointF to,
            int color,
            float width
    ) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length < 1f) {
            return;
        }

        float ux = dx / length;
        float uy = dy / length;
        float size = Math.max(12f, width * 2.8f);
        float baseX = to.x - ux * size;
        float baseY = to.y - uy * size;

        Path path = new Path();
        path.moveTo(to.x, to.y);
        path.lineTo(
                baseX - uy * size * .55f,
                baseY + ux * size * .55f
        );
        path.lineTo(
                baseX + uy * size * .55f,
                baseY - ux * size * .55f
        );
        path.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55000000);
        canvas.drawPath(path, paint);

        canvas.save();
        canvas.translate(-ux * 1.5f, -uy * 1.5f);
        paint.setColor(color);
        canvas.drawPath(path, paint);
        canvas.restore();
    }
}
