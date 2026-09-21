package com.vhx.chessengine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
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

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Arrow> arrows = new ArrayList<>();

    private int boardX;
    private int boardY;
    private int boardSize;
    private String orientation = "white";

    private boolean hidden;
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

    private void drawArrows(Canvas canvas) {
        for (Arrow arrow : arrows) {
            PointF a = center(arrow.from);
            PointF b = center(arrow.to);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(arrow.width);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(arrow.color);

            canvas.drawLine(a.x, a.y, b.x, b.y, paint);
            drawHead(canvas, a, b, arrow.color, arrow.width);
        }
    }

    private void drawEvalBar(Canvas canvas) {
        float barWidth = Math.max(8f, boardSize / 42f);
        float barLeft = Math.max(1f, boardX - barWidth - 8f);
        float barRight = barLeft + barWidth;
        float top = boardY;
        float bottom = boardY + boardSize;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(35, 35, 35));
        canvas.drawRect(barLeft, top, barRight, bottom, paint);

        double score = evaluationForBar();
        double normalized = 1.0 / (1.0 + Math.exp(-score / 400.0));
        float whiteHeight = (float) (boardSize * normalized);

        paint.setColor(Color.WHITE);
        canvas.drawRect(
                barLeft,
                bottom - whiteHeight,
                barRight,
                bottom,
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
            value = "M" + Math.abs(evalMate);
            if (evalMate < 0) {
                value = "-" + value;
            } else {
                value = "+" + value;
            }
        } else if (evalCp == null) {
            value = "0.00";
        } else {
            value = String.format(
                    Locale.US,
                    "%+.2f",
                    evalCp / 100.0
            );
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Math.max(13f, boardSize / 32f));
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setColor(Color.WHITE);

        float x = boardX + 6f;
        float y = Math.max(18f, boardY - 8f);

        canvas.drawText(value, x, y, paint);
    }

    private void drawClassification(Canvas canvas) {
        String text = classification;

        if (accuracy >= 0) {
            text += "  " + String.format(Locale.US, "%.1f%%", accuracy);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Math.max(12f, boardSize / 38f));
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setColor(Color.WHITE);

        float x = boardX + boardSize - estimateTextWidth(text) - 6f;
        float y = Math.max(18f, boardY - 8f);

        canvas.drawText(text, x, y, paint);
    }

    private void drawCoach(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Math.max(11f, boardSize / 46f));
        paint.setTypeface(android.graphics.Typeface.DEFAULT);

        String text = coach;
        float maxWidth = Math.max(100f, boardSize - 12f);

        if (estimateTextWidth(text) > maxWidth) {
            text = trimText(text, maxWidth);
        }

        paint.setColor(Color.WHITE);

        float x = boardX + 6f;
        float y = boardY + boardSize + Math.max(18f, boardSize / 24f);

        canvas.drawText(text, x, y, paint);
    }

    private float estimateTextWidth(String text) {
        return paint.measureText(text);
    }

    private String trimText(String text, float maxWidth) {
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
        float size = Math.max(13f, width * 2.5f);

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
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }
}
