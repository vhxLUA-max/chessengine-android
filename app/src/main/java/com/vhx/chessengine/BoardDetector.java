package com.vhx.chessengine;

import android.graphics.Bitmap;

public final class BoardDetector {
    public static final class Result {
        public final int x;
        public final int y;
        public final int size;

        public Result(int x, int y, int size) {
            this.x = x;
            this.y = y;
            this.size = size;
        }
    }

    private static final int MIN_SIZE = 240;
    private static final int MAX_SIZE = 1600;

    public Result detect(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() < MIN_SIZE) {
            return null;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int minSize = Math.max(MIN_SIZE, (int) (width * 0.62f));
        int maxSize = Math.min(
                MAX_SIZE,
                Math.min(width - 8, (int) (width * 0.99f))
        );

        if (minSize > maxSize) {
            return null;
        }

        Result best = null;
        double bestScore = 0.0;

        int centerX = width / 2;
        int xRadius = Math.min(80, Math.max(24, width / 10));

        for (int size = minSize; size <= maxSize; size += 12) {
            int minX = Math.max(4, centerX - size / 2 - xRadius);
            int maxX = Math.min(width - size - 4, centerX - size / 2 + xRadius);

            if (minX > maxX) {
                continue;
            }

            int minY = Math.max(0, Math.min(height - size, height / 10));
            int maxY = Math.min(
                    height - size,
                    Math.max(minY, (int) (height * 0.58f))
            );

            for (int x = minX; x <= maxX; x += 12) {
                for (int y = minY; y <= maxY; y += 16) {
                    double score = scoreBoard(bitmap, x, y, size);

                    if (score > bestScore) {
                        bestScore = score;
                        best = new Result(x, y, size);
                    }
                }
            }
        }

        return bestScore >= 0.56 ? best : null;
    }

    private double scoreBoard(Bitmap bitmap, int x, int y, int size) {
        float cell = size / 8f;

        double lightSum = 0.0;
        double darkSum = 0.0;
        int lightCount = 0;
        int darkCount = 0;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                float cx = x + (col + 0.5f) * cell;
                float cy = y + (row + 0.5f) * cell;

                int p = bitmap.getPixel(
                        Math.min(bitmap.getWidth() - 1, Math.max(0, (int) cx)),
                        Math.min(bitmap.getHeight() - 1, Math.max(0, (int) cy))
                );

                double brightness = (
                        ((p >> 16) & 255)
                                + ((p >> 8) & 255)
                                + (p & 255)
                ) / 3.0;

                if (((row + col) & 1) == 0) {
                    lightSum += brightness;
                    lightCount++;
                } else {
                    darkSum += brightness;
                    darkCount++;
                }
            }
        }

        double lightMean = lightSum / Math.max(1, lightCount);
        double darkMean = darkSum / Math.max(1, darkCount);
        double contrast = Math.abs(lightMean - darkMean);

        if (contrast < 10.0) {
            return 0.0;
        }

        double expectedContrast = Math.min(70.0, contrast) / 70.0;
        double edgeScore = edgeConsistency(bitmap, x, y, size);

        return expectedContrast * 0.72 + edgeScore * 0.28;
    }

    private double edgeConsistency(Bitmap bitmap, int x, int y, int size) {
        float cell = size / 8f;
        double total = 0.0;
        int count = 0;

        for (int i = 1; i < 8; i++) {
            int px = Math.min(bitmap.getWidth() - 1, Math.max(0, (int) (x + i * cell)));
            int py = Math.min(bitmap.getHeight() - 1, Math.max(0, (int) (y + i * cell)));

            int horizontalA = bitmap.getPixel(
                    Math.max(0, px - 2),
                    Math.min(bitmap.getHeight() - 1, Math.max(0, (int) (y + size * 0.25f)))
            );
            int horizontalB = bitmap.getPixel(
                    Math.min(bitmap.getWidth() - 1, px + 2),
                    Math.min(bitmap.getHeight() - 1, Math.max(0, (int) (y + size * 0.25f)))
            );

            int verticalA = bitmap.getPixel(
                    Math.min(bitmap.getWidth() - 1, Math.max(0, (int) (x + size * 0.25f))),
                    Math.max(0, py - 2)
            );
            int verticalB = bitmap.getPixel(
                    Math.min(bitmap.getWidth() - 1, Math.max(0, (int) (x + size * 0.25f))),
                    Math.min(bitmap.getHeight() - 1, py + 2)
            );

            total += colorDistance(horizontalA, horizontalB);
            total += colorDistance(verticalA, verticalB);
            count += 2;
        }

        double average = total / Math.max(1, count);
        return Math.min(1.0, average / 35.0);
    }

    private double colorDistance(int a, int b) {
        int ar = (a >> 16) & 255;
        int ag = (a >> 8) & 255;
        int ab = a & 255;

        int br = (b >> 16) & 255;
        int bg = (b >> 8) & 255;
        int bb = b & 255;

        return (
                Math.abs(ar - br)
                        + Math.abs(ag - bg)
                        + Math.abs(ab - bb)
        ) / 3.0;
    }
}
