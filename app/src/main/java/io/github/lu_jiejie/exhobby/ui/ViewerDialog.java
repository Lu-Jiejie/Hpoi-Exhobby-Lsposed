package io.github.lu_jiejie.exhobby.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import io.github.lu_jiejie.exhobby.data.ExhobbyApi;
import io.github.lu_jiejie.exhobby.data.ImageLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Fullscreen image viewer. A Dialog rather than an Activity because the module cannot
 * declare activities in the host manifest.
 */
final class ViewerDialog extends Dialog {

    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 6f;
    private static final float DOUBLE_TAP_SCALE = 2.5f;
    private static final float SWIPE_DP = 60f;

    private final List<String> paths;
    private final String pageUrl;
    private int index;

    private final ImageView image;
    private final TextView counter;

    private float scale = MIN_SCALE;
    private float tx, ty;
    private float downX, downY;
    private boolean zoomedAtDown;

    ViewerDialog(Activity act, List<String> paths, int index, String pageUrl) {
        super(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.paths = paths;
        this.index = index;
        this.pageUrl = pageUrl;

        FrameLayout container = new FrameLayout(act);
        container.setBackgroundColor(Color.BLACK);

        image = new ImageView(act);
        image.setScaleType(ImageView.ScaleType.MATRIX);
        container.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        counter = new TextView(act);
        counter.setTextColor(Color.WHITE);
        counter.setTextSize(13);
        FrameLayout.LayoutParams counterLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        counterLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        counterLp.topMargin = dp(28);
        container.addView(counter, counterLp);

        TextView save = new TextView(act);
        save.setText("保存");
        save.setTextColor(Color.WHITE);
        save.setTextSize(13);
        save.setPadding(dp(16), dp(8), dp(16), dp(8));
        FrameLayout.LayoutParams saveLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.gravity = Gravity.END | Gravity.BOTTOM;
        saveLp.rightMargin = dp(16);
        saveLp.bottomMargin = dp(16);
        save.setOnClickListener(v -> saveCurrent());
        container.addView(save, saveLp);

        TextView open = new TextView(act);
        open.setText("在浏览器中打开");
        open.setTextColor(0xFF35CDFF);
        open.setTextSize(13);
        open.setPadding(dp(16), dp(8), dp(16), dp(8));
        FrameLayout.LayoutParams openLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        openLp.gravity = Gravity.START | Gravity.BOTTOM;
        openLp.leftMargin = dp(16);
        openLp.bottomMargin = dp(16);
        open.setOnClickListener(v -> openGalleryPage());
        container.addView(open, openLp);

        setContentView(container);

        final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(act,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector det) {
                        float next = clamp(scale * det.getScaleFactor(), MIN_SCALE, MAX_SCALE);
                        float ratio = next / scale;
                        // Hold the pinch focus still; scale is applied about the view centre.
                        tx = (det.getFocusX() - image.getWidth() / 2f) * (1f - ratio) + tx * ratio;
                        ty = (det.getFocusY() - image.getHeight() / 2f) * (1f - ratio) + ty * ratio;
                        scale = next;
                        if (scale <= MIN_SCALE + 0.0001f) {
                            tx = 0;
                            ty = 0;
                        }
                        clampTranslate();
                        applyMatrix();
                        return true;
                    }
                });

        final GestureDetector tapDetector = new GestureDetector(act,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (scale > DOUBLE_TAP_SCALE / 2f) {
                            scale = MIN_SCALE;
                            tx = 0;
                            ty = 0;
                        } else {
                            float ratio = DOUBLE_TAP_SCALE / scale;
                            // Zoom around the tap point, in viewport coordinates.
                            tx = (image.getWidth() / 2f - e.getX()) * (ratio - 1f) + tx * ratio;
                            ty = (image.getHeight() / 2f - e.getY()) * (ratio - 1f) + ty * ratio;
                            scale = DOUBLE_TAP_SCALE;
                        }
                        clampTranslate();
                        applyMatrix();
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        dismiss();
                        return true;
                    }
                });

        image.setOnTouchListener((v, ev) -> {
            scaleDetector.onTouchEvent(ev);
            tapDetector.onTouchEvent(ev);
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    zoomedAtDown = scale > MIN_SCALE + 0.01f;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (scale > MIN_SCALE + 0.01f && ev.getPointerCount() == 1) {
                        tx += ev.getX() - downX;
                        ty += ev.getY() - downY;
                        downX = ev.getX();
                        downY = ev.getY();
                        applyMatrix();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    float threshold = SWIPE_DP * getContext().getResources().getDisplayMetrics().density;
                    if (!zoomedAtDown && scale <= MIN_SCALE + 0.01f
                            && Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy)) {
                        show(dx < 0 ? index + 1 : index - 1);
                    }
                    break;
                default:
                    break;
            }
            return true;
        });

        Window w = getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        show(index);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private int dp(float v) {
        return (int) (v * getContext().getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
    }

    private void openGalleryPage() {
        if (pageUrl == null || pageUrl.isEmpty()) {
            return;
        }
        try {
            getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Saves the original image. The full-size file is downloaded rather than reusing the
     * decoded bitmap so the phone keeps the untouched original.
     */
    private void saveCurrent() {
        if (paths.isEmpty()) {
            return;
        }
        final String path = paths.get(Math.max(0, Math.min(index, paths.size() - 1)));
        final String name = "exhobby_" + System.currentTimeMillis() + "_" + baseName(path);
        toast("正在保存…");
        new Thread(() -> {
            final String result = save(path, name);
            post(() -> toast(result));
        }, "exhobby-save").start();
    }

    private void post(Runnable r) {
        image.post(r);
    }

    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isEmpty() ? "image.jpg" : name;
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String save(String path, String name) {
        File tmp = null;
        try {
            tmp = new File(getContext().getCacheDir(), "save-" + System.currentTimeMillis() + ".img");
            if (!download(ExhobbyApi.imgUrl(path, "raw"), tmp)) {
                return "保存失败：图片下载出错";
            }
            String mime = mimeOf(name);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, mime);
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ExHobby");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = getContext().getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return "保存失败：无法写入相册";
                }
                try (InputStream in = new java.io.FileInputStream(tmp);
                     OutputStream out = getContext().getContentResolver().openOutputStream(uri)) {
                    if (out == null) {
                        throw new IllegalStateException("no stream");
                    }
                    copy(in, out);
                    out.flush();
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContext().getContentResolver().update(uri, values, null, null);
                return "已保存到相册 Pictures/ExHobby";
            }
            File dest = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "ExHobby");
            if (!dest.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dest.mkdirs();
            }
            File outFile = new File(dest, name);
            try (InputStream in = new java.io.FileInputStream(tmp);
                 OutputStream out = new FileOutputStream(outFile)) {
                copy(in, out);
            }
            getContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile)));
            return "已保存到相册 Pictures/ExHobby";
        } catch (SecurityException se) {
            requestWritePermission();
            return "需要存储权限，允许后请重试";
        } catch (Throwable t) {
            return "保存失败：" + t.getClass().getSimpleName();
        } finally {
            if (tmp != null && tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    private void requestWritePermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Context c = getContext();
                if (c instanceof Activity) {
                    ((Activity) c).requestPermissions(
                            new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 0x5EED);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
    }

    private static boolean download(String url, File dest) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(20000);
            // The CDN answers 403 without a browser-ish User-Agent.
            c.setRequestProperty("User-Agent", ExhobbyApi.UA);
            c.setRequestProperty("Referer", "https://www.exhobby.net/");
            if (c.getResponseCode() != 200) {
                return false;
            }
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
                copy(in, out);
            }
            return dest.length() > 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private float fitScale() {
        int vw = image.getWidth();
        int vh = image.getHeight();
        int dw = image.getDrawable() == null ? 0 : image.getDrawable().getIntrinsicWidth();
        int dh = image.getDrawable() == null ? 0 : image.getDrawable().getIntrinsicHeight();
        if (vw <= 0 || vh <= 0 || dw <= 0 || dh <= 0) {
            return 0f;
        }
        return Math.min((float) vw / dw, (float) vh / dh);
    }

    private void applyMatrix() {
        Matrix m = new Matrix();
        int vw = image.getWidth();
        int vh = image.getHeight();
        int dw = image.getDrawable() == null ? 0 : image.getDrawable().getIntrinsicWidth();
        int dh = image.getDrawable() == null ? 0 : image.getDrawable().getIntrinsicHeight();
        float fit = fitScale();
        if (fit > 0) {
            // MATRIX mode does no fitting, so centre-fit first.
            m.postScale(fit, fit);
            m.postTranslate((vw - dw * fit) / 2f, (vh - dh * fit) / 2f);
        }
        m.postScale(scale, scale, vw / 2f, vh / 2f);
        m.postTranslate(tx, ty);
        image.setImageMatrix(m);
    }

    /** Keeps the scaled image over the viewport instead of letting it drift away. */
    private void clampTranslate() {
        int vw = image.getWidth();
        int vh = image.getHeight();
        int dw = image.getDrawable() == null ? 0 : image.getDrawable().getIntrinsicWidth();
        int dh = image.getDrawable() == null ? 0 : image.getDrawable().getIntrinsicHeight();
        float fit = fitScale();
        if (fit <= 0) {
            return;
        }
        float w = dw * fit * scale;
        float h = dh * fit * scale;
        float maxX = Math.max(0, (w - vw) / 2f);
        float maxY = Math.max(0, (h - vh) / 2f);
        tx = Math.max(-maxX, Math.min(maxX, tx));
        ty = Math.max(-maxY, Math.min(maxY, ty));
    }

    private void show(int target) {
        if (paths.isEmpty()) {
            counter.setText("");
            return;
        }
        index = Math.max(0, Math.min(target, paths.size() - 1));
        scale = MIN_SCALE;
        tx = 0;
        ty = 0;
        applyMatrix();
        counter.setText((index + 1) + " / " + paths.size());

        int w = image.getWidth() > 0 ? image.getWidth() : 1080;
        int h = image.getHeight() > 0 ? image.getHeight() : 1920;

        // Low-res first so the frame fills fast, then swap in the original.
        final String preview = ExhobbyApi.imgUrl(paths.get(index), "n");
        final String full = ExhobbyApi.imgUrl(paths.get(index), "raw");
        image.setImageDrawable(null);
        ImageLoader.load(getContext(), preview, bmp -> {
            if (bmp != null && preview.equals(currentUrl("n"))) {
                image.setImageDrawable(new BitmapDrawable(getContext().getResources(), bmp));
                applyMatrix();
            }
        }, w, h);
        ImageLoader.load(getContext(), full, bmp -> {
            if (bmp != null && full.equals(currentUrl("raw"))) {
                image.setImageDrawable(new BitmapDrawable(getContext().getResources(), bmp));
                applyMatrix();
            }
        }, w, h);
    }

    private String currentUrl(String size) {
        if (paths.isEmpty()) {
            return null;
        }
        return ExhobbyApi.imgUrl(paths.get(Math.max(0, Math.min(index, paths.size() - 1))), size);
    }

    @Override
    public void onBackPressed() {
        if (scale > MIN_SCALE + 0.01f) {
            scale = MIN_SCALE;
            tx = 0;
            ty = 0;
            applyMatrix();
            return;
        }
        super.onBackPressed();
    }
}
