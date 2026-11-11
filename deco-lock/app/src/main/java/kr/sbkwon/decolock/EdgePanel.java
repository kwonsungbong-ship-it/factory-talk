package kr.sbkwon.decolock;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Slide-in panel with favourite apps and quick tools. */
public class EdgePanel extends FrameLayout {
    private final boolean fromLeft;
    private final Runnable close;
    private final LinearLayout card;
    private final View dim;

    private static final int[] TOOLS = {Actions.FLASHLIGHT, Actions.SCREENSHOT, Actions.LOCK, Actions.VOLUME,
            Actions.HISTORY, Actions.CAFFEINE, Actions.SPLIT, Actions.QUICK_SETTINGS};
    private static final String[] TOOL_ICONS = {"🔦", "📸", "🔒", "🔊", "🔔", "☕", "◫", "⚙️"};
    private static final String[] TOOL_LABELS = {"손전등", "캡처", "잠금", "볼륨", "알림기록", "꺼짐방지", "분할", "빠른설정"};

    public EdgePanel(final Context c, boolean fromLeft, Runnable close) {
        super(c);
        this.fromLeft = fromLeft;
        this.close = close;
        int accent = Prefs.accent(c);

        dim = new View(c);
        dim.setBackgroundColor(0x66000000);
        dim.setOnClickListener(v -> close.run());
        addView(dim, new LayoutParams(-1, -1));

        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(14), dp(12), dp(14));
        GradientDrawable bg = Ui.round(0xF2FFFFFF, dp(28));
        card.setBackground(bg);
        card.setElevation(dp(12));
        card.setClickable(true);

        TextView title = new TextView(c);
        title.setText("앱");
        title.setTextColor(accent);
        title.setTextSize(12);
        title.setPadding(dp(6), 0, 0, dp(6));
        card.addView(title);

        GridLayout apps = new GridLayout(c);
        apps.setColumnCount(3);
        List<Apps.App> list = Apps.resolve(c, Prefs.list(c, Prefs.PANEL_APPS));
        for (final Apps.App a : list) {
            apps.addView(cell(Apps.shaped(c, a, Prefs.i(c, Prefs.HOME_SHAPE, 1), dp(44)), null, a.label, v -> {
                close.run();
                Apps.launch(c, a);
            }));
        }
        apps.addView(cell(null, "+", "편집", v -> {
            close.run();
            Intent i = new Intent(c, AppPickerActivity.class)
                    .putExtra(AppPickerActivity.EXTRA_KEY, Prefs.PANEL_APPS)
                    .putExtra(AppPickerActivity.EXTRA_TITLE, "엣지 패널 앱 선택")
                    .putExtra(AppPickerActivity.EXTRA_MAX, 15)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        }));
        card.addView(apps);

        View div = new View(c);
        div.setBackgroundColor(0x14000000);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(1));
        dlp.setMargins(dp(6), dp(10), dp(6), dp(10));
        card.addView(div, dlp);

        TextView t2 = new TextView(c);
        t2.setText("도구");
        t2.setTextColor(accent);
        t2.setTextSize(12);
        t2.setPadding(dp(6), 0, 0, dp(6));
        card.addView(t2);

        GridLayout tools = new GridLayout(c);
        tools.setColumnCount(3);
        for (int i = 0; i < TOOLS.length; i++) {
            final int act = TOOLS[i];
            String icon = TOOL_ICONS[i];
            if (act == Actions.FLASHLIGHT && Actions.torchOn()) icon = "💡";
            tools.addView(cell(null, icon, TOOL_LABELS[i], v -> {
                if (act != Actions.FLASHLIGHT && act != Actions.CAFFEINE) close.run();
                Actions.run(c, act);
                if (act == Actions.FLASHLIGHT) {
                    TextView g = v.findViewWithTag("glyph");
                    if (g != null) g.setText(Actions.torchOn() ? "💡" : "🔦");
                }
            }));
        }
        card.addView(tools);

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(card);
        LayoutParams lp = new LayoutParams(dp(236), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | (fromLeft ? Gravity.LEFT : Gravity.RIGHT));
        lp.setMargins(dp(10), dp(40), dp(10), dp(40));
        addView(sv, lp);

        setFocusableInTouchMode(true);
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }

    private View cell(android.graphics.drawable.Drawable icon, String glyph, String label, OnClickListener l) {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(4), dp(6), dp(4), dp(6));
        if (icon != null) {
            ImageView iv = new ImageView(getContext());
            iv.setImageDrawable(icon);
            col.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
        } else {
            TextView g = new TextView(getContext());
            g.setTag("glyph");
            g.setText(glyph);
            g.setTextSize(20);
            g.setGravity(Gravity.CENTER);
            g.setTextColor(Prefs.accent(getContext()));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(0x14000000);
            g.setBackground(circle);
            col.addView(g, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        TextView t = new TextView(getContext());
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(0xFF333333);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(4), 0, 0);
        col.addView(t, new LinearLayout.LayoutParams(dp(64), -2));
        Ui.ripple(col, 0x00FFFFFF, dp(14));
        col.setOnClickListener(l);
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = dp(70);
        col.setLayoutParams(glp);
        return col;
    }

    public void animateIn() {
        float off = dp(260) * (fromLeft ? -1 : 1);
        View sv = getChildAt(1);
        sv.setTranslationX(off);
        sv.animate().translationX(0).setDuration(220).start();
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(220).start();
    }

    public void animateOut(final Runnable done) {
        float off = dp(260) * (fromLeft ? -1 : 1);
        View sv = getChildAt(1);
        sv.animate().translationX(off).setDuration(180).withEndAction(done).start();
        dim.animate().alpha(0f).setDuration(180).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
            close.run();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
