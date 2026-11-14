package com.kelvin.hpoi.exhobby.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kelvin.hpoi.exhobby.data.ExhobbyApi;
import com.kelvin.hpoi.exhobby.data.ImageLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Header card injected below the information card of a hobby page. Built in code so the
 * module does not need the host's resources; colours and spacing follow Hpoi's own grid.
 */
public final class ExhobbyCard {

    private static final int THUMB_DP = 104;
    private static final int THUMB_GAP_DP = 8;
    private static final float THUMB_RADIUS_DP = 7.5f;

    private static final int ACCENT = 0xFF35CDFF;

    private final Activity act;
    private final View root;
    private final LinearLayout thumbRow;
    private final HorizontalScrollView scroller;
    private final TextView hint;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String> paths = new ArrayList<>();
    private String pageUrl;
    private int loadedId;
    /** ViewBinding proxy handed to the host list, created once per card. */
    private Object binding;

    public ExhobbyCard(Activity act) {
        this.act = act;
        final float d = act.getResources().getDisplayMetrics().density;
        final boolean night = isNight();

        LinearLayout outer = new LinearLayout(act);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(d, 12), dp(d, 20), dp(d, 12), 0);
        outer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.setVisibility(View.GONE);

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(act);
        title.setText("ExHobby 实拍图");
        title.setTextSize(16);
        title.setTextColor(night ? 0xFFF2F2F2 : 0xFF333333);
        title.getPaint().setFakeBoldText(true);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        hint = new TextView(act);
        hint.setTextSize(11);
        hint.setTextColor(ACCENT);
        hint.setText("在浏览器中打开 ›");
        hint.setOnClickListener(v -> openGalleryPage());
        head.addView(hint);

        outer.addView(head);

        scroller = new HorizontalScrollView(act);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(0, dp(d, 10), 0, 0);
        scroller.setVisibility(View.GONE);

        thumbRow = new LinearLayout(act);
        thumbRow.setOrientation(LinearLayout.HORIZONTAL);
        scroller.addView(thumbRow);
        outer.addView(scroller);

        View divider = new View(act);
        divider.setBackgroundColor(night ? 0xFF2A2A2A : 0xFFEEEEEE);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerLp.topMargin = dp(d, 20);
        outer.addView(divider, dividerLp);

        root = outer;
    }

    private static int dp(float density, float value) {
        return (int) (value * density);
    }

    public View getView() {
        return root;
    }

    public Object getBinding() {
        return binding;
    }

    public void setBinding(Object binding) {
        this.binding = binding;
    }

    private boolean isNight() {
        return (act.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Fetches once per hobby id; repeat calls with the same id are no-ops. */
    public void load(final int itemId, final String itemType) {
        if (itemId <= 0) {
            return;
        }
        synchronized (this) {
            if (itemId == loadedId) {
                return;
            }
            loadedId = itemId;
        }
        // The browser entry point is a plain redirect, so offer it before the API answers.
        pageUrl = ExhobbyApi.browseUrl(itemId, itemType);
        renderShell();

        new Thread(() -> {
            final ExhobbyApi.Gallery gallery = ExhobbyApi.load(itemId, itemType, null);
            main.post(() -> apply(gallery));
        }, "exhobby-fetch").start();
    }

    /** Header plus browser link only; shown while the gallery is still loading. */
    private void renderShell() {
        root.setVisibility(View.VISIBLE);
    }

    private void apply(ExhobbyApi.Gallery gallery) {
        if (gallery == null) {
            return;
        }
        if (gallery.pageUrl != null && !gallery.pageUrl.isEmpty()) {
            pageUrl = gallery.pageUrl;
        }
        if (!gallery.paths.isEmpty()) {
            paths.clear();
            paths.addAll(gallery.paths);
        }
        if (paths.isEmpty()) {
            if (gallery.error != null) {
                // Network hiccup: allow a later callback to retry.
                synchronized (this) {
                    loadedId = 0;
                }
            } else {
                // Nothing on ExHobby for this item: leave the page untouched.
                root.setVisibility(View.GONE);
            }
            return;
        }
        final float d = act.getResources().getDisplayMetrics().density;
        final int size = dp(d, THUMB_DP);
        final boolean night = isNight();

        while (thumbRow.getChildCount() < paths.size()) {
            final int index = thumbRow.getChildCount();
            ImageView iv = new ImageView(act);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(night ? 0xFF222222 : 0xFFF2F2F2);
            bg.setCornerRadius(THUMB_RADIUS_DP * d);
            iv.setBackground(bg);
            iv.setClipToOutline(true);
            thumbRow.addView(iv, thumbLp(d, size));
            ImageLoader.loadInto(act, ExhobbyApi.imgUrl(paths.get(index), "s"), iv, size, size);
            iv.setOnClickListener(v -> open(index));
        }

        scroller.setVisibility(View.VISIBLE);
        root.setVisibility(View.VISIBLE);
    }

    private LinearLayout.LayoutParams thumbLp(float d, int size) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.rightMargin = dp(d, THUMB_GAP_DP);
        return lp;
    }

    private void open(int index) {
        try {
            new ViewerDialog(act, Collections.unmodifiableList(new ArrayList<>(paths)), index,
                    pageUrl).show();
        } catch (Throwable ignored) {
            // Never let a viewer problem take the host page down.
        }
    }

    private void openGalleryPage() {
        if (pageUrl == null || pageUrl.isEmpty()) {
            return;
        }
        try {
            act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)));
        } catch (Throwable ignored) {
        }
    }
}
