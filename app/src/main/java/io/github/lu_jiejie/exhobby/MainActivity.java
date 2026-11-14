package io.github.lu_jiejie.exhobby;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Info screen: the module has no settings, only usage notes. */
public class MainActivity extends Activity {

    private static final String EXHBOBBY_SITE = "https://www.exhobby.net/";

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        final float d = getResources().getDisplayMetrics().density;
        final int pad = (int) (20 * d);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad, pad, pad);

        column.addView(bold("Hpoi ExHobby  " + versionName(), 18));
        column.addView(body("在手办详情页展示 ExHobby 上的实拍图。", 14, 10 * d));

        column.addView(bold("使用", 15));
        column.addView(body("1. 在 LSPosed 中启用本模块\n"
                + "2. 作用域勾选 Hpoi（net.hpoi）\n"
                + "3. 强制停止 Hpoi 后重新打开\n"
                + "4. 打开任意手办详情页", 14, 10 * d));

        column.addView(bold("效果", 15));
        column.addView(body("信息卡下方会出现「ExHobby 实拍图」一栏，按手办 ID 自动拉取图片。"
                + "点击缩略图可全屏查看原图：双指缩放、双击放大、左右滑动切换、单击退出。"
                + "该手办在 ExHobby 上没有图片时，这一栏不会显示。", 14, 10 * d));

        column.addView(bold("说明", 15));
        column.addView(body("本模块只读，不修改 Hpoi 的数据，也不会上传任何信息。"
                + "图片来源为 ExHobby，内容由该站提供。", 14, 10 * d));

        TextView link = new TextView(this);
        SpannableString text = new SpannableString("打开 ExHobby 网站");
        text.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(EXHBOBBY_SITE)));
                } catch (ActivityNotFoundException ignored) {
                    // No browser installed.
                }
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                ds.setColor(0xFF35CDFF);
                ds.setUnderlineText(false);
            }
        }, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        link.setText(text);
        link.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        link.setMovementMethod(LinkMovementMethod.getInstance());
        link.setPadding(0, (int) (10 * d), 0, 0);
        column.addView(link);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.addView(column);
        setContentView(scroll);
    }

    private TextView bold(String text, float sizeSp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(0xFF333333);
        tv.getPaint().setFakeBoldText(true);
        int top = (int) (20 * getResources().getDisplayMetrics().density);
        tv.setPadding(0, top, 0, 0);
        return tv;
    }

    private TextView body(String text, float sizeSp, float topDp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(0xFF666666);
        tv.setLineSpacing(0, 1.35f);
        tv.setPadding(0, (int) (topDp * getResources().getDisplayMetrics().density), 0, 0);
        return tv;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}
