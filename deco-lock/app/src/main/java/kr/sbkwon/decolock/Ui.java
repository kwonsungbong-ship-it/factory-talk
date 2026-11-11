package kr.sbkwon.decolock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Small programmatic UI kit for the settings screens (no XML, no AndroidX). */
public class Ui {
    public static final int BG = 0xFFF3F4F8, CARD = 0xFFFFFFFF, TEXT = 0xFF1C1C28, SUB = 0xFF6B6F80;

    public final Activity a;
    public final LinearLayout root;
    public final ScrollView scroll;
    public final int accent;
    private LinearLayout card;

    public Ui(Activity a) {
        this.a = a;
        accent = Prefs.accent(a);
        Window w = a.getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        scroll = new ScrollView(a);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(32));
        scroll.addView(root);
        a.setContentView(scroll);
    }

    public int dp(float v) { return dp(a, v); }
    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static View ripple(View v, int bg, float r) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), round(bg, r), round(0xFF000000, r)));
        v.setClickable(true);
        return v;
    }

    public TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public void header(String title, String sub) {
        TextView t = text(title, 26, TEXT, true);
        root.addView(t);
        if (sub != null) {
            TextView s = text(sub, 14, SUB, false);
            s.setPadding(0, dp(4), 0, dp(8));
            root.addView(s);
        }
    }

    /** Starts a new rounded card; following rows go into it. */
    public LinearLayout section(String title) {
        if (title != null) {
            TextView t = text(title, 13, accent, true);
            t.setPadding(dp(8), dp(18), 0, dp(6));
            root.addView(t);
        }
        card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(CARD, dp(20)));
        card.setPadding(0, dp(4), 0, dp(4));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private LinearLayout target() { if (card == null) section(null); return card; }

    private LinearLayout rowBase() {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(18), dp(14), dp(18), dp(14));
        r.setMinimumHeight(dp(56));
        return r;
    }

    private LinearLayout labels(String label, String desc) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(label, 16, TEXT, false));
        if (desc != null && !desc.isEmpty()) {
            TextView d = text(desc, 13, SUB, false);
            d.setTag("desc");
            d.setPadding(0, dp(2), 0, 0);
            col.addView(d);
        }
        return col;
    }

    public TextView info(String s) {
        TextView t = text(s, 13, SUB, false);
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        target().addView(t);
        return t;
    }

    public Switch switchRow(String label, String desc, final String key, boolean def, final Runnable onChange) {
        LinearLayout r = rowBase();
        r.addView(labels(label, desc), new LinearLayout.LayoutParams(0, -2, 1));
        final Switch sw = new Switch(a);
        sw.setChecked(Prefs.b(a, key, def));
        sw.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{accent, 0xFFFAFAFA}));
        sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{(accent & 0x00FFFFFF) | 0x66000000, 0x33000000}));
        sw.setOnCheckedChangeListener((b, checked) -> {
            Prefs.put(a, key, checked);
            if (onChange != null) onChange.run();
        });
        r.addView(sw);
        ripple(r, CARD, 0);
        r.setOnClickListener(v -> sw.toggle());
        target().addView(r);
        return sw;
    }

    public void sliderRow(String label, final String key, final int min, final int max, int def, final String unit, final Runnable onChange) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(12), dp(18), dp(8));
        LinearLayout top = new LinearLayout(a);
        top.addView(text(label, 16, TEXT, false), new LinearLayout.LayoutParams(0, -2, 1));
        final TextView val = text("", 14, accent, true);
        top.addView(val);
        col.addView(top);
        SeekBar sb = new SeekBar(a);
        sb.setMax(max - min);
        int cur = Math.max(min, Math.min(max, Prefs.i(a, key, def)));
        sb.setProgress(cur - min);
        val.setText(cur + unit);
        sb.setProgressTintList(ColorStateList.valueOf(accent));
        sb.setThumbTintList(ColorStateList.valueOf(accent));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { val.setText((p + min) + unit); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {
                Prefs.put(a, key, s.getProgress() + min);
                if (onChange != null) onChange.run();
            }
        });
        col.addView(sb);
        target().addView(col);
    }

    public void choiceRow(final String label, final String key, final String[] options, final int def, final Runnable onChange) {
        LinearLayout r = rowBase();
        r.addView(text(label, 16, TEXT, false), new LinearLayout.LayoutParams(0, -2, 1));
        final TextView val = text(options[clamp(Prefs.i(a, key, def), options.length)], 14, accent, true);
        r.addView(val);
        ripple(r, CARD, 0);
        r.setOnClickListener(v -> new AlertDialog.Builder(a)
                .setTitle(label)
                .setSingleChoiceItems(options, clamp(Prefs.i(a, key, def), options.length), (d, which) -> {
                    Prefs.put(a, key, which);
                    val.setText(options[which]);
                    d.dismiss();
                    if (onChange != null) onChange.run();
                }).show());
        target().addView(r);
    }

    private static int clamp(int v, int n) { return v < 0 || v >= n ? 0 : v; }

    public static final int[] SWATCHES = {
            0xFF6C5CE7, 0xFF0984E3, 0xFF00B894, 0xFF00CEC9, 0xFFFDCB6E, 0xFFE17055,
            0xFFD63031, 0xFFE84393, 0xFFA29BFE, 0xFF2D3436, 0xFF55EFC4, 0xFFFF7675
    };

    public void colorRow(String label, final String key, int def, final Runnable onChange) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(12), dp(18), dp(12));
        col.addView(text(label, 16, TEXT, false));
        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(10), 0, 0);
        final int cur = Prefs.i(a, key, def);
        final View[] dots = new View[SWATCHES.length];
        LinearLayout line = null;
        for (int n = 0; n < SWATCHES.length; n++) {
            if (n % 6 == 0) {
                line = new LinearLayout(a);
                grid.addView(line);
            }
            final int color = SWATCHES[n];
            FrameLayout cell = new FrameLayout(a);
            View dot = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(color);
            if (color == cur) g.setStroke(dp(3), TEXT);
            dot.setBackground(g);
            dots[n] = dot;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER);
            cell.addView(dot, lp);
            line.addView(cell, new LinearLayout.LayoutParams(0, dp(46), 1));
            dot.setOnClickListener(v -> {
                Prefs.put(a, key, color);
                for (int m = 0; m < dots.length; m++) {
                    GradientDrawable gg = new GradientDrawable();
                    gg.setShape(GradientDrawable.OVAL);
                    gg.setColor(SWATCHES[m]);
                    if (SWATCHES[m] == color) gg.setStroke(dp(3), TEXT);
                    dots[m].setBackground(gg);
                }
                if (onChange != null) onChange.run();
            });
        }
        col.addView(grid);
        target().addView(col);
    }

    public LinearLayout navRow(String label, String desc, View.OnClickListener l) {
        LinearLayout r = rowBase();
        r.addView(labels(label, desc), new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(text("›", 22, SUB, false));
        ripple(r, CARD, 0);
        r.setOnClickListener(l);
        target().addView(r);
        return r;
    }

    public TextView button(String label, View.OnClickListener l) {
        TextView b = text(label, 16, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        ripple(b, accent, dp(28));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        root.addView(b, lp);
        return b;
    }

    public static ViewGroup.LayoutParams match() { return new ViewGroup.LayoutParams(-1, -1); }
}
