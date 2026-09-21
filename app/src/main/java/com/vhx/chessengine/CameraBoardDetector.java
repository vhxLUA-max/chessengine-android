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

    public Result detect(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int maxSize = Math.min(width, height) - 8;

        if (width < MIN_SIZE || maxSize < MIN_SIZE) {
            return null;
        }

        int minSize = Math.max(MIN_SIZE, (int) (Math.min(width, height) * 0.35f));
        Result best = null;
        double bestScore = 0.0;

        for (int size = minSize; size <= maxSize; size += Math.max(8, minSize / 12)) {
            int step = Math.max(12, size / 18);
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

        return bestScore >= 0.60 ? best : null;
    }

    private double score(Bitmap bitmap, int x, int y, int size) {
        float cell = size / 8f;
        double even = 0.0;
        double odd = 0.0;
        int evenCount = 0;
        int oddCount = 0;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int px = clamp((int) (x + (col + 0.5f) * cell), 0, bitmap.getWidth() - 1);
                int py = clamp((int) (y + (row + 0.5f) * cell), 0, bitmap.getHeight() - 1);

                double brightness = brightness(bitmap.getPixel(px, py));

                if (((row + col) & 1) == 0) {
                    even += brightness;
                    evenCount++;
                } else {
                    odd += brightness;
                    oddCount++;
                }
            }
        }

        double evenMean = even / Math.max(1, evenCount);
        double oddMean = odd / Math.max(1, oddCount);
        double contrast = Math.abs(evenMean - oddMean);

        if (contrast < 8.0) {
            return 0.0;
        }

        double contrastScore = Math.min(1.0, contrast / 55.0);
        double edgeScore = edgeScore(bitmap, x, y, size);

        return contrastScore * 0.70 + edgeScore * 0.30;
    }

    private double edgeScore(Bitmap bitmap, int x, int y, int size) {
        float cell = size / 8f;
        double total = 0.0;
        int count = 0;

        for (int i = 1; i < 8; i++) {
            int px = clamp((int) (x + i * cell), 1, bitmap.getWidth() - 2);
            int py = clamp((int) (y + i * cell), 1, bitmap.getHeight() - 2);

            total += Math.abs(
                    brightness(bitmap.getPixel(px - 2, clamp((int) (y + size * 0.25f), 0, bitmap.getHeight() - 1)))
                            - brightness(bitmap.getPixel(px + 2, clamp((int) (y + size * 0.25f), 0, bitmap.getHeight() - 1)))
            );

            total += Math.abs(
                    brightness(bitmap.getPixel(clamp((int) (x + size * 0.25f), 0, bitmap.getWidth() - 1), py - 2))
                            - brightness(bitmap.getPixel(clamp((int) (x + size * 0.25f), 0, bitmap.getWidth() - 1), py + 2))
            );

            count += 2;
        }

        return Math.min(1.0, (total / Math.max(1, count)) / 35.0);
    }

    private double brightness(int pixel) {
        return (
                ((pixel >> 16) & 255)
                        + ((pixel >> 8) & 255)
                        + (pixel & 255)
        ) / 3.0;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
