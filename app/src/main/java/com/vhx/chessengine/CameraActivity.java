package com.vhx.chessengine;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Collections;

public class CameraActivity extends Activity {
    private static final int CAMERA_REQUEST = 41;

    private TextureView preview;
    private DetectionOverlay detectionOverlay;
    private TextView status;

    private CameraDevice camera;
    private CameraCaptureSession session;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private final CameraBoardDetector detector = new CameraBoardDetector();

    private final Runnable detectLoop = new Runnable() {
        @Override
        public void run() {
            detectFrame();
            if (cameraHandler != null) {
                cameraHandler.postDelayed(this, 500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        preview = new TextureView(this);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        detectionOverlay = new DetectionOverlay(this);
        root.addView(detectionOverlay, new FrameLayout.LayoutParams(-1, -1));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        status.setBackgroundColor(0x99000000);
        status.setPadding(24, 18, 24, 18);

        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(-1, -2);
        statusParams.topMargin = 24;
        root.addView(status, statusParams);

        setContentView(root);

        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(
                    SurfaceTexture surface,
                    int width,
                    int height
            ) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(
                    @NonNull SurfaceTexture surface,
                    int width,
                    int height
            ) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(
                    @NonNull SurfaceTexture surface
            ) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraThread();

        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_REQUEST
            );
        } else if (preview != null && preview.isAvailable()) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopCameraThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] results
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (requestCode == CAMERA_REQUEST
                && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else if (requestCode == CAMERA_REQUEST) {
            status.setText("Camera permission is required for camera board detection.");
        }
    }

    private void startCameraThread() {
        if (cameraThread != null) {
            return;
        }

        cameraThread = new HandlerThread("CheezieCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopCameraThread() {
        if (cameraHandler != null) {
            cameraHandler.removeCallbacksAndMessages(null);
            cameraHandler = null;
        }

        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
        }
    }

    private void openCamera() {
        if (camera != null
                || preview == null
                || !preview.isAvailable()
                || checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            CameraManager manager =
                    (CameraManager) getSystemService(Context.CAMERA_SERVICE);

            String cameraId = null;

            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics =
                        manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(
                        CameraCharacteristics.LENS_FACING
                );

                if (facing != null
                        && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }

            if (cameraId == null) {
                status.setText("No rear camera found.");
                return;
            }

            status.setText("Camera active. Point it at the chessboard.");

            manager.openCamera(
                    cameraId,
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(CameraDevice device) {
                            camera = device;
                            createPreviewSession();
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice device) {
                            device.close();
                            camera = null;
                        }

                        @Override
                        public void onError(
                                @NonNull CameraDevice device,
                                int error
                        ) {
                            device.close();
                            camera = null;
                            runOnUiThread(() ->
                                    status.setText("Camera error: " + error)
                            );
                        }
                    },
                    cameraHandler
            );
        } catch (Exception e) {
            status.setText("Camera could not be opened.");
        }
    }

    private void createPreviewSession() {
        if (camera == null || !preview.isAvailable()) {
            return;
        }

        try {
            SurfaceTexture texture = preview.getSurfaceTexture();
            texture.setDefaultBufferSize(1280, 720);

            Surface surface = new Surface(texture);

            CaptureRequest.Builder request =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(surface);

            camera.createCaptureSession(
                    Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(
                                CameraCaptureSession configured
                        ) {
                            session = configured;

                            try {
                                request.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                );

                                session.setRepeatingRequest(
                                        request.build(),
                                        null,
                                        cameraHandler
                                );

                                cameraHandler.post(detectLoop);
                            } catch (CameraAccessException e) {
                                runOnUiThread(() ->
                                        status.setText("Camera preview failed.")
                                );
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession configured
                        ) {
                            runOnUiThread(() ->
                                    status.setText("Camera preview could not start.")
                            );
                        }
                    },
                    cameraHandler
            );
        } catch (Exception e) {
            status.setText("Camera preview could not start.");
        }
    }

    private void detectFrame() {
        if (preview == null || !preview.isAvailable()) {
            return;
        }

        try {
            int width = Math.max(320, preview.getWidth() / 2);
            int height = Math.max(240, preview.getHeight() / 2);
            android.graphics.Bitmap frame = preview.getBitmap(width, height);

            if (frame == null) {
                return;
            }

            CameraBoardDetector.Result result = detector.detect(frame);
            frame.recycle();

            if (result == null) {
                runOnUiThread(() -> {
                    detectionOverlay.clear();
                    status.setText("Camera active. Searching for chessboard...");
                });
                return;
            }

            runOnUiThread(() -> {
                detectionOverlay.setResult(
                        result,
                        width,
                        height
                );
                status.setText(
                        "Chessboard detected  •  "
                                + (int) (result.confidence * 100)
                                + "% confidence"
                );
            });
        } catch (Exception ignored) {
        }
    }

    private void closeCamera() {
        if (cameraHandler != null) {
            cameraHandler.removeCallbacks(detectLoop);
        }

        if (session != null) {
            session.close();
            session = null;
        }

        if (camera != null) {
            camera.close();
            camera = null;
        }
    }

    private static final class DetectionOverlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private CameraBoardDetector.Result result;
        private int bitmapWidth;
        private int bitmapHeight;

        DetectionOverlay(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6f);
        }

        void setResult(
                CameraBoardDetector.Result value,
                int width,
                int height
        ) {
            result = value;
            bitmapWidth = width;
            bitmapHeight = height;
            invalidate();
        }

        void clear() {
            result = null;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (result == null || bitmapWidth <= 0 || bitmapHeight <= 0) {
                return;
            }

            float scaleX = getWidth() / (float) bitmapWidth;
            float scaleY = getHeight() / (float) bitmapHeight;

            paint.setColor(0xFF82C75F);
            paint.setStrokeWidth(Math.max(4f, getWidth() / 180f));

            RectF rect = new RectF(
                    result.x * scaleX,
                    result.y * scaleY,
                    (result.x + result.size) * scaleX,
                    (result.y + result.size) * scaleY
            );

            canvas.drawRect(rect, paint);
        }
    }
}
