package com.vhx.chessengine;

import android.graphics.Bitmap;

public final class StableBoardDetector {
    private static final int POSITION_TOLERANCE = 36;
    private static final int SIZE_TOLERANCE = 48;
    private static final int REQUIRED_HITS = 2;

    private final BoardDetector detector = new BoardDetector();
    private BoardDetector.Result candidate;
    private BoardDetector.Result stable;
    private int hits;

    public synchronized BoardDetector.Result detect(Bitmap bitmap) {
        BoardDetector.Result next = detector.detect(bitmap);
        if (next == null) return stable;

        if (candidate != null && closeTo(candidate, next)) hits++;
        else {
            candidate = next;
            hits = 1;
        }

        if (hits >= REQUIRED_HITS) {
            stable = candidate;
            hits = REQUIRED_HITS;
        }

        return stable;
    }

    private boolean closeTo(BoardDetector.Result a, BoardDetector.Result b) {
        return Math.abs(a.x - b.x) <= POSITION_TOLERANCE
                && Math.abs(a.y - b.y) <= POSITION_TOLERANCE
                && Math.abs(a.size - b.size) <= SIZE_TOLERANCE;
    }
}
