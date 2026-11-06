package com.nsl.tv;

import android.os.Build;
import android.provider.Settings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import android.content.Context;
import android.content.pm.PackageManager;

/** Klien API upstream dengan skema signature SHA-256 per request. */
public final class Api {

    public static final String BASE = "https://dopaminetv.mikrofast.my.id";
    public static final String APP_VERSION = "1.0.7";
    private static final String SECRET = "DopamineTV_SecKey_2026_X9#";
    public static final String UA = "DopamineTV/1.0.7 (Android; ARM) Kodi/21.0";
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 20000;

    private static String deviceModel = "";
    private static String deviceType = "Smartphone";
    private static String deviceAndroid = "";

    private Api() {}

    public static void init(Context ctx) {
        String model = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        deviceModel = model;
        deviceAndroid = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        boolean leanback = ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        String m = model.toLowerCase(Locale.US);
        boolean boxLike = m.contains("tv") || m.contains("box") || m.contains("stb")
                || m.contains("zte") || m.contains("fiberhome");
        deviceType = (leanback || boxLike) ? "TV Box" : "Smartphone";
    }

    public static String deviceModel() { return deviceModel; }
    public static String deviceType() { return deviceType; }
    public static String deviceAndroid() { return deviceAndroid; }

    public static String sha256Hex(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String signature(String deviceId, long ts) {
        return sha256Hex(deviceId + ":" + ts + ":" + SECRET);
    }

    /** Exception dengan kode HTTP + body respons. */
    public static class HttpException extends IOException {
        public final int code;
        public final String body;
        HttpException(int code, String body) {
            super("HTTP " + code + (body != null ? ": " + body : ""));
            this.code = code;
            this.body = body;
        }
    }

    /** GET dengan header lengkap + signature. */
    public static String get(String path, String deviceId) throws IOException {
        return request(BASE + path, "GET", deviceId, null, true);
    }

    /** POST body JSON + signature. */
    public static String post(String path, String deviceId, String json) throws IOException {
        return request(BASE + path, "POST", deviceId, json, true);
    }

    private static String request(String urlStr, String method, String deviceId,
                                  String json, boolean signed) throws IOException {
        HttpURLConnection conn = null;
        try {
            long ts = System.currentTimeMillis();
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod(method);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("X-Device-Id", deviceId);
            conn.setRequestProperty("X-Device-Model", deviceModel);
            conn.setRequestProperty("X-Device-Type", deviceType);
            conn.setRequestProperty("X-Device-Android", deviceAndroid);
            conn.setRequestProperty("X-App-Version", APP_VERSION);
            conn.setRequestProperty("X-App-Timestamp", String.valueOf(ts));
            conn.setRequestProperty("X-App-Capabilities", "");
            conn.setRequestProperty("Accept", "*/*");
            if (signed) {
                conn.setRequestProperty("X-App-Signature", signature(deviceId, ts));
            }
            if (json != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                OutputStream os = conn.getOutputStream();
                os.write(bytes);
                os.flush();
                os.close();
            }
            int code = conn.getResponseCode();
            String body = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 400) throw new HttpException(code, body);
            return body;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            try { is.close(); } catch (IOException ignored) {}
        }
    }
}
