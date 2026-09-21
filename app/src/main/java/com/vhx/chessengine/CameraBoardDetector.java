package com.vhx.chessengine;

import android.graphics.Bitmap;

public final class CameraBoardDetector {
    public static final class Result {
        public final int x;
        public final int y;
        public final int size;
        public final double confidence;

        public Result(int x, int y, int size, double confidence) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.confidence = confidence;
        }
    }

    private static final int MIN_SIZE = 180;
    private static final int REQUIRED_STABLE_HITS = 2;
    private static final int POSITION_TOLERANCE = 24;
    private static final int SIZE_TOLERANCE = 40;

    private Result candidate;
    private Result stable;
    private int candidateHits;

    public synchronized Result detect(Bitmap bitmap) {
        if (bitmap == null) return stable;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int maxSize = Math.min(width, height) - 8;
        if (width < MIN_SIZE || maxSize < MIN_SIZE) return stable;

        int minSize = Math.max(MIN_SIZE, (int) (Math.min(width, height) * 0.35f));
        Result best = null;
        double bestScore = 0.0;

        for (int size = minSize; size <= maxSize; size += Math.max(12, minSize / 10)) {
            int step = Math.max(16, size / 14);
            for (int y = 4; y + size < height - 4; y += step) {
                for (int x = 4; x + size < width - 4; x += step) {
                    double score = score(bitmap, x, y, size);
                    if (score > bestScore) {
                        bestScore = score;
                        best = new Result(x, y, size, score);
                    }
                }
            }
        }

        if (best == null || bestScore < 0.64) {
            candidate = null;
            candidateHits = 0;
            return stable;
        }

        if (candidate != null && closeTo(candidate, best)) candidateHits++;
        else {
            candidate = best;
            candidateHits = 1;
        }

        if (candidateHits >= REQUIRED_STABLE_HITS) {
            stable = candidate;
            candidateHits = REQUIRED_STABLE_HITS;
        }
        return stable;
    }

    private boolean closeTo(Result a, Result b) {
        return Math.abs(a.x - b.x) <= POSITION_TOLERANCE
                && Math.abs(a.y - b.y) <= POSITION_TOLERANCE
                && Math.abs(a.size - b.size) <= SIZE_TOLERANCE;
    }

    private double score(Bitmap bitmap, int x, int y, int size) {
        float cell = size / 8f;
        double even = 0.0;
        double odd = 0.0;
        double variance = 0.0;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int px = clamp((int) (x + (col + 0.5f) * cell), 0, bitmap.getWidth() - 1);
                int py = clamp((int) (y + (row + 0.5f) * cell), 0, bitmap.getHeight() - 1);
                double value = brightness(bitmap.getPixel(px, py));
                if (((row + col) & 1) == 0) even += value;
                else odd += value;
            }
        }

        double evenMean = even / 32.0;
        double oddMean = odd / 32.0;
        double contrast = Math.abs(evenMean - oddMean);
        if (contrast < 8.0) return 0.0;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int px = clamp((int) (x + (col + 0.5f) * cell), 0, bitmap.getWidth() - 1);
                int py = clamp((int) (y + (row + 0.5f) * cell), 0, bitmap.getHeight() - 1);
                double value = brightness(bitmap.getPixel(px, py));
                double expected = ((row + col) & 1) == 0 ? evenMean : oddMean;
                variance += Math.abs(value - expected);
            }
        }

        double contrastScore = Math.min(1.0, contrast / 55.0);
        double consistencyScore = 1.0 - Math.min(1.0, (variance / 64.0) / 75.0);
        double edgeScore = edgeScore(bitmap, x, y, size);
        return contrastScore * 0.55 + consistencyScore * 0.20 + edgeScore * 0.25;
    }

    private double edgeScore(Bitmap bitmap, int x, int y, int size) {
        float cell = size / 8f;
        double total = 0.0;
        int count = 0;

        for (int i = 1; i < 8; i++) {
            int px = clamp((int) (x + i * cell), 2, bitmap.getWidth() - 3);
            int py = clamp((int) (y + i * cell), 2, bitmap.getHeight() - 3);
            int horizontalY = clamp((int) (y + size * 0.25f), 0, bitmap.getHeight() - 1);
            int verticalX = clamp((int) (x + size * 0.25f), 0, bitmap.getWidth() - 1);
            total += Math.abs(brightness(bitmap.getPixel(px - 2, horizontalY))
                    - brightness(bitmap.getPixel(px + 2, horizontalY)));
            total += Math.abs(brightness(bitmap.getPixel(verticalX, py - 2))
                    - brightness(bitmap.getPixel(verticalX, py + 2)));
            count += 2;
        }

        return Math.min(1.0, (total / Math.max(1, count)) / 35.0);
    }

    private double brightness(int pixel) {
        return (((pixel >> 16) & 255) + ((pixel >> 8) & 255) + (pixel & 255)) / 3.0;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
