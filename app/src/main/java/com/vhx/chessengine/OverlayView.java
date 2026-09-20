package com.vhx.chessengine;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {
    public static class Arrow {
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
    private boolean hidden;

    public OverlayView(android.content.Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setWillNotDraw(false);
    }

    public void setBoard(int x, int y, int size) {
        boardX = x;
        boardY = y;
        boardSize = size;
        invalidate();
    }

    public void setHidden(boolean hidden) {
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
        super.onDraw(canvas);
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

        float cell = boardSize / 8f;
        float x = boardX + (file + 0.5f) * cell;
        float y = boardY + (7 - rank + 0.5f) * cell;
        return new PointF(x, y);
    }

    private void drawHead(Canvas canvas, PointF from, PointF to, int color, float width) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;

        float ux = dx / len;
        float uy = dy / len;
        float size = Math.max(12, width * 2.4f);

        float bx = to.x - ux * size;
        float by = to.y - uy * size;

        Path p = new Path();
        p.moveTo(to.x, to.y);
        p.lineTo(bx - uy * size * .55f, by + ux * size * .55f);
        p.lineTo(bx + uy * size * .55f, by - ux * size * .55f);
        p.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(p, paint);
    }
}
