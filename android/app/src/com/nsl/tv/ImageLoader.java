package com.nsl.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loader logo saluran: cache memori + unduh async + fallback ikon. */
public final class ImageLoader {

    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String LOGO_UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36";

    private ImageLoader() {}

    public static void load(ImageView iv, String url) {
        if (url == null || url.isEmpty()) {
            showFallback(iv);
            return;
        }
        Bitmap cached = MEM.get(url);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        iv.setImageDrawable(null);
        iv.setTag(url);
        POOL.execute(() -> {
            Bitmap bmp = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", LOGO_UA);
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream is = conn.getInputStream();
                    byte[] raw = readAll(is);
                    is.close();
                    bmp = decode(raw, 640);
                }
            } catch (Exception ignored) {
                bmp = null;
            } finally {
                if (conn != null) conn.disconnect();
            }
            if (bmp != null) MEM.put(url, bmp);
            Bitmap finalBmp = bmp;
            MAIN.post(() -> {
                if (finalBmp != null && url.equals(iv.getTag())) {
                    iv.setImageBitmap(finalBmp);
                } else if (finalBmp == null && url.equals(iv.getTag())) {
                    showFallback(iv);
                }
            });
        });
    }

    private static void showFallback(ImageView iv) {
        iv.setImageResource(R.drawable.ic_tv);
        iv.setImageAlpha(60);
    }

    private static byte[] readAll(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static Bitmap decode(byte[] raw, int maxDim) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
        int sample = 1;
        while (opts.outWidth / (sample * 2) >= maxDim && opts.outHeight / (sample * 2) >= maxDim) sample *= 2;
        opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
    }
}
