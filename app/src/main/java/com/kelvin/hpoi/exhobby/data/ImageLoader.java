package com.kelvin.hpoi.exhobby.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small memory + disk bitmap loader for the exhobby CDN. */
public final class ImageLoader {

    public interface Callback {
        void onBitmap(Bitmap bmp);
    }

    private static final LruCache<String, Bitmap> MEM =
            new LruCache<String, Bitmap>(24 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ImageLoader() {
    }

    public static void load(Context ctx, String url, Callback cb, int reqW, int reqH) {
        Bitmap mem = MEM.get(url);
        if (mem != null) {
            cb.onBitmap(mem);
            return;
        }
        final Context app = ctx.getApplicationContext();
        final int w = reqW;
        final int h = reqH;
        POOL.execute(() -> {
            final Bitmap bmp = loadSync(app, url, w, h);
            MAIN.post(() -> cb.onBitmap(bmp));
        });
    }

    public static void loadInto(Context ctx, String url, ImageView iv, int reqW, int reqH) {
        // The tag guards against a recycled view finishing an older request.
        iv.setTag(url);
        Bitmap mem = MEM.get(url);
        if (mem != null) {
            iv.setImageBitmap(mem);
            return;
        }
        iv.setImageDrawable(null);
        load(ctx, url, bmp -> {
            if (bmp != null && url.equals(iv.getTag())) {
                iv.setImageBitmap(bmp);
            }
        }, reqW, reqH);
    }

    private static File dir(Context ctx) {
        File d = new File(ctx.getCacheDir(), "exhobby");
        if (!d.exists()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        return d;
    }

    private static File cacheFile(Context ctx, String url) {
        return new File(dir(ctx), Integer.toHexString(url.hashCode()) + "_" + url.length() + ".img");
    }

    private static Bitmap loadSync(Context ctx, String url, int reqW, int reqH) {
        File f = cacheFile(ctx, url);
        Bitmap b = decode(f.getAbsolutePath(), reqW, reqH);
        if (b != null) {
            MEM.put(url, b);
            return b;
        }
        try {
            byte[] data = download(url);
            if (data == null || data.length == 0) {
                return null;
            }
            try (FileOutputStream fo = new FileOutputStream(f)) {
                fo.write(data);
            }
            b = decode(f.getAbsolutePath(), reqW, reqH);
            if (b != null) {
                MEM.put(url, b);
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap decode(String path, int reqW, int reqH) {
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, probe);
            if (probe.outWidth <= 0 || probe.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            int tw = Math.max(reqW, 1);
            int th = Math.max(reqH, 1);
            while (probe.outWidth / (sample * 2) >= tw && probe.outHeight / (sample * 2) >= th) {
                sample *= 2;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(20000);
            // The CDN answers 403 without a browser-ish User-Agent.
            c.setRequestProperty("User-Agent", ExhobbyApi.UA);
            c.setRequestProperty("Referer", "https://www.exhobby.net/");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
            in.close();
            return bo.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
