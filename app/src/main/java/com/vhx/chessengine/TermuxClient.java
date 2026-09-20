package com.vhx.chessengine;

import android.content.Context;
import android.util.Base64;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TermuxClient {
    private static final ExecutorService IO = Executors.newCachedThreadPool();

    private TermuxClient() {}

    public static void checkHealth(Context context, String baseUrl, String token) {
        IO.execute(() -> {
            try {
                HttpURLConnection c = connection(baseUrl + "/health", token, "GET");
                int code = c.getResponseCode();
                String body = read(c.getInputStream());
                c.disconnect();

                Context app = context.getApplicationContext();
                app.getMainExecutor().execute(() ->
                        Toast.makeText(app, "Termux engine: HTTP " + code + "\n" + body, Toast.LENGTH_LONG).show()
                );
            } catch (Exception e) {
                Context app = context.getApplicationContext();
                app.getMainExecutor().execute(() ->
                        Toast.makeText(app, "Termux connection failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    public static String postJson(String baseUrl, String path, String token, String json) throws Exception {
        HttpURLConnection c = connection(baseUrl + path, token, "POST");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setDoOutput(true);

        try (OutputStream out = c.getOutputStream()) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }

        InputStream stream = c.getResponseCode() >= 400
                ? c.getErrorStream()
                : c.getInputStream();

        String body = read(stream);
        c.disconnect();
        return body;
    }

    private static HttpURLConnection connection(String rawUrl, String token, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(rawUrl).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(2500);
        c.setReadTimeout(15000);
        if (!token.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }
        return c;
    }

    private static String read(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    public static String bitmapToBase64(android.graphics.Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 65, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }
}
