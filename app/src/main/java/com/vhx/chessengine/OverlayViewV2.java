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

    public OverlayViewV2(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
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
        if (next != null) arrows.addAll(next);
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        if (hidden || boardSize <= 0) return;

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

    private void drawHead(Canvas canvas, PointF from, PointF to, int color, float width) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length < 1f) return;

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
